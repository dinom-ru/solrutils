package dinom.solr.op;


import java.io.IOException;

import org.noggit.ObjectBuilder;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import dinom.solr.QueryBuilder;
import dinom.solr.Template;

public class Call extends Composite {
	
	String key;
	Object value;
	Template template;
	
	SolrQueryOperator parent;

	@Override
	public SolrQueryOperator parse(Element el) {
		key = el.getAttribute("key");
		
		String s = el.getTextContent();
		if(!s.isEmpty()) {
			template = Template.compile(s);
		
			if(template instanceof Template.Const) {
				try {
					value = ObjectBuilder.fromJSON(template.apply(null));
					template = null;
				} 
				catch (Exception ex) {
					throw xmlError("fromJSON failure",ex, el);
				}
			}
		}
		return this;
	}
	

	@Override
	public void setParent(SolrQueryOperator op) {
		this.parent = op;
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		if(parent == null) {
			return;
		}
		if(this.template == null) {
			parent.apply(query, key, this.value);
		}
		else {
			String json = template.apply(value);
			Object v;
			try {
				v = ObjectBuilder.fromJSON(json);
			} 
			catch (IOException ex) {
				LoggerFactory.getLogger(getClass()).error("fromJSON failed.",ex);
				return;
			}
			parent.apply(query, key, v);			
		}
	}

}
