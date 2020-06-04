package dinom.solr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Node;

/*
 * Scenario:  IN(${value:list(sep=',' prefix='', postfix=''),escape})
 * 
 * probably next should be 
 * 
 * 
 */
public interface Template {
	
	public void write(StringBuilder out, Object val);
	
	public default String apply(Object val) {
		StringBuilder buf = new StringBuilder();
		this.write(buf,val);
		return buf.toString();
	}
	
	public static Template create( ArrayList<String> txts, ArrayList<String> names, ArrayList<Conversion> types) {
		boolean mapval = false;
		for(String nm : names) {
			if(!"value".equals(nm)) { mapval = true; break; }
		}
		return mapval ? new MapValue(txts,names,types) : new PlainValue(txts, types);
	}
	
	public static Template compile(String text) {
		return compile(text,Conversion.NONE);
	}
	
	public static Template compile(String text, Conversion defType) {

		int i = text.indexOf("${");
		if(i == -1) {
			return new Const(text);
		}
		if(i == 0 && "${value}".equals(text) && defType == Conversion.NONE) {
			return VALUE;
		}
		ArrayList<String> txts = new ArrayList<>();
		ArrayList<String> names = new ArrayList<>(); 
		ArrayList<Conversion> types = new ArrayList<>();
		
		compile(text,defType,txts,names,types);
		
		return Template.create(txts, names, types);
	}
	public static Template compile(Node node, Conversion defType, Conversion.Factory tagFactory) {
		
		if(node.getNodeType() == Node.TEXT_NODE  && node.getNextSibling() == null) {
			return compile(node.getNodeValue(), defType);
		}
		
		ArrayList<String> txts = new ArrayList<>();
		ArrayList<String> names = new ArrayList<>(); 
		ArrayList<Conversion> types = new ArrayList<>();
		
		while(node != null) {
			switch(node.getNodeType()) {
			case Node.TEXT_NODE:
				compile(node.getNodeValue(),defType,txts,names,types);
				break;
				
			case Node.ELEMENT_NODE:
				//TODO implement correct compilation
				break;
			}
			
			
			node = node.getNextSibling();
		}
		
		return Template.create(txts, names, types);
	}
	
	public static void compile(String text, Conversion convDefault, 
			ArrayList<String> txts, ArrayList<String> names, ArrayList<Conversion> types) {
		
		int i = text.indexOf("${");
		if(i == -1) {
			txts.add(text);
			return;
		}
		txts.add(text.substring(0,i));
		
		int p = i += 2, max = text.length();
		
		while(i < max) {
			char c = text.charAt(i++);
			if(c != ':' && c != '}') continue; 
				
			names.add(text.substring(p,i-1).trim());
			if(c == ':') {
				i = Util.indexOf(text, '}', p=i);
				if(i == -1) throw new IllegalArgumentException("No matching '}' found: "+text);
				types.add(Conversion.parse(text.substring(p,i++)));
			}
			else types.add(convDefault) ;
			
			i = text.indexOf("${",p = i);
			if(i == -1) {
				if(p < max) txts.add(text.substring(p));
				break;
			}
			else {
				txts.add(text.substring(p, i));
				p = i += 2;
			}
		}
	}
	
	///////////////////////////////////
	
	public Template VALUE = new Template() {
		@Override
		public void write(StringBuilder out, Object val) {	
			if(val != null) out.append(val.toString());
		}
		@Override
		public String apply(Object val) {
			return val == null ? "" : val.toString();
		}
		
	};
	
	public static class Const implements Template {
		String s;
		
		public Const(String text) {
			if(text == null) throw new NullPointerException();
			s = text;
		}
		
		@Override
		public void write(StringBuilder out, Object val) {	
			out.append(s);
		}

		@Override
		public String apply(Object val) {
			return s;
		}
	}
	
	public static class PlainValue implements Template {
		List<String> text;
		List<Conversion> vars;
		
		public PlainValue(List<String> text, List<Conversion> vars ) {
			this.vars = vars;
			this.text = text;
			if(text.size() < vars.size())
				throw new IllegalArgumentException("'text/values' count mismatch.");
		}
		@Override
		public void write(StringBuilder out, Object val) {
			int i;
			for(i=0; i < vars.size(); ++i) {
				out.append( text.get(i) );
				
				Conversion slot = vars.get(i);
				Object v = slot.apply(val);
				if(v != null) {
					out.append(v.toString());
				}
			}	
			while(i < text.size()) out.append(text.get(i++));
		}
	}
	public static class MapValue extends PlainValue {
		List<String> names;
		
		public MapValue(List<String> text, List<String> names, List<Conversion> vars) {
			super(text, vars);
			this.names = names;
			if(names.size() != vars.size())
				throw new IllegalArgumentException("'names/values' count mismatch.");
		}
		
		@Override
		public void write(StringBuilder out, Object val) {
			Map<String,Object> map;
			
			if(val == null) {
				map = Collections.emptyMap();
			}
			else if(val instanceof Map) {
				map = (Map<String,Object>)val;
			}
			else {
				map = new HashMap<>(1);
				map.put("value", val);
			}			
			int i;
			for(i=0; i < vars.size(); ++i) {
				out.append( text.get(i) );
				
				Conversion slot = vars.get(i);
				
				Object v = slot.apply(map.get(names.get(i)));
				if(v != null) {
					out.append(v.toString());
				}
			}	
			while(i < text.size()) out.append(text.get(i++));
		}
		public List<String> getNames() {
			return names;
		}
	}

	
	


}
