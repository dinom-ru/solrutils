package dinom.solr;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.util.NamedList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dinom.solr.op.Convert;
import dinom.solr.op.FilterQuery;

public final class Util {
	private static DateTimeFormatter US_TIME = DateTimeFormatter.ofPattern("hh:mm[:ss] a");
	private static Logger LOG = LoggerFactory.getLogger(Util.class);
	public static TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone("America/New_York");
	private static Pattern ILLEGAL_CHARS_REGEX = Pattern.compile("[\\\"\\.%/\\\\:*?\\[\\]|\\n\\t\\r ]|[\\x7f-\\uffff]");

	/**
	 * Parse text representation of time. Time value can be presented in one of
	 * following formats: 'HH:mm' or 'HH:mm:ss' or 'HH:mm:ss.SSS'. Milliseconds
	 * separator can also be ',' character, milliseconds value allowed to be 1
	 * or 2 digits.
	 * 
	 * @param tm
	 *            String, time value.
	 * 
	 * @return a time as number of milliseconds or '-1' if 'tm' is not in proper
	 *         format.
	 */
	public final static int parseTime(CharSequence tm) {
		int val = 0, max = tm.length(), v;

		if (max < 5 || tm.charAt(2) != ':') {
			return -1;
		}
		v = tm.charAt(0) - '0';
		if (v < 0 || v > 9) { return -1; } else { val += v * 36000000; }
		v = tm.charAt(1) - '0';
		if (v < 0 || v > 9) { return -1; } else { val += v * 3600000; }
		// :
		v = tm.charAt(3) - '0';
		if (v < 0 || v > 9) { return -1; } else	{ val += v * 600000; }
		v = tm.charAt(4) - '0';
		if (v < 0 || v > 9) { return -1; } else { val += v * 60000; }

		if (max < 8 || tm.charAt(5) != ':') {
			return val;
		}
		v = tm.charAt(6) - '0';
		if (v < 0 || v > 9) { return -1; } else { val += v * 10000; }
		v = tm.charAt(7) - '0';
		if (v < 0 || v > 9) { return -1;  } else { val += v * 1000; }

		int ms = 0;
		if (max > 9) { // parse milliseconds
			v = tm.charAt(8);
			if (v == '.' || v == ',') {
				
				v = tm.charAt(9) - '0';
				if (v >= 0 && v < 10) {
					ms = ms * 10 + v;
					
					if (max > 10) {
						v = tm.charAt(10) - '0';
						
						if (v >= 0 && v < 10) {
							ms = ms * 10 + v;
							
							if (max > 11) {
								v = tm.charAt(11) - '0';
								if (v >= 0 && v < 10) {
									ms = ms * 10 + v;
								}
							}
						}
					}
				}
			}
		}
		return val + ms;
	}

	/**
	 * Parses date string in format "yyyy-MM-dd". If source has more characters than required, the
	 * rest is ignored. 
	 * 
	 * @param s String, date in "yyyy-MM-dd" format.
	 * 
	 * @return a Calendar instance with specified date set or <code>null</code> in case of invalid format.
	 * Other fields are set to 0. Time zone is UTC.
	 */
	public final static Calendar parseDate(CharSequence s) {
		int max = s.length(), yr, mm, dd, v;
		 
		if(max < 10 || s.charAt(4) != '-' || s.charAt(7) != '-') {
			if(max == 4) {
				s = s.toString() + "-01-01";
				max = 10;
			}
			else if( max == 7 && s.charAt(4) == '-') {
				s = s.toString() + "-01";
				max = 10;
			}
			else return null;
		}
		Calendar c = Calendar.getInstance();
		c.setTimeZone(TimeZone.getTimeZone("GMT"));
		// year
		v = s.charAt(0)-'0';
		if(v <0 || v>9) return null; else yr = 1000*v;
		v = s.charAt(1)-'0';
		if(v <0 || v>9) return null; else yr += 100*v;
		v = s.charAt(2)-'0';
		if(v <0 || v>9) return null; else yr += 10*v;
		v = s.charAt(3)-'0';
		if(v <0 || v>9) return null; else yr += + v;
		// month
		v = s.charAt(5)-'0';
		if(v <0 || v>9) return null; else mm = 10*v;
		v = s.charAt(6)-'0';
		if(v <0 || v>9) return null; else mm += v;
		// day of month
		v = s.charAt(8)-'0';
		if(v <0 || v>9) return null; else dd = 10*v;
		v = s.charAt(9)-'0';
		if(v <0 || v>9) return null; else dd += v;
		
		c.clear();
		c.set(yr, mm-1, dd);
		return c;
	}

