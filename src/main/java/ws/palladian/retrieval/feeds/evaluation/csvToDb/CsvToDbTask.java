package ws.palladian.retrieval.feeds.evaluation.csvToDb;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;

import ws.palladian.helper.StopWatch;
import ws.palladian.helper.date.DateHelper;
import ws.palladian.retrieval.feeds.Feed;
import ws.palladian.retrieval.feeds.FeedItem;
import ws.palladian.retrieval.feeds.FeedTaskResult;
import ws.palladian.retrieval.feeds.evaluation.DatasetCreator;
import ws.palladian.retrieval.feeds.evaluation.DatasetMerger;
import ws.palladian.retrieval.feeds.persistence.FeedDatabase;

/**
 * TUDCS6 specific.<br />
 * Load the reconstructed feed entry streams from csv files (created when creating a dataset such as TUDCS6) to
 * database.
 * 
 * Similar to {@link FeedTask}, this class uses a thread to process a single feed. While {@link FeedTask} retrieves the
 * feed from the web, this task iterates over persisted files generated by the {@link DatasetCreator}.
 * 
 * @author Sandro Reichert
 * 
 */
public class CsvToDbTask implements Callable<FeedTaskResult> {

    /** The logger for this class. */
    private final static Logger LOGGER = Logger.getLogger(CsvToDbTask.class);

    /**
     * The feed to process by this task.
     */
    private Feed feed = null;

    /**
     * The feed DB.
     */
    private final FeedDatabase feedDatabase;

    /**
     * Warn if processing of a feed takes longer than this.
     */
    public static final long EXECUTION_WARN_TIME = 3 * DateHelper.MINUTE_MS;

    /**
     * All items that have ever been seen in this feed. Remember to call {@link FeedItem#freeMemory()} on all items
     * since there may be MANY items.
     */
    List<FeedItem> allItems = new ArrayList<FeedItem>();

    /**
     * Creates a new gz processing task for a provided feed.
     * 
     * @param feed The feed retrieved by this task.
     */
    public CsvToDbTask(Feed dbFeed, FeedDatabase feedDatabase) {
        this.feed = dbFeed;
        this.feedDatabase = feedDatabase;
    }

    /** A collection of all intermediate results that can happen, e.g. when updating meta information or a data base. */
    private Set<FeedTaskResult> resultSet = new HashSet<FeedTaskResult>();

    @Override
    public FeedTaskResult call() throws Exception {
        StopWatch timer = new StopWatch();
        try {
            LOGGER.debug("Start processing of feed id " + feed.getId() + " (" + feed.getFeedUrl() + ")");

            // skip feeds that have never been checked. We dont have any files, nor a folder for them.
            if (feed.getChecks() == 0) {
                LOGGER.debug("Feed id " + feed.getId() + " has never been checked. Nothing to do.");
                resultSet.add(FeedTaskResult.SUCCESS);
                doFinalLogging(timer);
                return getResult();
            }

            // skip feeds that contain no item
            if (feed.getNumberOfItemsReceived() == 0) {
                LOGGER.debug("Feed id " + feed.getId() + " has no items. Nothing to do.");
                resultSet.add(FeedTaskResult.SUCCESS);
                doFinalLogging(timer);
                return getResult();
            }

            // get the path of the feed's folder and csv file
            String csvFilePath = DatasetCreator.getCSVFilePath(feed.getId(),
                    DatasetCreator.getSafeFeedName(feed.getFeedUrl()));

            // get items from csv file
            // Caution. some csv files are huge, might be better to stream the file instead of loading it into memory.
            List<String> items = DatasetMerger.readCsv(csvFilePath);

            // split items into parts
            List<String[]> splitItems = DatasetMerger.splitItems(items);

            int batchSize = 1000;
            int listCapacity = Math.max(items.size(), batchSize);
            List<FeedItem> allItems = new ArrayList<FeedItem>(listCapacity);

            int itemCounter = 0;
            for (String[] splitItem : splitItems) {

                // ignore MISS line
                if (splitItem[0].startsWith("MISS")) {
                    continue;
                }

                try {
                    itemCounter++;
                    // get timestamps and windowSize from csv
                    String csvPublishDate = splitItem[0];

                    Date publishDate = null;
                    // set only if entry has a publish time. In case the item had no publish date, we wrote
                    // 0000000000000 to the csv file to indicate a not existing publish date
                    if (!csvPublishDate.equals("0000000000000")) {
                        publishDate = new Date(Long.parseLong(csvPublishDate));
                    }

                    Date pollTime = new Date(Long.parseLong(splitItem[1]));
                    String hash = splitItem[2];

                    // create new, minimal item and add to feed
                    FeedItem item = new FeedItem();
                    item.setFeedId(feed.getId());
                    item.setHash(hash, true);
                    item.setPublished(publishDate);
                    item.setPollTimestamp(pollTime);

                    // keep all items locally
                    allItems.add(item);

                    // do not waste memory: collect only 1000 items, add them to db and continue. TUDCS6 had up to
                    // 12 million items per feed...
                    if (allItems.size() == batchSize) {
                        addItemsToDb(allItems);
                        allItems = new ArrayList<FeedItem>(listCapacity);
                    }

                } catch (NumberFormatException e) {
                    LOGGER.fatal("Could not get number from csv: " + e.getLocalizedMessage());
                }
            }

            if (itemCounter != feed.getNumberOfItemsReceived()) {
                LOGGER.error("Feed id " + feed.getId() + ": feed.getNumberOfItemsReceived() = "
                        + feed.getNumberOfItemsReceived() + " but there were " + itemCounter + " items in csv file!");
                resultSet.add(FeedTaskResult.ERROR);
            } else {
                resultSet.add(FeedTaskResult.SUCCESS);
            }

            // store all Items in db
            doFinalStuff(timer, allItems);
            return getResult();

            // This is ugly but required to catch everything. If we skip this, threads may run much longer till they are
            // killed by the thread pool internals. Errors are logged only and not written to database.
        } catch (Throwable th) {
            LOGGER.fatal("Error processing feedID " + feed.getId() + ": " + th);
            resultSet.add(FeedTaskResult.ERROR);
            doFinalLogging(timer);
            return getResult();
        }

    }


