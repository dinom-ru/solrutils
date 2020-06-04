package dinom.solr.op;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.util.NamedList;
import org.noggit.ObjectBuilder;
import org.w3c.dom.Element;

import dinom.solr.Conversion;
import dinom.solr.QueryBuilder;
import dinom.solr.Template;

public class FacetOp extends SolrQueryOperator {

	private static final String TYPES = "field,query,range,json";
	
	protected String key;
	protected String type;
	
	protected DataDecorator titleDcor;
	
	protected Template template;
	boolean keyParam;
	
	@Override
	public FacetOp parse(Element e) {
		key = e.getTagName();
		
		setType(e.getAttribute("type"));
		if(type == null) {
			throw xmlError("'type' attribute can be one of "+TYPES, e);
		}
		String data = e.getAttribute("data");
		String valueTitle = e.getAttribute("valueTitle");
		
		if(!(data.isEmpty() && valueTitle.isEmpty())) try {
			titleDcor = new DataDecorator(key,data,valueTitle);
		}
		catch(Exception ex) {
			throw xmlError("Failed to parse DataDecorator",ex, e);
		}
		
		template = Template.compile(e.getTextContent());
		if(template instanceof Template.MapValue) {
			keyParam = ((Template.MapValue)template).getNames().contains("key");
		}
		
		return this;
	}

	@Override
	public void apply(QueryBuilder builder, String key, Object value) {
		if(keyParam) {
			Map<String, Object> map;
			
			if(value == null) {
				map = new HashMap<>();
			}
			else if(value instanceof Map) {
				map = (Map<String,Object>)value;
			}
			else {
				map = new HashMap<>();
				map.put("value", value);
			}
			if(!map.containsKey("key")) map.put("key", key);
		}
		String result = template.apply(value);
		
		builder.addFacet(type, result, titleDcor == null 
				? null
				: (this.key == key 
					? titleDcor 
					: new DataDecorator(titleDcor,key)));
	}

	@Override
	public void apply(QueryBuilder query, Object value) {
		apply(query,this.key,value);
	}
	
	public String getKey() {
		return key;
	}

	public String getType() {
		return  type;
	}
	public void setType(String type) {
		if(type == null || type.isEmpty()) return;
		
		if((TYPES+",").contains(type+",")) {	
			this.type = type;
		}
	}
	
	static class DataDecorator extends FacetDecorator {
		Map<String,Object> data;
		
		Conversion valueTitleOp;
		
		public DataDecorator(String key, String data, String valueTitle) throws IOException {
			this.key = key;
			if(data != null && !data.isEmpty()) {
				
				Object obj = ObjectBuilder.fromJSON(data);

				if(obj instanceof Map) {
					this.data = (Map<String,Object>)obj;
				}
				else {
					this.data = new HashMap<>();
					this.data.put("data", obj);
				}
			}
			
			if(valueTitle != null && !valueTitle.isEmpty()) {
				valueTitleOp = Conversion.parse(valueTitle);
			}
		}
		
		public DataDecorator(DataDecorator proto, String key) {
			data = proto.data;
			valueTitleOp = proto.valueTitleOp;
			this.key = key;
		}

		@Override
		public void decorate(NamedList<Object> facets) {
			
			Object obj = facets.get(key);
			if(obj == null || !(obj instanceof NamedList)) {
				return;
			}
			
			NamedList<Object> lst = (NamedList<Object>) obj;
			if(data != null) {
				for(Map.Entry<String, Object> e : data.entrySet()) {
					lst.add(e.getKey(), e.getValue());
				}
			}
			if(valueTitleOp != null) {
				obj = lst.get("buckets");
				
				if(obj == null || !(obj instanceof List)) {
					return;
				}
				List<Object> buckets= (List<Object>) obj;
				
				for(int i=0; i < buckets.size(); ++i) {
					obj = buckets.get(i);
					
					if(obj instanceof NamedList) {
						lst = (NamedList<Object>)obj;
						
						Object val = ((NamedList<Object>) obj).get("val");
						if(val != null) {
							lst.add("title", valueTitleOp.apply(val));
						}
					}
				}
			}
			
		}
		
	}
}
