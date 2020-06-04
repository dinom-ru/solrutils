package dinom.solr;

import org.junit.Test;

import dinom.solr.Conversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;


public class ConversionTest {
	String k;
	Conversion cv;
	Object obj;
	
	@Test
	public void simpleConversions() {

		cv = new Conversion.Uppercase();
		assertEquals("Uppercase","UPPER(23A)",cv.apply("Upper(23a)"));
		assertNull("Uppercase", cv.apply(null));
		
		cv = new Conversion.Lowercase();
		assertNull("Lowercase", cv.apply(null));
		assertEquals("Lowercase","lower(23a)",cv.apply("LoweR(23A)"));
		
		cv = Conversion.parse(k="int");
		assertNull(k, cv.apply(null));
		assertEquals(k,"2",cv.apply(Double.valueOf(2.1)));
		assertEquals(k,"2.1",cv.apply("2.1"));
		
		cv = Conversion.parse(k="default(value='123'),uppercase");
		assertEquals(k,"123",cv.apply(null));
		assertEquals(k,"A=0.5",cv.apply("a=0.5"));
		
		cv = new Conversion.Const(k="Const");
		assertEquals(k,k,cv.apply("other value"));
	}
	
	@Test
	public void quotesTest() {
		cv = Conversion.parse(k="quotes");
		assertEquals(k,"\"hello\"", cv.apply("hello"));
		assertEquals(k,"\"\"", cv.apply(""));
		assertNull(k, cv.apply(null));
		assertEquals(k,"\"\\\\\"", cv.apply("\\"));
		assertEquals(k,"\"test \\\"me\\\"!\"", cv.apply("test \"me\"!"));
		assertEquals(k,"\"test \\\\\\\"me\\\"!\"", cv.apply("test \\\"me\"!"));
	}
	
	@Test
	public void mainTests() {
		
		cv = Conversion.parse(k="max(length='4')");
		assertNull("MaxLen", cv.apply(null));
		assertEquals(k,"1234",cv.apply("1234567"));
		
		cv = Conversion.parse(k="escape");
		assertNull(k, cv.apply(null));
		assertEquals(k,"title\\:Hello",cv.apply("title:Hello"));
		
		
		cv = Conversion.parse(k="string");
		assertNull(k, cv.apply(null));
		obj = new java.util.Date();
		assertEquals(k,obj.toString(), cv.apply(obj));
		
		cv = Conversion.parse(k="string(before='(', after=')' ), match(pattern='\\w+', separator=',') ");
		assertNull(k, cv.apply(null));
		assertEquals(k,"(one,two,three)", cv.apply("one={two,three}"));
		((Conversion.Match)cv.next).separator = " ";
		assertEquals(k,"(one two three)", cv.apply("one=(two,three);"));
		
		cv = Conversion.parse(k="quotes,replace(what='\"',with=\"'\" )");
		assertEquals(k,"\"name:'Phil'\"", cv.apply("name:\"Phil\""));
		
		cv = Conversion.parse(k="replace(pattern='[-;.=:]+', with=' ' )");
		assertEquals(k,"a 535 23 b 17 ", cv.apply("a=535.23;b:=17;"));
	}
	
	@Test
	public void substrTests() {
		cv = Conversion.parse(k="substr(start='0', end='4')");
		assertNull("Substr", cv.apply(null));
		assertEquals(k,"1234",cv.apply("1234567"));
		assertEquals(k,"67",cv.apply("67"));
		
		cv = Conversion.parse(k="substr(start='-4', end='0')");
		assertEquals(k,"4567",cv.apply("1234567"));
		assertEquals(k,"12", cv.apply("12"));
	}
	
	@Test
	public void joinTests() {
		
		List<Object> val = new ArrayList<>();
		for(int i =1; i <  4; ++i) val.add(i);
		val.add("title:hello");
		
		cv = Conversion.parse(k="join(separator=' OR '),escape ");
		assertEquals(k,"1 OR 2 OR 3 OR title\\:hello",cv.apply(val));
		assertEquals(k,"title\\:hello",cv.apply("title:hello"));
		
		cv = Conversion.parse(k="join(separator=' OR ', postfix='*'),escape ");
		assertEquals(k,"1* OR 2* OR 3* OR title\\:hello*",cv.apply(val));
		assertEquals(k,"title\\:hello*",cv.apply("title:hello"));

		cv = Conversion.parse(k="join(separator=' OR ', prefix='tag:'),quotes ");
		assertEquals(k,"tag:\"1\" OR tag:\"2\" OR tag:\"3\" OR tag:\"title:hello\"",cv.apply(val));
		assertEquals(k,"tag:\"title:hello\"",cv.apply("title:hello"));
	}

}
