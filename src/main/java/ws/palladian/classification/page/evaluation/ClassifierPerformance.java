package ws.palladian.classification.page.evaluation;

import java.io.Serializable;

import org.apache.log4j.Logger;

import ws.palladian.classification.Categories;
import ws.palladian.classification.Category;
import ws.palladian.classification.CategoryEntry;
import ws.palladian.classification.ClassifierPerformanceResult;
import ws.palladian.classification.page.ClassificationDocuments;
import ws.palladian.classification.page.TestDocument;
import ws.palladian.classification.page.TextClassifier;
import ws.palladian.classification.page.TextInstance;

/**
 * This class calculates scores for a given classifier such as precision, recall, and F1.
 * 
 * @author David Urbansky
 * 
 */
public class ClassifierPerformance implements Serializable {

    private static final long serialVersionUID = -7375053843995436850L;

    /** The classifier's categories. */
    private Categories categories = null;

    private int classificationType = -1;

    /** The training documents. */
    private ClassificationDocuments trainingDocuments = null;

    /** The test documents that can be used to calculate recall, precision, and F-score. */
    private ClassificationDocuments testDocuments = null;

    /**
     * Create a new ClassifierPerformance for a given classifier.
     * 
     * @param classifier The classifier.
     */
    public ClassifierPerformance(TextClassifier classifier) {
        categories = classifier.getCategories();
        trainingDocuments = classifier.getTrainingDocuments();
        testDocuments = classifier.getTestDocuments();
        classificationType = classifier.getClassificationType();
    }

    public Categories getCategories() {
        return categories;
    }

    public void setCategories(Categories categories) {
        this.categories = categories;
    }

    public void setClassificationType(int classificationType) {
        this.classificationType = classificationType;
    }

    public int getClassificationType() {
        return classificationType;
    }

    public void setTrainingDocuments(ClassificationDocuments trainingDocuments) {
        this.trainingDocuments = trainingDocuments;
    }

    public ClassificationDocuments getTrainingDocuments() {
        return trainingDocuments;
    }

    public void setTestDocuments(ClassificationDocuments testDocuments) {
        this.testDocuments = testDocuments;
    }

    public ClassificationDocuments getTestDocuments() {
        return testDocuments;
    }

    /**
     * Get the number of correct classified documents in a given category.
     * 
     * @param category The category.
     * @return Number of correct classified documents in a given category.
     */
    public int getNumberOfCorrectClassifiedDocumentsInCategory(Category category) {
        int number = 0;

        for (TextInstance document : getTestDocuments()) {
            TestDocument testDocument = (TestDocument) document;

            if (category.getClassType() == ClassificationTypeSetting.SINGLE) {
                if (document.getMainCategoryEntry().getCategory().getName().equals(category.getName())
                        && testDocument.isCorrectClassified()) {
                    ++number;
                }
            } else {
                for (CategoryEntry c : testDocument.getAssignedCategoryEntries()) {
                    if (c.getCategory().getName().equals(category.getName()) && testDocument.isCorrectClassified()) {
                        ++number;
                        break;
                    }
                }
            }

        }

        return number;
    }

    public double getCorrectlyClassified() {
        int correctlyClassified = 0;
        for (Category c : getCategories()) {
            correctlyClassified += getNumberOfCorrectClassifiedDocumentsInCategory(c);
        }

        return correctlyClassified / (double) getTestDocuments().size();
    }

    /**
     * calculate and return the precision for a given category
     * 
     * @param category
     * @return the precision for a given category
     */
    public double getPrecisionForCategory(Category category) {
        try {
            double correct = getNumberOfCorrectClassifiedDocumentsInCategory(category);
            double classified = getTestDocuments().getClassifiedNumberOfCategory(category);

            if (classified < 1.0) {
                return -1.0;
            }

            return correct / classified;
        } catch (ArithmeticException e) {
            Logger.getRootLogger().error("ERROR Division By Zero for Precision in Category " + category.getName());
            return -1.0;
        }
    }

    /**
     * calculate and return the recall for a given category
     * 
     * @param category
     * @return the recall for a given category
     */
    public double getRecallForCategory(Category category) {
        try {
            double correct = getNumberOfCorrectClassifiedDocumentsInCategory(category);
            double real = getTestDocuments().getRealNumberOfCategory(category);
            if (real < 1.0) {
                return -1.0;
            }
            return correct / real;
        } catch (ArithmeticException e) {
            Logger.getRootLogger().error("ERROR Division By Zero for Recall in Category " + category.getName());
            return -1.0;
        }
    }

