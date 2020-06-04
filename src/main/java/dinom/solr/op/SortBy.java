package dinom.solr.op;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import dinom.solr.QueryBuilder;

/**
 * Maps various 'abstract' sort names into Solr sort commands with particular fields.
 */
public class SortBy extends SolrQueryOperator {
	static Logger LOG = LoggerFactory.getLogger( SortBy.class);

	HashMap<String,String> nameMap;
	
	@Override
	public SolrQueryOperator parse(Element el) {
		nameMap = new HashMap<>();
		
		NodeList lst = el.getChildNodes();
		for(int i=0; i < lst.getLength(); ++i) {
			
			Node n = lst.item(i);
			if(n.getNodeType() != Node.ELEMENT_NODE) continue;
			
			Element e = (Element)n;
			
			String value = e.getAttribute("value");
			if(value.isEmpty())
				throw xmlError("'value' attribute is required", e);
			
			nameMap.put(e.getTagName().toLowerCase(), value);
		}
		return this;
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		if(value == null || !(value instanceof String))  {
			
			LOG.warn("Unexpected value of 'sortBy': "+value);
			return;
		}
		
		String key = (String)value, dir;
		
		if(key.startsWith("!")) {
			// a way to pass arbitrary string
			query.set("sort", key.substring(1));
			return;
		}
		
		int idx = key.indexOf(' ');
		if(idx == -1) dir = "";
		else {
			dir = key.substring(idx);
			key = key.substring(0,idx);
		}
		
		String sortBy = nameMap.get(key.toLowerCase());
		
		if(sortBy == null) {
			LOG.warn("undefined 'sortBy': "+key);
			return;
		}
		if(!dir.isEmpty()) {
			// if more than simple name is passed, sortBy should
			// be transformed.
			int i0 = dir.indexOf(','); 
			int i1 = sortBy.indexOf(' ');
			
			// if specified sort (key) is multiple, - ignore sortBy 
			// and take specified sort as what should go to Solr.
			if(i0 != -1) {
				sortBy = key+(dir.endsWith(",") ? dir.substring(0, dir.length()-1): dir);
			}
			else if(i1 != -1) {
				// the change of primary sort is specified, secondary sorts if
				// present should remain the same.
				
				i0 = sortBy.indexOf(',',i1+1);
				sortBy = sortBy.substring(0, i1) + dir +(i0 != -1 ? sortBy.substring(i0) : ""); 
			}
			else {
				sortBy += dir;
			}
		}
		query.set("sort", sortBy);		
	}

}
