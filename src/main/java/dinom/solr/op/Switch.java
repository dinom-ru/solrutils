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
 * &lt;switch&gt; allows to switch execution to different blocks depending on passed JSON value.
 * 
 * @see #parse(Element)
 * @see #apply(QueryBuilder, Object)
 * 
 * @author VF
 */
public class Switch extends SolrQueryOperator {
	private static Logger LOG = LoggerFactory.getLogger(Switch.class);
	private static final String DEFAULT_CHOICE_NAME = "Else";
	
	Map<String, SolrQueryOperator> choices;
	SolrQueryOperator defaultOp;
	
	boolean checkType;
	
	/**
	 * Parses child nodes and adds an operator (see {@link #newInstance(Element) newInstance} for
	 * each of them. Child tag name is a key for operator execution, passed JSON value is compared
	 * against it and if they are equal, the operator is applied. 
	 * 
	 * <p>Attributes of the element and non element children are ignored.</p>
	 * 
	 * @return this instance or 'null' if no children are parsed.
	 */
	@Override
	public SolrQueryOperator parse(Element el) {
		
		String dfltChoice = el.getAttribute("default");
		if(dfltChoice.isEmpty()) {
			dfltChoice = DEFAULT_CHOICE_NAME;
		}

		String v = el.getAttribute("check");
		if(!v.isEmpty()) {
			if("type".equals(v)) checkType = true;
			else if(!"value".equals(v)) {
				throw xmlError("'check' must be 'type'or 'value'.", el);
			}
		}
		
		choices = new HashMap<>();
		
		NodeList lst = el.getChildNodes();
		for(int i=0; i < lst.getLength(); ++i) {
			
			Node n = lst.item(i);
			if(n.getNodeType() != Node.ELEMENT_NODE) continue;
			
			Element e = (Element)n;
			SolrQueryOperator op = SolrQueryOperator.newInstance(e).parse(e);
			
			if(op != null) {
				choices.put(e.getTagName(), op);
			}
		}
		defaultOp = choices.remove(dfltChoice);

		if(defaultOp == null && !dfltChoice.equals(DEFAULT_CHOICE_NAME)) {
			throw xmlError("default choice is not defined: "+dfltChoice,el);
		}
		return choices.isEmpty() && defaultOp == null ? null : this; 
	}

	/**
	 * Checks if any of choices matches to passed JSON value and executes the
	 * operator. 
	 */
	@Override
	public void apply(QueryBuilder query, Object value) {
		String v;
		if(value == null) v = "null";
		else if(checkType) {
			if(value instanceof List) v = "List";
			else if(value instanceof Map) v = "Map";
			else v = value.getClass().getSimpleName();
		}
		else v = value.toString();

		SolrQueryOperator op = choices.get( v );
		if(op == null) {
			if((op = defaultOp) == null) {
				LOG.warn("no operator matches '{}'",v);
				return;
			}
		}
		op.apply(query, value);
	}

}
