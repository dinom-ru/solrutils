package dinom.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Random;

import org.junit.Test;

import dinom.solr.Template;

public class TemplateTest {
	private static final String HELLO = "Hello, World!";
	String s,v;
	Object obj;
	Template tmpl;
	HashMap<String,Object> map;
	
	@Test
	public void basicTest() {
		
		/// Const ///
		
		s = HELLO;
		tmpl = Template.compile(s);
		assertTrue(tmpl.getClass() == Template.Const.class);
		assertEquals("Template.Const", s, tmpl.apply(null));
		
		/// Value ///
		
		s = "${value}";
		tmpl = Template.compile(s);
		
		assertTrue(tmpl == Template.VALUE);
		
		obj = "Hello, Mister!";
		assertEquals("Template.Value", obj.toString(), tmpl.apply(obj));
		
		obj = Long.valueOf(new Random(12345).nextLong());
		assertEquals("Template.Value", obj.toString(), tmpl.apply(obj));
		assertEquals("Template.Value 2", "", tmpl.apply(null));
		
		s = "${value:int}";
		tmpl = Template.compile(s);
		assertEquals("Template.Int","1",tmpl.apply("1"));
		
		/// SingleValue ///
		
		s = HELLO.replace("World", "${value}"); // slot in the middle
		tmpl = Template.compile(s);
		assertEquals("Template.SingleValue 1", HELLO, tmpl.apply("World"));
		
		s = s + s;    // more than 1 slot
		tmpl = Template.compile(s);
		assertEquals("Template.SingleValue 2", HELLO+HELLO, tmpl.apply("World"));
		
		s = "${value}"+HELLO+"${value}";  // slot strictly on start/end
		tmpl = Template.compile(s);
		assertEquals("Template.SingleValue 3", s.replace("${value}", "Oh.."), tmpl.apply("Oh.."));
		
		
		/// MultiValue ///
		
		s = HELLO.replace("World", "${first}") +" and ${second} too!";
		tmpl = Template.compile(s);
		
		map = new HashMap<>();
		map.put("first", "Robert");
		map.put("second", "Terra");
		
		v = s.replace("${first}", map.get("first").toString());
		v = v.replace("${second}", map.get("second").toString());
		assertEquals("Template.MultiValue 1", v, tmpl.apply(map));
		v = s.replace("${first}", "").replace("${second}", "");
		assertEquals("Template.MultiValue 2", v, tmpl.apply(null));
		
		
	}

}
