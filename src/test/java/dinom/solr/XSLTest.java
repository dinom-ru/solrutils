package dinom.solr;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;


public class XSLTest {
	
	
	
	public static void main(String[] args) throws Exception {
		
		if(args.length < 2) {
			System.out.println("Usage: XSLTest file template [outFile] ");
			return ;
		}
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		
		Document doc = builder.parse(new File(args[0]));
		
		TransformerFactory tFactory = TransformerFactory.newInstance();
		StreamSource stylesource = new StreamSource(new File(args[1]));
		Transformer transformer = tFactory.newTransformer(stylesource);
		StreamResult result = new StreamResult(System.out);
		
		transformer.transform(new DOMSource(doc), result);
		
	}

}
