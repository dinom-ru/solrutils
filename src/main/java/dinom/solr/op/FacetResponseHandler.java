package dinom.solr.op;

import java.util.ArrayList;
import java.util.List;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.response.SolrQueryResponse;

import dinom.solr.QueryBuilder;

public class FacetResponseHandler extends ResponseHandler {
	protected static final String FACET_COUNTS = "facet_counts";
	protected static final String FACETS = "facets";
	
	@Override
	public void process(QueryBuilder xquery, SolrQueryResponse rsp) {
		
		NamedList<Object> values = rsp.getValues();
		NamedList<?> facetCounts = (NamedList<?>)values.get(FACET_COUNTS);
		NamedList<Object> facets = (NamedList<Object>)values.get(FACETS);
		
		if(facets == null) {
			if(facetCounts == null)	return; // facets not used
			
			facets = new SimpleOrderedMap<>();
			rsp.add(FACETS, facets);
		}
		if(facetCounts != null) {
			for(int i=0, max = facetCounts.size(); i < max; ++i) {
				Object val = facetCounts.getVal(i);
				if(val instanceof NamedList) {
					moveCounts(facets,(NamedList<?>) val);
				}
			}
			
			values.remove(FACET_COUNTS);
		}
		
		List<FacetDecorator> decorators = xquery.getFacetDecorators();
		if(decorators != null) {
			for(FacetDecorator dcor : decorators) dcor.decorate(facets);
		}
	}
	
	private void moveCounts(NamedList<Object> dst, NamedList<?> src) {

		for(int i=0, max = src.size(); i < max; ++i) {
			String key = src.getName(i);
			Object val = src.getVal(i);
			
			if(val.getClass() == NamedList.class) {
				NamedList<Object> lst = (NamedList<Object>)val;
				ArrayList<Object> buckets = new ArrayList<>(lst.size());
				for(int j=0; j < lst.size(); ++j) {

					SimpleOrderedMap<Object> item = new SimpleOrderedMap<>(2);
					item.add("val", lst.getName(j));
					item.add("count", lst.getVal(j));
					
					buckets.add(item);
				}
				
				SimpleOrderedMap<Object> result = new SimpleOrderedMap<>(1);
				result.add("buckets", buckets);
				val = result;
			}
			dst.add(key, val);
		}
	}


}
