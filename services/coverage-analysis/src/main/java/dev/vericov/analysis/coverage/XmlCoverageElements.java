package dev.vericov.analysis.coverage;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class XmlCoverageElements {
    private XmlCoverageElements() {
    }

    public static List<Element> descendants(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return List.copyOf(elements);
    }

    public static List<Element> children(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                elements.add(element);
            }
        }
        return List.copyOf(elements);
    }

    public static String attr(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name).trim() : "";
    }

    public static int intAttr(Element element, String name) {
        String value = attr(element, name);
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }
}