    /**
     * Calculate and return the F for a given category.
     * 
     * @param category The category.
     * @param alpha A value between 0 and 1 to weight precision and recall (0.5 for F1).
     * @return F for a given category.
     */
    public double getFForCategory(Category category, double alpha) {
        try {
            double pfc = getPrecisionForCategory(category);
            double rfc = getRecallForCategory(category);

            if (pfc < 0 || rfc < 0) {
                return -1.0;
            }

            return 1.0 / (alpha * 1.0 / pfc + (1.0 - alpha) * 1.0 / rfc);
            // double alphaSquared = alpha * alpha;
            // return ((1+alphaSquared)*getPrecisionForCategory(category) * getRecallForCategory(category)) /
            // (alphaSquared*getPrecisionForCategory(category)+getRecallForCategory(category));
        } catch (ArithmeticException e) {
            Logger.getRootLogger().error("ERROR Division By Zero for F in Category " + category.getName());
            return -1.0;
        }
    }

    /**
     * Calculate the sensitivity for a given category. Sensitivity = TP / (TP + FN). Sensitivity specifies what
     * percentage of actual category members were
     * found. 100% sensitivity means that all actual documents belonging to the category were classified correctly.
     * 
     * @param category
     * @return
     */
    public double getSensitivityForCategory(Category category) {
        double sensitivity = 0.0;
        try {
            double truePositives = getNumberOfCorrectClassifiedDocumentsInCategory(category);
            double realPositives = getTestDocuments().getRealNumberOfCategory(category);

            double falseNegatives = realPositives - truePositives;

            if (truePositives + falseNegatives == 0) {
                return -1.0;
            }

            sensitivity = truePositives / (truePositives + falseNegatives);

        } catch (ArithmeticException e) {
            Logger.getRootLogger().error("ERROR Division By Zero for Sensitivity in Category " + category.getName());
            return -1.0;
        }

        return sensitivity;
    }

    /**
     * Calculate the specificity for a given category. Specificity = (TN) / (TN + FP). Specificity specifies what
     * percentage of not-category members were
     * recognized as such. 100% specificity means that there were no documents classified as category member when they
     * were actually not.
     * 
     * @param category The category.
     * @return The specificity.
     */
    public double getSpecificityForCategory(Category category) {
        double specificity = 0.0;
        try {
            double truePositives = getNumberOfCorrectClassifiedDocumentsInCategory(category);
            double realPositives = getTestDocuments().getRealNumberOfCategory(category);
            double classifiedPositives = getTestDocuments().getClassifiedNumberOfCategory(category);

            double falsePositives = classifiedPositives - truePositives;
            double falseNegatives = realPositives - truePositives;
            double trueNegatives = getTestDocuments().size() - classifiedPositives - falseNegatives;

            if (trueNegatives + falsePositives == 0) {
                return -1.0;
            }

            specificity = trueNegatives / (trueNegatives + falsePositives);

        } catch (ArithmeticException e) {
            Logger.getRootLogger().error("ERROR Division By Zero for Specificity in Category " + category.getName());
            return -1.0;
        }

        return specificity;
    }

    /**
     * Calculate the accuracy for a given category. Accuracy = (TP + TN) / (TP + TN + FP + FN).
     * 
     * @param category The category.
     * @return The accuracy.
     */
    public double getAccuracyForCategory(Category category) {
        double accuracy = 0.0;
        try {
            double truePositives = getNumberOfCorrectClassifiedDocumentsInCategory(category);
            double realPositives = getTestDocuments().getRealNumberOfCategory(category);
            double classifiedPositives = getTestDocuments().getClassifiedNumberOfCategory(category);

            double falsePositives = classifiedPositives - truePositives;
            double falseNegatives = realPositives - truePositives;
            double trueNegatives = getTestDocuments().size() - classifiedPositives - falseNegatives;

            if (truePositives + trueNegatives + falsePositives + falseNegatives == 0) {
                return -1.0;
            }

            accuracy = (truePositives + trueNegatives)
            / (truePositives + trueNegatives + falsePositives + falseNegatives);

        } catch (ArithmeticException e) {
            Logger.getRootLogger().error("ERROR Division By Zero for Accuracy in Category " + category.getName());
            return -1.0;
        }

        return accuracy;
    }

