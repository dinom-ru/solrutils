package dinom.solr.op;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import dinom.solr.QueryBuilder;
/**
 * 
 * REF https://lucene.apache.org/solr/guide/7_2/json-facet-api.html
 * 
 * @author VF
 */
public class Facets extends SolrQueryOperator {
	private static Logger LOG = LoggerFactory.getLogger(Facets.class);
	
	Map<String,SolrQueryOperator> ops;
	
	@Override
	public SolrQueryOperator parse(Element el) {
		ops = new HashMap<>();
		
		NodeList list = el.getChildNodes();
		for(int i=0; i < list.getLength(); ++i) {
			
			Node n = list.item(i);
			if(n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Element e = (Element)n;
			
			SolrQueryOperator op;
			String className = e.getAttribute("class");
			if(className.isEmpty()) {
				op = (e.hasAttribute("type")? new FacetOp() : new Composite()).parse(e);
			}
			else {
				try {
					Class<?> cls = Class.forName(className.contains(".") 
							? className : "dinom.solr.op."+className);
					op = ((SolrQueryOperator) cls.newInstance()).parse(e);
				}
				catch(Exception ex) {
					LOG.error("failed to create "+className+" instance.", ex);
					continue;
				}
			}
			if(op != null) {
				ops.put(e.getTagName(), op);
				op.setParent(this);
			}
		}
		
		return this;
	}
	

	@Override
	public void apply(QueryBuilder query, String key, Object value) {
		int idx = key.indexOf('.');
		
		SolrQueryOperator op = ops.get(idx == -1 ? key : key.substring(0, idx));
		if(op == null) {
			LOG.error("'{}' facet is not defined.", key);
		}
		else {
			op.apply(query, key, value);
		}
	}


	@Override
	public void apply(QueryBuilder query, Object value) {
		if(value instanceof List) {
			apply(query,(List<?>)value);
		}
		else if( value instanceof Map) {
			Map<?,?> map = (Map<?,?>)value;
			
			for(Map.Entry<?, ?> e : map.entrySet()) {
				
				apply(query, e.getKey().toString(), e.getValue());
			}
		}
		else if( value != null ) {
			String key = value.toString();
			
			SolrQueryOperator op = ops.get(key);
			if(op == null) {
				LOG.error("'{}' facet field is not defined.", key);
			}
			else {
				op.apply(query, key, null);
			}
		}
	}
	private void apply(QueryBuilder query, List<?> facets) {
		for(Object val : facets) {
			apply(query,val);
		}
	}
	
	/**
	 * 
	 * @param s SOLR String
	 * @return the key that will be used in response to link with facet count.
	 */
	public static String extractKey(String s) {
		if(!s.startsWith("{!")) return s;
		int idx = s.indexOf('}');
		if(idx == -1) return s;
		
		String key = s.substring(idx+1);
		s = s.substring(2, idx);
		idx = s.indexOf("key=");
		if(idx == -1) return key;
		
		int edx = s.indexOf(' ',idx+=4);
		return edx == -1 
				? s.substring(idx).replace("'", "")
				: s.substring(idx,edx).replace("'", "");
	}
}
