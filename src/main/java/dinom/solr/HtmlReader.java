package dinom.solr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiConsumer;



public class HtmlReader {
	
    String text, lcaseText;
    protected TagInfo tag = new TagInfo();
    
    private boolean iCase=true;
    
    public void setText(String aText) {
        text = aText;
        lcaseText = iCase ? aText.toLowerCase() : aText;
    }

    public boolean ignoresCase() {
        return iCase;
    }
    public void ignoreCase(boolean ignore) {
        iCase = ignore;
        if(text != null) {
            setText(text);
        }
    }
    
    public int next(int i, int max) {
        int j;
        while(i < max) {
            if(text.charAt(i++) != '<' || i >= max) {
                continue;
            }
            char c = text.charAt(i);
            if(c == '!') {
                // comments
                if(text.startsWith("!--",i)) {
                    j = text.indexOf("-->",i);
                    if(j == -1 || j+3 > max) {
                        break;
                    }
                    tag.reset("!--", i, 0);
                    tag.end = j + 3;
                    return tag.end;
                }
                // rare type of comment, actually not officially legal, skip it.
                j = text.indexOf(">",i+1);
                if(j == -1 || j+1 > max) {
                    break;
                }
                tag.reset("!", i, 0);
                tag.end = j+1;
                return tag.end;
            }
            if(c == '/') {
                j = text.indexOf('>', i+1);
                if(j == -1 || j+1 > max) {
                    break;
                }
                tag.reset(lcaseText.substring(i+1,j), i, 2);
                tag.end = j+1;
                return tag.end;
            }
            if(!Character.isLetter(c)) {
                continue;
            }
            parseAttributes(i, max);

            return processTag();
        }
        return -1;
    }
    /**
    *
    * @param i - an Integer, position where the name starts.
    * @param max - an Integer, maximum position upto which parse is allowed
    * to be made.
    */
	protected int parseAttributes(int i, int max) {
		// parse name
		int p = i+1; 
		char c = '>';
		while (p < max ){
			c = text.charAt(p);
			if(c <= 32 || c == '>' || c == '/') break; 
			++p;
		}
		tag.reset(text.substring(i,p).toLowerCase(), i, 1);
		if(c != '>'){
			++p;
		}
		
		// parse attributes
		
		// 0-means no string, otherwise it is either ' or ", - char that starts the string
		char stringType = 0;
		boolean beginAttr = false;
		
		// name start/end
		int ns = 0, ne = 0;
		for( i = p; i < max; ++i) {
			c = text.charAt(i);
			switch (c) {
			case '>':
				if(stringType != 0){
					// ignore inside strings
					break;
				}
				// add pending attribute
				if(ne > ns){
					tag.addAttr(ns, ne, p, i);
				}
				else if(!text.substring(p,i).trim().isEmpty()){
					tag.addAttr(p, i, i, i);
				}
				
				// end of attributes reached
				return tag.end = i+1;
			case '=':
				if(stringType == 0) {
					ns = p; 
					ne = i;
					p = i+1;
					//beginAttr is set to true after =, to mark beginning of attr value.
					beginAttr = true;
					if(text.substring(ns,ne).trim().isEmpty()){
						ns = ne;
					}
				}
				break;
			case '"': case '\'':
				// set stringType to " or ' only when beginAttr is true, which means 
				// there is no character between = and " or ' except white space.
				if(stringType == 0 && beginAttr) {
					stringType = c; 
					p = i+1; 
				}
				else if(stringType == c) {
					if(ne > ns) {
						tag.addAttr(ns,ne,p,i);
						ns = ne;
					}
					p = i+1;
					stringType = 0;
				}
				break;			
			default:
				// white space
				if(c <= 32 && stringType == 0 && ne > ns) {
					// html attribute without quotes
					if(!text.substring(p,i).trim().isEmpty()) {
						tag.addAttr(ns, ne, p, i);
						ns = ne;
						p = i+1;
					}
				} else{
					// sets beginAttr to false in case of any character other than white spaces.
					beginAttr = false;
				}
				break;				
			}
		}
		return tag.end = max;
	}
    /**
     * Default Tag Handling, returns position for further parsing. In some cases such
     * as scripts and styles can be different from tag end.
     */
    protected int processTag() {
        int pp;
        switch(tag.name) {
        case "script":
            pp = lcaseText.indexOf("</script>",tag.end);
            if(pp == -1) {
                return text.length();
            } else {
                return pp;
            }
        case "style":
            pp = lcaseText.indexOf("</style>",tag.end);
            if(pp == -1) {
                return text.length();
            } else {
                return pp;
            }
        }

        return tag.end;
    }
    
