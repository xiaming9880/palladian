package ws.palladian.retrieval.wikipedia;

/**
 * <p>
 * Internal link on a Wikipedia page.
 * </p>
 */
public class WikipediaLink {

    private final String destination;
    private final String title;

    public WikipediaLink(String destination, String title) {
        this.destination = destination;
        this.title = title;
    }

    public String getDestination() {
        return destination;
    }

    public String getTitle() {
        // return title != null ? title : destination;
        return title;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("WikipediaLink [");
        builder.append("destination=");
        builder.append(destination);
        if (title != null) {
            builder.append(", title=");
            builder.append(title);
        }
        builder.append("]");
        return builder.toString();
    }

}