package dinom.solr.op;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import dinom.solr.QueryBuilder;

public class If extends Composite {
	private static final Map<String,Class<?>> commonOps = new HashMap<>();
	
	If next;
	String arg;
	
	@Override
	public SolrQueryOperator parse(Element el) {
		super.parse(el);

		if(el.hasAttributes()) {
			Node n = el.getAttributes().item(0);
			
			Class<?> cls = commonOps.get(n.getNodeName());
			if(cls != null) try {
				If op = (If)cls.newInstance();
				op.list = this.list;
				op.setArg(n.getNodeValue());
				return op;
			}
			catch (Exception e) {
				throw xmlError("Failed to create instance for attribute: "+n.getNodeName(),el);
			}
			else {
				throw xmlError("Unexpected 'If' attribute: "+n.getNodeName(),el);
			}
		}
		else if("else".equals(el.getTagName())) {
			return this;
		}
		else {
			throw xmlError("comparison attribute expected", el);
		}
	}

	protected void setArg(String arg) {
		this.arg = arg;
	}

	public static class Has extends If {

		@Override
		public void apply(QueryBuilder query, Object value) {
			Object v = query.getValue(arg);
			
			if(v != null) {
				super.apply(query, choose(value,v));
			}
			else if(next != null) {
				next.apply(query, value);
			}
		}
		protected Object choose(Object value, Object v) {
			return value;
		}
	}
	
	public static class Get extends Has {
		@Override
		protected Object choose(Object value, Object v) {
			return v;
		}
	}
	
	public static class Equals extends If {
		@Override
		public void apply(QueryBuilder query, Object value) {
			if(equals(value)) {
				super.apply(query, value);
			}
			else if(next != null) {
				next.apply(query, value);
			}
		}		
		@Override
		public boolean equals(Object val) {
			return arg.equals(val == null ? "" : val.toString());
		}
	}
	public static class NotEquals extends Equals {

		@Override
		public boolean equals(Object val) {
			return !arg.equals(val == null ? "" : val.toString());
		}
	}
	public static class Contains extends Equals {

		@Override
		public boolean equals(Object val) {
			if(val == null) return false;
			else 
				return val.toString().contains(arg);
		}
	}
	
	static {
		commonOps.put("has", Has.class);
		commonOps.put("get", Get.class);
		commonOps.put("eq", Equals.class);
		commonOps.put("ne", NotEquals.class);
		commonOps.put("contains", Contains.class);
	}

}
