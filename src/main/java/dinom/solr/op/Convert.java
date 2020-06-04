package dinom.solr.op;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import dinom.solr.QueryBuilder;
import dinom.solr.Util;

//TODO delete this class as Obsolete. Use Conversion
public abstract class Convert extends SolrQueryOperator {
	private static Logger LOG = LoggerFactory.getLogger(Convert.class);
	

	protected SolrQueryOperator op;
	
	public abstract Object convert(Object value);
	
	@Override
	public SolrQueryOperator parse(Element el) {
		op = new Composite().parse(el);
		return this;
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		op.apply(query, convert(value));		
	}

	/**
	 * If passed argument is instance of Double, converts to String of 'intValue'.
	 */
	public static class ToInt extends Convert {

		@Override
		public Object convert(Object value) {
			if(value instanceof Double) {
				return String.valueOf(((Number)value).intValue());
			}
			return value;
		}
	}
	public static class Lowcase extends Convert {

		@Override
		public Object convert(Object value) {
			
			return value != null ? value.toString().toLowerCase() : null;
		}
	}
	public static class Uppercase extends Convert {

		@Override
		public Object convert(Object value) {
			
			return value != null ? value.toString().toUpperCase() : null;
		}
	}

	public static class Escape extends Convert {

		@Override
		public Object convert(Object value) {
			if(value instanceof List){
				List<String> lst = new ArrayList<>();
				for(Object val : (List<?>)value ) {
					lst.add(ClientUtils.escapeQueryChars(val.toString()));
				}
				return lst;
			} else {
				return value != null ? ClientUtils.escapeQueryChars(value.toString()) : null;
			}
		}
	}

	/**
	 * A place where checks for what user passes should be made and possible conversions are made.
	 * 
	 * @param jsonParam - user parameter passed with JSON search query.
	 *
	 * @param type  a String, required Solr parameter type.
	 * 
	 * @return a valid SolrQuery parameter, "toSolrParam-undefined" if type is not known.
	 */

	public static Object toSolrParam(Object jsonParam, String type) {
		switch(type) {
		case "int":
			if(jsonParam instanceof Double) {
				return String.valueOf(((Number)jsonParam).intValue());
			}
			return jsonParam;
		case "SolrDate":
			return toSolrDate(jsonParam.toString());
		default:
			return "toSolrParam-undefined";
		}
	}
	
    /**
     * Change format of date from "MM/dd/yy" to "yyyy-MM-dd'T'hh:mm:ss'Z'"
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
        
        Calendar c = Util.parseDateTime(dateString);
        if(c == null) {
            LOG.error("Invalid SOLR date: {}", dateString);
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(c.toInstant());
    }
}
