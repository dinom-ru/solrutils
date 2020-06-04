package dinom.solr;

import static org.junit.Assert.assertEquals;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class HtmlReaderTest {
	final static String HTML = "Does <p style=\"color:#000;\" id='my'>it really <br><span>work</span></p>?";
	final static String TEXT = "Does it really work?";
	
	@Test
	public void simpleTest() {
		assertEquals("Trivial", TEXT, Util.html2text(HTML));
	}
	@Test
	public void brTest() throws ParserConfigurationException {
		String text = "<br/>";

		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element el = doc.createElement("test");
		Html2Xml converter = new Html2Xml();

		converter.convert(text, el, doc);
		
		assertEquals("<BR/> Test", "br", el.getChildNodes().item(0).getNodeName());

	}

}