	/**
	 * Parses date and time from full date/time format "yyyy-MM-dd HH:mm:ss.SSSZ".
	 * It is considered to be legal if time zone is separated by space character. Following
	 * time zone formats considered to be valid: -08, -0800, -08:00, EST, EAT etc. 
	 * If time zone is recognized then UTC is used. Milliseconds, seconds or time itself can be dropped.
	 * 
	 * <p>If time zone is absent parsed time is in JVM default time zone. Calendar instance time zone
	 * is set to parsed string time zone. </p>
	 * 
	 * @param s String, date time in full format.
	 * 
	 * @return Calendar instance with proper fields set.
	 */
	public final static Calendar parseDateTime(String s) {
		// e.g. 2014-04-09T08:00:00.000-04:00
		int max = s.length(), ms;
		
		Calendar c = parseDate(s);
		if(c == null) {
			return null;
		}
		if(max < 16 || (s.charAt(10) != ' ' && s.charAt(10)!= 'T')) {
			return c;
		}
		ms = parseTime(s.substring(11, max));
		
		if(ms != -1) {
			c.add(Calendar.MILLISECOND, ms);
			
			//now check if time zone is specified, allow .SSS being dropped (it is why 19)
			if(max > 19) {
				int v = 0, i = 19;
				while(i < max) {
					v=s.charAt(i);
					if(v == '-' || v == '+') {
						break;
					}
					v |= 32;
					if(v >= 'a' && v <= 'z') {
						break;
					}
					++i;
				}
				if( i < max) {
					ms = 0;			
					if(v == '-' || v == '+') {
						int tzPos = i;
						if(++i < max) {
							v = s.charAt(i)-'0';
							if(v <0 || v>9) return c; else ms += v * 36000000;
						}
						if(++i < max) {
							v = s.charAt(i)-'0';
							if(v <0 || v>9) return c; else ms += v * 3600000;
						}
						if(++i < max) {
							v = s.charAt(i);
							if(v == ':') v = ++i < max ? s.charAt(i)-'0': -1; 
							else v -= '0';
							if(v <0 || v>9) return c; else ms += v * 600000;
						}
						if(++i < max) {
							v = s.charAt(i)-'0';
							if(v <0 || v>9) return c; else ms += v * 60000;
						}
						if(s.charAt(tzPos) == '-') {
							ms = 0-ms;
						}
						c.add(Calendar.MILLISECOND, 0-ms);
						c.setTimeZone(TimeZone.getTimeZone(
								"GMT"+s.substring(tzPos, i<max? i+1: max)));
					}
					else {
						// It can be named time zone such EST or PST or GMT
						String tz = s.substring(i).trim();
						TimeZone timezone = TimeZone.getTimeZone(tz); // UTC/GMT if not recognized
						c.add(Calendar.MILLISECOND, 
								0-timezone.getOffset(c.getTimeInMillis()));
						c.setTimeZone(timezone);
					}
				}
				else {
					//if time zone is not specified, parsed time is for default time zone
					TimeZone timezone = DEFAULT_TIMEZONE;
					c.add(Calendar.MILLISECOND, 
							0-timezone.getOffset(c.getTimeInMillis()));
					c.setTimeZone(timezone);
				}
			}
		}
		return c;
	}

	public static int indexOf(String src, int ch, int i) {
		boolean singleQuote = false, doubleQuote = false;
		
		for(int max = src.length(); i < max; ++i) {
			char c = src.charAt(i);
			if(c == ch && (!singleQuote) && (!doubleQuote)) return i;
			if(c == '\'') {
				if(!doubleQuote) singleQuote = !singleQuote;
			}
			else if( c == '"') {
				if(!singleQuote) doubleQuote = !doubleQuote;
			}
		}
		return -1;
	}

