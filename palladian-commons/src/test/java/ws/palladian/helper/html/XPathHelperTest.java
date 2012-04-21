package ws.palladian.helper.html;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import ws.palladian.helper.io.ResourceHelper;

public class XPathHelperTest {

    // private Parser htmlParser = ParserFactory.makeParser();
//    private DocumentParser xmlParser = ParserFactory.createXmlParser();

    @Test
    public void testAddNamespaceToXPath() {

        // test add XMLNS
        assertEquals(XPathHelper.addXhtmlNsToXPath("//TABLE/TR/TD/A[4]"), "//xhtml:TABLE/xhtml:TR/xhtml:TD/xhtml:A[4]");
        assertEquals(XPathHelper.addXhtmlNsToXPath("/TABLE/TR/TD/A[4]"), "/xhtml:TABLE/xhtml:TR/xhtml:TD/xhtml:A[4]");
        assertEquals(XPathHelper.addXhtmlNsToXPath("/TABLE/TR[2]/TD/A"), "/xhtml:TABLE/xhtml:TR[2]/xhtml:TD/xhtml:A");
        assertEquals(XPathHelper.addXhtmlNsToXPath("/TABLE/TR[2]/TD/A/text()"),
                "/xhtml:TABLE/xhtml:TR[2]/xhtml:TD/xhtml:A/text()");

        // TODO assertEquals(XPathHelper.addXhtmlNsToXPath("//a[img]"), "//xhtml:a[xhtml:img]");

    }

    // FIXME needs to be re-added
    // @Test
    // public void testGetXhtmlChildNodes() throws FileNotFoundException, ParserException {
    //
    // Document doc = htmlParser.parse(ResourceHelper.getResourceStream("/webPages/NekoTableTestcase1.html"));
    //
    // List<Node> rows = XPathHelper.getXhtmlNodes(doc, "//table/tr");
    // assertEquals(3, rows.size());
    //
    // for (Node row : rows) {
    //
    // // iterate over TDs
    // List<Node> cells = XPathHelper.getXhtmlChildNodes(row, "//td"); // does not work EDIT: now it does
    // assertEquals(3, cells.size());
    //
    // cells = XPathHelper.getXhtmlChildNodes(row, "*"); // infinite loop? EDIT: yes, stupid me :) solved.
    // assertEquals(3, cells.size());
    // }
    // }

    @Test
    public void testGetElementById() throws ParserConfigurationException, SAXException, IOException {
        Document doc = parse(ResourceHelper.getResourceFile("events.xml"));

        assertEquals("event", XPathHelper.getNodeByID(doc, "e01").getNodeName());
        assertEquals("events", XPathHelper.getParentNodeByID(doc, "e01").getNodeName());
    }

    @Test
    public void testNamespace() throws ParserConfigurationException, SAXException, IOException {
//        Document doc = parse(ResourceHelper.getResourceFile("/feeds/atomSample1.xml"));
//
//        Map<String, String> mapping = new HashMap<String, String>();
//        mapping.put("atom", "http://www.w3.org/2005/Atom");
//        List<Node> nodes = XPathHelper.getNodes(doc, "//atom:author", mapping);
//        assertEquals(2, nodes.size());
        Document doc = parse(ResourceHelper.getResourceFile("/multipleNamespaces.xml"));
        Map<String, String> mapping = new HashMap<String, String>();
        mapping.put("h", "http://www.w3.org/TR/html4/");
        mapping.put("f", "http://www.w3schools.com/furniture");
        
        List<Node> nodes = XPathHelper.getNodes(doc, "//h:td", mapping);
        assertEquals(2, nodes.size());
        
        nodes = XPathHelper.getNodes(doc, "/root/f:table/f:name", mapping);
        assertEquals(1, nodes.size());
        
    }
    
    
    private final Document parse(File file) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(file);
    }

}
