package dinom.solr;

import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Conversion {
	private static Logger LOG = LoggerFactory.getLogger(Conversion.class);
	
	public Conversion next;
	
	public abstract Object apply(Object value);
	
	
	public void setup(Map<String,String> props) {
		// does nothing by default
		
	}
	
	public static Conversion NONE = new Conversion() {
		@Override
		public Object apply(Object value) { return value; }
		
	};
	
	public static Conversion create(String name) {
		
		switch(name) {
		case "int": return new ToInt();
		case "string":return new ToString();
		case "default":return new Default();
		case "uppercase": return new Uppercase(); 
		case "lowercase": return new Lowercase();	
		case "escape": return new Escape();
		case "quotes": return new Quotes();
		case "max": return new MaxLen();
		
		case "join": return new Join();
		case "replace": return new Replace();
		case "match": return new Match();
		case "substr": return new Substr();
	
		case "utc": return new UTCString();
		case "SolrDate": return new SolrDate();
		
		case "none": return NONE;
		

		default:
			throw new IllegalArgumentException("Unknown conversion name: "+name);
		}
	}
	public static Conversion parse(String text) {
		int p = 0, i= 0, max = text.length();
		 
		Conversion root = null, last = null, tmp;
		String name;
		while(i < max) {
			switch(text.charAt(i++)) {
			case '(':
				tmp = Conversion.create(text.substring(p, i-1).trim());
				if(root == null) root = last = tmp;
				else last = last.next = tmp;
				i = Util.indexOf(text, ')', p=i);
				if(i == -1) {
					throw new IllegalArgumentException("Unmatched '(' in: "+text);
				}
				last.setup( parseArgs(text.substring(p,i) ));
				p = ++i;
				break;
				
			case ',':
				name = text.substring(p,i-1).trim();
				if(!name.isEmpty()) {
					tmp = Conversion.create(name);
					if(root == null) root = last = tmp;
					else last = last.next = tmp;
				}
				p=i;
				break;
			}			
		}
		name = text.substring(p).trim();
		if(!name.isEmpty()) {
			tmp = Conversion.create(name);
			if(root == null) root = last = tmp;
			else last = last.next = tmp;
		}
		
		return root;
	}
	
	private static Map<String,String> parseArgs(String text) {
		Map<String,String> props = new LinkedHashMap<>();
		
		int p = 0, i= 0, max = text.length();
		boolean singleQuote = false, doubleQuote = false;
		String name=null, value=null;
		while(i < max) {
			char c = text.charAt(i++);
			switch(c) {
			case ',': 
				if(singleQuote || doubleQuote) {
					continue;
				}
				if(name != null) {
					value = text.substring(p, i-1).trim();
					props.put(name, value.isEmpty() ? "true" : value);
					name = null;
				}
				p = i;
				break;
				
			case '=':
				if(singleQuote || doubleQuote) {
					continue;
				}
				name = text.substring(p, i-1).trim();
				value = null;
				p = i;
				break;
			case '"':
				if(singleQuote) {
					continue;
				}
				if(doubleQuote) {
					props.put(name, text.substring(p, i-1));
					name = null;
					doubleQuote = false;
				}
				else {
					if(!text.substring(p,i-1).trim().isEmpty()) {
						throw new IllegalArgumentException("Unexpected text before '\"' in: "+text);
					}
					doubleQuote = true;
				}
				p = i;
				break;
				
			case '\'':
				if(doubleQuote) {
					continue;
				}
				if(singleQuote) {
					props.put(name, text.substring(p, i-1));
					name = null;
					singleQuote = false;
				}
				else {
					if(!text.substring(p,i-1).trim().isEmpty()) {
						throw new IllegalArgumentException("Unexpected text before single quote in: "+text);
					}
					singleQuote = true;
				}
				p = i;
				break;
			}
		}
		if(name != null) {
			value = text.substring(p).trim();
			props.put(name, value.isEmpty() ? "true" : value);
		}
		return props;
	}
	
	public static class Const extends Conversion {
		Object constVal;

		public Const(Object val) {
			constVal = val;
		}
		
		@Override
		public Object apply(Object value) {
			return constVal;
		}
	}
	
	public static class ToInt extends Conversion {
		@Override
		public Object apply(Object value) {
			if(next != null) {
				value = next.apply(value);
			}
			if(value instanceof Double) {
				return String.valueOf(((Number)value).intValue());
			}
			return value;
		}
	}
	public static class Lowercase extends Conversion {

		@Override
		public Object apply(Object value) {
			if(next != null) {
				value = next.apply(value);
			}
			return value != null ? value.toString().toLowerCase() : null;
		}
	}

	public static class Uppercase extends Conversion {

		@Override
		public Object apply(Object value) {
			if(next != null) {
				value = next.apply(value);
			}
			return value != null ? value.toString().toUpperCase() : null;
		}
	}
	public static class Escape extends Conversion {

		@Override
		public Object apply(Object value) {
			if(next != null) {
				value = next.apply(value);
			}
			return value != null ? ClientUtils.escapeQueryChars(value.toString()) : null;
		}
	}
	public static class SolrDate extends Conversion {

		@Override
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			if(value == null) return null;
			
			String solrDate = Util.toSolrDate(value.toString());
			if(solrDate == null) {
				LOG.error("Invalid SOLR date: {}", value);
			}
			return solrDate;
		}
	}	

	public static class UTCString extends Conversion {

		@Override
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			if(value == null) return null;
			
			if(value instanceof Date) {
				value = DateTimeFormatter.ISO_INSTANT.format(
						((Date)value).toInstant());
			}

			return value;
		}
	}
	
	
	public static class Join extends Conversion {
		
		public String prefix;
		public String postfix;
		public String separator = " ";
		
		@Override
		public Object apply(Object value) {
			if(value == null) return null;
			
			StringBuilder buf = new StringBuilder();
			if(value instanceof List) {
				List<?> vals = (List<?>) value;
				int i=0;
				for(Object v : vals) {
					if(next != null) v = next.apply(v);
					if(v == null) continue;
					
					if(separator != null && i++ > 0) buf.append(separator);
					
					if(prefix != null) buf.append(prefix);
					buf.append(v.toString());
					if(postfix != null) buf.append(postfix);
				}
			}
			else {
				if(next != null) value = next.apply(value);
				
				if(prefix != null) buf.append(prefix);
				buf.append(value.toString());
				if(postfix != null) buf.append(postfix);
			}
			return buf.toString();
		}

		@Override
		public void setup(Map<String, String> props) {
			String v;
			if( (v=props.get("prefix")) != null) prefix = v;
			if( (v=props.get("postfix")) != null) postfix = v;
			if( (v=props.get("separator")) != null) separator = v;
		}
	}
	public static class MaxLen extends Conversion {
		
		public int len = 128;
		
		@Override
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			if(value == null) return null;
			
			String s = value.toString();
			if(s.length() > len) s = s.substring(0, len);
			return s;
		}

		@Override
		public void setup(Map<String, String> props) {
			
			String length = props.get("length");
			if(length != null) try {
				len = Integer.parseInt(length);
			}
			catch(NumberFormatException e) {
				throw new IllegalArgumentException("'int' value expected: "+length);
			}
		}
	}
	public static class ToString extends Conversion {

		public String before = "";
		public String after="";
		
		@Override
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			if(value == null) return null;
			
			return before+value.toString()+after;
		}
		
		@Override
		public void setup(Map<String, String> props) {
			String v;
			if( (v=props.get("before")) != null) before = v;
			if( (v=props.get("after")) != null) after = v;
		}

	}
	public static class Quotes extends Conversion {

		@Override
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			if(value == null) return null;
			
			String s = value.toString();
			StringBuilder buf = null;
			
			int p=0,i=0,max=s.length();
			for(; i < max; ++i) {
				char c = s.charAt(i);
				if(c == '"' || c == '\\') {
					if(buf == null) buf = new StringBuilder();
					if(p < i) {
						buf.append(s,p,i);
						p = i;
					}
					buf.append('\\');
				}
			}
			if(buf != null) {
				if(p < max) buf.append(s,p,max);
				s = buf.toString();
			}
			return '"'+s+'"';
		}
	}
	public static class Default extends Conversion {
		public Object value;

		@Override
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			
			return value == null ? this.value : value;
		}

		@Override
		public void setup(Map<String, String> props) {
			String v;
			if( (v=props.get("value")) != null) value = v;
		}
		
	}
	public static class Replace extends Conversion {
		public String what;
		public Pattern pattern;
		public String with = " ";
		
		@Override
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			if(value == null) return null;
			
			if(pattern != null) 
				return pattern.matcher(value.toString()).replaceAll(with);
			else if(what != null ) 
				return value.toString().replace(what, with);			
			else 
				return value;
		}

		@Override
		public void setup(Map<String, String> props) {
			
			String v;
			if( (v=props.get("with")) != null) with = v;
			if( (v=props.get("what")) != null) what = v;
			if( (v=props.get("pattern")) != null) {
				pattern = Pattern.compile(v);
			}
		}
	}
	public static class Substr extends Conversion {
		public int start;
		public int end;
		@Override
		
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			if(value == null) return null;
			
			String v = value.toString();
			int s = Math.max(start < 0 ? v.length()+start : start, 0);
			int e = Math.min(end <= 0 ? v.length()+end : end, v.length());
			
			return (s >= e) ?  "" : v.substring(s, e);
		}
		@Override
		public void setup(Map<String, String> props) {
			String v;
			if( (v=props.get("start")) != null) start = Integer.parseInt(v);
			if( (v=props.get("end")) != null) end = Integer.parseInt(v);
		}
		
	}
	public static class Match extends Conversion {
		public Pattern pattern;
		public String separator = " ";
		
		@Override
		public void setup(Map<String, String> props) {
			
			String v;
			if( (v=props.get("separator")) != null) separator = v;
			if( (v=props.get("pattern")) != null) {
				pattern = Pattern.compile(v);
			}
		}

		@Override
		public Object apply(Object value) {
			if(next != null) value = next.apply(value);
			if(value == null) return null;
			Matcher m = pattern.matcher(value.toString());
			
			StringBuilder out = new StringBuilder();
			while(m.find()) {
				if(out.length() > 0) {
					out.append(separator);
				}
				out.append(m.group());
			}
			return out.toString();
		}
	}
	
	public interface Factory {
		
	}

}