	/**
	 * Read parameter parameters from tag attributes.
	 * 
	 * @param el - Element to get attributes from.
	 * @param parent - parameters of parent element (optional).
	 * @param filter - marks what attributes should have special treatment (required).
	 * 
	 * @return named list of parameters. Can be null if there are no parameters.
	 */
	public static NamedList<String> readAttributes(Element el, NamedList<String> parent, Map<String,String> filter) {
		NamedList<String> params = null;
		NamedNodeMap attrs = el.getAttributes();
		for(int i=0; i < attrs.getLength(); ++i) {
			Node n = attrs.item(i);
			
			String name = n.getNodeName();
			String val = n.getTextContent();
			
			String prefix = filter.get(name);
			if(prefix != null) {
				if(prefix.isEmpty()) continue; // allows to specify attributes that should be ignored
				name = prefix + name; 
			}
			if(params == null) {
				if(parent != null) {
					// copy all from parent list
					params = new NamedList<>(parent.size());
					params.addAll(parent);
				}
				else params = new NamedList<>();
			}
			params.add(name, val);
		}
		return  params == null ? parent : params;
		
	}

	/**
	 * Change format of date from 'weak' full date to "yyyy-MM-dd'T'hh:mm:ss'Z'"
	 *
	 * @param dateString
	 * @return date as String in SOLR format, predefined constant or 
	 * <code>yyyy-MM-dd'T'HH:mm:ss'Z'</code> pattern.
	 */
	public static String toSolrDate(String dateString) {
		dateString = dateString.trim();
	
	    if (dateString.isEmpty() || dateString.equals("*") || dateString.contains("NOW")) {
	        return dateString;
	    }
	    if(dateString.endsWith("Z") && (dateString.length() == 24 || dateString.length()==20)) {
	    	return dateString;
	    }
	    
	    Calendar c = parseDateTime(dateString);
	    if(c == null) {
	        return null;
	    }
	    return DateTimeFormatter.ISO_INSTANT.format(c.toInstant());
	}
	public static String toSolrDate(String dateString, String timeString, String timezone) {
		
		Calendar c = parseDate(dateString);
		if(c == null) {
	        return null;
	    }
		
		if(timeString != null) {
			
			timeString = timeString.trim();
			if(!timeString.isEmpty()) {
				LocalTime tm = null;
				try {
					tm = LocalTime.parse(timeString, DateTimeFormatter.ISO_TIME);
				}
				catch(DateTimeParseException ex) {
					try {
						tm = LocalTime.parse(timeString, US_TIME);
					}
					catch (DateTimeParseException ex2) {
						LOG.error("Invalid time format: {}",timeString);
					}
				}
				if(tm != null) {
					c.add(Calendar.SECOND, tm.toSecondOfDay());
				}
			}

		}
		
		TimeZone tz = DEFAULT_TIMEZONE;
		
		if(timezone != null) {
			timezone = timezone.trim();
			if(!timezone.isEmpty()) {
				if(timezone.startsWith("+") || timezone.startsWith("-")) {
					timezone = "GMT"+timezone;
				}
				tz = TimeZone.getTimeZone(timezone);
			}
		}

		c.add(Calendar.MILLISECOND, 0-tz.getOffset(c.getTimeInMillis()));
		c.setTimeZone(tz);
		
	    return DateTimeFormatter.ISO_INSTANT.format(c.toInstant());
	}
	
	public static String html2text(String html) {
		return new HtmlReader().extractText(html);
	}

