package ws.palladian.retrieval.search;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ws.palladian.helper.StopWatch;
import ws.palladian.helper.collection.CollectionHelper;
import ws.palladian.helper.constants.Language;
import ws.palladian.helper.io.FileHelper;
import ws.palladian.retrieval.resources.WebContent;
import ws.palladian.retrieval.search.ClueWebSearcher.ClueWebResult;

/**
 * <p>
 * Allows to query the ClueWeb09 (English) corpus. The index path must be supplied via constructor in order for this to
 * return results.
 * </p>
 * 
 * @author David Urbansky
 * @author Philipp Katz
 */
public final class ClueWebSearcher extends AbstractSearcher<ClueWebResult> implements Closeable {

    /** The logger for this class. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClueWebSearcher.class);

    private static final String SEARCHER_NAME = "ClueWeb09";

    private static final String FIELD_WORD_INDEX = "Wordindex";

    private static final String FIELD_WARC_ID = "WARC-TREC-ID";

    public static final class ClueWebResult implements WebContent {

        private final String id;
        private final String content;
        private final float score;

        public ClueWebResult(String id, String content, float score) {
            this.id = id;
            this.content = content;
            this.score = score;
        }

		@Override
		public String getUrl() {
			return null;
		}

		@Override
		public String getTitle() {
			return null;
		}

		@Override
		public String getSummary() {
			return content;
		}

		@Override
		public Date getPublished() {
			return null;
		}
		
		public String getId() {
			return id;
		}
		
		public float getScore() {
			return score;
		}

    }

    /** The Lucene directory instance. */
    private final Directory directory;

    /**
     * <p>
     * Create a new {@link ClueWebSearcher} for the specified Lucene index.
     * </p>
     * 
     * @param indexPath The path to the Lucene index, not <code>null</code>.
     * @throws IllegalStateException In case the index cannot be read/accessed.
     */
    public ClueWebSearcher(File indexPath) {
        Validate.notNull(indexPath, "indexPath must not be null");
        try {
            directory = new SimpleFSDirectory(indexPath);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<ClueWebResult> search(String query, int resultCount, Language language) throws SearcherException {
        StopWatch stopWatch = new StopWatch();
        List<ClueWebResult> rankedDocuments = CollectionHelper.newArrayList();
        IndexReader indexReader = null;
        try {
            indexReader = DirectoryReader.open(directory);
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            Query luceneQuery = createQuery(query);
            TopScoreDocCollector collector = TopScoreDocCollector.create(resultCount, false);
            indexSearcher.search(luceneQuery, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;

            for (int i = 0; i < hits.length; i++) {
                int docId = hits[i].doc;
                Document doc = indexSearcher.doc(docId);
                float score = hits[i].score;
                String id = doc.get(FIELD_WARC_ID);
                String content = doc.get(FIELD_WORD_INDEX);
                rankedDocuments.add(new ClueWebResult(id, content, score));
            }
        } catch (ParseException e) {
            throw new SearcherException(e);
        } catch (IOException e) {
            throw new SearcherException(e);
        } finally {
            FileHelper.close(indexReader);
            LOGGER.debug("search took {}", stopWatch);
        }
        return rankedDocuments;
    }

    private final Query createQuery(String query) throws ParseException {
        SimpleAnalyzer analyzer = new SimpleAnalyzer(Version.LUCENE_42);
        return new QueryParser(Version.LUCENE_42, FIELD_WORD_INDEX, analyzer).parse(query);
    }

    @Override
    public String getName() {
        return SEARCHER_NAME;
    }

    @Override
    public long getTotalResultCount(String query, Language language) throws SearcherException {
        StopWatch stopWatch = new StopWatch();
        IndexReader indexReader = null;
        try {
            indexReader = DirectoryReader.open(directory);
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            Query luceneQuery = createQuery(query);
            TotalHitCountCollector collector = new TotalHitCountCollector();
            indexSearcher.search(luceneQuery, collector);
            return collector.getTotalHits();
        } catch (ParseException e) {
            throw new SearcherException(e);
        } catch (IOException e) {
            throw new SearcherException(e);
        } finally {
            FileHelper.close(indexReader);
            LOGGER.debug("getTotalResultCount took {}", stopWatch);
        }
    }

    @Override
    public void close() throws IOException {
        directory.close();
    }

    public static void main(String[] args) throws SearcherException {
        ClueWebSearcher searcher = new ClueWebSearcher(new File("/Volumes/SAMSUNG/ClueWeb09"));
        // List<ClueWebResult> result = searcher.search("Philipp", 10);
        // CollectionHelper.print(result);

        System.out.println(searcher.getTotalResultCount("\"Zentralfriedhof\""));
        System.out.println(searcher.getTotalResultCount("Plants"));
        System.out.println(searcher.getTotalResultCount("Thomas"));
        System.out.println(searcher.getTotalResultCount("League"));
        System.out.println(searcher.getTotalResultCount("Lee"));
        System.out.println(searcher.getTotalResultCount("Supa"));
        System.out.println(searcher.getTotalResultCount("Randall"));
        System.out.println(searcher.getTotalResultCount("Martha"));
        System.out.println(searcher.getTotalResultCount("Barlow"));
        System.out.println(searcher.getTotalResultCount("Linda"));
        System.out.println(searcher.getTotalResultCount("Sullivan"));
        System.out.println(searcher.getTotalResultCount("Bush"));
        System.out.println(searcher.getTotalResultCount("Turner"));
        System.out.println(searcher.getTotalResultCount("George"));
        System.out.println(searcher.getTotalResultCount("\"San Francisco\""));
        System.out.println(searcher.getTotalResultCount("Cannes"));
        System.out.println(searcher.getTotalResultCount("Manchester"));
        System.out.println(searcher.getTotalResultCount("Hawaii"));
        System.out.println(searcher.getTotalResultCount("Glashütte"));
        System.out.println(searcher.getTotalResultCount("Nelson"));
        System.out.println(searcher.getTotalResultCount("Raby"));
        System.out.println(searcher.getTotalResultCount("Ragland"));
        System.out.println(searcher.getTotalResultCount("Stuttgart"));
        System.out.println(searcher.getTotalResultCount("Colombo"));
        System.out.println(searcher.getTotalResultCount("Georgia"));
        System.out.println(searcher.getTotalResultCount("Denton"));
        System.out.println(searcher.getTotalResultCount("Vaduz"));
        System.out.println(searcher.getTotalResultCount("\"New York\""));
        System.out.println(searcher.getTotalResultCount("\"United States\""));
        System.out.println(searcher.getTotalResultCount("Baden-Württemberg"));
        System.out.println(searcher.getTotalResultCount("Sadovyy"));
        System.out.println(searcher.getTotalResultCount("Flein"));
        System.out.println(searcher.getTotalResultCount("Heilbronn"));
        System.out.println(searcher.getTotalResultCount("Bol’sheust’ikinskoye"));
        System.out.println(searcher.getTotalResultCount("Neckarsulm"));
        System.out.println(searcher.getTotalResultCount("Europe"));
        System.out.println(searcher.getTotalResultCount("Hannover"));
        System.out.println(searcher.getTotalResultCount("Alabama"));

    }


}
