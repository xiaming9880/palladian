package tud.iir.extraction.mio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tud.iir.knowledge.Entity;

/**
 * The PageAnalyzer analyzes MIOPageCandidates for MIO-Existence. Also some links and IFRAMEs are analyzed.
 * 
 * @author Martin Werner
 */
public class PageAnalyzer {

    private List<String> MIOPageCandidates;
    private List<MIOPage> MIOPages;

    /**
     * Instantiates a new page analyzer.
     *
     * @param MIOPageCandidates the mIO page candidates
     */
    public PageAnalyzer(List<String> MIOPageCandidates) {

        this.MIOPageCandidates = MIOPageCandidates;
        MIOPages = new ArrayList<MIOPage>();

    }

    /**
     * the central method.
     *
     * @param entity the entity
     * @return the list
     */
    public List<MIOPage> analyzePages(Entity entity) {

        // initialize SearchWordMatcher
        SearchWordMatcher swMatcher = new SearchWordMatcher(entity.getName());

        for (String mioPageCandidate : MIOPageCandidates) {

            // System.out.println("PageAnalysing started for: " +
            // mioPageCandidate);
            String pageContent = getPage(mioPageCandidate);
            if (!pageContent.equals("")) {

                // use fast MIO-Detection
                FastMIODetector fMIODec = new FastMIODetector();
                MIOPages.addAll(fMIODec.getMioPages(pageContent, mioPageCandidate));

                // IFRAME-Analysis
                IFrameAnalyzer iframeAnalyzer = new IFrameAnalyzer(swMatcher);
                MIOPages.addAll(iframeAnalyzer.getIframeMioPages(pageContent, mioPageCandidate));

                // Link-Analysis
                LinkAnalyzer linkAnalyzer = new LinkAnalyzer(swMatcher);
                MIOPages.addAll(linkAnalyzer.getLinkedMioPages(pageContent, mioPageCandidate));
            }
        }

        return removeDuplicates(MIOPages);
    }

    /**
     * get WebPage as String.
     *
     * @param URLString the uRL string
     * @return the page
     */
    private String getPage(String URLString) {
        GeneralAnalyzer generalAnalyzer = new GeneralAnalyzer();
        return generalAnalyzer.getPage(URLString, false);
    }

    /**
     * remove duplicates, but pay attention to different ways of finding.
     *
     * @param mioPages the mio pages
     * @return the list
     */
    private List<MIOPage> removeDuplicates(List<MIOPage> mioPages) {
        List<MIOPage> resultList = new ArrayList<MIOPage>();

        Map<String, MIOPage> tempMap = new HashMap<String, MIOPage>();
        for (MIOPage mioPage : mioPages) {

            if (!tempMap.containsKey(mioPage.getUrl())) {
                tempMap.put(mioPage.getUrl(), mioPage);

            } else {
                MIOPage tempMIOPage = tempMap.get(mioPage.getUrl());

                // organize the different ways of finding the same miopage
                if (mioPage.isLinkedPage()) {
                    if (tempMIOPage.getLinkName().equals("")) {
                        tempMIOPage.setLinkName(mioPage.getLinkName());
                    }
                    if (tempMIOPage.getLinkTitle().equals("")) {
                        tempMIOPage.setLinkTitle(mioPage.getLinkTitle());
                    }
                    if (!tempMIOPage.isLinkedPage()) {
                        tempMIOPage.setLinkedPage(mioPage.isLinkedPage());
                        tempMIOPage.setLinkParentPage(mioPage.getLinkParentPage());
                    }
                }

                if (mioPage.isIFrameSource()) {
                    if (!tempMIOPage.isIFrameSource()) {
                        tempMIOPage.setIFrameSource(mioPage.isIFrameSource());
                        tempMIOPage.setIframeParentPage(mioPage.getIframeParentPage());
                    }
                }
            }
        }
        for (MIOPage mioPage : tempMap.values()) {
            resultList.add(mioPage);

        }

        return resultList;

    }
}