	public static final String combine(String template, Object value, String op, boolean escape, boolean quotes) {
		if(value == null) return template;
		
		StringBuilder buf = null;
	
		Map<?,?> vals = value instanceof Map ? (Map<?,?>) value : null;
		
		int p = 0;
		for(;;) {
			int e = template.indexOf("${", p);
			if(e == -1) break;
			
			int ee = template.indexOf('}', e);
			if(ee == -1) break;
	
			if(p == 0) buf = new StringBuilder();
			buf.append(template,p,e);
			p = ee + 1;
	
			if(template.startsWith("${value",e)) {
				// it is inclusion like ${value}. We should include 
				// quoted value or if it is a list, join them with " OR ".
				boolean wildcard = p < template.length() && template.charAt(p) == '*';
				if(wildcard) p++;
	
				// should we check value for special SOLR characters?
				if(value instanceof List) {
					List<?> lst = (List<?>)value;
					for(int i=0; i < lst.size(); ++i) {
						if( i > 0 ) buf.append(" ").append(op).append(" ");
						Object val = lst.get(i);
	
						if(quotes) buf.append('"');
						String sval = val == null ? "" : val.toString();
						if(escape) sval = ClientUtils.escapeQueryChars(sval);
						buf.append(sval);
						if(quotes) buf.append('"');
						if(wildcard) buf.append('*');
					}
				}
				else {
					if(vals != null) {
						value = vals.get("value");
					}
					if(quotes) buf.append('"');
					buf.append(escape ? ClientUtils.escapeQueryChars(value.toString()) : value.toString());
					if(quotes) buf.append('"');
					if(wildcard) buf.append('*');
				}
			}
			else {
				// it is inclusion like ${from:SolrDate}. Passed value should
				// be a map where 'from' is expected. before inserting the value
				// we should check provided value and convert to SolrDate if required.
				
				String var = template.substring(e+2,ee), type = null;
				int idx = var.indexOf(':');
				if(idx != -1) {
					type = var.substring(idx+1);
					var = var.substring(0, idx);
				}
				Object val = vals == null 
						? ("value".equals(var) ? value : null) 
						: vals.get(var);
				
				if(val == null) {
					FilterQuery.LOG.error("'{}' is not found in {}",var, value);
					return null;
				}
				if(type != null) {
					val = Convert.toSolrParam(val, type);
					if(val == null) {
						FilterQuery.LOG.error("failed to convert {} to type {}",val, type);
						return null;
					}
				}
				buf.append(val);
			}
		}
		return p == 0 
				? template
				: buf.append(template,p,template.length()).toString();
	}
	public static String normalize(String text, int types) {
		if((types & 1) != 0) {
			text = text.trim().replaceAll("\r\n?", "\n").replaceAll("\n +","\n").replaceAll(" +\n", "\n");
		}
		return text;
	}
	public static final String trim(String text) {
		return text == null ? "" : text.trim();
	}
	
	public static NodeList split(String s, String delimeter) {		
		NodeSet resultSet = new NodeSet();
		
		if(s == null || s.isEmpty() || delimeter == null || delimeter.isEmpty()) {
			return resultSet;
		}
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			for(String token : s.split(delimeter)) {
				Element element = doc.createElement("token");
				Text text = doc.createTextNode(token);
				element.appendChild(text);
				resultSet.add(element);
			}
		} 
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return resultSet;
	}
	

	/**
	 * Create a valid jcr node name out of an arbitrary string.
	 * <p>
	 * Converts the input to lower case and replaces restricted characters with hyphen.
	 * Mimics the code to create tagId from tag title in the AEM Tagging UI,
	 * see /libs/cq/tagging/gui/components/admin/clientlibs/actions.js
	 *</p>
	 * @param title user-supplied string, e.g. John Dow
	 * @return valid jcr name, e.g. john-dow
	 */
	public static String createValidName(String title) {
		return ILLEGAL_CHARS_REGEX.matcher(title.toLowerCase()).replaceAll("-");
	}

	/**
	 * Convert category from RSS feed into a tag path.
	 *
	 * @param category	category from rss feed, e.g. <code>Research and Development: R&D Portfolio Management</code>
	 * @param sep	separator to designate parent/child relationships, e.g. <code>': '</code>
	 * @return tag name, for the example above it will return <code>research-and-development/r&d-portfolio-management</code>
	 */
	public static String toTagId(String category, String sep, String join) {
		return Arrays.stream(category.split(sep))
				.map(p -> createValidName(p)).collect(Collectors.joining(join));
	}

	/**
	 * Convert category from RSS feed into a tag path .
	 * The default separator of tag parts is ': ' (colon followed by a space)
	 *
	 * @param category	string read from rss, e.g. <code>Research and Development: R&D Portfolio Management</code>
	 * @return tagId, for the example above it will return <code>research-and-development/r&d-portfolio-management</code>
	 */
	public static String toTagId(String category) {
		return toTagId(category, ": ", "/");
	}
	
	public static class NodeSet extends ArrayList<Node> implements NodeList {
		private static final long serialVersionUID = 1L;

		@Override
		public Node item(int index) {
			return get(index);
		}

		@Override
		public int getLength() {
			return size();
		}
		
	}
}
