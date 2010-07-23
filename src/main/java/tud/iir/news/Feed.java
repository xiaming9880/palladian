package tud.iir.news;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Represents a news feed.
 * 
 * @author Philipp Katz
 * @author David Urbansky
 * @author klemens.muthmann@googlemail.com
 * 
 */
public class Feed {

    /**
     * different formats of feeds; this has just informational character; the parser of the aggregator will determine the feed's format automatically.
     */
    public static final int FORMAT_ATOM = 1;
    public static final int FORMAT_RSS = 2;

    // different text lengths in feeds
    public static final int TEXT_TYPE_UNDETERMINED = 0;
    public static final int TEXT_TYPE_NONE = 1;
    public static final int TEXT_TYPE_PARTIAL = 2;
    public static final int TEXT_TYPE_FULL = 3;

    private int id = -1;
    private String feedUrl;
    private String siteUrl;
    private String title;
    private int format;
    private Date added;
    private String language;
    private int textType = TEXT_TYPE_UNDETERMINED;
    
    private List<FeedEntry> entries;

    /** number of times the feed has been retrieved and read */
    private int checks = 0;

    /**
     * time in minutes until it is expected to find at least one new entry in the feed
     */
    private int minCheckInterval = 30;

    /**
     * time in minutes until it is expected to find only new but one new entries in the feed
     */
    private int maxCheckInterval = 60;
    
    /**
     * The date this feed was checked for updates the last time.
     */
    private Date lastChecked;

    /** a list of headlines that were found at the last check */
    private String lastHeadlines = "";

    /** number of times the feed was checked but could not be found or parsed */
    private int unreachableCount = 0;

    /** timestamp of the last feed entry found in this feed */
    private Date lastFeedEntry = null;

    /**
     * number of news that were posted in a certain minute of the day, minute of the day : frequency of posts; chances a post could have appeared
     */
    private Map<Integer, int[]> meticulousPostDistribution = new HashMap<Integer, int[]>();

    /** the update class of the feed is one of {@link FeedClassifier}s classes */
    private int updateClass = -1;

    public Feed() {
        super();
    }