    public String extractText(String html) {
    	
    	StringBuilder out = new StringBuilder();
    	setText(html);
    	int p = 0, max = html.length();
    	
    	for(int i=0;; p=i) {
    		i = next(i, max);
            if(i == -1) {
                break;
            }
            int ts = tag.start - 1;
            if(p < ts) {
            	out.append(
            			unescape(html.substring(p,ts)));
            }
    	}
    	if(p < max) {
        	out.append(unescape(html.substring(p)));
    	}
    	
    	return out.toString();
    }
    
	public class TagInfo {
        /**
         * 0 - comment, 1 - start tag, 2 - end tag.
         */
        private int type;
        private int start, end;
        private String name;
        private int[] attrs = new int[8];
        private int attrEnd;

        private Map<String, Integer> indexMap = new HashMap<String,Integer>();

        public int size() {
            return attrEnd / 4;
        }
        public void reset(String name, int start, int type ) {
            this.start = start;
            this.name = name;
            indexMap.clear();
            attrEnd = 0;
            this.type = type;
        }
        public boolean isTag() {
            return type == 1;
        }
        public boolean isComment() {
            return type == 0;
        }
        public boolean isEndTag() {
            return type == 2;
        }
        public String getName() {
            return name;
        }
        public int getStart() {
            return start;
        }
        public void addAttr(int nameStart, int nameEnd, int valueStart, int valueEnd) {
            if(attrEnd + 4 > attrs.length) {
                int[] arr = new int[attrs.length * 2];
                System.arraycopy(attrs, 0, arr, 0, attrEnd);
                attrs = arr;
            }
            indexMap.put(text.substring(nameStart,nameEnd).trim().toLowerCase(), attrEnd);
            attrs[attrEnd++] = nameStart;
            attrs[attrEnd++] = nameEnd;
            attrs[attrEnd++] = valueStart;
            attrs[attrEnd++] = valueEnd;
        }

        public String getAttr(String name) {
            Integer idx = indexMap.get(name);
            if(idx == null) {
                return null;
            }
            String value = text.substring(attrs[idx+2],attrs[idx+3]).trim();
            return value.isEmpty() ? null : value;
        }
        public void doAttributes(BiConsumer<String, String> op) {
        	
        	for(int i=0; i < attrEnd; i+=4) {
        		
        		String name = text.substring(attrs[i], attrs[i+1]).trim().toLowerCase();
        		String value = text.substring(attrs[i+2], attrs[i+3]);
        		if(!value.isEmpty()) {
        			char fc = value.charAt(0), lc = value.charAt(value.length()-1);
        			if((fc == '"' && lc == '"') || (fc == '\'' && lc=='\'')) {
        				value = value.substring(1, value.length()-1);
        			}
        		}
        		op.accept(name, value);
        	}
        }
        

    }

	@SuppressWarnings("deprecation")
	public static final String unescape(final String text) {
		return org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4(text);
	}
    protected static HashSet<String> inlineTags = new HashSet<String>();
    static {
    	for(String nm : new String[] {"br","input", "embed","meta"}) {
    		inlineTags.add(nm);
    	}
    }
}
