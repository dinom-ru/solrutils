package dinom.solr;

import java.util.*;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

import dinom.solr.op.Composite;
import dinom.solr.op.FacetDecorator;
import dinom.solr.op.FacetResponseHandler;
import dinom.solr.op.ResponseHandler;
import dinom.solr.op.SolrQueryOperator;


/**
 * <p>A wrapper over SolrQuery object. Most of methods translate the behavior directly into underlying
 * SolrQuery objects.</p>
 *
 * <p>Extras include:</p>
 * <ul>
 * <li>linkage with configuration object (and assigned search model)</li>
 * <li>filter combination into * groups with postponed join using " OR " separator.</li>
 * <li>allows to set and change not only SolrQuery parameters but also 'query type' part of the url.</li>
 * </ul> 
 *
 * @author VF
 */
public class QueryBuilder extends SolrQuery {
	
	private static final long serialVersionUID = 1L;

	/**
	 * Handler that processes search request
	 */
	public CustomSearchHandler handler;
	
	/**
	 * Original request
	 */
	public SolrQueryRequest request;
	
	/**
	 * Original response
	 */
	public SolrQueryResponse response;

	/**
	 * Value that is being passed consequently along {@link Composite} apply chain. 
	 * This is the way how passed value can be changed by preceding element in composite.
	 */
	public Object currentValue;


	ResponseHandler responseHandler;

	/**
	 * JSON object (root) that is processed.
	 */
	Map<String,Object> json;
	
	/**
	 * true if old-fashioned facets were added and 'facet
	 */
	boolean facet;
	/**
	 * to keep JSON facet spec.
	 */
	StringBuilder jsonFacet;
	
	
	ArrayList<FacetDecorator> facetDecorators;

	public QueryBuilder() {
	}
	public QueryBuilder(CustomSearchHandler handler, SolrQueryRequest request, SolrQueryResponse response) {
		init(handler,request,response);
	}
	public QueryBuilder init(CustomSearchHandler handler, SolrQueryRequest request, SolrQueryResponse response) {
		this.handler = handler;
		this.request = request;
		this.response = response;
		
		return this;
	}
	
	/**
	 * @return first handler in response handler chain.
	 */
	public ResponseHandler getResponseHandler() {
		return responseHandler;
	}
	/**
	 * Adds a handler on the top of handler chain. In this way, as handler
	 * chain is single direction chain, model response handlers are not
	 * modified.
	 * 
	 * @param handler ResponseHandler to add.
	 */
	public void addResponseHandler(ResponseHandler handler) {
		handler.next = this.responseHandler;
		this.responseHandler = handler;
	}

	
	public void addFacet(String type, String facetValue, FacetDecorator decorator) {
		switch(type) {
		case "field": 
			add("facet.field",facetValue);
			facet = true;
			break;
		case "query":
			add("facet.query",facetValue);
			facet = true;
			break;
		case "range":
			add("facet.range",facetValue);
			facet = true;
			break;
		case "json":
			if(jsonFacet == null) {
				jsonFacet = new StringBuilder().append("{");
			}
			else jsonFacet.append(", ");
			
			jsonFacet.append(facetValue);
			break;
		}
		if(decorator != null) {
			if(facetDecorators == null) facetDecorators = new ArrayList<>();
			facetDecorators.add(decorator);
		}
	}

	public List<FacetDecorator> getFacetDecorators() {
		return facetDecorators;
	}
	public Map<String,Object> getJSON() {
		return json;
	}
	public Object getValue(String valueRef) {
		if(valueRef == null || valueRef.isEmpty()) return null;
		
		char c = valueRef.charAt(0);
		if(c == '/') {
			//WISH implement nested references like /page/size, or ./sample/value
			return json.get(valueRef.substring(1));
		}
		if( c == '.') {
			if(valueRef.length() > 1 && currentValue != null && currentValue instanceof Map) {
				
				return ((Map<String,Object>)currentValue).get(
						valueRef.substring(valueRef.charAt(1) == '/' ? 2 : 1));
			}
			return null;
		}

		String[] vals = getParams(valueRef);
		if(vals == null) return null;
		if(vals.length == 1) return vals[0];
		
		return Arrays.asList(vals);
	}

	public QueryBuilder build(SearchModel model, Map<String,Object> json) {

		this.json = json;
		this.responseHandler = null;
		
		if(model.onopen != null) {
			model.onopen.apply(this, json);
		}
		for (Map.Entry<?, ?> e : json.entrySet()) {
			String key = e.getKey().toString();

			SolrQueryOperator op = model.get(key);
			if (op == null) {
				if(key.startsWith("__")) {
					// a way to keep references, e.g. SearchOp.PARENT_QUERY
					continue;
				}
				throw new IllegalArgumentException("Undefined query element: " + key);
			}

			op.apply(this, e.getValue());
		}
		if(facet) {
			set("facet",true);
		}
		if(jsonFacet != null) {
			set("json.facet", jsonFacet.append("}").toString());
		}
		if(facet || facetDecorators != null) {
			addResponseHandler(new FacetResponseHandler());
		}

		if(model.onclose != null) {
			model.onclose.apply(this, json);
		}

		return this;
	}
}