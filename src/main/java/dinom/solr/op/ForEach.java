package dinom.solr.op;


import java.util.List;

import org.w3c.dom.Element;

import dinom.solr.QueryBuilder;

/**
 * As a difference from usual composite, it does not just pass the value to component, but checks if
 * the value is list, then pass each list item as separate value to components.
 * 
 * @author VF
 */
public class ForEach extends Composite {
	
	
	@Override
	public SolrQueryOperator parse(Element el) {
		
		super.parse(el);
		
		return this;
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		
		if(value instanceof List) {
			List<?> vals = (List<?>) value;
			for(Object val : vals) {
				super.apply(query, val);
			}
		}
		else {
			super.apply(query, value);
		}
	}
	
}
