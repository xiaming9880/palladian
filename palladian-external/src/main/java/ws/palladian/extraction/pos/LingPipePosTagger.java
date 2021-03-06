package ws.palladian.extraction.pos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ws.palladian.core.TextTokenizer;
import ws.palladian.extraction.token.LingPipeTokenizer;
import ws.palladian.helper.ProgressMonitor;
import ws.palladian.helper.StopWatch;
import ws.palladian.helper.io.FileHelper;
import ws.palladian.helper.math.ConfusionMatrix;
import ws.palladian.helper.math.MathHelper;

import com.aliasi.hmm.HiddenMarkovModel;
import com.aliasi.hmm.HmmDecoder;
import com.aliasi.tag.Tagging;
import com.aliasi.util.FastCache;

/**
 * <p>
 * POS tagger based on <a href="http://alias-i.com/lingpipe/">LingPipe</a>.
 * </p>
 * 
 * @see Look <a href="http://alias-i.com/lingpipe/web/models.html">here</a> for models.
 * @author Martin Wunderwald
 * @author Philipp Katz
 */
public final class LingPipePosTagger extends AbstractPosTagger {

    /** The logger for this class. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LingPipePosTagger.class);

    /** The name of this {@link PosTagger}. */
    private static final String TAGGER_NAME = "LingPipe POS-Tagger";

    /** The model used by the LingPipe POS tagger. */
    private final HiddenMarkovModel model;

    /** The tokenizer used by the LingPipe POS tagger. */
    private static final TextTokenizer TOKENIZER = new LingPipeTokenizer();

    /**
     * <p>
     * Creates a new completely initalized LingPipe PoS tagger from the given model using the provided {@link TagFilter}
     * .
     * </p>
     * 
     * @param modelFile The model used by the LingPipe POS tagger.
     */
    public LingPipePosTagger(File modelFile) {
        Validate.notNull(modelFile, "modelFile must not be null");
        InputStream modelStream = null;
        try {
            modelStream = new FileInputStream(modelFile);
            this.model = loadModel(modelStream);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        } finally {
            FileHelper.close(modelStream);
        }
    }

    public LingPipePosTagger(InputStream modelStream) {
        Validate.notNull(modelStream, "modelStream must not be null");
        this.model = loadModel(modelStream);
    }

    /**
     * <p>
     * Loads the trained PoS tagger model.
     * </p>
     * 
     * @param modelStream The {@link InputStream} containing the data of the model to load.
     */
    private HiddenMarkovModel loadModel(InputStream modelStream) {
        HiddenMarkovModel ret = null;
        ObjectInputStream inputStream = null;
        try {
            inputStream = new ObjectInputStream(modelStream);
            ret = (HiddenMarkovModel)inputStream.readObject();
        } catch (IOException e) {
            throw new IllegalStateException("Error while loading model file: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Error while loading model file: " + e.getMessage());
        } finally {
            FileHelper.close(inputStream);
        }
        return ret;
    }

    @Override
    protected List<String> getTags(List<String> tokens) {
        FastCache<String, double[]> cache = new FastCache<>(100);
        HmmDecoder posTagger = new HmmDecoder(model, null, cache);
        return posTagger.tag(tokens).tags();
    }

    public void evaluate(String folderPath, String modelFilePath) {

        StopWatch stopWatch = new StopWatch();
        LOGGER.info("start evaluating the tagger");

        ConfusionMatrix matrix = new ConfusionMatrix();

        int correct = 0;
        int total = 0;

        FastCache<String, double[]> cache = new FastCache<>(100);

        // construct chunker
        HmmDecoder posTagger = new HmmDecoder(model, null, cache);

        File[] testFiles = FileHelper.getFiles(folderPath);
        ProgressMonitor progressMonitor = new ProgressMonitor(testFiles.length);
        
        for (File file : testFiles) {

            String content = FileHelper.tryReadFileToString(file);

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

                matrix.add(correctTag, assignedTag);

                if (assignedTag.equals(correctTag)) {
                    correct++;
                }
                total++;
            }

            progressMonitor.incrementAndPrintProgress();
        }

        LOGGER.info("all files read in " + stopWatch.getElapsedTimeString());

        LOGGER.info("Accuracy: " + MathHelper.round(100.0 * correct / total, 2) + "%");
        LOGGER.info("\n" + matrix);

        LOGGER.info("finished evaluating the tagger in " + stopWatch.getElapsedTimeString());
    }

    @Override
    public String getName() {
        return TAGGER_NAME;
    }

    @Override
    protected TextTokenizer getTokenizer() {
        return TOKENIZER;
    }

    public static void main(String[] args) {

        // final File modelFile = new File("pos-en-general-brown.HiddenMarkovModel");

        // PipelineDocument document = new PipelineDocument("I'm here to say that we're about to do that.");
        // ProcessingPipeline pipeline = new ProcessingPipeline();
        // pipeline.add(new LingPipeTokenizer());
        // pipeline.add(new LingPipePosTagger(modelFile));
        // pipeline.process(document);
        // AnnotationFeature annotations = (AnnotationFeature)
        // document.getFeatureVector().get(Tokenizer.PROVIDED_FEATURE);
        // System.out.println(annotations.getValue());
        //
        // System.exit(0);

        // BasePosTagger tagger = new LingPipePosTagger(modelFile);
        // System.out.println(tagger.tag("I'm here to say that we're about to do that.").getTaggedString());
        // System.out.println(tagger.tag("The quick brown fox jumps over the lazy dog").getTaggedString());
        // tagger.evaluate("data/datasets/pos/testSmall/",
        // "data/models/lingpipe/pos-en-general-brown.HiddenMarkovModel");
    }
    
}
