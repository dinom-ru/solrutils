package dinom.solr;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Yegor Kozlov
 */
public class QueryStatsComponent extends SearchComponent {
    private static Logger LOG = LoggerFactory.getLogger(QueryStatsComponent.class);

    private final static SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
    private String defaultPattern = "(\\d{4}-\\d{2}-\\d{2}) .*? d.s.CustomSearchHandler \\[reporting\\] query: (.*?), numFound: (\\d+), time: (\\d+) ms(, spellcheck: (.*))?";

    Pattern ptrn;

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        String logDir = System.getProperty("solr.log.dir");

        Date before, after;
        Date today;
        String v1 = rb.req.getParams().get("before");
        String v2 = rb.req.getParams().get("after");
        try {
            today = fmt.parse(fmt.format(new Date()));
            before = v1 == null ? today : fmt.parse(v1);
            after = v2 == null ? new Date(System.currentTimeMillis() * 1000 * 3600 * 24 * 30) : fmt.parse(v2);
        } catch (ParseException e) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
        }
        long min = rb.req.getParams().getLong("min", 1);
        long limit = rb.req.getParams().getLong("limit", Long.MAX_VALUE);

        List<Path> files = Files.list(Paths.get(logDir))
                .filter(path -> path.getFileName().toString().startsWith("search.log"))
                .filter(path -> {
                    String name = path.getFileName().toString();
                    int idx = name.lastIndexOf(".2");
                    Date date;
                    if (idx != -1) {
                        try {
                            date = fmt.parse(name.substring(idx + 1));
                        } catch (ParseException e) {
                            LOG.error("invalid file name. expected search.log<.yyyy-mm-dd>", e);
                            return false;
                        }
                    } else {
                        date = today;
                    }
                    return date.getTime() >= after.getTime() && date.getTime() <= before.getTime();
                }).collect(Collectors.toList());

        rb.rsp.add("paths", files.stream().map(path -> path.getFileName().toString()).collect(Collectors.toList()));

        if (rb.req.getParams().getBool("allQueries", true)) {
            NamedList<Long> allQueries = collect(files, min, limit, e -> e.numFound > 0, e -> e.spellcheck == null);
            rb.rsp.add("allQueries", allQueries);
        }
        if (rb.req.getParams().getBool("zeroQueries", true)) {
            NamedList<Long> zeroQueries = collect(files, min, limit, e -> e.numFound == 0, e -> e.spellcheck == null);
            rb.rsp.add("zeroQueries", zeroQueries);
        }
        if (rb.req.getParams().getBool("spellcheckerQueries", true)) {
            NamedList<Long> spellcheckerQueries = collect(files, min, limit, e -> e.spellcheck != null);
            rb.rsp.add("spellcheckerQueries", spellcheckerQueries);
        }
    }

    @Override
    public String getDescription() {
        return "Query Stats Component";
    }


    @Override
    public void init(NamedList params) {
        String pattern = (String) params.get("pattern");
        if (pattern == null) pattern = defaultPattern;
        ptrn = Pattern.compile(pattern);
    }

    private NamedList<Long> collect(List<Path> files, long min, long limit,
                                    Predicate<Entry>... filters) throws IOException {
        Stream<String> stream = Stream.empty();
        for (Path path : files) {
            Stream<String> s = Files.lines(path)
                    .parallel()
                    .map(ptrn::matcher)
                    .filter(Matcher::find)
                    .map(Entry::new)
                    .filter(e -> filters == null || Arrays.stream(filters).allMatch(f -> f.test(e)))
                    .map(e -> (e.q + (e.spellcheck == null ? "" : ("{$}" + e.spellcheck))) );

            stream = Stream.concat(stream, s);
        }
        NamedList<Long> stats = new NamedList<>();

        Map<String, Long> counts = stream.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() >= min)
                .sorted((o1, o2) -> -o1.getValue().compareTo(o2.getValue()))
                .limit(limit)
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        }
                        , LinkedHashMap::new));
        stats.addAll(counts);
        return stats;
    }

    private static class Entry {

        private final String q;
        private final int numFound;
        private final String spellcheck;
        private final int time;

        Entry(Matcher m) {
            q = m.group(2);
            numFound = Integer.parseInt(m.group(3));
            time = Integer.parseInt(m.group(4));
            spellcheck = m.group(6);
        }

        @Override
        public String toString() {
            String result = "query: " + q + ", numFound: " + numFound + ", time: " + time + " ms";
            if (spellcheck != null) result += ", spellcheck: " + spellcheck;
            return result;
        }
    }

}
