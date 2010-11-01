package tud.iir.news.statistics;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import tud.iir.helper.CountMap;
import tud.iir.helper.FileHelper;
import tud.iir.helper.MathHelper;
import tud.iir.news.Feed;
import tud.iir.news.FeedBenchmarkFileReader;
import tud.iir.news.FeedChecker;
import tud.iir.news.FeedClassifier;
import tud.iir.news.FeedPostStatistics;
import tud.iir.news.FeedStore;
import tud.iir.news.evaluation.FeedReaderEvaluator;
import tud.iir.persistence.DatabaseManager;
import tud.iir.web.Crawler;

/**
 * The FeedStatisticCreator creates a file with statistics about feeds from a feed store.
 * 
 * @author David Urbansky
 * 
 */
public class FeedStatisticCreator {

    /**
     * <p>
     * After running the FeedChecker in {@link FeedChecker.BENCHMARK_MAX} mode and importing the resulting poll data
     * into the "feed_evaluation_polls" table in the database, this method calculates the coverage score and meta
     * information.
     * </p>
     * <ul>
     * <li>Coverage = percentNew / sqrt(missedItems/windowSize), averaged over all feeds and all polls except the first
     * one.</li>
     * <li>The average percentage of new entries whenever no item was missed.</li>
     * <li>The number of missed items on average.</li>
     * <li>The percentage of missed items in relation to the window size on average.</li>
     * </ul>
     * 
     * @throws SQLException
     */
    public static void maxCoveragePolicyEvaluation() throws SQLException {

        StringBuilder csv = new StringBuilder();

        DatabaseManager dbm = DatabaseManager.getInstance();

        Double coverage = null;
        Double percentNew = null;
        Double missed = null;
        Double missedPercent = null;

        ResultSet rs = dbm
                .runQuery("SELECT AVG(newWindowItems/(windowSize * SQRT(missedItems))) AS coverage, AVG(newWindowItems/windowSize) AS percentNew, AVG(missedItems) AS missedItems, AVG(missedItems/windowSize) AS missedPercent FROM feed_evaluation_polls WHERE numberOfPoll > 1");
        while (rs.next()) {
            coverage = rs.getDouble("coverage");
            percentNew = rs.getDouble("percentNew");
            missed = rs.getDouble("missedItems");
            missedPercent = rs.getDouble("missedPercent");
        }

        // build csv
        csv.append("\"================= Average Performance (averaged over all feeds, activity patterns, and polls) =================\"\n");
        csv.append("Coverage:;" + coverage).append("\n");
        csv.append("Percent New:;" + 100 * percentNew).append("\n");
        csv.append("Missed:;" + missed).append("\n");
        csv.append("Missed Items / Window Size:;" + 100 * missedPercent).append("\n\n");

        // create statistics by activity pattern
        Integer[] activityPatternIDs = FeedClassifier.getActivityPatternIDs();

        for (Integer activityPatternID : activityPatternIDs) {

            if (activityPatternID < 5 || activityPatternID == FeedClassifier.CLASS_ON_THE_FLY) {
                continue;
            }

            coverage = null;
            percentNew = null;
            missed = null;
            missedPercent = null;

            csv.append("\"================= Performance for ").append(FeedClassifier.getClassName(activityPatternID))
                    .append(" (averaged over all feeds and polls) =================\"\n");

            rs = dbm
                    .runQuery("SELECT AVG(newWindowItems/(windowSize * SQRT(missedItems))) AS coverage, AVG(newWindowItems/windowSize) AS percentNew, AVG(missedItems) AS missedItems, AVG(missedItems/windowSize) AS missedPercent FROM feed_evaluation_polls WHERE numberOfPoll > 1 AND activityPattern = "
                            + activityPatternID);

            while (rs.next()) {
                coverage = rs.getDouble("coverage");
                percentNew = rs.getDouble("percentNew");
                missed = rs.getDouble("missedItems");
                missedPercent = rs.getDouble("missedPercent");
            }
            csv.append("Coverage:;" + coverage).append("\n");
            csv.append("Percent New:;" + 100 * percentNew).append("\n");
            csv.append("Missed:;" + missed).append("\n");
            csv.append("Missed Items / Window Size:;" + 100 * missedPercent).append("\n\n");
        }

        System.out.println(csv);
        FileHelper.writeToFile("data/temp/feedEvaluationMaxCoverage.csv", csv);

        Logger.getRootLogger().info("logs written to data/temp/feedEvaluationMaxCoverage.csv");
    }