    /**
     * Sets the feed task result and processing time of this task, saves the feed to database, does the final logging
     * and frees the feed's memory.
     * 
     * @param timer The {@link StopWatch} to estimate processing time
     */
    private void doFinalStuff(StopWatch timer, List<FeedItem> allItems) {
        if (timer.getElapsedTime() > EXECUTION_WARN_TIME) {
            LOGGER.warn("Processing feed id " + feed.getId() + " took very long: "
                    + timer.getElapsedTimeString());
            resultSet.add(FeedTaskResult.EXECUTION_TIME_WARNING);
        }
        addItemsToDb(allItems);
        doFinalLogging(timer);
        // since the feed is kept in memory we need to remove all items and the document stored in the feed
        feed.freeMemory();
    }

    /**
     * Decide the status of this FeedTask. This is done here to have a fixed ranking on the values.
     * 
     * @return The (current) result of the feed task.
     */
    private FeedTaskResult getResult() {
        FeedTaskResult result = null;
        if (resultSet.contains(FeedTaskResult.ERROR)) {
            result = FeedTaskResult.ERROR;
        } else if (resultSet.contains(FeedTaskResult.UNREACHABLE)) {
            result = FeedTaskResult.UNREACHABLE;
        } else if (resultSet.contains(FeedTaskResult.UNPARSABLE)) {
            result = FeedTaskResult.UNPARSABLE;
        } else if (resultSet.contains(FeedTaskResult.EXECUTION_TIME_WARNING)) {
            result = FeedTaskResult.EXECUTION_TIME_WARNING;
        } else if (resultSet.contains(FeedTaskResult.MISS)) {
            result = FeedTaskResult.MISS;
        } else if (resultSet.contains(FeedTaskResult.SUCCESS)) {
            result = FeedTaskResult.SUCCESS;
        } else {
            result = FeedTaskResult.OPEN;
        }

        return result;
    }

    /**
     * Do final logging of result to error or debug log, depending on the FeedTaskResult.
     * 
     * @param timer the {@link StopWatch} started when started processing the feed.
     */
    private void doFinalLogging(StopWatch timer) {
        FeedTaskResult result = getResult();
        String msg = "Finished processing of feed id " + feed.getId() + ". Result: " + result
                + ". Processing took " + timer.getElapsedTimeString();
        if (result == FeedTaskResult.ERROR) {
            LOGGER.error(msg);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(msg);
        }
    }

    /**
     * Save the feed back to the database. In case of database errors, add error to {@link #resultSet}.
     * 
     * @param allItems The list of all items to add.
     */
    private void addItemsToDb(List<FeedItem> allItems) {
        boolean dbSuccess = feedDatabase.addEvaluationItems(allItems);
        if (!dbSuccess) {
            resultSet.add(FeedTaskResult.ERROR);
        }
    }

}
