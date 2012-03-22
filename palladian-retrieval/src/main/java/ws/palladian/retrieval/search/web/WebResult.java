package ws.palladian.retrieval.search.web;

import java.util.Date;

import ws.palladian.retrieval.search.SearchResult;

/**
 * <p>
 * {@link WebResult}s represent search results from web search engines.
 * </p>
 * 
 * @author David Urbansky
 * @author Philipp Katz
 */
public class WebResult extends SearchResult {

    /** The URL of the web result. */
    private final String url;

    public WebResult(String url, String title, String summary, Date date) {
        super(title, summary, date);
        this.url = url;
    }

    public WebResult(String url, String title, String summary) {
        super(title, summary);
        this.url = url;
    }

    public WebResult(String url, String title, String summary, String searchEngine) {
        super(title, summary, searchEngine);
        this.url = url;
    }

    public WebResult(String url, String title, String summary, Date date, String searchEngine) {
        super(title, summary, date, searchEngine);
        this.url = url;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("WebResult [url=");
        builder.append(url);
        builder.append(", getTitle()=");
        builder.append(getTitle());
        builder.append(", getSummary()=");
        builder.append(getSummary());
        builder.append(", getDate()=");
        builder.append(getDate());
        builder.append(", getSearchEngine()=");
        builder.append(getSearchEngine());
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        WebResult other = (WebResult) obj;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        return true;
    }

}