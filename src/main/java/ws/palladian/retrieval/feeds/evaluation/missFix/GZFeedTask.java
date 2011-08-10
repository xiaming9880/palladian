package ws.palladian.retrieval.feeds.evaluation.missFix;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;

import sun.net.www.protocol.http.HttpURLConnection;
import ws.palladian.helper.FileHelper;
import ws.palladian.helper.HTTPHelper;
import ws.palladian.helper.StopWatch;
import ws.palladian.helper.date.DateHelper;
import ws.palladian.retrieval.DocumentRetriever;
import ws.palladian.retrieval.HttpResult;
import ws.palladian.retrieval.feeds.Feed;
import ws.palladian.retrieval.feeds.FeedClassifier;
import ws.palladian.retrieval.feeds.FeedReader;
import ws.palladian.retrieval.feeds.FeedRetriever;
import ws.palladian.retrieval.feeds.FeedRetrieverException;
import ws.palladian.retrieval.feeds.FeedTaskResult;
import ws.palladian.retrieval.feeds.evaluation.DatasetCreator;
import ws.palladian.retrieval.feeds.meta.MetaInformationExtractor;

/**
 * TUDCS6 specific.<br />
 * Reconstruct the csv file from persisted gz files to eliminate false positive MISSes caused by altering window sizes
 * and wrong item hashes in case of seesionIDs in items' link and raw id attributes.
 * 
 * Similar to {@link FeedTask}, this class uses a thread to process a single feed. While {@link FeedTask} retrieves the
 * feed from the web, this task iterates over persisted files generated by the {@link DatasetCreator}.
 * 
 * @author Sandro Reichert
 * 
 */
public class GZFeedTask implements Callable<FeedTaskResult> {

    /** The logger for this class. */
    private final static Logger LOGGER = Logger.getLogger(GZFeedTask.class);

    /**
     * The feed corrected by this task.
     */
    private Feed correctedFeed = null;

    /** Number of MISSES the feed had before running this task. */
    private int initialMisses = 0;

    /** Number of checks the feed had before running this task. */
    private int initialChecks = 0;

    /**
     * The feed checker calling this task. // FIXME This is a workaround. Can be fixed by externalizing update
     * strategies to a true strategy pattern.
     */
    private final FeedReader feedReader;

    /**
     * Warn if processing of a feed takes longer than this.
     */
    public static final long EXECUTION_WARN_TIME = 3 * DateHelper.MINUTE_MS;

    /**
     * Creates a new gz processing task for a provided feed.
     * 
     * @param feed The feed retrieved by this task.
     */
    public GZFeedTask(Feed dbFeed, FeedReader feedChecker) {
        this.correctedFeed = copyRequiredFeedProperties(dbFeed);
        this.feedReader = feedChecker;
        this.initialMisses = dbFeed.getMisses();
        this.initialChecks = dbFeed.getChecks();
    }

    /** A collection of all intermediate results that can happen, e.g. when updating meta information or a data base. */
    private Set<FeedTaskResult> resultSet = new HashSet<FeedTaskResult>();

