package dinom.solr;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.JSONResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.search.ReturnFields;


public class JSONDocsWriter extends JSONResponseWriter {

	@Override
	public void write(Writer writer, SolrQueryRequest req, SolrQueryResponse rsp) throws IOException {
		
		Object r = rsp.getValues().get("response");
		if(r == null || !(r instanceof ResultContext)) {
			super.write(writer, req, rsp);
			return;
		}
				
		DocsWriter w = new DocsWriter(writer, req, rsp);
		try {
			w.writeVal("docs", ((ResultContext)r).getProcessedDocuments());
		}
		finally {
			w.close();
		}
	}
	
	static class DocsWriter extends TextResponseWriter {

		public DocsWriter(Writer writer, SolrQueryRequest req, SolrQueryResponse rsp) {
			super(writer, req, rsp);
		}

		@Override
		public void writeStartDocumentList(String name, long start, int size, long numFound, Float maxScore)
				throws IOException {
		}
		@Override
		public void writeEndDocumentList() throws IOException {
		}
		@Override
		public void writeNamedList(String name, NamedList val) throws IOException {
			writeStr(name, "NamedList?", false);
		}

		@Override
		public void writeSolrDocument(String name, SolrDocument doc, ReturnFields fields, int idx) throws IOException {
			if (idx > 0)
				writer.write(',');

			writer.write('{');

			boolean first = true;
			for (String fname : doc.getFieldNames()) {
				
				if (returnFields != null && !returnFields.wantsField(fname)) 
					continue;

				if (first) first = false;
				else writer.write(',');

				indent();
				writeKey(fname, true);
				Object val = doc.getFieldValue(fname);

				if (val instanceof List) {
					writeArray(name, ((Iterable<?>) val).iterator());
				} else {
					writeVal(fname, val);
				}
			}
		    writer.write('}');
		}

		@Override
		public void writeStr(String name, String val, boolean needsEscaping) throws IOException {
			// it might be more efficient to use a stringbuilder or write substrings
			// if writing chars to the stream is slow.
			if (needsEscaping) {

				/*
				 * http://www.ietf.org/internet-drafts/draft-crockford-jsonorg-json-04.txt All
				 * Unicode characters may be placed within the quotation marks except for the
				 * characters which must be escaped: quotation mark, reverse solidus, and the
				 * control characters (U+0000 through U+001F).
				 */
				writer.write('"');

				for (int i = 0; i < val.length(); i++) {
					char ch = val.charAt(i);
					if ((ch > '#' && ch != '\\' && ch < '\u2028') || ch == ' ') { // fast path
						writer.write(ch);
						continue;
					}
					switch (ch) {
					case '"':
					case '\\':
						writer.write('\\');
						writer.write(ch);
						break;
					case '\r':
						writer.write('\\');
						writer.write('r');
						break;
					case '\n':
						writer.write('\\');
						writer.write('n');
						break;
					case '\t':
						writer.write('\\');
						writer.write('t');
						break;
					case '\b':
						writer.write('\\');
						writer.write('b');
						break;
					case '\f':
						writer.write('\\');
						writer.write('f');
						break;
					case '\u2028': // fallthrough
					case '\u2029':
						unicodeEscape(writer, ch);
						break;
					// case '/':
					default: {
						if (ch <= 0x1F) {
							unicodeEscape(writer, ch);
						} else {
							writer.write(ch);
						}
					}
					}
				}

				writer.write('"');
			} else {
				writer.write('"');
				writer.write(val);
				writer.write('"');
			}
		}

		@Override
		public void writeMap(String name, Map val, boolean excludeOuter, boolean isFirstVal) throws IOException {
			if (!excludeOuter) {
				writer.write('{');
				incLevel();
				isFirstVal = true;
			}

			boolean doIndent = excludeOuter || val.size() > 1;

			for (Map.Entry<?, ?> entry : (Set<Map.Entry<?, ?>>) val.entrySet()) {
				Object e = entry.getKey();
				String k = e == null ? "" : e.toString();
				Object v = entry.getValue();

				if (isFirstVal)
					isFirstVal = false;
				else
					writer.write(',');

				if (doIndent) indent();
				writeKey(k, true);
				writeVal(k, v);
			}

			if (!excludeOuter) {
				decLevel();
				writer.write('}');
			}
		}

		protected void writeKey(String fname, boolean needsEscaping) throws IOException {
			writeStr(null, fname, needsEscaping);
			writer.write(':');
		}
		
		@Override
		public void writeArray(String name, Iterator val) throws IOException {
			writer.write("[");
			writeJsonIter(val);
			writer.write("]");
		}
		private void writeJsonIter(Iterator<?> val) throws IOException {
			incLevel();
			boolean first = true;
			while (val.hasNext()) {
				if (!first)
					indent();
				writeVal(null, val.next());
				if (val.hasNext())
					writer.write(",");
				first = false;
			}
			decLevel();
		}
		@Override
		public void writeNull(String name) throws IOException {
			writer.write("null");
		}
		@Override
		public void writeInt(String name, String val) throws IOException {
			writer.write(val);
		}
		@Override
		public void writeLong(String name, String val) throws IOException {
			writer.write(val);
		}
		@Override
		public void writeBool(String name, String val) throws IOException {
			writer.write(val);	
		}
		@Override
		public void writeFloat(String name, String val) throws IOException {
			writer.write(val);
		}
		@Override
		public void writeDouble(String name, String val) throws IOException {
			writer.write(val);
		}
		@Override
		public void writeDate(String name, String val) throws IOException {
			writeStr(name, val, false);
		}
		
	}

	private static char[] hexdigits = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };

	protected static void unicodeEscape(Appendable out, int ch) throws IOException {
		out.append('\\');
		out.append('u');
		out.append(hexdigits[(ch >>> 12)]);
		out.append(hexdigits[(ch >>> 8) & 0xf]);
		out.append(hexdigits[(ch >>> 4) & 0xf]);
		out.append(hexdigits[(ch) & 0xf]);
	}
}
