package dinom.solr.op;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import dinom.solr.QueryBuilder;

/**
 * Element &lt;fq&gt;to add filters to SolrQuery. 
 * 
 * @author VF
 */
public class FilterBy extends FilterQuery {
	private static Logger LOG = LoggerFactory.getLogger(FilterBy.class);

	/**
	 * filters that can be inside this filterBy set. Passed value in JSON must be a map.
	 */
	Map<String,SolrQueryOperator> filters;
	
	boolean strict;

	
	public final SolrQueryOperator getFilter(String filterName) {
		return filters == null ? null : filters.get(filterName);
	}

	@Override
	public SolrQueryOperator parse(Element el) {
		
		Node first = el.getFirstChild();
		if(first != null && first.getNodeType() == Node.TEXT_NODE && !first.getNodeValue().trim().isEmpty()) {
			return new FilterQuery().parse(el);

		}
		setProperties(el);
		
		strict = !"false".equals(el.getAttribute("strict"));
		
		filters = new HashMap<>();
		Composite.parseChildren(el, FilterQuery.class, (e, op) -> {
			
			op.setParent(this);
			if((op = op.parse(e)) !=null ) filters.put(e.getTagName(), op);
		});
		return this;
	}
	
	@Override
	public void apply(QueryBuilder query, String key, Object value) {
		int idx = key.indexOf('.');
		
		if(idx != -1) key = key.substring(0, idx);
		
		SolrQueryOperator fq = filters.get(key);
		if(fq == null) {
			LOG.error("filter is not defined: {}",key);
		}
		else {
			fq.apply(query, value);
		}
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		
		if(filters == null) {
			super.apply(query, value);
		}
		else if(value instanceof Map) {
			apply(query, (Map<?,?>) value);
		}
		else if(value instanceof List) {
			for(Object v : (List<?>)value) {
				if(v instanceof Map) {
					apply(query, (Map<?,?>) v);
				}
				else LOG.error("Map expected as 'value', while received: {}", v);
			}
		}
		else if(value != null && value instanceof String && !strict) {
			
			query.addFilterQuery(value.toString());
		}
		else {
			LOG.error("Map expected as 'value', while received: {}", value);
		}
	}
	public void apply(QueryBuilder query, Map<?,?> map) {
		
		for(Map.Entry<?, ?> e : map.entrySet()) {
			apply(query,e.getKey().toString(), e.getValue());
		}
	}
	
}