    @Override
    public FeedTaskResult call() throws Exception {
        StopWatch timer = new StopWatch();
        try {
            LOGGER.debug("Start processing of feed id " + correctedFeed.getId() + " (" + correctedFeed.getFeedUrl()
                    + ")");
            String safeFeedName = DatasetCreator.getSafeFeedName(correctedFeed.getFeedUrl());
            String folderPath = DatasetCreator.getFolderPath(correctedFeed.getId());
            String csvPath = DatasetCreator.getCSVFilePath(correctedFeed.getId(), safeFeedName);
            File originalCsv = new File(csvPath);
            String newCsvPath = FileHelper.getRenamedFilename(originalCsv, originalCsv.getName() + ".bak");
            FileHelper.renameFile(originalCsv, newCsvPath);

            boolean storeMetadata = false;

            int filesProcessed = 0;
            File[] allFiles = FileHelper.getFiles(folderPath, ".gz");
            for (File file : allFiles) {
                // skip files that have been unparsable before.
                if (file.getName().endsWith("unparsable.gz")) {
                    continue;
                }

                FeedRetriever feedRetriever = new FeedRetriever();
                DocumentRetriever documentRetriever = new DocumentRetriever();
                HttpResult gzHttpResult = documentRetriever.loadSerializedGzip(file);

                correctedFeed.setLastPollTime(getChecktimeFromFile(file));

                // process the returned header first
                // case 1: client or server error, statuscode >= 400
                if (gzHttpResult.getStatusCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    LOGGER.error("Could not get Document for feed id " + correctedFeed.getId()
                            + ". Server returned HTTP status code " + gzHttpResult.getStatusCode());

                    boolean actionSuccess = feedReader.getFeedProcessingAction().performActionOnHighHttpStatusCode(
                            correctedFeed, gzHttpResult);
                    if (!actionSuccess) {
                        resultSet.add(FeedTaskResult.ERROR);
                    }

                } else {
                    // case 2: document has not been modified since last request
                    // should not happen since we do not have written gz files in case of HTTP 304
                    if (gzHttpResult.getStatusCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                        // feedReader.updateCheckIntervals(correctedFeed);
                        correctedFeed.setLastSuccessfulCheckTime(correctedFeed.getLastPollTime());
                        correctedFeed.increaseChecks();
                        boolean actionSuccess = feedReader.getFeedProcessingAction().performActionOnUnmodifiedFeed(
                                correctedFeed, gzHttpResult);
                        if (!actionSuccess) {
                            resultSet.add(FeedTaskResult.ERROR);
                        }

                        // case 3: default case, try to process the feed.
                    } else {
                        // store http header information
                        correctedFeed.setLastETag(gzHttpResult.getHeaderString("ETag"));
                        correctedFeed.setHttpLastModified(HTTPHelper.getDateFromHeader(gzHttpResult, "Last-Modified",
                                false));

                        Feed gzFeed = null;
                        try {
                            gzFeed = feedRetriever.getFeed(gzHttpResult);
                        } catch (FeedRetrieverException e) {
                            LOGGER.fatal("Could not read feed from file " + file.getAbsolutePath() + " . "
                                    + e.getLocalizedMessage());
                            resultSet.add(FeedTaskResult.UNPARSABLE);
                            continue;
                        }

                        correctedFeed.increaseChecks();
                        correctedFeed.setItems(gzFeed.getItems());
                        correctedFeed.setLastSuccessfulCheckTime(correctedFeed.getLastPollTime());
                        correctedFeed.setWindowSize(gzFeed.getItems().size());

                        // store metadata if it has been created before or now.
                        storeMetadata = storeMetadata || generateMetaInformation(gzHttpResult, gzFeed);
                        // feedReader.updateCheckIntervals(correctedFeed);

                        // perform actions on this feeds entries.
                        LOGGER.debug("Performing action on feed: " + correctedFeed.getId() + "("
                                + correctedFeed.getFeedUrl() + ")");
                        boolean actionSuccess = feedReader.getFeedProcessingAction().performAction(correctedFeed,
                                gzHttpResult);
                        if (!actionSuccess) {
                            resultSet.add(FeedTaskResult.ERROR);
                        }

                        // rename gz file if there are no new items in it. File may be removed afterwards.
                        if (!correctedFeed.hasNewItem()) {
                            String newGzPath = FileHelper.getRenamedFilename(file, file.getName() + ".removeable");
                            FileHelper.renameFile(file, newGzPath);
                        }

                    }
                }

                filesProcessed++;
                if ((filesProcessed % 100) == 0) {
                    LOGGER.info("Processed " + filesProcessed + " gz files so far.");
                }

            }

            // Check whether we eliminated some misses or got even more.
            if (initialMisses < correctedFeed.getMisses()) {
                LOGGER.fatal("After processing, feed id " + correctedFeed.getId() + " has more MISSes than before!!");
                resultSet.add(FeedTaskResult.MISS);
            } else {
                resultSet.add(FeedTaskResult.SUCCESS);
                int removedMisses = initialMisses - correctedFeed.getMisses();
                if (removedMisses > 0) {
                    LOGGER.info("Feed id " + correctedFeed.getId() + ": removed " + removedMisses
                            + " MISSes. Initial MISSes: " + initialMisses
                            + ", remaining MISSes: " + correctedFeed.getMisses());
                }
            }

            doFinalStuff(timer, storeMetadata);
            return getResult();

            // This is ugly but required to catch everything. If we skip this, threads may run much longer till they are
            // killed by the thread pool internals. Errors are logged only and not written to database.
        } catch (Throwable th) {
            LOGGER.error("Error processing feedID " + correctedFeed.getId() + ": " + th);
            resultSet.add(FeedTaskResult.ERROR);
            doFinalLogging(timer);
            return getResult();
        }

    }

