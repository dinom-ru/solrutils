package dinom.solr.op;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;
import org.noggit.ObjectBuilder;
import org.w3c.dom.Element;

import dinom.solr.QueryBuilder;
import dinom.solr.SearchModel;
import dinom.solr.Template;

public class SearchOp extends SolrQueryOperator {
	
	SearchModel model;
	
	Map<String, Object> json;
	ArrayList<String> jsonExtra;
	ArrayList<String> export;
	
	Template params;
	String[] collectFields;

	@Override
	public SolrQueryOperator parse(Element el) {
		String s;
		
		s = el.getAttribute("json");
		if(s.isEmpty()) {
			json = new HashMap<>();
		}
		else try {

			json = (Map<String, Object>) ObjectBuilder.fromJSON(s);
			// json structure expected to be static values or
			// values that should be borrowed from passed query JSON
			// Borrowed values should be specified as '${name}' strings
			// 
			for(String key : new ArrayList<>(json.keySet())) {
				
				Object v = json.get(key);
				if(v instanceof String ) {
					String sv = (String)v;
					if(sv.startsWith("${") && sv.endsWith("}")) {
						if(jsonExtra == null) jsonExtra = new ArrayList<>();
						jsonExtra.add(key);
						jsonExtra.add(sv.substring(2, sv.length()-1));
						json.remove(key);
					}
				}
			}
		}
		catch(Exception ex) {
			throw new RuntimeException("Failed to parse 'json' attribute",ex);
		}
		
		s = el.getAttribute("export");
		if(!s.isEmpty()) {
			export = new ArrayList<>();
			// it is either one string such as 'swg' or set of assignments
			// like 'swg=response,split.highlighted=highlighting'
			
			for(String a : s.split(",")) {
				int idx = a.indexOf('=');
				if(idx == -1) {
					export.add(a.trim());
				}
				else {
					export.add(a.substring(0, idx).trim());
					export.add(a.substring(idx+1).trim());
				}
			}
		}
		
		s = el.getAttribute("params");
		if(!s.isEmpty()) params = Template.compile(s);
		
		s = el.getAttribute("collect");
		if(!s.isEmpty()) {
			collectFields = s.split(",");
			for(int i=0; i<collectFields.length; ++i) {
				collectFields[i] = collectFields[i].trim();
			}
		}
		
		if(el.hasChildNodes()) {
			model = new SearchModel(el);
		}
		return this;
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		
		QueryBuilder xq = new QueryBuilder();
	

		SolrQueryResponse rsp = new SolrQueryResponse();
		
		SearchModel model = this.model;
		if(model == null) model = query.handler.getSearchModel();
		
		Map<String,Object> json = this.json;
		if(jsonExtra != null) {
			// clone static values and borrow specified extra
			// from original query JSON.
			
			json = new HashMap<>(json);
			Map<String,Object> queryJson = query.getJSON();
			
			for(int i=1; i < jsonExtra.size(); i+=2) {
				String key = jsonExtra.get(i-1);
				String vKey = jsonExtra.get(i);
				
				Object val = queryJson.get(vKey);
				if(val != null) {
					json.put(key, val);
				}
			}
		}

		RefCounted<SolrIndexSearcher> rs = query.request.getCore().getSearcher();
		// YK: request.getSearcher() can return a new instance if a new searcher was warmed-up during the call
		// Ensure we use the same searcher across the call and decrement its references to avoid resource leak
		LocalSolrQueryRequest req = new LocalSolrQueryRequest(query.request.getCore(), xq){
			@Override
			public SolrIndexSearcher getSearcher() {
				return rs.get();
			}

			// Solr javadoc states that this method <b>must</b>
			// be called when the object is no longer in use
			@Override
			public void close() {
				rs.decref();
			}
		};
		try {
			xq.init(query.handler,req, rsp).build(model, json);
			
			if(params != null) {
				// low level specification of SOLR native parameters, without
				// relevance to model. Useful to set what is not exposed
				// via public model API. Overrides what was set by model
				// if specified.
				
				String qs = params.apply(query.getJSON());
				for(String prm : qs.split("&")) {
					int idx = prm.indexOf('=');
					if(idx != -1) {
						String key = prm.substring(0, idx).trim();
						String val = prm.substring(idx+1).trim();
						if(val.indexOf('%') !=-1) {
							val = URLDecoder.decode(val,"UTF-8");
						}
						xq.set(key, val);
					}
				}
			}

			req.setParams(xq);
			query.request.getCore().execute(query.handler, req, rsp);
		}
		catch(Exception e) {
			throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,e.getMessage(), e);
		} finally {
			req.close();
		}


		NamedList<?> result = rsp.getValues();
		
		if(export != null) {
			if(export.size() == 1) query.response.add(export.get(0), result);
			else {
				
				for(int i=1; i < export.size(); i+=2) {
					String toName = export.get(i-1);
					String fromName = export.get(i);
					query.response.add(toName, result.get(fromName));
				}
			}
			
		}
		else {
			if(collectFields != null) query.currentValue = result;
		}
		if(collectFields != null) {
			boolean singleField = collectFields.length == 1;
			Object o = rsp.getValues().get("response");
		
			ArrayList<Object> lst = new ArrayList<>();
			
			if(o != null && o instanceof ResultContext) {	
				Iterator<SolrDocument> itor = ((ResultContext)o).getProcessedDocuments();
				while(itor.hasNext()) {
					SolrDocument doc = itor.next();
					
					if(singleField) {
						Object val = doc.get(collectFields[0]);
						
						if(val != null) lst.add(val);
					}
					else {
						HashMap<String,Object> map = new HashMap<>();
						for(String fld : collectFields) {
							Object val = doc.get(fld);
							if(val != null) map.put(fld, val);
						}
						if(map.size() > 0) lst.add(map);
					}
				
				}
			}
			query.currentValue = lst;
		}
	}

}
