package ws.palladian.preprocessing.nlp;

import ws.palladian.preprocessing.featureextraction.Annotation;
import ws.palladian.preprocessing.nlp.ner.Annotations;
import ws.palladian.preprocessing.nlp.ner.NamedEntityRecognizer;
import ws.palladian.preprocessing.nlp.phrasechunking.AbstractPhraseChunker;
import ws.palladian.preprocessing.nlp.pos.PosTagger;
import ws.palladian.preprocessing.nlp.sentencedetection.AbstractSentenceDetector;

/**
 * <p>The NaturalLanguageProcessor holds functionality from the field of natural language processing, such as a <code>NamedEntityRecognizer</code>, <code>POSTagger</code> and <code>Parser</code>.</p>
 * 
 * @author Martin Wunderwald
 */
public abstract class AbstractNaturalLanguageProcessor {

    /** The Named Entity Recognizer. **/
    protected NamedEntityRecognizer ner;

    /** The POS-Tagger used in this class. **/
    protected PosTagger posTagger;

    /** The PhraseChunker. **/
    protected AbstractPhraseChunker phraseChunker;

    /** The Parser. **/
    protected AbstractParser parser;

    /** The SentenceDetector. **/
    protected AbstractSentenceDetector sentenceDetector;

    /**
     * @param text
     * @return
     */
    public final Annotations getNamedEntityAnnotations(final String text) {
        return ner.getAnnotations(text);
    }

    /**
     * @return the ner
     */
    public final NamedEntityRecognizer getNer() {
        return ner;
    }

    /**
     * returns a Parse on a sentence.
     * 
     * @param sentence
     * @return the parse
     */
    public final TagAnnotations getParse(final String sentence) {
        return parser.loadDefaultModel().parse(sentence).getTagAnnotations();
    }

    /**
     * @return the parser
     */
    public final AbstractParser getParser() {
        return parser;
    }

    /**
     * @return the phraseChunker
     */
    public final AbstractPhraseChunker getPhraseChunker() {
        return phraseChunker;
    }

    /**
     * performs phrase chunking on a sentence.
     * 
     * @param sentence
     *            - The sentence
     * @return The part of speach tags.
     */
    public final TagAnnotations getPhraseChunks(final String sentence) {
        return phraseChunker.loadDefaultModel().chunk(sentence).getTagAnnotations();
    }

    /**
     * @return the posTagger
     */
    public final PosTagger getPosTagger() {
        return posTagger;
    }

    /**
     * returns POS-Tags of a string.
     * 
     * @param sentence
     * @return the tag annotations
     */
    public final TagAnnotations getPOSTags(final String sentence) {
        return posTagger.tag(sentence);
    }

    /**
     * @return the sentenceDetector
     */
    public final AbstractSentenceDetector getSentenceDetector() {
        return sentenceDetector;
    }

    /**
     * Split a provided string into sentences and return a set of sentence
     * chunks.
     * 
     * @param text
     * @return All sentences extracted from {@code text}.
     */
    public final Annotation[] getSentences(String text) {
        return sentenceDetector.detect(text).getSentences();
    }
    
    public final String[] getSentenceValues(String text) {
        Annotation[] sentences = getSentences(text);
        String[] ret = new String[sentences.length];
        for(int i=0;i<sentences.length;i++) {
            ret[i] = sentences[i].getValue();
        }
        return ret;
    }

    /**
     * prepares the processor by loading its parts.
     */
    protected abstract void init();

    /**
     * @param ner
     *            the ner to set
     */
    public final void setNer(final NamedEntityRecognizer ner) {
        this.ner = ner;
    }

    /**
     * @param parser
     *            the parser to set
     */
    public final void setParser(final AbstractParser parser) {
        this.parser = parser;
    }

    /**
     * @param phraseChunker
     *            the phraseChunker to set
     */
    public final void setPhraseChunker(final AbstractPhraseChunker phraseChunker) {
        this.phraseChunker = phraseChunker;
    }

    /**
     * @param posTagger
     *            the posTagger to set
     */
    public final void setPosTagger(final PosTagger posTagger) {
        this.posTagger = posTagger;
    }

    /**
     * @param sentenceDetector
     *            the sentenceDetector to set
     */
    public final void setSentenceDetector(final AbstractSentenceDetector sentenceDetector) {
        this.sentenceDetector = sentenceDetector;
    }

}