    /**
     * Classify feed and process general meta data like feed title, language, size, format.
     * Everything in this method is done only if it has never been done before or once every month.
     * 
     * @param httpResult
     * @param downloadedFeed
     * @return
     */
    private boolean generateMetaInformation(HttpResult httpResult, Feed downloadedFeed) {
        boolean metadataCreated = false;

        if (correctedFeed.getActivityPattern() == FeedClassifier.CLASS_UNKNOWN
                || correctedFeed.getLastPollTime() != null
                && (System.currentTimeMillis() - correctedFeed.getLastPollTime().getTime()) > DateHelper.MONTH_MS) {

            metadataCreated = true;
            correctedFeed.setActivityPattern(FeedClassifier.classify(correctedFeed));
            MetaInformationExtractor metaInfExt = new MetaInformationExtractor(httpResult);
            metaInfExt.updateFeedMetaInformation(correctedFeed.getMetaInformation());
            correctedFeed.getMetaInformation().setTitle(downloadedFeed.getMetaInformation().getTitle());
            correctedFeed.getMetaInformation().setByteSize(downloadedFeed.getMetaInformation().getByteSize());
            correctedFeed.getMetaInformation().setLanguage(downloadedFeed.getMetaInformation().getLanguage());
        }
        return metadataCreated;
    }

    /**
     * Sets the feed task result and processing time of this task, saves the feed to database, does the final logging
     * and frees the feed's memory.
     * 
     * @param timer The {@link StopWatch} to estimate processing time
     * @param storeMetadata Specify whether metadata should be updated in database.
     */
    private void doFinalStuff(StopWatch timer, boolean storeMetadata) {
        if (timer.getElapsedTime() > EXECUTION_WARN_TIME) {
            LOGGER.warn("Processing feed id " + correctedFeed.getId() + " took very long: "
                    + timer.getElapsedTimeString());
            resultSet.add(FeedTaskResult.EXECUTION_TIME_WARNING);
        }

        // Caution. The result written to db may be wrong since we can't write a db-error to db that occurs while
        // updating the database. This has no effect as long as we do not restart the FeedReader in this case.
        correctedFeed.setLastFeedTaskResult(getResult());
        correctedFeed.increaseTotalProcessingTimeMS(timer.getElapsedTime());

        // It is important to write the initial number of checks back to the feed since we iterate over all files but
        // there may were more checks than gz files in case we got HTTP-not-modified responses when creating the
        // dataset. In case of 304, we didn't store a gz.
        correctedFeed.setChecks(initialChecks);
        updateFeed(storeMetadata);

        doFinalLogging(timer);
        // since the feed is kept in memory we need to remove all items and the document stored in the feed
        correctedFeed.freeMemory();
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
        String msg = "Finished processing of feed id " + correctedFeed.getId() + ". Result: " + result
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
     * @param storeMetadata
     */
    private void updateFeed(boolean storeMetadata) {
        boolean dbSuccess = feedReader.updateFeed(correctedFeed, storeMetadata, correctedFeed.hasNewItem());
        if (!dbSuccess) {
            resultSet.add(FeedTaskResult.ERROR);
        }
    }

    /**
     * Creates a new feed that has most but not all of the properties the provided feed has.
     * 
     * @param dbFeed feed to copy.
     * @return New feed with partly copied properties.
     */
    private Feed copyRequiredFeedProperties(Feed dbFeed) {
        Feed newFeed = new Feed();
        newFeed.setActivityPattern(dbFeed.getActivityPattern());
        newFeed.setAdditionalData(dbFeed.getAdditionalData());
        newFeed.setBlocked(dbFeed.isBlocked());
        // newFeed.setCachedItems(dbFeed.getCachedItems());
        // newFeed.setChecks(dbFeed.getChecks());
        newFeed.setFeedMetaInformation(dbFeed.getMetaInformation());
        newFeed.setFeedUrl(dbFeed.getFeedUrl());
        newFeed.setHttpLastModified(dbFeed.getHttpLastModified());
        newFeed.setId(dbFeed.getId());
        newFeed.setLastFeedTaskResult(dbFeed.getLastFeedTaskResult());
        newFeed.setTotalProcessingTime(dbFeed.getTotalProcessingTime());
        newFeed.setUnparsableCount(dbFeed.getUnparsableCount());
        newFeed.setUnreachableCount(dbFeed.getUnreachableCount());
        newFeed.setUpdateInterval(dbFeed.getUpdateInterval());
        return newFeed;
    }

    /**
     * Get the time this feed has been checked from the timestamp in millisecond in the file name. It is assumed
     * that the file name starts with "<Java-Timestamp>_"
     * 
     * @param file File to get timestamp from
     * @return The timestamp.
     */
    private Date getChecktimeFromFile(File file) {
        Date checkTime = null;
        String fileName = file.getName();
        Long timestamp = Long.parseLong(fileName.substring(0, fileName.indexOf("_")));
        checkTime = new Date(timestamp);
        return checkTime;
    }
}
