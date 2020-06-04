package dinom.solr.op;

import java.util.Map;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.response.SolrQueryResponse;
import org.noggit.ObjectBuilder;
import org.w3c.dom.Element;

import dinom.solr.QueryBuilder;

public abstract class ResponseHandler extends SolrQueryOperator {
	
	/**
	 * Simplest way to organize multiple processing of response.
	 * When multiple _response_ tags are found in model, 'add' method
	 * should be used.
	 */
	public ResponseHandler next;
	
	public ResponseHandler parse(Element el) {
		return this;
	}
	
	@Override
	public void apply(QueryBuilder query, Object value) {
		query.addResponseHandler(this);	
	}
	
	/**
	 * Adds a handler at the end of response handlers chain.
	 * @param nextHandler - a ResponseHandler to add.
	 */
	public void add(ResponseHandler nextHandler) {
		ResponseHandler h = this;
		while(h.next != null) {
			h = h.next;
		}
		h.next = nextHandler;
	}
	
	public abstract void process(QueryBuilder xquery, SolrQueryResponse rsp);
	
	
	public static class Data extends ResponseHandler {
		Map<String,Object> data;

		@Override
		public ResponseHandler parse(Element el) {
			
			Object obj;
			try {
				obj = ObjectBuilder.fromJSON(el.getTextContent());
			} 
			catch (Throwable ex) {
				throw xmlError("fromJSON failed",ex,el);
			} 
			if(obj instanceof Map) {
				data = (Map<String, Object>) obj;
			}
			else throw xmlError("JSON object expected: {...} ", el);
			
			return this;
		}

		@Override
		public void process(QueryBuilder xquery, SolrQueryResponse rsp) {
			if(data == null) return;
			
			NamedList<Object> vals = rsp.getValues();
			
			for(Map.Entry<String, Object> e : data.entrySet()) {
				vals.add(e.getKey(), e.getValue());
			}
		}
		
	}

}