    /**
     * Calculate the prior for the given category. The prior is determined by calculating the frequency of the category
     * in the training and test set and
     * dividing it by the total number of documents. XXX use only test documents to determine prior?
     * 
     * @param category The category for which the prior should be determined.
     * @return The prior for the category.
     */
    public double getWeightForCategory(Category category) {
        if (category.getTestSetWeight() > -1) {
            return category.getTestSetWeight();
        }

        try {
            // the number of documents that belong to the given category
            int documentCount = getTestDocuments().getRealNumberOfCategory(category)
            + getTrainingDocuments().getRealNumberOfCategory(category);

            // the total number of documents assigned to categories, one document can be assigned to multiple
            // categories!
            int totalAssigned = 0;
            for (Category c : getCategories()) {

                // skip categories that are not main categories because they are classified according to the main
                // category
                if (category.getClassType() == ClassificationTypeSetting.HIERARCHICAL && !c.isMainCategory()) {
                    continue;
                }

                totalAssigned += getTestDocuments().getRealNumberOfCategory(c)
                + getTrainingDocuments().getRealNumberOfCategory(c);
            }

            // double ratio = (double) documentCount / (double) (testDocuments.size() + trainingDocuments.size());
            double weight = (double) documentCount / (double) totalAssigned;
            category.setTestSetWeight(weight);
            return weight;

        } catch (ArithmeticException e) {
            Logger.getRootLogger().error("ERROR Division By Zero for Recall in Category " + category.getName());
            return 0.0;
        }
    }

    /**
     * Get the average precision of all categories.
     * 
     * @return The average precision of all categories.
     */
    public double getAveragePrecision(boolean weighted) {
        double precision = 0.0;

        double count = 0.0;

        if (getClassificationType() == ClassificationTypeSetting.TAG) {

            for (TextInstance document : getTestDocuments()) {

                int correctlyAssigned = ((TestDocument) document).getCorrectlyAssignedCategoryEntries().size();
                int totalAssigned = document.getAssignedCategoryEntries().size();

                if (totalAssigned == 0) {
                    Logger.getRootLogger().warn("no category has been assigned to document " + document.getUrl());
                    continue;
                }

                // for (int i = 1; i <= precisionAtRank; i++) {
                // totalPrecisionAts[i - 1] += ((TestDocument) document).getPrecisionAt(i);
                // }

                double microPrecision = (double) correctlyAssigned / (double) totalAssigned;

                precision += microPrecision;
                count++;
            }

        } else {

            for (Category c : getCategories()) {

                // skip categories that are not main categories because they are classified according to the main
                // category
                if (c.getClassType() == ClassificationTypeSetting.HIERARCHICAL && !c.isMainCategory()) {
                    continue;
                }

                double pfc = getPrecisionForCategory(c);
                if (pfc < 0) {
                    continue;
                }

                if (weighted) {
                    precision += getWeightForCategory(c) * pfc;
                } else {
                    precision += pfc;
                }
                ++count;
            }

            if (weighted) {
                return precision;
            }

        }


        if (count == 0) {
            return -1.0;
        }

        return precision / count;
    }

    /**
     * Get the average recall of all categories.
     * 
     * @return The average recall of all categories.
     */
    public double getAverageRecall(boolean weighted) {
        double recall = 0.0;

        double count = 0.0;

        if (getClassificationType() == ClassificationTypeSetting.TAG) {

            for (TextInstance document : getTestDocuments()) {

                int correctlyAssigned = ((TestDocument) document).getCorrectlyAssignedCategoryEntries().size();
                int real = document.getRealCategories().size();

                if (real == 0) {
                    Logger.getRootLogger().warn("no category has been assigned to document " + document.getUrl());
                    continue;
                }

                double microRecall = (double) correctlyAssigned / (double) real;

                recall += microRecall;
                count++;
            }

        } else {

            for (Category c : getCategories()) {

                // skip categories that are not main categories because they are classified according to the main
                // category
                if (c.getClassType() == ClassificationTypeSetting.HIERARCHICAL && !c.isMainCategory()) {
                    continue;
                }

                double rfc = getRecallForCategory(c);
                if (rfc < 0.0) {
                    continue;
                }

                if (weighted) {
                    recall += getWeightForCategory(c) * rfc;
                } else {
                    recall += rfc;
                }
                ++count;
            }

            if (weighted) {
                return recall;
            }

        }

        if (count == 0) {
            return -1.0;
        }

        return recall / count;
    }

    /**
     * Get the average F of all categories.
     * 
     * @param alpha To weight precision and recall (0.5 for F1).
     * @return The average F of all categories.
     */
    public double getAverageF(double alpha, boolean weighted) {
        double f = 0.0;

        double count = 0.0;
        for (Category c : getCategories()) {

            // skip categories that are not main categories because they are classified according to the main category
            if (c.getClassType() == ClassificationTypeSetting.HIERARCHICAL && !c.isMainCategory()) {
                continue;
            }

            double ffc = getFForCategory(c, alpha);

            if (ffc < 0.0) {
                continue;
            }

            if (weighted) {
                f += getWeightForCategory(c) * ffc;
            } else {
                f += ffc;
            }

            ++count;
        }

        if (weighted) {
            return f;
        }

        if (count == 0) {
            return -1.0;
        }

        return f / count;
    }

