package ws.palladian.preprocessing.nlp.pos;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;

import org.apache.commons.configuration.PropertiesConfiguration;

import ws.palladian.helper.Cache;
import ws.palladian.helper.ConfigHolder;
import ws.palladian.preprocessing.featureextraction.Annotation;

/**
 * @author Martin Wunderwald
 * @author Philipp Katz
 */
public final class OpenNlpPosTagger extends BasePosTagger {

    private static final long serialVersionUID = 1L;

    /** The name of this POS tagger. */
    private static final String TAGGER_NAME = "OpenNLP POS-Tagger";

    /** The actual OpenNLP POS tagger. */
    private final POSTagger tagger;

    public OpenNlpPosTagger(File modelFile) {
        this.tagger = loadModel(modelFile);
    }
    
    public OpenNlpPosTagger() {
        PropertiesConfiguration config = ConfigHolder.getInstance().getConfig();
        File modelFile = new File(config.getString("models.root") + config.getString("models.opennlp.en.postag"));
        this.tagger = loadModel(modelFile);
    }

    private POSTagger loadModel(File modelFile) {
        String modelPath = modelFile.getAbsolutePath();
        POSTagger model = (POSTagger) Cache.getInstance().getDataObject(modelPath);
        if (model == null) {
            try {
                model = new POSTaggerME(new POSModel(new FileInputStream(modelFile)));
                Cache.getInstance().putDataObject(modelPath, model);
            } catch (IOException e) {
                throw new IllegalStateException("Error initializing OpenNLP POS Tagger from \"" + modelPath + "\": "
                        + e.getMessage());
            }
        }
        return model;
    }


    @Override
    public void tag(List<Annotation> annotations) {
        List<String> tokenList = getTokenList(annotations);
        String[] tags = tagger.tag(tokenList.toArray(new String[annotations.size()]));
        for (int i = 0; i < tags.length; i++) {
            assignTag(annotations.get(i), tags[i]);
        }
    }

    @Override
    public String getName() {
        return TAGGER_NAME;
    }

}
