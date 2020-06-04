package dinom.solr.op;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import dinom.solr.QueryBuilder;

/**
 * Represent an operator concept, atomic unit of what can be done with SolrQuery object.
 * 
 * <p>All subclasses should override two methods: {@link #parse(Element)} and {@link #apply(QueryBuilder, Object)}.</p>
 * 
 * @author VF
 */
public abstract class SolrQueryOperator {
	public static final String VALUE_VAR = "${value}";
	
	/**
	 * Build itself from XML element specification. Each subclass 
	 * itself specifies what attributes or child nodes should be specified.
	 * 
	 * @param el XML element, initialization parameters for the instance.
	 * 
	 * @return parsed instance of the operator. Usually the same instance, but can be
	 * a different instance (see {@link Composite#parse(Element)} or 'null' if parsing
	 * fails and element does not contain enough information to build the instance.
	 */
	public abstract SolrQueryOperator parse(Element el);
	
	/**
	 * Performs the operation of transformation JSON search query object parameter into
	 * SolrQuery object parameters. value can one of simple types: String, Double, List or Map.
	 * 
	 * @param query QueryBuilder, 
	 * 
	 * @param value an Object, parameter value that come with JSON search query object. 
	 */
	public abstract void apply( QueryBuilder query, Object value);
	
	/**
	 * Similar to {@link #apply(QueryBuilder, Object)} but accepts one more argument, the 'key' associated
	 * with the value.
	 * 
	 * @param query QueryBuilder, builder object.
	 * @param key String, key associated with value.
	 * @param value 
	 */
	public void apply( QueryBuilder query, String key, Object value) {
		this.apply(query, value);
	}
	/**
	 * Called when 'this' operator will act in the context of another operator.
	 * 
	 * <p>Does nothing by default as in most cases operator behavior does not
	 * depend on the context.</p>
	 * 
	 * @param op - operator, the parent context of 'this' operator.
	 */
	public void setParent(SolrQueryOperator op) {
		
	}
	
	/**
	 * Creates new operator instance, given initialization element. Looks for 'class' attribute
	 * to understand what class should be used. If absent uses {@link #getClass()} to
	 * get default mapping of tag name into Java class that should be used. 
	 * 
	 * @param el XML element, instance specification.
	 * @return SolrQueryOperator instance,
	 */
	public static SolrQueryOperator newInstance(Element el) {
		return newInstance(el,null);
	}
	/**
	 * Creates new operator instance, given initialization element. Looks for 'class' attribute
	 * to understand what class should be used. If absent, 'cls' attribute is used. 
	 * Then creates default constructor instance and calls {@link #parse(Element)} method to initialize it.
	 * 
	 * @param el XML element, instance specification.
	 * @param cls Class to use for instance, if 'class' attribute not present. If 'null' is passed, works as {@link #newInstance(Element)}.
	 * @return SolrQueryOperator instance
	 */
	public static SolrQueryOperator newInstance(Element el, Class<?> cls) {
		String tagName = el.getTagName(), className = el.getAttribute("class");
		
		if(!className.isEmpty()) try {
			cls = Class.forName(className.contains(".") 
					? className : "dinom.solr.op."+className);
		} 
		catch (ClassNotFoundException e) {
			throw xmlError("Invalid class name: "+className, el);
		}
		if(cls == null) {
			cls = getClass(tagName);
			if(cls == null) cls = Composite.class;
		}
		
		try {
			return (SolrQueryOperator)cls.newInstance();
			
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException("Failed to create the instance of: "+className, e);
		}
	}
	
	/**
	 * Default mapping of XML tag name into corresponding Java class. 
	 * Currently 'q, fq, set, switch, sortBy, int, lowercase, uppercase' tag names are reserved.<br>
	 * If none of this, {@link Composite Composite.class} is returned.
	 * 
	 * <p>See method body for details of the mapping.</p>
	 * 
	 * @param tagName String tag name of the instance.
	 * 
	 * @return Class that should be used.
	 */
	public static Class<?> getClass(String tagName) {
		switch(tagName) {
		case "if": 
		case "elseif":
		case "else": return If.class;
		case "call": return Call.class;
		case "q": return Query.class;
		case "fq": return FilterBy.class;
		case "facet": return Facets.class;
		case "set": return Param.class;
		case "add": return Param.Add.class;
		case "remove": return Param.Remove.class;
		case "get": return Param.Get.class;
		case "switch": return Switch.class;
		case "sortBy": return SortBy.class;
		case "search": return SearchOp.class;
		default:
			return null;
		}
	}
	
	protected static RuntimeException xmlError(String msg, Throwable ex, Element ctx) {
		return xmlError(msg+": "+ex.getClass().getName()+": "+ex.getMessage(),ctx);
	}
	protected static RuntimeException xmlError(String msg, Element ctx) {
		StringBuilder buf = new StringBuilder(msg.length()+64);
		while(ctx != null) {
			if(buf.length() != 0) buf.insert(0, "><");
			buf.insert(0,ctx.getTagName());
			Node p = ctx.getParentNode();
			if(p == null) break;
			if(!(p instanceof Element)) break;
			ctx = (Element)p;
		}
		return new IllegalArgumentException("<"+buf.toString()+"> "+msg);
	}

	public static final SolrQueryOperator NOOP = new SolrQueryOperator() {
		@Override
		public SolrQueryOperator parse(Element el) {
			return null;
		}
		@Override
		public void apply(QueryBuilder query, Object value) {			
		}
	};
}
