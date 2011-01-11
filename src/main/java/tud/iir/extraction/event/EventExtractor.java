package tud.iir.extraction.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import tud.iir.classification.Classifier;
import tud.iir.classification.FeatureObject;
import tud.iir.daterecognition.DateEvaluator;
import tud.iir.daterecognition.DateGetter;
import tud.iir.daterecognition.dates.ExtractedDate;
import tud.iir.extraction.Extractor;
import tud.iir.extraction.content.PageContentExtractor;
import tud.iir.extraction.content.PageContentExtractorException;
import tud.iir.extraction.entity.ner.Annotation;
import tud.iir.extraction.entity.ner.Annotations;
import tud.iir.helper.ConfigHolder;
import tud.iir.helper.DateArrayHelper;
import tud.iir.helper.RatedDateComparator;
import tud.iir.helper.StopWatch;
import tud.iir.helper.StringHelper;
import tud.iir.news.Feed;
import tud.iir.news.FeedItem;
import tud.iir.news.NewsAggregator;
import tud.iir.news.NewsAggregatorException;
import weka.core.stemmers.SnowballStemmer;

import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.Tokenizer;
import com.aliasi.tokenizer.TokenizerFactory;

/**
 * <p>
 * Event Extractor. The Event Extractor extracts the 5W1H answers from a news
 * article.
 * </p>
 * 
 * @author Martin Wunderwald
 * @author David Urbansky
 */
public class EventExtractor extends Extractor {

    /** The logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(EventExtractor.class);

    /** A set of events that have been extracted. */
    private Set<Event> extractedEvents;

    /** Keep track of which news articles we have analyzed for events already. */
    private Set<String> seenNewsArticles = new HashSet<String>();

    /** Number of events to extracts before saving them. */
    private static final int SAVE_COUNT = 5;

    /** The WhereClassfier to classify possible where entities. **/
    private WhereClassifier whereClassifier;

    /** The WhoClassifier to classify possible who entities. **/
    private WhoClassifier whoClassifier;

    /** An instance of the featureExtractor used for text processing. **/
    private EventFeatureExtractor featureExtractor;

    private final String MODEL_WHERE;
    private final String MODEL_WHO;

    /** Experimental deepMode for deeper 5W1H extraction. **/
    private boolean deepMode = false;

    /**
     * @author Martin Wunderwald
     */
    static class SingletonHolder {
        static EventExtractor instance = new EventExtractor();
    }

    /**
     * @return instance of EventExtractor
     */
    public static EventExtractor getInstance() {
        return SingletonHolder.instance;
    }

    /**
     * construtor of this class
     */
    private EventExtractor() {
        super();
        // do not analyze any binary files
        featureExtractor = EventFeatureExtractor.getInstance();

        PropertiesConfiguration config = null;

        config = ConfigHolder.getInstance().getConfig();

        if (config != null) {
            MODEL_WHO = config.getString("models.palladian.en.event.who");
            MODEL_WHERE = config.getString("models.palladian.en.event.where");

        } else {
            MODEL_WHO = "";
            MODEL_WHERE = "";
        }

    }

    /**
     * Creates an event with title and text from an given url.
     * 
     * @param url
     * @return
     */
    public Event createEventFromURL(final String url) {
        Event event = null;
        try {

            PageContentExtractor pce = new PageContentExtractor();
            pce.setDocument(url);
            final String rawText = pce.getResultText();
            event = new Event(pce.getResultTitle(), StringHelper
                    .makeContinuousText(rawText), url);
            event.setRawText(rawText);

        } catch (final PageContentExtractorException e) {

            LOGGER.error(e);
            LOGGER.error("URL not found: " + url);

        }
        return event;
    }

