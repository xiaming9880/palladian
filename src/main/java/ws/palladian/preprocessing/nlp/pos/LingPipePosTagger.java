package ws.palladian.preprocessing.nlp.pos;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;

import ws.palladian.helper.Cache;
import ws.palladian.helper.ConfigHolder;
import ws.palladian.helper.FileHelper;
import ws.palladian.helper.ProgressHelper;
import ws.palladian.helper.StopWatch;
import ws.palladian.helper.math.ConfusionMatrix;
import ws.palladian.helper.math.MathHelper;
import ws.palladian.preprocessing.nlp.TagAnnotation;
import ws.palladian.preprocessing.nlp.TagAnnotations;

import com.aliasi.hmm.HiddenMarkovModel;
import com.aliasi.hmm.HmmDecoder;
import com.aliasi.tag.Tagging;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;
import com.aliasi.util.FastCache;

/**
 * @author Martin Wunderwald
 */
public class LingPipePosTagger extends PosTagger {

    /** The logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(LingPipePosTagger.class);

    /**
     * Constructor.
     */
    public LingPipePosTagger() {
        super();
        setName("LingPipe POS-Tagger");
        final PropertiesConfiguration config = ConfigHolder.getInstance().getConfig();

        MODEL = config.getString("models.root") + config.getString("models.lingpipe.en.postag");
    }

    @Override
    public LingPipePosTagger loadModel(final String modelFilePath) {

        ObjectInputStream inputStream = null;

        try {
            HiddenMarkovModel hmm = null;

            if (Cache.getInstance().containsDataObject(modelFilePath)) {
                hmm = (HiddenMarkovModel) Cache.getInstance().getDataObject(modelFilePath);
            } else {

                final StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                inputStream = new ObjectInputStream(new FileInputStream(modelFilePath));
                hmm = (HiddenMarkovModel) inputStream.readObject();
                Cache.getInstance().putDataObject(modelFilePath, hmm);

                stopWatch.stop();
                LOGGER.info("Reading " + getName() + " from file " + modelFilePath + " in "
                        + stopWatch.getElapsedTimeString());
            }

            setModel(hmm);

        } catch (final IOException ie) {
            LOGGER.error("IO Error: " + ie.getMessage());

        } catch (final ClassNotFoundException ce) {
            LOGGER.error("Class error: " + ce.getMessage());

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (final IOException ie) {
                    LOGGER.error(ie.getMessage());
                }
            }
        }
        return this;
    }

    /*
     * (non-Javadoc)
     * @see tud.iir.extraction.event.AbstractPOSTagger#tag(java.lang.String)
     */
    @Override
    public LingPipePosTagger tag(final String sentence) {

        int cacheSize = Integer.valueOf(100);
        FastCache<String, double[]> cache = new FastCache<String, double[]>(cacheSize);

        // read HMM for pos tagging

        // construct chunker
        HmmDecoder posTagger = new HmmDecoder((HiddenMarkovModel)getModel(), null, cache);
        TokenizerFactory tokenizerFactory = IndoEuropeanTokenizerFactory.INSTANCE;

        // apply pos tagger
        String[] tokens = tokenizerFactory.tokenizer(sentence.toCharArray(), 0, sentence.length()).tokenize();
        List<String> tokenList = Arrays.asList(tokens);
        Tagging<String> tagging = posTagger.tag(tokenList);

        TagAnnotations tagAnnotations = new TagAnnotations();
        for (int i = 0; i < tagging.size(); i++) {

            TagAnnotation tagAnnotation = new TagAnnotation(sentence.indexOf(tagging.token(i)), tagging.tag(i)
                    .toUpperCase(new Locale("en")), tagging.token(i));
            tagAnnotations.add(tagAnnotation);

        }
        setTagAnnotations(tagAnnotations);
        return this;
    }

    @Override
    public LingPipePosTagger tag(String sentence, String modelFilePath) {
        return this.loadModel(modelFilePath).tag(sentence);
    }

    public void evaluate(String folderPath, String modelFilePath) {
        loadModel(modelFilePath);

        StopWatch stopWatch = new StopWatch();
        LOGGER.info("start evaluating the tagger");

        ConfusionMatrix matrix = new ConfusionMatrix();

        int c = 1;
        int correct = 0;
        int total = 0;

        int cacheSize = Integer.valueOf(100);
        FastCache<String, double[]> cache = new FastCache<String, double[]>(cacheSize);

        // construct chunker
        HmmDecoder posTagger = new HmmDecoder((HiddenMarkovModel)getModel(), null, cache);

        File[] testFiles = FileHelper.getFiles(folderPath);
        for (File file : testFiles) {

            String content = FileHelper.readFileToString(file);

            String[] wordsAndTagPairs = content.split("\\s");

            for (String wordAndTagPair : wordsAndTagPairs) {

                if (wordAndTagPair.isEmpty()) {
                    continue;
                }

                String[] wordAndTag = wordAndTagPair.split("/");

                if (wordAndTag.length < 2) {
                    continue;
                }

                Tagging<String> tagging = posTagger.tag(Arrays.asList(wordAndTag[0]));

                String assignedTag = tagging.tags().get(0);
                String correctTag = normalizeTag(wordAndTag[1]).toLowerCase();

                matrix.increment(correctTag, assignedTag);

                if (assignedTag.equals(correctTag)) {
                    correct++;
                }
                total++;
            }

            ProgressHelper.showProgress(c++, testFiles.length, 1);
        }

        LOGGER.info("all files read in " + stopWatch.getElapsedTimeString());

        LOGGER.info("Accuracy: " + MathHelper.round(100.0 * correct / total, 2) + "%");
        LOGGER.info("\n" + matrix);

        LOGGER.info("finished evaluating the tagger in " + stopWatch.getElapsedTimeString());
    }

    public static void main(String[] args) {
        LingPipePosTagger tagger = new LingPipePosTagger();
        tagger.loadModel();
        System.out.println(tagger.tag("I'm here to say that we're about to do that.").getTaggedString());
        // System.out.println(tagger.tag("The quick brown fox jumps over the lazy dog").getTaggedString());
        // tagger.evaluate("data/datasets/pos/testSmall/",
        // "data/models/lingpipe/pos-en-general-brown.HiddenMarkovModel");
    }

}
