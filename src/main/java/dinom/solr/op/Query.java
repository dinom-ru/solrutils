package dinom.solr.op;


import org.w3c.dom.Element;

import dinom.solr.Conversion;
import dinom.solr.QueryBuilder;
import dinom.solr.Template;

public class Query extends SolrQueryOperator {
	
	Template template;
	Conversion defType;

	@Override
	public SolrQueryOperator parse(Element el) {
		String value = el.getAttribute("valueType");
		defType = value.isEmpty() ? Conversion.NONE : Conversion.parse(value);
		String text = el.getTextContent();
		template = Template.compile(text, defType);
		return this;
	}

	@Override
	public void apply(QueryBuilder query, Object aValue) {
		String q = template.apply(aValue);
				
		query.setQuery(q);
	}
	
}
