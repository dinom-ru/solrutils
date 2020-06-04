package dinom.solr.op;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import dinom.solr.Conversion;
import dinom.solr.QueryBuilder;
import dinom.solr.Template;

public class FilterQuery extends SolrQueryOperator {
	public static Logger LOG = LoggerFactory.getLogger(FilterQuery.class);
	
	/**
	 * Template to us. JSON value from query is passed to it.
	 */
	Template template;
	
	/**
	 * Default conversion to use for template variables.
	 */
	Conversion defType;


	public FilterQuery() {
		defType = Conversion.NONE;
	}
	public FilterQuery(FilterQuery parent) {
		setParent(parent);
	}
	public void setProperties(Element el) {
		String v = el.getAttribute("valueType");
		defType = v.isEmpty() ? Conversion.NONE : Conversion.parse(v);
	}
	

	@Override
	public void setParent(SolrQueryOperator op) {
		if(op instanceof FilterQuery) {
			this.defType = ((FilterQuery)op).defType;
		}
	}
	@Override
	public SolrQueryOperator parse(Element el) {
		setProperties(el);
		template = Template.compile(el.getTextContent(), defType);
		return this;
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		if(template == null) {
			LOG.warn("ignored: {}", value);
			return;
		}
		
		String fq = template.apply(value);
		
		if(fq != null) query.addFilterQuery(fq);
	}
	
	

}