    /**
     * extracts an event from given url and performs the whole 5W1H extraction
     * 
     * @param url
     *            - url of a news article
     * @return Event - The event
     */
    public Event extractEventFromURL(String url) {

        Event event = createEventFromURL(url);

        setWhoClassifier(Classifier.BAGGING);
        setWhereClassifier(Classifier.BAGGING);

        getFeatureExtractor().setFeatures(event);

        extract5W1H(event);

        return event;
    }

    /**
     * extracts the 5W1H from an given event
     * 
     * @param event
     */
    public void extract5W1H(Event event) {

        extractWho(event);
        extractWhat(event);
        extractWhere(event);
        extractWhy(event);
        extractWhen(event);
        extractHow(event);

        LOGGER.info("who:   " + event.getWho());
        LOGGER.info("what:  " + event.getWhat());
        LOGGER.info("where: " + event.getWhere());
        LOGGER.info("when:  " + event.getWhen());
        LOGGER.info("why:   " + event.getWhy());
        LOGGER.info("how:   " + event.getHow());

    }

    /**
     * extracts the when from an given event
     * 
     * @param event
     */
    public void extractWhen(Event event) {

        Map<String, Double> rankedCandidates = new HashMap<String, Double>();

        ArrayList<ExtractedDate> list = new ArrayList<ExtractedDate>();
        DateGetter dg = new DateGetter();
        DateEvaluator dr = new DateEvaluator();
        if (event.getUrl() != null) {
            ArrayList<ExtractedDate> dates = new ArrayList<ExtractedDate>();
            HashMap<ExtractedDate, Double> ratedDates = new HashMap<ExtractedDate, Double>();

            dg.setURL(event.getUrl());
            dg.setTechReference(false);
            dg.setTechArchive(false);
            dg.setTechHTTP(true);
            dg.setTechURL(true);
            dg.setTechHTMLStruct(true);
            dg.setTechHTMLHead(true);
            dg.setTechHTMLContent(true);
            dates = dg.getDate();

            ratedDates = dr.rate(dates);
            list = DateArrayHelper.hashMapToArrayList(ratedDates);

            for (Entry<ExtractedDate, Double> entry : ratedDates.entrySet()) {
                rankedCandidates.put(entry.getKey().getNormalizedDateString(),
                        entry.getValue());
            }
        }
        ExtractedDate date = new ExtractedDate();
        if (list != null && list.size() > 0) {
            ArrayList<ExtractedDate> orderedList = list;
            Collections.sort(orderedList,
                    new RatedDateComparator<ExtractedDate>());
            date = orderedList.get(0);
            event.setWhen(date.getNormalizedDateString());
        }

        // appling Namend Entity Recognition to find DateStrings
        if (this.deepMode) {
            for (Annotation anno : featureExtractor.getDateAnnotations(event
                    .getText())) {
                rankedCandidates.put(anno.getEntity().getName(), 0.5);
            }
        }

        // CollectionHelper.print(rankedCandidates);
        event.setWhenCandidates(rankedCandidates);

    }

    /**
     * extracts the where from an given event
     * 
     * @param event
     */
    public void extractWhere(Event event) {

        final Map<Annotations, FeatureObject> features = event
                .getAnnotationFeatures();

        double[] result = null;

        Map<String, Double> rankedCandidates = new HashMap<String, Double>();

        for (final Entry<Annotations, FeatureObject> entry : features
                .entrySet()) {

            final FeatureObject fo = entry.getValue();

            // only accept location entities for where slot
            if (fo.getFeature("type").equals(
                    EventFeatureExtractor.CATEGORY_LOCATION)) {
                result = whereClassifier.classifySoft(fo);

                rankedCandidates.put(longestTerm(entry.getKey()), result[0]);
            }

        }

        rankedCandidates = sortByValue(rankedCandidates);

        if (rankedCandidates.size() > 0) {
            event.setWhereCandidates(rankedCandidates);
            event.setWhere(rankedCandidates.keySet().toArray()[0].toString());
        } else {
            LOGGER.info("no WHERE was found.");
        }
    }

