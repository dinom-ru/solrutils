package dinom.solr;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.HighlightComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.highlight.SolrHighlighter;
import org.apache.solr.search.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Yegor Kozlov
 */
public class SearchSplitComponent extends SearchComponent {
    public static final String SEARCH_SPLIT = "search.split";
    public static final String SEARCH_SPLIT_FQ = "search.split.fq";
    public static final String SEARCH_SPLIT_ROWS = "search.split.rows";
    public static final String SEARCH_SPLIT_KEY = "search.split.key";
    public static final String SEARCH_SPLIT_HL = "split.highlighted";
    public static final String ORIGINAL_FILTERS = "originalFilters";
    public static final String SEARCH_SPLIT_FL = "search.split.fl";


    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        SolrParams params = rb.req.getParams();
        String fq = params.get(SEARCH_SPLIT_FQ);
        if (Boolean.TRUE.equals(params.getBool(SEARCH_SPLIT)) && fq != null) {
            List<Query> filters = rb.getFilters();
            if (filters == null) filters = new ArrayList<>();
            rb.req.getContext().put(ORIGINAL_FILTERS, new ArrayList<>(filters));
            try {
                BooleanQuery.Builder bq = new BooleanQuery.Builder();
                QParser qp = QParser.getParser(fq, rb.req);
                qp.setIsFilter(true);
                Query q = qp.getQuery();
                bq.add(q, BooleanClause.Occur.MUST_NOT);
                filters.add(bq.build());
                rb.setFilters(filters);
                rb.req.getContext().put(SEARCH_SPLIT_FQ, q);
            } catch (SyntaxError e) {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
            }

        }
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        SolrParams params = rb.req.getParams();
        SolrIndexSearcher searcher = rb.req.getSearcher();
        Query query = rb.getQuery();
        if (Boolean.TRUE.equals(params.getBool(SEARCH_SPLIT))) {
            List<Query> filters = (List<Query>) rb.req.getContext().get(ORIGINAL_FILTERS);
            Query fq = (Query) rb.req.getContext().get(SEARCH_SPLIT_FQ);
            filters.add(fq);

            int rows = params.getInt(SEARCH_SPLIT_ROWS, 10);
            DocList docs = searcher.getDocList(query, filters, null, 0, rows, 0);

            String fl = params.get(SEARCH_SPLIT_FL);
            if (fl != null) {
                rb.rsp.setReturnFields(new SolrReturnFields(fl, rb.req));
            }

            String key = params.get(SEARCH_SPLIT_KEY, "split");
            rb.rsp.add(key, docs);

            Iterator<String> it = params.getParameterNamesIterator();
            while(it.hasNext()){
                String name = it.next();
                if(name.startsWith(SEARCH_SPLIT) && name.contains(".hl.")){
                    String hlParam = name.substring(SEARCH_SPLIT.length() + 1);
                    ModifiableSolrParams mp = new ModifiableSolrParams(rb.req.getParams());
                    mp.set(hlParam, params.get(name));
                    mp.remove(name);
                    rb.req.setParams(mp);
                }
            }

            @SuppressWarnings("deprecation")
			SolrHighlighter highlighter = HighlightComponent.getHighlighter(rb.req.getCore());
            String[] defaultHighlightFields = rb.getQparser() != null ? rb.getQparser().getDefaultHighlightFields() : null;
            Query highlightQuery = rb.getHighlightQuery();
            if (highlightQuery != null) {
                NamedList<Object> sumData = highlighter.doHighlighting(
                        docs,
                        highlightQuery,
                        rb.req, defaultHighlightFields);

                if (sumData != null) {
                    rb.rsp.add(SEARCH_SPLIT_HL, sumData);
                }
            }

        }

    }

    @Override
    public String getDescription() {
        return "Search Split Component";
    }
}
