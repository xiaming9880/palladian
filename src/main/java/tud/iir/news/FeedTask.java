package tud.iir.news;

import java.util.Date;

import org.apache.log4j.Logger;

/**
 * The {@link FeedChecker} schedules {@link FeedTask}s for each {@link Feed}. The {@link FeedTask} will run every time
 * the feed is checked and also performs all
 * set {@link FeedProcessingAction}s.
 * 
 * @author David Urbansky
 * @author Klemens Muthmann
 * @see FeedChecker
 * 
 */
class FeedTask implements Runnable {

    private final static Logger LOGGER = Logger.getLogger(FeedTask.class);

    /**
     * The feed retrieved by this task.
     */
    private Feed feed = null;

    /**
     * The feed checker calling this task. // FIXME This is a workaround. Can be fixed by externalizing update
     * strategies to a true strategy pattern.
     */
    private final FeedChecker feedChecker;

    /**
     * Creates a new retrieval task for a provided feed.
     * 
     * @param feed The feed retrieved by this task.
     */
    public FeedTask(Feed feed, FeedChecker feedChecker) {
        this.feed = feed;
        this.feedChecker = feedChecker;
    }

    @Override
    public void run() {
        synchronized (this) {
            SchedulerTask.THREADS_ALIVE++;
            SchedulerTask.THREAD_POOL_QUEUE_SIZE--;
            LOGGER.info("Beginning of thread.");
            LOGGER.info("Threads alive: " + SchedulerTask.THREADS_ALIVE);
            LOGGER.info("Queue size: " + SchedulerTask.THREAD_POOL_QUEUE_SIZE);
        }
        NewsAggregator fa = new NewsAggregator();

        // parse the feed and get all its entries, do that here since that takes some time and this is a thread so
        // it can be done in parallel

        feed.updateEntries(false);

        // classify feed if it has never been classified before
        if (feed.getUpdateClass() == -1) {
            FeedClassifier.classify(feed);
        }

        // remember the time the feed has been checked
        feed.setLastChecked(new Date());

        feedChecker.updateCheckIntervals(feed);

        // perform actions on this feeds entries
        feedChecker.getFeedProcessingAction().performAction(feed);

        // save the feed back to the database
        fa.updateFeed(feed);
        synchronized (this) {
            SchedulerTask.THREADS_ALIVE--;
            LOGGER.info("End of Thread");
            LOGGER.info("Threads alive: " + SchedulerTask.THREADS_ALIVE);
            LOGGER.info("Queue size: " + SchedulerTask.THREAD_POOL_QUEUE_SIZE);
            
        }
    }

}