    /**
     * Calculate the average sensitivity.
     * 
     * @param weighted If true, the average sensitivity is weighted using the priors of the categories.
     * @return The (weighted) average sensitivity.
     */
    public double getAverageSensitivity(boolean weighted) {
        double sensitivity = 0.0;

        double count = 0.0;
        for (Category c : getCategories()) {

            // skip categories that are not main categories because they are classified according to the main category
            if (c.getClassType() == ClassificationTypeSetting.HIERARCHICAL && !c.isMainCategory()) {
                continue;
            }

            double sfc = getSensitivityForCategory(c);

            if (sfc < 0.0) {
                continue;
            }

            if (weighted) {
                sensitivity += getWeightForCategory(c) * sfc;
            } else {
                sensitivity += sfc;
            }

            ++count;
        }

        if (weighted) {
            return sensitivity;
        }

        if (count == 0) {
            return -1.0;
        }

        return sensitivity / count;
    }

    /**
     * Calculate the average specificity.
     * 
     * @param weighted If true, the average accuracy is weighted using the priors of the categories.
     * @return The (weighted) average accuracy.
     */
    public double getAverageSpecificity(boolean weighted) {
        double specificity = 0.0;

        double count = 0.0;
        for (Category c : getCategories()) {

            // skip categories that are not main categories because they are classified according to the main category
            if (c.getClassType() == ClassificationTypeSetting.HIERARCHICAL && !c.isMainCategory()) {
                continue;
            }

            double sfc = getSpecificityForCategory(c);

            if (sfc < 0) {
                return -1.0;
            }

            if (weighted) {
                specificity += getWeightForCategory(c) * sfc;
            } else {
                specificity += sfc;
            }

            ++count;
        }

        if (weighted) {
            return specificity;
        }

        if (count == 0) {
            return -1.0;
        }

        return specificity / count;
    }

    /**
     * Calculate the average accuracy.
     * 
     * @param weighted If true, the average accuracy is weighted using the priors of the categories.
     * @return The (weighted) average accuracy.
     */
    public double getAverageAccuracy(boolean weighted) {
        double accuracy = 0.0;

        double count = 0.0;
        for (Category c : getCategories()) {

            // skip categories that are not main categories because they are classified according to the main category
            if (c.getClassType() == ClassificationTypeSetting.HIERARCHICAL && !c.isMainCategory()) {
                continue;
            }

            double afc = getAccuracyForCategory(c);

            if (afc < 0.0) {
                return -1.0;
            }

            if (weighted) {
                accuracy += getWeightForCategory(c) * afc;
            } else {
                accuracy += afc;
            }

            ++count;
        }

        if (weighted) {
            return accuracy;
        }

        if (count == 0) {
            return -1.0;
        }

        return accuracy / count;
    }

    /**
     * Write the most basic scores to a result class which only holds these but not the documents. The
     * ClassifierPerformanceResult class saves memory.
     * 
     * @return The most basic results held in the {@link ClassifierPerformanceResult} class.
     */
    public ClassifierPerformanceResult getClassifierPerformanceResult() {

        ClassifierPerformanceResult classifierPerformanceResult = new ClassifierPerformanceResult();

        classifierPerformanceResult.setPrecision(getAveragePrecision(true));
        classifierPerformanceResult.setRecall(getAverageRecall(true));
        classifierPerformanceResult.setF1(getAverageF(0.5, true));

        classifierPerformanceResult.setSensitivity(getAverageSensitivity(true));
        classifierPerformanceResult.setSpecificity(getAverageSpecificity(true));
        classifierPerformanceResult.setAccuracy(getAverageAccuracy(true));

        classifierPerformanceResult.setCorrectlyClassified(getCorrectlyClassified());

        return classifierPerformanceResult;
    }

    public String toReadableString() {

        ClassifierPerformanceResult cpr = getClassifierPerformanceResult();

        StringBuilder builder = new StringBuilder();
        builder.append("Precision: ").append(cpr.getPrecision()).append("\n");
        builder.append("Recall: ").append(cpr.getRecall()).append("\n");
        builder.append("F1: ").append(cpr.getF1()).append("\n");
        builder.append("Sensitivity: ").append(cpr.getSensitivity()).append("\n");
        builder.append("Specificity: ").append(cpr.getSpecificity()).append("\n");
        builder.append("Accuracy: ").append(cpr.getAccuracy()).append("\n");
        builder.append("Correctly Classified: ").append(cpr.getCorrectlyClassified()).append("\n");
        return builder.toString();

    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ClassifierPerformance [");
        builder.append(getClassifierPerformanceResult());
        builder.append("]");
        return builder.toString();
    }

}