    /**
     * <p>
     * After running the FeedChecker in {@link FeedChecker.BENCHMARK_MIN} mode and importing the resulting poll data
     * into the "feed_evaluation_polls" table in the database, this method calculates the timeliness scores.
     * </p>
     * <ul>
     * <li>Timeliness = 1 / sqrt((cumulatedDelay/surroundingInterval + 1)), averaged over all feeds and item
     * discoveries.</li>
     * 
     * </ul>
     * 
     * @throws SQLException
     */
    public static void minDelayPolicyEvaluation() throws SQLException {

        StringBuilder csv = new StringBuilder();

        DatabaseManager dbm = DatabaseManager.getInstance();

        Double timeliness = null;
        Double timelinessLate = null;
        Double pollsPerNewItem = null;
        Double newItemsPerDiscovery = null;
        Double trafficPerNewItem = null;
        Double trafficPerNewItemCG = null;

        ResultSet rs = dbm
                .runQuery("SELECT AVG(1/sqrt(cumulatedDelay/surroundingIntervalsLength + 1)) AS timeliness, AVG(1/sqrt(cumulatedLateDelay/currentIntervalLength + 1)) AS timelinessLate FROM feed_evaluation_polls WHERE surroundingIntervalsLength > 0");
        if (rs.next()) {
            timeliness = rs.getDouble("timeliness");
            timelinessLate = rs.getDouble("timelinessLate");
        }

        rs = dbm.runQuery("SELECT COUNT(*) AS totalPolls, SUM(sizeOfPoll) AS totalTraffic FROM feed_evaluation_polls WHERE numberOfPoll > 1");
        rs.next();
        Integer totalPolls = rs.getInt("totalPolls");
        Double totalTraffic = rs.getDouble("totalTraffic");

        rs = dbm.runQuery("SELECT SUM(conditionalGetResponseSize) AS totalTrafficCG FROM feed_evaluation_polls WHERE newWindowItems = 0 AND numberOfPoll > 1 AND conditionalGetResponseSize > -1");
        rs.next();
        Double totalTrafficCG = rs.getDouble("totalTrafficCG");
        rs = dbm.runQuery("SELECT SUM(sizeOfPoll) AS sizeOfPoll FROM feed_evaluation_polls WHERE newWindowItems = 0 AND numberOfPoll > 1 AND conditionalGetResponseSize < 0");
        rs.next();
        totalTrafficCG += rs.getDouble("sizeOfPoll");

        rs = dbm.runQuery("SELECT SUM(newWindowItems) AS totalNewItems, SUM(newWindowItems)/COUNT(*) AS newItemsPerDiscovery FROM feed_evaluation_polls WHERE newWindowItems > 0");
        rs.next();
        Double totalNewItems = rs.getDouble("totalNewItems");
        newItemsPerDiscovery = rs.getDouble("newItemsPerDiscovery");

        pollsPerNewItem = totalPolls / totalNewItems;
        trafficPerNewItem = totalTraffic / totalNewItems;
        trafficPerNewItemCG = totalTrafficCG / totalNewItems;

        csv.append("\"================= Average Performance (averaged over all feeds, activity patterns, and item discoveries) =================\"\n");
        csv.append("Timeliness:;" + timeliness).append("\n");
        csv.append("Timeliness Late:;" + timelinessLate).append("\n");
        csv.append("Polls Per New Item:;" + pollsPerNewItem).append("\n");
        csv.append("New Items Per Discovery:;" + newItemsPerDiscovery).append("\n");
        csv.append("Traffic Per New Item:;" + trafficPerNewItem).append("\n");
        csv.append("Traffic Per New Item (conditional get):;" + trafficPerNewItemCG).append("\n\n");

        // create statistics by activity pattern
        Integer[] activityPatternIDs = FeedClassifier.getActivityPatternIDs();

        for (Integer activityPatternID : activityPatternIDs) {

            if (activityPatternID < 5 || activityPatternID == FeedClassifier.CLASS_ON_THE_FLY) {
                continue;
            }

            timeliness = null;
            timelinessLate = null;
            pollsPerNewItem = null;
            newItemsPerDiscovery = null;
            trafficPerNewItem = null;
            trafficPerNewItemCG = null;

            csv.append("\"================= Performance for ").append(FeedClassifier.getClassName(activityPatternID))
                    .append(" (averaged over all feeds and polls) =================\"\n");

            rs = dbm.runQuery("SELECT AVG(1/sqrt(cumulatedDelay/surroundingIntervalsLength + 1)) AS timeliness, AVG(1/sqrt(cumulatedLateDelay/currentIntervalLength + 1)) AS timelinessLate FROM feed_evaluation_polls WHERE surroundingIntervalsLength > 0 AND activityPattern = "
                    + activityPatternID);
            if (rs.next()) {
                timeliness = rs.getDouble("timeliness");
                timelinessLate = rs.getDouble("timelinessLate");
            }

            rs = dbm.runQuery("SELECT COUNT(*) AS totalPolls, SUM(sizeOfPoll) AS totalTraffic FROM feed_evaluation_polls WHERE numberOfPoll > 1 AND activityPattern = "
                    + activityPatternID);
            rs.next();
            totalPolls = rs.getInt("totalPolls");
            totalTraffic = rs.getDouble("totalTraffic");

            rs = dbm.runQuery("SELECT SUM(conditionalGetResponseSize) AS totalTrafficCG FROM feed_evaluation_polls WHERE newWindowItems = 0 AND numberOfPoll > 1 AND conditionalGetResponseSize > -1 AND activityPattern = "
                    + activityPatternID);
            rs.next();
            totalTrafficCG = rs.getDouble("totalTrafficCG");
            rs = dbm.runQuery("SELECT SUM(sizeOfPoll) AS sizeOfPoll FROM feed_evaluation_polls WHERE newWindowItems = 0 AND numberOfPoll > 1 AND conditionalGetResponseSize < 0 AND activityPattern = "
                    + activityPatternID);
            rs.next();
            totalTrafficCG += rs.getDouble("sizeOfPoll");

            rs = dbm.runQuery("SELECT SUM(newWindowItems) AS totalNewItems, SUM(newWindowItems)/COUNT(*) AS newItemsPerDiscovery FROM feed_evaluation_polls WHERE newWindowItems > 0 AND activityPattern = "
                    + activityPatternID);
            rs.next();
            totalNewItems = rs.getDouble("totalNewItems");
            newItemsPerDiscovery = rs.getDouble("newItemsPerDiscovery");

            pollsPerNewItem = totalPolls / totalNewItems;
            trafficPerNewItem = totalTraffic / totalNewItems;
            trafficPerNewItemCG = totalTrafficCG / totalNewItems;

            csv.append("Timeliness:;" + timeliness).append("\n");
            csv.append("Timeliness Late:;" + timelinessLate).append("\n");
            csv.append("Polls Per New Item:;" + pollsPerNewItem).append("\n");
            csv.append("New Items Per Discovery:;" + newItemsPerDiscovery).append("\n");
            csv.append("Traffic Per New Item:;" + trafficPerNewItem).append("\n");
            csv.append("Traffic Per New Item (conditional get):;" + trafficPerNewItemCG).append("\n\n");

        }

        System.out.println(csv);
        FileHelper.writeToFile("data/temp/feedEvaluationMinDelay.csv", csv);

        Logger.getRootLogger().info("logs written to data/temp/feedEvaluationMinDelay.csv");
    }

