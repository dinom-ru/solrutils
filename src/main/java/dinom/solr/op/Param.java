package dinom.solr.op;

import org.w3c.dom.Element;

import dinom.solr.QueryBuilder;
import dinom.solr.Template;

/**
 * &lt;set&gt; operator. Accepts two arguments - 'name' and 'value' to set a parameter.
 * <ul>
 * <li>'name' is the name of parameter that will be set in Solr query.</li>
 * <li>'value' is the value of the parameter - static.</li>
 * <li>text content is template otherwise</li
 * </ul>
 * <p>Examples:</p>
 * <ul>
 * <li>&lt;set name="rows" value="100" /&gt; </li>
 * <li>&lt;set name="q"&gt;title:${value: escape}*&lt;/set&gt; </li>
 * <li> &lt;remove name="shards" /&gt;</li>
 * </ul>
 * @author VF 
 */
public class Param extends SolrQueryOperator {
	
	String name;
	Template template;
	
	@Override
	public SolrQueryOperator parse(Element el) {
		name = el.getAttribute("name");		
		if(name.isEmpty()) {
			throw xmlError("'name' attribute is required.", el);
		}
		
		String value = el.getAttribute("value");
		
		template = value.isEmpty()
			? Template.compile(el.getTextContent())
			: new Template.Const(value);		
		return this;
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		query.set(name, template.apply(value));
	}
	
	public static class Remove extends Param {
		@Override
		public void apply(QueryBuilder query, Object value) {
			query.remove(name);
		}
	}
	
	public static class Add extends Param {
		@Override
		public void apply(QueryBuilder query, Object value) {
			query.add(name,template.apply(value));
		}
	}
	
	public class Get extends SolrQueryOperator {
		String valueRef;

		@Override
		public SolrQueryOperator parse(Element el) {
			String at;
			if((at = el.getAttribute("name")).isEmpty()) 
				throw xmlError("'name' attribute expected", el);
			valueRef = at;
			return this;
		}

		@Override
		public void apply(QueryBuilder query, Object value) {
			query.currentValue = query.getValue(valueRef);
		}
	}
	
}