    static Map<String, Double> sortByValue(Map<String, Double> map) {

        final List<Entry<String, Double>> list = new LinkedList<Entry<String, Double>>(
                map.entrySet());
        Collections.sort(list, new Comparator<Entry<String, Double>>() {
            @Override
            public int compare(Entry<String, Double> obj1,
                    Entry<String, Double> obj2) {
                return ((Comparable<Double>) obj1.getValue()).compareTo(obj2
                        .getValue());
            }
        });

        Collections.reverse(list);

        final Map<String, Double> result = new LinkedHashMap<String, Double>();
        for (Entry<String, Double> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    /**
     * extracts the why from an given event
     * 
     * @param event
     */
    public void extractWhy(Event event) {

        final Map<String, Double> whyCandidates = new HashMap<String, Double>();

        String whatVerb = null;

        if (event.getWhat() != null) {
            final String tagged = featureExtractor.getPOSTags(event.getWhat())
                    .getTaggedString();

            // extracting the verb from whatPhrase
            try {
                final Pattern p = Pattern.compile("(.*?)/VB(.*)");

                final Matcher m = p.matcher(tagged);

                while (m.find()) {
                    for (int i = 1; i <= m.groupCount(); i++) {
                        // LOGGER.info(m.group(i) + " (" + m.start(i) + ","+
                        // m.end(i) + ")");
                        if (whatVerb == null) {
                            whatVerb = m.group(i);
                        }
                    }
                }
            } catch (final PatternSyntaxException e) {
                LOGGER.error("Regex syntax error: " + e.getMessage());
                LOGGER.error("Error description: " + e.getDescription());
                LOGGER.error("Error index: " + e.getIndex());
                LOGGER.error("Erroneous pattern: " + e.getPattern());
                return;
            }
        }
        // @TODO: better weighting on the result .. because should rate higher
        // than whatVerb+
        final String who = event.getWho();
        // System.out.println("who:" + who + ",what:" + event.getWhat() + "("
        // + whatVerb + ")");

        final Map<String, Double> regExpMap = getWhyRegExp(who, whatVerb);
        String taggedStc = null;
        for (final String stc : event.getSentences()) {
            taggedStc = featureExtractor.getPOSTags(stc).getTaggedString();
            for (final Entry<String, Double> entry : regExpMap.entrySet()) {

                final Pattern pattern = Pattern.compile(entry.getKey());
                Double confidence = entry.getValue();
                confidence += StringHelper.calculateSimilarity(stc, who + " "
                        + whatVerb);
                final Matcher mToMatcher = pattern.matcher(taggedStc);
                while (mToMatcher.find()) {
                    whyCandidates.put(stc, confidence);
                }
            }
        }

        // CollectionHelper.print(whyCandidates);
        if (whyCandidates.size() > 0) {
            event.setWhyCandidates(whyCandidates);
            event.setWhy(whyCandidates.keySet().toArray()[0].toString());

        } else {
            LOGGER.info("no WHY was found.");
        }
    }

    private Map<String, Double> getWhyRegExp(String who, String what) {

        final Map<String, Double> regExpMap = new HashMap<String, Double>();

        regExpMap.put("(" + what + "(.*)to/TO [^ \t](.*)/VB)", 0.5);
        // regExpMap.put("(" + who + "(.*)to/TO [^ \t](.*)/VB)", 0.5);
        regExpMap.put("(" + what + "(.*)will)", 0.5);
        regExpMap.put("(since)", 0.2);
        regExpMap.put("(cause)", 0.3);
        regExpMap.put("(because)", 0.3);
        regExpMap.put("(hence)", 0.2);
        regExpMap.put("(therefore)", 0.3);
        regExpMap.put("(why)", 0.2);
        regExpMap.put("(result)", 0.4);
        regExpMap.put("(reason)", 0.3);
        regExpMap.put("(provide)", 0.1);
        regExpMap.put("('s behind)", 0.2);
        regExpMap.put("(Due to)", 0.2);

        return regExpMap;
    }

    public List<String> longestSubstrings(String str1, String str2) {

        final String s1 = StringHelper.removeStopWords(str1);
        final String s2 = StringHelper.removeStopWords(str2);

        final TokenizerFactory mTokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;
        final Tokenizer tokenizer1 = mTokenizerFactory.tokenizer(s1
                .toCharArray(), 0, s1.length());
        final Tokenizer tokenizer2 = mTokenizerFactory.tokenizer(s2
                .toCharArray(), 0, s2.length());

        final List<String> a1 = new ArrayList<String>();
        final List<String> a2 = new ArrayList<String>();
        final List<String> w1 = new ArrayList<String>();
        final List<String> w2 = new ArrayList<String>();

        tokenizer1.tokenize(a1, w1);
        tokenizer2.tokenize(a2, w2);

        // CollectionHelper.print(a1);
        final SnowballStemmer ss = new SnowballStemmer();

        for (int i = 0; i < a1.size(); i++) {
            a1.set(i, ss.stem(a1.get(i).toLowerCase().trim()));
        }
        for (int i = 0; i < a2.size(); i++) {
            a2.set(i, ss.stem(a2.get(i).toLowerCase().trim()));
        }

        if (a1.size() > a2.size()) {
            a1.retainAll(a2);
            return a1;
        } else {
            a2.retainAll(a1);
            return a2;
        }

    }

    /**
     * extracts the what from an given event
     * 
     * @param event
     */
    public void extractWhat(Event event) {

        String what = "";

        final String title = StringHelper.makeContinuousText(event.getTitle());

        LOGGER.info("title: " + title);
        // LOGGER.info("text: " + text);

        // event.setWho("police");

        String titleVerbPhrase = null;
        String who;
        boolean changed = false;
        int i = 0;

        // iterating throug whoCandidates until a subsequent verbPhrase is found
        // in title
        while (titleVerbPhrase == null
                && i < event.getWhoCandidates().keySet().size()) {
            who = (String) event.getWhoCandidates().keySet().toArray()[i];

            /*
             * String[] parts = null; if (who.contains(" ")) { parts =
             * who.split(" "); } else { parts = new String[] { who }; }
             */

            if (!changed) {
                titleVerbPhrase = getSubsequentVerbPhrase(event.getTitle(), who);
                if (titleVerbPhrase != null && !changed) {
                    event.setWho(who);
                    changed = true;
                    // System.out.println("new who: " + who);
                }

            }

            i++;
        }

        // if no titleVerbPhrase was found explore the text
        if (titleVerbPhrase == null) {

            // finding sentences containing the who
            for (final String stc : event.getSentences()) {

                if (stc.contains(event.getWho()) && what == null) {
                    what = getSubsequentVerbPhrase(stc, event.getWho());
                }

            }
        } else {
            what = titleVerbPhrase;

        }

        // LOGGER.info("most likely what: " + what);
        event.setWhat(what);

    }

    /**
     * @param sentence
     * @param word
     * @return
     */
    private String getSubsequentVerbPhrase(String sentence, String word) {

        final ArrayList<String> verbPhrases = new ArrayList<String>();
        boolean foundNoun = false;
        // boolean foundVerb = false;
        for (final TagAnnotation tagAnnotation : featureExtractor
                .getParse(sentence)) {

            if (foundNoun && tagAnnotation.getTag().contains("V")
                    && tagAnnotation.getChunk().length() < 100) {
                verbPhrases.add(tagAnnotation.getChunk());
                // foundVerb = true;
                foundNoun = false;

            }
            if (tagAnnotation.getTag().contains("N")
                    && tagAnnotation.getChunk().contains(word)) {

                foundNoun = true;
            }
            /*
             * if (foundVerb && tagAnnotation.getTag().contains("NN")) {
             * verbPhrases.set(0, verbPhrases.get(0) +
             * tagAnnotation.getChunk()); }
             */
        }
        return verbPhrases.size() > 0 ? verbPhrases.get(0) : null;
    }

    public String longestTerm(Annotations annotations) {

        String term = "";

        for (final Annotation annotation : annotations) {
            if (term.length() < annotation.getLength()) {
                term = annotation.getEntity().getName();

            }

        }
        return term;

    }

    public String shortestTerm(Annotations annotations) {

        String term = "longlonglonglonglonglong";

        for (final Annotation annotation : annotations) {
            if (term.length() > annotation.getLength()) {
                term = annotation.getEntity().getName();
            }

        }
        return term;

    }

    /**
     * Extracts the How from a given event.
     * 
     * @param event
     */
    public void extractHow(Event event) {
        // pattern matching "like..."

        Map<String, Double> rankedCandidates = new HashMap<String, Double>();
        double position = 0.0;
        for (final String stc : event.getSentences()) {

            double confidence = StringHelper.calculateSimilarity(stc, event
                    .getWho()
                    + " " + event.getWhat() + " " + event.getWhere())
                    - position;
            position = position + 0.001;

            if (stc.contains("like")) {
                confidence = confidence + 0.1;
            }
            if (confidence > 0.1) {
                rankedCandidates.put(stc, confidence);
            }
        }

        rankedCandidates = sortByValue(rankedCandidates);
        if (rankedCandidates.size() > 0) {
            event.setHowCandidates(rankedCandidates);
            event.setHow(rankedCandidates.keySet().toArray()[0].toString());
        }

    }

    /**
     * extracts the who and whoCandidates from an given event.
     * 
     * @param event
     */
    public void extractWho(Event event) {

        final Map<Annotations, FeatureObject> features = event
                .getAnnotationFeatures();

        double[] result = null;

        Map<String, Double> rankedCandidates = new HashMap<String, Double>();

        for (final Entry<Annotations, FeatureObject> entry : features
                .entrySet()) {

            final FeatureObject fo = entry.getValue();

            result = whoClassifier.classifySoft(fo);

            // put in the shortest term of the annotations found in text
            rankedCandidates.put(longestTerm(entry.getKey()), result[0]);

        }

        rankedCandidates = sortByValue(rankedCandidates);
        final Object[] values = rankedCandidates.keySet().toArray();
        // final Object[] keys = rankedCandidates.values().toArray();

        if (rankedCandidates.size() > 0) {
            event.setWhoCandidates(rankedCandidates);
            event.setWho(values[0].toString());
        }

    }

    /**
     * Returns the Where Classifier
     * 
     * @return
     */
    public WhereClassifier getWhereClassifier() {
        return whereClassifier;
    }

    /**
     * Setter of WhereClassifier.
     * 
     * @param type
     */
    public void setWhereClassifier(int type) {
        whereClassifier = new WhereClassifier(type);
        whereClassifier.useTrainedClassifier(MODEL_WHERE);
    }

    /**
     * Getter of WhoClassifier
     * 
     * @return
     */
    public WhoClassifier getWhoClassifier() {
        return whoClassifier;
    }

    /**
     * Setter of WhoClassifier
     * 
     * @param type
     */
    public void setWhoClassifier(int type) {
        whoClassifier = new WhoClassifier(type);
        whoClassifier.useTrainedClassifier(MODEL_WHO);
    }

    /**
     * @return the featureExtractor
     */
    public EventFeatureExtractor getFeatureExtractor() {
        return featureExtractor;
    }

    /**
     * @param featureExtractor
     *            the featureExtractor to set
     */
    public void setFeatureExtractor(EventFeatureExtractor featureExtractor) {
        this.featureExtractor = featureExtractor;
    }

    private Set<String> collectURLs() {
        Set<String> feedURLs = new HashSet<String>();
        feedURLs.add("http://rss.cnn.com/rss/edition.rss");
        // feedURLs.add("http://rss.cnn.com/rss/edition_world.rss");
        // feedURLs.add("http://rss.cnn.com/rss/edition_us.rss");
        // feedURLs.add("http://rss.cnn.com/rss/edition_europe.rss");
        // feedURLs.add("http://rss.cnn.com/rss/edition_business.rss");
        // feedURLs.add("http://rss.cnn.com/rss/edition_entertainment.rss");
        // feedURLs.add("http://newsrss.bbc.co.uk/rss/newsonline_uk_edition/front_page/rss.xml");
        // feedURLs.add("http://newsrss.bbc.co.uk/rss/newsonline_uk_edition/world/rss.xml");
        // feedURLs.add("http://newsrss.bbc.co.uk/rss/newsonline_uk_edition/sci/tech/rss.xml");
        // feedURLs.add("http://newsrss.bbc.co.uk/rss/newsonline_uk_edition/entertainment/rss.xml");
        // feedURLs.add("http://www.nytimes.com/services/xml/rss/nyt/HomePage.xml");
        // feedURLs.add("http://www.nytimes.com/services/xml/rss/nyt/GlobalHome.xml");

        NewsAggregator na = new NewsAggregator();
        Set<String> newsURLs = new HashSet<String>();

        for (String feedURL : feedURLs) {
            try {
                Feed feed = na.downloadFeed(feedURL, true);
                List<FeedItem> feedItems = feed.getEntries();

                for (FeedItem feedItem : feedItems) {
                    if (!seenNewsArticles.contains(feedItem.getLink())) {
                        newsURLs.add(feedItem.getLink());
                    }
                }

            } catch (NewsAggregatorException e) {
                LOGGER.error(e.getMessage());
            }
        }

        LOGGER.info(newsURLs.size() + " new URLs added for event exraction");

        return newsURLs;
    }

    public void startExtraction() {

        LOGGER.info("start event extraction");

        // reset stopped command
        setStopped(false);

        setWhoClassifier(Classifier.LINEAR_REGRESSION);
        setWhereClassifier(Classifier.LINEAR_REGRESSION);

        extractedEvents = new HashSet<Event>();

        while (!isStopped()) {

            Set<String> urls = collectURLs();

            for (String url : urls) {

                if (isStopped()) {
                    break;
                }

                Event event = extractEventFromURL(url);

                extractedEvents.add(event);

                seenNewsArticles.add(url);

                if (extractedEvents.size() == SAVE_COUNT) {
                    saveExtractions(true);
                }
            }

        }

    }

    @Override
    protected void saveExtractions(boolean saveExtractions) {

        if (saveExtractions) {

            EventDatabase edb = EventDatabase.getInstance();
            for (Event event : extractedEvents) {
                edb.addEvent(event);
            }
            extractedEvents.clear();

        }

    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        LOGGER.setLevel(Level.ALL);

        // evaluateEvents();

        // eventExtractor.extractWho(event);
        // eventExtractor.extractWhat(event);
        // eventExtractor.extractWhere(event);
        // eventExtractor.extractWhy(event);
        // eventExtractor.extractHow(event);

        final StopWatch sw = new StopWatch();
        sw.start();
        EventExtractor eventExtractor = EventExtractor.getInstance();
        Event event = eventExtractor
                .extractEventFromURL("http://www.guardian.co.uk/society/2010/dec/24/freezing-weather-homeless-charities/print");
        eventExtractor.extract5W1H(event);
        // System.out.println(StringHelper.makeContinuousText(event.getText()));
        // ee.featureExtractor
        // .getPOSTags("Mr Gates said they wanted to show solidarity with their allies in Seoul.");

        // ee.featureExtractor.getPOSTags(event.getText());

        sw.stop();
        LOGGER.info("time elapsed: " + sw.getElapsedTimeString());
        // eventExtractor.extract5W1H(event);

    }
}
