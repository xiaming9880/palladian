package ws.palladian.extraction.location.sources.importers;

public class WikipediaPage {

    private final int pageId;
    private final int namespaceId;
    private final String title;
    private final String text;
    private final String redirectTitle;

    public WikipediaPage(int pageId, int namespaceId, String title, String text, String redirectTitle) {
        this.pageId = pageId;
        this.namespaceId = namespaceId;
        this.title = title;
        this.text = text;
        this.redirectTitle = redirectTitle;
    }

    public int getPageId() {
        return pageId;
    }

    public int getNamespaceId() {
        return namespaceId;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public boolean isRedirect() {
        return redirectTitle != null;
    }

    public String getRedirectTitle() {
        return redirectTitle;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("WikipediaPage [pageId=");
        builder.append(pageId);
        builder.append(", namespaceId=");
        builder.append(namespaceId);
        builder.append(", title=");
        builder.append(title);
        builder.append(", redirectTitle=");
        builder.append(redirectTitle);
        builder.append("]");
        return builder.toString();
    }

}