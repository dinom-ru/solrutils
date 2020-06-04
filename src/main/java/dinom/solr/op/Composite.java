package dinom.solr.op;

import java.util.ArrayList;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import dinom.solr.QueryBuilder;

/**
 * Represents an array (list ) of operators, that should be called one by one when {@link #apply(QueryBuilder, Object) apply}
 * method is called.
 * 
 * See {@link #parse(Element) parse} method for XML spec.
 * 
 * @author VFilatov
 *
 */
public class Composite extends SolrQueryOperator {
	
	protected ArrayList<SolrQueryOperator> list;
	
	/**
	 * Parses child nodes and adds an operator each of them. Attributes of the element and non element childs are ignored.
	 * 
	 * @return this instance or first child instance if there is only one element in the list.
	 */
	@Override
	public SolrQueryOperator parse(Element el) {
		list = new ArrayList<>();
		
		// The action is a bit complicated, - for each added operator
		// it checks if it is 'If' operator and if so, it glues all them
		// together
		parseChildren(el, new BiConsumer<Element, SolrQueryOperator>() {
			If last = null;

			@Override
			public void accept(Element e, SolrQueryOperator op) {
				if(op instanceof If) {
					if(last == null) {
						list.add(last = (If)op);
					}
					else {
						last = (last.next = (If)op);
					}
				}
				else {
					list.add(op);
					last = null; 
				}
			}
		});
		return list.isEmpty() ? null :( list.size() == 1 ? list.get(0) : this); 
	}
	
	/**
	 * 
	 * Calls {@link SolrQueryOperator#apply(QueryBuilder, Object) apply} for
	 * every component from the list with passing 'value' argument.
	 * 
	 * <p>Usually every component in the list receives the same 'value' argument. Yet
	 * the component can modify what next operator in list will get by assigning
	 * new value to {@link QueryBuilder#currentValue} field.</p>
	 */
	@Override
	public void apply(QueryBuilder query, Object value) {
		Object old = query.currentValue;
		
		query.currentValue = value;
		
		for(SolrQueryOperator op : list) {
			op.apply(query, query.currentValue);
		}	
		query.currentValue = old;
	}
	
	@Override
	public void setParent(SolrQueryOperator op) {
		for(SolrQueryOperator ch: list) {
			ch.setParent(op);
		}
	}

	public static void parseChildren(Element el, BiConsumer<Element, SolrQueryOperator> action) {
		parseChildren(el,null,action);
	}
	public static void parseChildren(Element el, Class<?> clazz, BiConsumer<Element, SolrQueryOperator> action) {
		NodeList lst = el.getChildNodes();
		for(int i=0; i < lst.getLength(); ++i) {
			
			Node n = lst.item(i);
			if(n.getNodeType() != Node.ELEMENT_NODE) continue;
			
			Element e = (Element)n;
			
			SolrQueryOperator op = SolrQueryOperator.newInstance(e,clazz).parse(e);
			
			if(op != null) action.accept(e, op);
		}
	}
}
