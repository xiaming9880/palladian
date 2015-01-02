package ws.palladian.classification.liblinear;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ws.palladian.classification.utils.ClassificationUtils;
import ws.palladian.classification.utils.DummyVariableCreator;
import ws.palladian.classification.utils.NoNormalizer;
import ws.palladian.classification.utils.Normalization;
import ws.palladian.classification.utils.Normalizer;
import ws.palladian.classification.utils.ZScoreNormalizer;
import ws.palladian.core.FeatureVector;
import ws.palladian.core.Instance;
import ws.palladian.core.Learner;
import ws.palladian.core.value.NumericValue;
import ws.palladian.core.value.Value;
import ws.palladian.helper.collection.CollectionHelper;
import ws.palladian.helper.collection.Vector.VectorEntry;
import ws.palladian.helper.io.Slf4JOutputStream;
import ws.palladian.helper.io.Slf4JOutputStream.Level;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;

/**
 * <p>
 * LIBLINEAR, A Library for Large Linear Classification. Wrapper for <a
 * href="http://liblinear.bwaldvogel.de">liblinear-java</a>. For a documentation about liblinear see <a
 * href="http://www.csie.ntu.edu.tw/~cjlin/liblinear/">here</a> and <a
 * href="http://www.csie.ntu.edu.tw/~cjlin/papers/liblinear.pdf">here</a>. In addition, to the pure LIBLINEAR
 * classifier, this wrapper adds the following functionality: a) Numerical data can be normalized using a
 * {@link Normalizer}, b) nominal data is transformed to a numerical representation, using dummy coding (see
 * {@link DummyVariableCreator} for more information).
 * </p>
 * 
 * @author Philipp Katz
 */
public final class LibLinearLearner implements Learner<LibLinearModel> {

    /** The logger for this class. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LibLinearLearner.class);

    /** The training parameters. */
    private final Parameter parameter;

    /** Bias parameter, will be set in case value is greater zero. */
    private final double bias;

    /** The normalizer for the numeric features. */
    private final Normalizer normalizer;

    static {
        // redirect debug output to logger.
        Linear.setDebugOutput(new PrintStream(new Slf4JOutputStream(LOGGER, Level.DEBUG)));
    }

    /**
     * <p>
     * Create a new {@link LibLinearLearner} with the specified {@link Parameter} for training.
     * </p>
     * 
     * @param parameter The parameter, not <code>null</code>.
     * @param bias The value for the bias term, use a value <code>&lt; 0</code> to add no bias term.
     * @param normalizer The normalizer to use, not <code>null</code>. Use a {@link NoNormalizer} to skip
     *            normalization.
     */
    public LibLinearLearner(Parameter parameter, double bias, Normalizer normalizer) {
        Validate.notNull(parameter, "parameter must not be null");
        Validate.notNull(normalizer, "normalizer must not be null");
        if (parameter.getSolverType().isSupportVectorRegression()) {
            throw new UnsupportedOperationException(
                    "Support vector regression is not supported by this learner. This learner is for classification only!");
        }
        this.parameter = parameter;
        this.bias = bias;
        this.normalizer = normalizer;
    }

    /**
     * <p>
     * Create a new {@link LibLinearLearner} with 'L2-regularized logistic regression', a cost value of 1.0 for
     * constraints violation, a value of 0.01 as stopping criterion, a bias term of one, and Z-Score normalization for
     * features.
     * </p>
     */
    public LibLinearLearner() {
        this(new Parameter(SolverType.L2R_LR, //
                1.0, // cost of constraints violation
                0.01), // stopping criteria
                1, // bias term
                new ZScoreNormalizer()); // normalizer
    }

    @Override
    public LibLinearModel train(Iterable<? extends Instance> instances) {
        Validate.notNull(instances, "instances must not be null");
        Iterable<FeatureVector> featureVectors = ClassificationUtils.unwrapInstances(instances);
        Normalization normalization = normalizer.calculate(featureVectors);
        DummyVariableCreator dummyCoder = new DummyVariableCreator(featureVectors);
        Problem problem = new Problem();
        List<String> featureLabels = new ArrayList<>();
        List<String> classIndices = new ArrayList<>();
        for (Instance instance : instances) {
            problem.l++;
            FeatureVector featureVector = dummyCoder.convert(instance.getVector());
            for (VectorEntry<String, Value> entry : featureVector) {
                Value value = entry.value();
                if (value instanceof NumericValue) {
                    if (!featureLabels.contains(entry.key())) {
                        featureLabels.add(entry.key());
                    }
                }
            }
            if (!classIndices.contains(instance.getCategory())) {
                classIndices.add(instance.getCategory());
            }
        }
        LOGGER.debug("Features = {}", featureLabels);
        LOGGER.debug("Classes = {}", classIndices);
        problem.n = featureLabels.size();
        problem.x = new de.bwaldvogel.liblinear.Feature[problem.l][];
        problem.y = new double[problem.l];
        if (bias >= 0) {
            LOGGER.debug("Add bias correction {}", bias);
            problem.bias = bias; // bias feature
            problem.n++; // add one for bias term
        }
        int index = 0;
        for (Instance instance : instances) {
            FeatureVector featureVector = normalization.normalize(instance.getVector());
            featureVector = dummyCoder.convert(featureVector);
            problem.x[index] = makeInstance(featureLabels, featureVector, bias);
            problem.y[index] = classIndices.indexOf(instance.getCategory());
            index++;
        }
        LOGGER.debug("n={}, l={}", problem.n, problem.l);
        Model model = Linear.train(problem, parameter);
        return new LibLinearModel(model, featureLabels, classIndices, normalization, dummyCoder);
    }

    static de.bwaldvogel.liblinear.Feature[] makeInstance(List<String> labels, FeatureVector featureVector, double bias) {
        List<de.bwaldvogel.liblinear.Feature> features = new ArrayList<>();
        int index = 0; // 1-indexed
        for (String label : labels) {
            index++;
            Value value = featureVector.get(label);
            if (!(value instanceof NumericValue)) {
                LOGGER.trace("NumericValue {}@{} not present", label, index);
                continue;
            }
            double numericValue = ((NumericValue)value).getDouble();
            if (numericValue == 0) {
                continue;
            }
            features.add(new FeatureNode(index, numericValue));
        }
        if (bias >= 0) {
            features.add(new FeatureNode(index + 1, bias)); // bias term
        }
        return features.toArray(new de.bwaldvogel.liblinear.Feature[features.size()]);
    }

}
