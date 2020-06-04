package dinom.solr.op;

import org.apache.solr.common.util.NamedList;

/**
 * Object that provides the logic to extend facet result in SOLR response.
 *
 */
public abstract class FacetDecorator {
	
	protected String key;

	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	
	public abstract void decorate(NamedList<Object> facets);	
}