    public Feed(String feedUrl) {
        this();
        this.feedUrl = feedUrl;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFeedUrl() {
        return feedUrl;
    }

    public void setFeedUrl(String feedUrl) {
        this.feedUrl = feedUrl;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String pageUrl) {
        this.siteUrl = pageUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getFormat() {
        return format;
    }

    public void setFormat(int format) {
        this.format = format;
    }

    public Date getAdded() {
        return added;
    }

    public Timestamp getAddedSQLTimestamp() {
        if (added != null) {
            return new Timestamp(added.getTime());
        }
        return null;
    }

    public void setAdded(Date added) {
        this.added = added;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getTextType() {
        return textType;
    }

    public void setTextType(int textType) {
        this.textType = textType;
    }

    public void setEntries(List<FeedEntry> entries) {
        this.entries = entries;
    }

    public List<FeedEntry> getEntries() {
        return entries;
    }

    public void setChecks(int checks) {
        this.checks = checks;
    }

    public void increaseChecks() {
        this.checks++;
    }

    public int getChecks() {
        return checks;
    }

    public void setMaxCheckInterval(int maxCheckInterval) {
        maxCheckInterval = Math.max(1, maxCheckInterval);
        this.maxCheckInterval = maxCheckInterval;
    }

    public int getMaxCheckInterval() {
        return maxCheckInterval;
    }

    public void setMinCheckInterval(int minCheckInterval) {
        minCheckInterval = Math.max(1, minCheckInterval);
        this.minCheckInterval = minCheckInterval;
    }

    public int getMinCheckInterval() {
        return minCheckInterval;
    }

    public void setLastHeadlines(String lastHeadlines) {
        this.lastHeadlines = lastHeadlines;
    }

    public String getLastHeadlines() {
        return lastHeadlines;
    }

    public void setUnreachableCount(int unreachableCount) {
        this.unreachableCount = unreachableCount;
    }

    public int getUnreachableCount() {
        return unreachableCount;
    }

    public void setLastFeedEntry(Date lastFeedEntry) {
        this.lastFeedEntry = lastFeedEntry;
    }

    public Date getLastFeedEntry() {
        return lastFeedEntry;
    }

    public Timestamp getLastFeedEntrySQLTimestamp() {
        if (lastFeedEntry != null) {
            return new Timestamp(lastFeedEntry.getTime());
        }
        return null;
    }

    public void setMeticulousPostDistribution(Map<Integer, int[]> meticulousPostDistribution) {
        this.meticulousPostDistribution = meticulousPostDistribution;
    }

    public Map<Integer, int[]> getMeticulousPostDistribution() {
        return meticulousPostDistribution;
    }

    /**
     * Check whether the checked entries in the feed were spread over at least one day yet. That means in every minute of the day the chances field should be
     * greater of equal to one.
     * 
     * @return True, if the entries span at least one day, false otherwise.
     */
    public boolean oneFullDayHasBeenSeen() {
        boolean daySeen = true;

        for (Entry<Integer, int[]> entry : meticulousPostDistribution.entrySet()) {
            // if feed had no chance of having a post entry in any minute of the day, no full day has been seen yet
            if (entry.getValue()[1] == 0) {
                daySeen = false;
                break;
            }
        }

        if (meticulousPostDistribution.isEmpty()) {
            daySeen = false;
        }

        return daySeen;
    }

    public void setUpdateClass(int updateClass) {
        this.updateClass = updateClass;
    }

    /**
     * Returns the update class of the feed which is one of the following: {@link FeedClassifier#CLASS_CONSTANT}, {@link FeedClassifier#CLASS_CHUNKED},
     * {@link FeedClassifier#CLASS_SLICED} , {@link FeedClassifier#CLASS_ZOMBIE}, {@link FeedClassifier#CLASS_UNKNOWN} or
     * {@link FeedClassifier#CLASS_ON_THE_FLY}
     * 
     * @return The classID of the class. You can get the name using {@link FeedClassifier#getClassName()}
     */
    public int getUpdateClass() {
        return updateClass;
    }
    

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("Feed");
        // sb.append(" id:").append(id);
        sb.append(" feedUrl:").append(feedUrl);
        // sb.append(" siteUrl:").append(siteUrl);
        sb.append(" title:").append(title);
        // sb.append(" format:").append(format);
        // sb.append(" language:").append(language);
        // sb.append(" added:").append(added);
        
        return sb.toString();
    }

    /**
     * @return The date this feed was checked for updates the last time.
     */
    public final Date getLastChecked() {
        return lastChecked;
    }

    /**
     * @param lastChecked The date this feed was checked for updates the last time.
     */
    public final void setLastChecked(Date lastChecked) {
        this.lastChecked = lastChecked;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((added == null) ? 0 : added.hashCode());
        result = prime * result + checks;
        result = prime * result + ((entries == null) ? 0 : entries.hashCode());
        result = prime * result + ((feedUrl == null) ? 0 : feedUrl.hashCode());
        result = prime * result + format;
        result = prime * result + id;
        result = prime * result + ((language == null) ? 0 : language.hashCode());
        result = prime * result + ((lastChecked == null) ? 0 : lastChecked.hashCode());
        result = prime * result + ((lastFeedEntry == null) ? 0 : lastFeedEntry.hashCode());
        result = prime * result + ((lastHeadlines == null) ? 0 : lastHeadlines.hashCode());
        result = prime * result + maxCheckInterval;
        result = prime * result + ((meticulousPostDistribution == null) ? 0 : meticulousPostDistribution.hashCode());
        result = prime * result + minCheckInterval;
        result = prime * result + ((siteUrl == null) ? 0 : siteUrl.hashCode());
        result = prime * result + textType;
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        result = prime * result + unreachableCount;
        result = prime * result + updateClass;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Feed other = (Feed) obj;
        if (added == null) {
            if (other.added != null) {
                return false;
            }
        } else if (!added.equals(other.added)) {
            return false;
        }
        if (checks != other.checks) {
            return false;
        }
        if (entries == null) {
            if (other.entries != null) {
                return false;
            }
        } else if (!entries.equals(other.entries)) {
            return false;
        }
        if (feedUrl == null) {
            if (other.feedUrl != null) {
                return false;
            }
        } else if (!feedUrl.equals(other.feedUrl)) {
            return false;
        }
        if (format != other.format) {
            return false;
        }
        if (id != other.id) {
            return false;
        }
        if (language == null) {
            if (other.language != null) {
                return false;
            }
        } else if (!language.equals(other.language)) {
            return false;
        }
        if (lastChecked == null) {
            if (other.lastChecked != null) {
                return false;
            }
        } else if (!lastChecked.equals(other.lastChecked)) {
            return false;
        }
        if (lastFeedEntry == null) {
            if (other.lastFeedEntry != null) {
                return false;
            }
        } else if (!lastFeedEntry.equals(other.lastFeedEntry)) {
            return false;
        }
        if (lastHeadlines == null) {
            if (other.lastHeadlines != null) {
                return false;
            }
        } else if (!lastHeadlines.equals(other.lastHeadlines)) {
            return false;
        }
        if (maxCheckInterval != other.maxCheckInterval) {
            return false;
        }
        if (meticulousPostDistribution == null) {
            if (other.meticulousPostDistribution != null) {
                return false;
            }
        } else if (!meticulousPostDistribution.equals(other.meticulousPostDistribution)) {
            return false;
        }
        if (minCheckInterval != other.minCheckInterval) {
            return false;
        }
        if (siteUrl == null) {
            if (other.siteUrl != null) {
                return false;
            }
        } else if (!siteUrl.equals(other.siteUrl)) {
            return false;
        }
        if (textType != other.textType) {
            return false;
        }
        if (title == null) {
            if (other.title != null) {
                return false;
            }
        } else if (!title.equals(other.title)) {
            return false;
        }
        if (unreachableCount != other.unreachableCount) {
            return false;
        }
        if (updateClass != other.updateClass) {
            return false;
        }
        return true;
    }
    
    
}