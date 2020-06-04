package dinom.solr;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.QueryElevationParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rerank search results by applying predefined filters and elevating the selected documents to top.
 * <p>
 * The component can be enabled|disabled at runtime with the <code>rerank</code> request parameter:
 * http://localhost:8983/solr/emt/select?q=risk&rerank=true|false
 * </p>
 *
 * @author Yegor Kozlov
 */
public class QueryReRankComponent extends SearchComponent {

    public static final String RERANK = "rerank";

    private List<SolrParams> elevationBlocks;
    private String primaryKey;

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        rb.setNeedDocSet(true);
        SolrParams params = rb.req.getParams();
        if(Boolean.TRUE.equals(params.getBool(RERANK))) {
            rerank(rb);
        }
    }

    void rerank(ResponseBuilder rb) throws IOException {
        SolrIndexSearcher searcher = rb.req.getSearcher();

        Query query = rb.getQuery();

        List<String> ids = new ArrayList<>();

        for (SolrParams ev : elevationBlocks) {
            String[] fqs = ev.getParams(CommonParams.FQ);
            int rows = ev.getInt(CommonParams.ROWS, 1);

            List<Query> filters = new ArrayList<>();
            if(rb.getFilters() != null) filters.addAll(rb.getFilters());
            if(fqs != null) for (String fq : fqs) {
                try {
                    QParser fqp = QParser.getParser(fq, rb.req);
                    fqp.setIsFilter(true);
                    filters.add(fqp.getQuery());
                } catch (SyntaxError e) {
                    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
                }
            }
            if (!ids.isEmpty()) {
                // exclude previously collected ids from search, i.e. each n-th elevation block excludes
                // documents collected by  1 ... n-1  elevation bloks
                BooleanQuery.Builder fq = new BooleanQuery.Builder();
                for (String id : ids) {
                    fq.add(new TermQuery(new Term(primaryKey, id)), BooleanClause.Occur.MUST_NOT);
                }
                filters.add(fq.build());
            }
            DocIterator docs = searcher.getDocList(query, filters, null, 0, rows, 0).iterator();
            while (docs.hasNext()) {
                Document doc = searcher.getIndexReader().document(docs.nextDoc());
                String id = doc.getField(primaryKey).stringValue();
                ids.add(id);
            }

        }
        if (!ids.isEmpty()) {
            // delegate the work to the QueryElevationComponent by passing elevateIds=id1,id2,id3,...
            ModifiableSolrParams params = new ModifiableSolrParams(rb.req.getParams());

            params.set(QueryElevationParams.IDS, ids.stream().collect(Collectors.joining(",")));
            rb.req.setParams(params);
        }
    }


    @Override
    public void process(ResponseBuilder rb) throws IOException {

    }

    @Override
    public String getDescription() {
        return "Query Re-rank Sorter";
    }

    
	@SuppressWarnings("rawtypes")
	@Override
    public void init(NamedList params) {
        elevationBlocks = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            String name = params.getName(i);
            Object val = params.getVal(i);
            switch (name) {
                case "primaryKey":
                    primaryKey = (String) val;
                    break;
                case "elevate":
                    elevationBlocks.add(SolrParams.toSolrParams((NamedList) val));
                    break;
            }
        }
        if(primaryKey == null){
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "'primaryKey' is a required configuration parameter");

        }
    }
}
