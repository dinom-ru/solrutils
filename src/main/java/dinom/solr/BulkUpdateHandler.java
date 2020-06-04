package dinom.solr;

import static org.apache.solr.security.PermissionNameProvider.Name.UPDATE_PERM;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.RequestHandlerUtils;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;

public class BulkUpdateHandler extends RequestHandlerBase implements PermissionNameProvider {

	@Override
	public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
		
		String ukey = req.getCore().getLatestSchema().getUniqueKeyField().getName();
		SolrParams params = req.getParams();
		
		String xmlField = params.get("xmlField");
		String xslt = params.get(CommonParams.TR);
		//TODO support script - StatelessScriptUpdateProcessorFactory.getInstance
		
		ArrayList<String> track = params.getBool("quiet",false) ? null : new ArrayList<>();
		boolean dryrun = params.getBool("dryrun", false);
		String substr = params.get("contains");
		
		ArrayList<Query> filters = new ArrayList<>();
		ArrayList<String> fqs = new ArrayList<>();
		
		String q = params.get(CommonParams.Q);
		if(q != null && !q.trim().isEmpty()) {
			fqs.add(q);
		}
		String[] arr = params.getParams(CommonParams.FQ);
		if(arr != null) {
			for(String fq : arr) fqs.add(fq);
		}
		try {
			for(String fq : fqs) {
				QParser prs = QParser.getParser(fq, QParserPlugin.DEFAULT_QTYPE, false, req);
				prs.setIsFilter(true);
				filters.add(prs.getQuery());
			}
		}
		catch(SyntaxError err) {
			throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,err);
		}
		if(filters.size() == 0) {
			throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,"at least one filter is requied.");
		}
	
		SolrIndexSearcher searcher = req.getSearcher();
		
		XsltUpdateHandler.Loader loader = new XsltUpdateHandler.Loader(xmlField);
		loader.init(invariants);
		loader.compile(req.getCore(), "xslt/"+xslt);
		
		SimpleOrderedMap<Object> oResp = new SimpleOrderedMap<>();
		int numFound = 0;


		DocIterator itor = searcher.getDocSet(filters).iterator();
		Exception ex = null;
		
		UpdateRequestProcessorChain processorChain =
		        req.getCore().getUpdateProcessorChain(params);
		UpdateRequestProcessor processor = processorChain.createProcessor(req, rsp);
		
		try {
			while(itor.hasNext()) {
				
				Document doc = searcher.doc(itor.nextDoc());
				String id = doc.get(ukey);
				
				if(id != null) {
					String xml = doc.get(xmlField);
					
					if(xml == null) continue;
					if(substr != null && !xml.contains(substr)) continue;

					ContentStreamBase.ByteArrayStream bStream = new ContentStreamBase.ByteArrayStream(
							xml.getBytes(StandardCharsets.UTF_8),
							id,
							"text/xml; charset=utf-8");
					try {
						if(!dryrun) {
							loader.load(req, rsp, bStream, processor);
						}
						
						++numFound;
						if(track != null) track.add(id);
						/*
						if(numFound == 1) {
							
							SimpleOrderedMap<Object> map = new SimpleOrderedMap<>();
							IndexSchema schema = req.getCore().getLatestSchema();
							
							for(IndexableField fl : doc) {
								String name = fl.name();
								SchemaField sfl = schema.getField(name);
								
								Object val ;
								if(sfl == null) val = fl.stringValue();
								else val = sfl.getType().toObject(fl);
								
								if(val == null) val = "???";
								else if(val instanceof String && val.toString().length() > 64) {
									val = val.toString().substring(0, 64)+"...";
								}
								map.add(fl.name(),val);
							}
							rsp.add("example", map);
						}*/
					}
					catch(Exception e) {
						ex = new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Failure for "+id, e);
						break;
					}
				}
			}
			oResp.add("numFound", numFound);
			if(dryrun) {
				oResp.add("dryrun", "true");
			}
			if(track != null) {
				oResp.add("ids",track);
			}
			rsp.add("response",oResp);
			
			if(ex != null) throw ex;
			
			RequestHandlerUtils.handleCommit(req, processor, params, false);

		} finally {

			// finish the request
			try {
				processor.finish();
			} finally {
				processor.close();
			}

			// YK: closing the searcher results in a org.apache.lucene.store.AlreadyClosedException
			// in subsequenet requests
			// searcher.close();
		}
	}
	
	@Override
	public PermissionNameProvider.Name getPermissionName(AuthorizationContext ctx) {
		return UPDATE_PERM;
	}

	@Override
	public String getDescription() {
		return "Updates set of documents in collection using various ways";
	}


}
