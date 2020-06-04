package dinom.solr;

import static org.apache.solr.security.PermissionNameProvider.Name.UPDATE_PERM;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.common.util.XMLErrorLogger;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.ContentStreamHandlerBase;
import org.apache.solr.handler.loader.ContentStreamLoader;
import org.apache.solr.handler.loader.XMLLoader;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.util.SystemIdResolver;
import org.apache.solr.util.plugin.PluginInfoInitialized;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class XsltUpdateHandler extends ContentStreamHandlerBase implements SolrCoreAware, PermissionNameProvider, PluginInfoInitialized {
	
	private static Logger LOG = LoggerFactory.getLogger(XsltUpdateHandler.class);

	String xslt;
	Loader loader;
	String requestTraceHeader;

	@Override
	public void init(PluginInfo info) {
		init(info.initArgs);
		
	    // Only invariants list is worked with. It can not be changed by request parameters.
		String xmlField = null;
	    if(invariants != null ) {
	    	xslt = invariants.get(CommonParams.TR);
	    	xmlField = invariants.get("xmlField");
	    }
	    if(xslt == null) {
	    	xslt = info.attributes.get("name").substring(1)+".xsl";
	    }
		requestTraceHeader = invariants.get("requestTrackHeader");

	    (loader = new Loader(xmlField)).init(invariants);
	}

	@Override
	public void inform(SolrCore core) {

		loader.compile(core, "xslt/" + xslt);
	}

	@Override
	public PermissionNameProvider.Name getPermissionName(AuthorizationContext ctx) {
		return UPDATE_PERM;
	}

	@Override
	public String getDescription() {
		return "Add documents using XML (with XSLT), allow custom functions";
	}
	
	@Override
	protected ContentStreamLoader newLoader(SolrQueryRequest req, final UpdateRequestProcessor processor) {
		
		return loader;
	}

	@Override
	public void handleRequest(SolrQueryRequest req, SolrQueryResponse rsp) {
		String requestId = requestTraceHeader == null ? null :
				req.getHttpSolrCall().getReq().getHeader(requestTraceHeader);
		try {
			if(requestId != null) MDC.put(requestTraceHeader, requestId);

			super.handleRequest(req, rsp);
		} finally {
			if(requestId != null) MDC.remove(requestTraceHeader);
		}
	}

	public static class Loader extends XMLLoader {
		
		protected String xmlField;
		protected Templates templates;
		private Exception compileErr;
		
		public Loader(String xmlField) {
			this.xmlField = xmlField;
		}
		public boolean isValid() {
			return templates != null;
		}
		
		public String getXmlField() {
			return xmlField;
		}
		
		public void compile(SolrCore core, String filename) {
			
			ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
			
			try(InputStream is = core.getResourceLoader().openResource(filename)) {
				
				final StreamSource src = new StreamSource(is,
						SystemIdResolver.createSystemIdFromResourceName(filename));
				
				final TransformerFactory tFactory = TransformerFactory.newInstance();
				tFactory.setURIResolver(new SystemIdResolver(core.getResourceLoader()).asURIResolver());
				tFactory.setErrorListener(new XMLErrorLogger(LOG));
				Thread.currentThread().setContextClassLoader(core.getMemClassLoader());
				
				templates = tFactory.newTemplates(src);
			}
			catch (Exception e) {
				LOG.error("Failed to open and compile "+filename, compileErr = e);
			}
			finally {
				Thread.currentThread().setContextClassLoader(ctxLoader);
			}
		}

		@Override
		public void load(SolrQueryRequest req, SolrQueryResponse rsp, ContentStream stream,
				UpdateRequestProcessor processor) throws Exception {
			
			if(templates == null) {
				throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "XSLT template not initialized", compileErr);
			}
			
			ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(req.getCore().getMemClassLoader());
				
				req.getContext().put(CONTEXT_TRANSFORMER_KEY, templates.newTransformer());
			}
			finally {
				Thread.currentThread().setContextClassLoader(ctxLoader);
			}
			
			if(xmlField != null) {
				// if to storage of posted xml is required, read all bytes, wrap processor
				// into another one that adds xml as document field.
				
				InputStream is = stream.getStream();
				byte[] bytes;
				try {
					bytes = IOUtils.toByteArray(is);
				}
				finally {
					IOUtils.closeQuietly(is);
				}
				
				String charset = ContentStreamBase.getCharsetFromContentType(stream.getContentType());
				if(charset == null) {
					charset = detectCharset(bytes);
				}
				final String contentXml = new String(bytes,charset);

				@SuppressWarnings("resource")
				UpdateRequestProcessor xmlSaver = new UpdateRequestProcessor(processor) {
					int count = 0;
					
					@Override
					public void processAdd(AddUpdateCommand cmd) throws IOException {
						if(++count > 1) {
							throw new IOException("Posted XML contains more than one document.");
						}
						cmd.solrDoc.setField(xmlField, contentXml);
						super.processAdd(cmd);
					}
				};
				
				processor = xmlSaver;
				
				ContentStreamBase.ByteArrayStream bStream = new ContentStreamBase.ByteArrayStream(
						bytes, stream.getName(), "text/xml; charset="+charset);
				bStream.setSourceInfo(stream.getSourceInfo());
				stream = bStream;
			}
			super.load(req, rsp, stream, processor);
		}
	}
	
	private static String detectCharset(byte[] data) {
		int len = data == null ? 0 : data.length, i, b;
		if(len == 0) return null;
		b = 0xFF & data[i=0];
		switch(b) {
		case 0xFF:
			if(len > 1 && (0xFE == (0xFF & data[1]) )) 
				return "utf-16";
			break;
		case 0xFE:
			if(len > 1 && (0xFF == (0xFF & data[1])) ) 
				return "utf-16";
			break;
		case 0xEF:
			if(len > 3 && (0xBB == (0xFF & data[1])) && (0xBF == (0xFF & data[2])) )
				return "utf-8";
			break;
		case '<':
			// XML prolog.
			if(len > 1 && data[1] == '?') {
				i=2;
				while(i < len) if(data[i++] == '>' && data[i-2] == '?') break;
				if(i > 2) {
					char[] arr = new char[i];
					for(int j=0; j < i; ++j) arr[j] = (char) data[i];
					String prolog = new String(arr);
					
					String anchor = "encoding=";
					i = prolog.indexOf(anchor,2);
					
					if(i != -1) {
						i += anchor.length();
						len = prolog.length();
						if( i + 2 < len) {
							char c = prolog.charAt(i++);
							int p = prolog.indexOf(c, i);
							if(p != -1) {
								return prolog.substring(i, p).trim();
							}
						}
					}
				}
			}
			break;
		}
		return "utf-8";
	}
}