    public static void timelinessChart() throws SQLException {

        StringBuilder csv = new StringBuilder();

        DatabaseManager dbm = DatabaseManager.getInstance();

        // new item number, [total timeliness, number of feeds]
        Map<Integer, Double[]> timelinessChartData = new TreeMap<Integer, Double[]>();

        ResultSet rs = dbm
                .runQuery("SELECT feedID, 1/SQRT(cumulatedDelay/surroundingIntervalsLength + 1) AS timeliness FROM feed_evaluation_polls WHERE surroundingIntervalsLength > 0");
        int previousFeedID = -1;
        int newItemNumber = 1;
        while (rs.next()) {

            Integer feedID = rs.getInt("feedID");

            if (feedID != previousFeedID) {
                newItemNumber = 1;
            }

            Double[] data = timelinessChartData.get(newItemNumber);
            if (data == null) {
                data = new Double[2];
                data[0] = 0.0;
                data[1] = 0.0;
            }
            data[0] += rs.getDouble("timeliness");
            data[1]++;
            timelinessChartData.put(newItemNumber, data);
            previousFeedID = feedID;
            newItemNumber++;
        }

        csv.append("new item number;average timeliness;number of feeds\n");
        for (Entry<Integer, Double[]> dataEntry : timelinessChartData.entrySet()) {
            double avgTimeliness = dataEntry.getValue()[0] / dataEntry.getValue()[1];
            csv.append(dataEntry.getKey()).append(";").append(avgTimeliness).append(";")
                    .append(dataEntry.getValue()[1]).append("\n");
        }

        FileHelper.writeToFile("data/temp/feedEvaluationTimelinessChart.csv", csv);

        Logger.getRootLogger().info("logs written to data/temp/feedEvaluationTimelinessChart.csv");

    }

