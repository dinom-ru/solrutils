package dinom.solr;

import java.net.URI;

import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


public class Html2Xml extends HtmlReader {
	public static final int STYLE_KEEP = 1;
	public static final int STYLE_IGNORE = 2;
	public static final int STYLE_DECODE = 3;
	
	private URI baseUri;
    private StringBuilder buf = new StringBuilder();

    private boolean copyClass = true;
    private int doStyle = STYLE_KEEP;
    
    private Logger log;
    
    public Html2Xml copyClassAttribute(boolean value) {
    	copyClass = value; 
    	return this;
    }
    
    public Html2Xml setLogger(Logger logger) {
    	log = logger;
    	return this;
    }
    /**
     * If set, local URLs will be conv
     * @param uri base URI to use when coverting local URLs.
     * @return this instance
     */
    public Html2Xml setBaseUri( URI uri) {
    	baseUri = uri;
    	return this;
    }
    /**
     * 
     * @param value {@link #STYLE_KEEP} or {@link #STYLE_IGNORE} or {@link #STYLE_DECODE}
     */
    public Html2Xml doStyleAttribute(int value) {
    	doStyle = value;
    	return this;
    }

    public void convert(String html, Element el) {
    	convert(html,el, el.getOwnerDocument());
    	
    }
	public void convert(String html, Element el, Document doc) {
    	buf.delete(0, buf.length());
    	setText(html);
    	int p = 0, max = html.length();
    	
        for(int i=0;; p=i) {
            i = next(i, max);
            if(i == -1) {
                break;
            }
            int ts = tag.getStart() - 1;
            if(p < ts) {
                el.appendChild(doc.createTextNode(
                		unescape(html.substring(p,ts))));
            }
            
            if(tag.isTag()) {
            	String tName = tag.getName().toLowerCase();
            	Element e = doc.createElement(tName);
            	el.appendChild(e);
            	tag.doAttributes((key,val) -> addAttr(e,key,val));
            	
            	if(!inlineTags.contains(tName)) {
            		el = e;
            	}
            }
            else if (tag.isEndTag()) {
            	if(el.getTagName().equals(tag.getName().toLowerCase())) {
            		el = (Element) el.getParentNode();
            	}
            }
		}
        if(p < max) {
        	String s = html.substring(p);
        	if(!s.trim().isEmpty()) {
        		el.appendChild(doc.createTextNode(unescape(s)));
        	}
        }
	}
	
	private void addAttr(Element el, String name, String value) {
		switch(name) {

		case "style":
			addStyle(value,el); 
			break;
		case "href":
			addRef(value,el);
			break;
		case "class":
			if(copyClass) {
				el.setAttribute("class", value);
			}
			break;
		case "title": case "id":
			el.setAttribute(name, value); // copy silently
			break;
		case "name":
			el.setAttribute("id", value); 
			break;
		case "adhocenable":
		case "valign":
			break; // silently ignore
		case "height":
			if(!value.endsWith("%")) {
				if((value = toEm(value)) != null) {
					el.setAttribute("height",value);
				}
			}
			break;
		case "width":
			Element tr = (Element)el.getParentNode();
			if("tr".equals(tr.getTagName())){
				Element tbl = (Element)tr.getParentNode().getParentNode();
				if(tbl.getTagName().startsWith("table")) {
					String key = "column"+tr.getChildNodes().getLength();
					if(!tbl.hasAttribute(key)) {
						tbl.setAttribute(key, value);
					}
				}
			}
			break;
		default:
			// ignore but log to be visible for investigation
			//TODO LOG.debug("Attribute ignored: {}",name)
			//System.out.println("Attribute ignored: "+name);
			break;
		}
	}
    private void addRef(String value, Element el) {
    	
    	value = unescape(value);
    	
    	if(!(value.startsWith("#") || value.startsWith("http"))) {
    		
    		if(baseUri != null) try {
    			value = baseUri.resolve(value.replace(" ", "%20")).toString();
    		}
    		catch(IllegalArgumentException e) {
    			if(log != null) {
    				log.debug("Invalid 'href' value: {}", value);
    			}
    		}
    	}
    	el.setAttribute("href", value);
    }
    private void addStyle(String value, Element el) {
    	if(doStyle == STYLE_KEEP) {
    		el.setAttribute("style", value);
    		return;
    	}
    	else if(doStyle == STYLE_IGNORE) {
    		return;
    	}
    	for(String css : value.toLowerCase().split(";")) {
			int idx = css.indexOf(':');
			if(idx > 0) {
				String cssName = css.substring(0,idx).trim();
				String cssValue = css.substring(idx+1).trim();
				
				if(cssValue.endsWith("px")) {
					cssValue = toEm(cssValue.substring(0, cssValue.length()-2));
					if(cssValue == null) {
						if(log != null) log.debug("Integer expected for 'px' value: {}",value);
						continue;
					}
				} 					
				el.setAttribute(cssName, cssValue);
			}
		}
    }
    private String toEm(String pixels) {
		try {
			int px = Integer.parseInt(pixels);
			String val = String.valueOf(((double)px)/18.0); // about 18 pixels per 'em'
			if(val.length() > 4) val = val.substring(0, 4);
			return val+"em"; 
		}
		catch(NumberFormatException e) {
			return null;
		}
    }
   

}
