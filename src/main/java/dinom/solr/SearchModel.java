package dinom.solr;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import dinom.solr.op.*;

/**
 * <p>The model of how JSON query will be translated into SolrQuery object.</p>
 * 
 * 
 * <p>Usually the model is described in XML file which should be parsed and passed to 
 * {@link #SolrXSearchModel(Document)} constructor.Typically XML consists of the following:</p>
 * <ul>
 * 	<li>'_open_' tag - describes what should be done when SolrQuery object is just created. A good place
 * for various defaults and invariants.</li>
 * 	<li>All tags that do not start with '_' represent keys that are allowed on top level of JSON query. 
 * For example if XML has &lt;query&gt;...&lt;/query&gt; tag, then it means that it is legal for 
 * JSON to have { "query": ... } key in posted search query. The behavior of what 'query' does depends on
 * 'class' attribute of &lt;query&gt; and the content of the tag. See {@link SolrQueryOperator#newInstance(Element)}
 * for default 'class' attribute values if it is omitted.</li>
 * 	<li>'_close_' - describes actions that should be done <strong>after</strong> JSON search query was transformed into 
 * SolrObject. An example of such action can be check for 'page' parameter presence - as SolrQuery does not support
 * 'page' parameter, it should be properly converted into 'start' SolrQuery parameter.</li>
 *  </ul>
 *  
 * <p>The list of models should be specified by 'com.ceb.search.models' property as
 * comma separated list. By default, the list includes only 'DXM'. For each model name, an attempt is made
 * to load model from 'conf/{name}.xml' file. If it is absent, '{name}.xml' resource is looked for. 
 * Models considered to be immutable objects. After they are parsed at initialization moment (or due to admin
 * command) they will not be modified by usage threads. So, no synchronization is required.</p>. 
 * 
 * @author VF
 */
public class SearchModel {
	private static Logger LOG = LoggerFactory.getLogger(SearchModel.class);
	

	Element root;
	Map<String, SolrQueryOperator> model; 
	
	/**
	 * '_open_' block if any. 'null' is passed as 'value' argument in 
	 * {@link SolrQueryOperator#apply(SolrQueryBuilder, Object) apply} method call.
	 */
	public SolrQueryOperator onopen;
	/**
	 * '_close_' block if any. 'null' is passed as 'value' argument in 
	 * {@link SolrQueryOperator#apply(SolrQueryBuilder, Object) apply} method call.
	 * 
	 * @param query a SolrQuery target, the block should be applied to.
	 */
	public SolrQueryOperator onclose;
	

	public SearchModel(Document document) {
		this(document.getDocumentElement());
	}
	
	public SearchModel(Element root) {
		model = new HashMap<>();
		parse(root);
	}
	
	
	public SearchModel(SearchModel proto) {
		onopen = proto.onopen;
		onclose = proto.onclose;
		model = new HashMap<>(proto.model);
		
		// responseHandler is discarded.
	}
	
	public void parse(Element root) {
		this.root = root;
		
		NodeList nodes = root.getChildNodes();
		
		for(int i=0; i < nodes.getLength(); ++i) {
			Node n = nodes.item(i);
			
			if(n.getNodeType() == Node.ELEMENT_NODE) {
				Element el = (Element) n;
				
				String tagName = el.getTagName();
				
				SolrQueryOperator op = SolrQueryOperator.newInstance(el).parse(el);
				if(op == null) {
					op = SolrQueryOperator.NOOP;
				}
		
				if(tagName.startsWith("_")) {
					// one of predefined operators
					switch(el.getTagName()) {
					case "_open_":
						onopen = op;
						break;
					case "_close_":
						onclose = op;
						break;						
					default:
						LOG.error("unknown predefined operator: {}.",el.getTagName());
					}
				}
				else {
					model.put(el.getTagName(), op);
				}
			}
		}
	}
	
	/**
	 * Get the operator that should be applied to convert JSON query parameter into corresponding
	 * SolrQuery parameters.
	 * 
	 * See {@link SolrQueryOperator#apply(SolrQueryBuilder, Object)} method that will be called.
	 * JSON entry value will be passed as 'value' argument in this call.
	 * 
	 * @param key a String, JSON query top level entry key.
	 * 
	 * @return an operator that should be applied to build a SolrQuery, or 'null' if
	 * no match is found.
	 */
	public final SolrQueryOperator get(String key) {
		return model.get(key);
	}
	
}