    public static void createFeedUpdateIntervalDistribution(FeedStore feedStore, String statisticOutputPath)
            throws IOException {

        FeedChecker fc = new FeedChecker(feedStore);
        FeedReaderEvaluator.setBenchmarkPolicy(FeedReaderEvaluator.BENCHMARK_MAX_COVERAGE);

        FileWriter csv = new FileWriter(statisticOutputPath);

        int c = 0;
        int totalSize = feedStore.getFeeds().size();
        for (Feed feed : feedStore.getFeeds()) {

            // if (feed.getId() != 27) {
            // continue;
            // }

            FeedBenchmarkFileReader fbfr = new FeedBenchmarkFileReader(feed, fc);
            fbfr.updateEntriesFromDisk();
            if (feed.getEntries() == null || feed.getEntries().size() < 1) {
                continue;
            }
            FeedPostStatistics fps = new FeedPostStatistics(feed);
            csv.write(String.valueOf(feed.getId() + ";"));
            csv.write(String.valueOf(feed.getActivityPattern()) + ";");
            csv.write(String.valueOf(fps.getAvgEntriesPerDay()) + ";");
            csv.write(String.valueOf(fps.getMedianPostGap()) + ";");
            csv.write(String.valueOf((long) fps.getAveragePostGap()) + ";");
            csv.write("\n");

            csv.flush();

            c++;

            feed.freeMemory();
            feed.setLastHeadlines("");
            feed.setMeticulousPostDistribution(null);
            feed = null;

            Logger.getRootLogger().info("percent done: " + MathHelper.round(100 * c / (double) totalSize, 2));
        }

        csv.close();
    }

    public static void createGeneralStatistics(FeedStore feedStore, String statisticOutputPath) {

        // colors for the google chart
        List<String> colors = new ArrayList<String>();
        colors.add("E72727");
        colors.add("34B434");
        colors.add("3072F3");
        colors.add("FF9900");
        colors.add("7777CC");
        colors.add("AA0033");
        colors.add("626262");
        colors.add("9ABE9A");
        colors.add("E6FF00");
        colors.add("F626B1");
        colors.add("FF9900");

        // count number of feeds in each updateClass
        CountMap updateClassCounts = new CountMap();

        // number of unique domain names
        Set<String> uniqueDomains = new HashSet<String>();

        List<Feed> feeds = feedStore.getFeeds();
        for (Feed feed : feeds) {

            // int updateClassCount = (Integer) updateClassCounts.get(feed.getUpdateClass());
            // updateClassCount++;
            // updateClassCounts.put(feed.getUpdateClass(), updateClassCount);

            updateClassCounts.increment(feed.getActivityPattern());

            uniqueDomains.add(Crawler.getDomain(feed.getFeedUrl()));
        }

        StringBuilder stats = new StringBuilder();
        stats.append("Number of feeds:").append(feeds.size()).append("\n");
        stats.append("Number of unique domains:").append(uniqueDomains.size()).append("\n");

        String chartData = "";
        String chartDataLabels = "";
        String chartColors = "";
        for (Entry<Object, Integer> o : updateClassCounts.entrySet()) {
            stats.append("Number of feeds in update class ").append(o.getKey()).append(":").append(o.getValue())
                    .append("\n");
            chartData += o.getValue().intValue() + ",";
            chartDataLabels += o.getKey() + "|";
            chartColors += colors.get((Integer) o.getKey()) + "|";
        }
        chartData = chartData.substring(0, chartData.length() - 1);
        chartDataLabels = chartDataLabels.substring(0, chartDataLabels.length() - 1);
        chartColors = chartColors.substring(0, chartColors.length() - 1);

        stats.append("Google pie chart:").append("http://chart.apis.google.com/chart?chs=600x425&chco=")
                .append(chartColors).append("&chdl=").append(chartDataLabels).append("&chds=0,").append(feeds.size())
                .append("&cht=p&chd=t:").append(chartData).append("\n");

        FileHelper.writeToFile(statisticOutputPath, stats);

    }

    /**
     * @param args
     * @throws IOException
     * @throws SQLException
     */
    public static void main(String[] args) throws IOException, SQLException {
        // FeedStatisticCreator.createGeneralStatistics(FeedDatabase.getInstance(), "data/temp/feedstats_combined.txt");
        // FeedStatisticCreator.createFeedUpdateIntervalDistribution(FeedDatabase.getInstance(),"data/temp/feedUpdateIntervals.csv");
        // FeedStatisticCreator.maxCoveragePolicyEvaluation();
        FeedStatisticCreator.minDelayPolicyEvaluation();
        // FeedStatisticCreator.timelinessChart();

    }

}
