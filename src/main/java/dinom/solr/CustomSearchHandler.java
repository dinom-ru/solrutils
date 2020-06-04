package dinom.solr;


import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.LocalSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.servlet.HttpSolrCall;
import org.noggit.JSONParser;
import org.noggit.ObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.w3c.dom.Document;

import dinom.solr.op.ResponseHandler;

public class CustomSearchHandler extends SearchHandler {
	private static Logger LOG = LoggerFactory.getLogger(CustomSearchHandler.class);

	String pathToModel;
	SearchModel model;
	Throwable modelError;
	String requestTraceHeader;

	public SearchModel getSearchModel() {
		return model;
	}

	@Override
	public void init(PluginInfo info) {

		super.init(info);
		pathToModel = info.attributes.get("model");
		if (pathToModel == null) {
			pathToModel = info.attributes.get("name").substring(1) + "-model.xml";
		}
		requestTraceHeader = invariants.get("requestTrackHeader");
	}

	@Override
	public void inform(SolrCore core) {
		super.inform(core);

		//parse used query model
		LOG.info("loading search model: {}", pathToModel);
		try (InputStream is = core.getResourceLoader().openResource(pathToModel)) {

			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
			model = new SearchModel(doc);

		} catch (Exception e) {
			LOG.error("Failed to open and parse " + pathToModel, modelError = e);
		}
	}

	private String getRequestHeader(SolrQueryRequest req, String name){
		HttpSolrCall hc = req.getHttpSolrCall(); // can  be null in recursive calls
		HttpServletRequest hr = hc == null ? null : hc.getReq();
		if(hr != null){
			Enumeration<String> en = hr.getHeaders(name);
			StringBuilder out = new StringBuilder();
			while(en.hasMoreElements()){
				String val = en.nextElement();
				if(out.length() > 0) out.append(',');
				out.append(val);
			}
			return out.toString();
		} else {
			return null;
		}
	}

	public void handleRequest(SolrQueryRequest req, SolrQueryResponse rsp) {
		String requestId = requestTraceHeader == null ? null :
				getRequestHeader(req, requestTraceHeader);
		try {
			if(requestId != null) MDC.put(requestTraceHeader, requestId);

			handleJsonRequest(req, rsp);
		} finally {
			if(requestId != null) MDC.remove(requestTraceHeader);
		}
	}

	void handleJsonRequest(SolrQueryRequest req, SolrQueryResponse rsp) {
		if (req instanceof LocalSolrQueryRequest) {
			super.handleRequest(req, rsp);
			return;
		}

		req.setParams(defaults); // temporary to use in case when error (e.g. wt=json)
		long t0 = System.currentTimeMillis();

		if (model == null) {
			String msg = "Query Model is not defined.";
			if (modelError != null) {
				msg += " (ERROR: " + modelError.getClass().getName() + ": " + modelError.getMessage() + ")";
			}
			throw new SolrException(ErrorCode.SERVICE_UNAVAILABLE, msg);
		}

		Map<String, Object> json = null;

		if (req.getContentStreams() != null) for (ContentStream cs : req.getContentStreams()) {

			String contentType = cs.getContentType();
			if (contentType == null || !contentType.contains("/json")) {

				throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
						"Bad contentType for search handler :" + contentType + " request=" + req);
			}
			try {
				String jsonString = IOUtils.toString(cs.getReader());
				LOG.info(jsonString);

				((SolrQueryRequestBase) req).setContentStreams(null);
				Object o;
				try {
					o = ObjectBuilder.fromJSON(jsonString);
				}
				catch(JSONParser.ParseException e) {
					throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
				}
				if (o instanceof Map) {
					json = (Map<String, Object>) o;
				}
				else {
					throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "BAD JSON: {} object expected.");
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e); // no IOException expected at this time.
			}
		}
		if (json == null) {
			json = new HashMap<String, Object>();
		}
		toMap(req.getOriginalParams(), json);



		QueryBuilder xq;
		try {
			xq = new QueryBuilder(this, req, rsp).build(model, json);
		}
		catch(IllegalArgumentException e) {

			throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e.getMessage(), e);
		}

		req.setParams(xq);

		super.handleRequest(req, rsp);

		if (xq.getResponseHandler() != null) {

			for (ResponseHandler handler = xq.getResponseHandler(); handler != null; handler = handler.next) {
				handler.process(xq, rsp);
			}
		}
		if (json.containsKey("debug")) {
			rsp.getResponseHeader().add("params", xq);
		}


		// log total time taken by the request. It will be typically longer than qTime
		rsp.getResponseHeader().add("RTime", (System.currentTimeMillis() - t0));

		// propagate json parameters to the 'params' section of the standard request log
		// webapp=/solr path=/search params={q=risk&rows=0&...} hits=0 status=0 QTime=19363
		NamedList<Object> nl = rsp.getToLog();
		int idx = nl.indexOf("params", 0);
		if (idx != -1) nl.setVal(idx, "{" + xq.toString() + "}");

		searchInfo(json, rsp);
	}

	private static void toMap(SolrParams params, Map<String,Object> json) {
		if(params == null || json == null) return;
		
		Iterator<String> itor = params.getParameterNamesIterator();
		while(itor.hasNext()) {
			String key = itor.next(), v;
			String[] vals = params.getParams(key);
			if(vals == null || vals.length == 0 || (v=vals[0])==null)  continue;
			
			Object val;
			if(vals.length > 1) {
				
				ArrayList<Object> lst = new ArrayList<>(v.length());
				for(String s : vals) lst.add(s);
				val = lst;				
			}
			else val = v;
			
			for(;;) {
				int idx = key.indexOf('.');
				if(idx == -1 || idx + 1 >= key.length()) break;
				char c = key.charAt(idx+1);
				if(c >= '0' && c <= '9') break;
				
				Object o = json.get(v = key.substring(0, idx));
				Map<String,Object> map;
				if(o == null) {
					json.put(v, map = new HashMap<>() );
				}
				else if(o instanceof Map) {
					map = (Map<String,Object>)o;
				}
				else break;
				
				json = map;
				key = key.substring(idx+1);
			}
			
			json.put(key, val);
		}
		
	}

	void searchInfo(Map<String,Object> json, SolrQueryResponse rsp){
		String query = (String)json.get("query");
		long numFound = rsp.getResponse() == null ? 0 : ((ResultContext)rsp.getResponse()).getDocList().matches();

		String collationQuery = null;
		NamedList<?> spellcheck = (NamedList<?>)rsp.getValues().get("spellcheck");
		if(spellcheck != null){
			NamedList<?> collations = (NamedList<?>)spellcheck.get("collations");
			if(collations != null) {
				NamedList<?> firstCollation = (NamedList<?>) collations.get("collation");
				if(firstCollation != null) {
					collationQuery = (String) firstCollation.get("collationQuery");

				}
			}
		}
		LOG.info("[reporting] query: {}, numFound: {}, time: {} ms" +
				(collationQuery == null ? "" : ", spellcheck: {}"),
				query, numFound, rsp.getResponseHeader().get("RTime"), collationQuery);
	}
}
