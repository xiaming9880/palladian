package ws.palladian.extraction.token;

import ws.palladian.processing.PipelineDocument;
import ws.palladian.processing.features.FeatureVector;
import ws.palladian.processing.features.PositionAnnotation;

import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;

/**
 * <p>
 * A {@link BaseTokenizer} implementation based on <a href="http://alias-i.com/lingpipe/">LingPipe</a>'s <a
 * href="http://alias-i.com/lingpipe/docs/api/com/aliasi/tokenizer/IndoEuropeanTokenizerFactory.html"
 * >IndoEuropeanTokenizerFactory</a>.
 * </p>
 * 
 * @author Philipp Katz
 */
public final class LingPipeTokenizer extends BaseTokenizer {

    /** Factory for creating a LingPipe tokenizer. */
    private final TokenizerFactory tokenizerFactory;

    public LingPipeTokenizer() {
        tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;
    }

    @Override
    public void processDocument(PipelineDocument<String> document) {
        String text = document.getContent();
        FeatureVector featureVector = document.getFeatureVector();
        com.aliasi.tokenizer.Tokenizer tokenizer = tokenizerFactory.tokenizer(text.toCharArray(), 0, text.length());
        int index = 0;
        String nextToken = tokenizer.nextToken();
        while (nextToken != null) {
            int startPosition = tokenizer.lastTokenStartPosition();
            int endPosition = tokenizer.lastTokenEndPosition();
            featureVector.add(new PositionAnnotation(PROVIDED_FEATURE, document, startPosition, endPosition, index++, nextToken));
            nextToken = tokenizer.nextToken();
        }
    }
}
