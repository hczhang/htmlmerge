// Copyright 2013 Google Inc. All Rights Reserved.

/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.documents.core.treesync;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.dom.HtmlDocumentBuilder;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * HTML utilities for the Tree Merger.
 * @author tancred (Tancred Lindholm)
 */
public class HtmlUtil {

  /**
   * Traverser to Serialize DOM to HTML. 
   */
  public static class HtmlSerializer extends Util.DomTraverser {
  
    private static final ImmutableSet<String> SELF_CLOSING = ImmutableSet.of(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "menuitem",
        "meta", "param", "source", "track", "wbr");
  
    private StringBuilder sb = new StringBuilder();
    
    @Override
    protected void visit(Node n) {
      if (n instanceof Element) {
        Element e = (Element) n;
        sb.append('<');
        sb.append(e.getNodeName());
        NamedNodeMap atts = e.getAttributes();
        for (int i = 0; i < atts.getLength(); i++) {
          Node att = atts.item(i);
          if (Util.ID_ATTRIBUTE.equalsIgnoreCase(att.getNodeName()) &&
              Strings.nullToEmpty(att.getNodeValue()).startsWith(Util.ID_PREFIX)) {
            // Do not serialize generated ids.
            continue;
          }
          sb.append(' ');
          sb.append(att.getNodeName());
          sb.append("=\"");
          sb.append(att.getNodeValue());
          sb.append("\"");
        }
        sb.append('>');
      } else {
        sb.append(n.getNodeValue());
      }
    }
  
    @Override
    protected void postVisitNode(Node n) {
      if (n instanceof Element) { 
        Element e = (Element) n;
        if (!SELF_CLOSING.contains(e.getNodeName().toLowerCase())) {
          sb.append("</");
          sb.append(e.getNodeName());
          sb.append('>');
        }
      }
    }
  
    public String getHtml() {
      return sb.toString();
    }
  }

  public static class WhitespaceNodeRemover extends Util.DomTraverser {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    
    @Override
    protected void postVisitNode(Node node) {
      if (node instanceof Text && WHITESPACE.matcher(node.getNodeValue()).matches()) {
        node.getParentNode().removeChild(node);
      }
    }

  }
  
  static Element parseHtml(String content) throws SAXException, IOException {
    InputSource inputSource =
        new InputSource(new ByteArrayInputStream(content.getBytes(UTF_8)));
    inputSource.setEncoding(UTF_8.name());
    HtmlDocumentBuilder parser = new HtmlDocumentBuilder(XmlViolationPolicy.ALLOW);
    parser.setIgnoringComments(false);
    return parser.parse(inputSource).getDocumentElement();
  }

  static Element removeWhitespace(Element root) {
    (new WhitespaceNodeRemover()).traverse(root);
    return root;
  }
  
  static String serialize(Node n) {
    HtmlSerializer ser = new HtmlSerializer();
    ser.traverse(n);
    return ser.getHtml();
  }

  // For testing and debug.
  static Element parseBody(String body) {
    try {
      return (Element) parseHtml(String.format(
          "<html><head id='id-head'></head><body id='id-body'>%s</body></html>", body))
          .getElementsByTagName("body").item(0);
    } catch (Exception e) {
      throw new RuntimeException();
    }
  }
  
  static String serializeBody(Element body) {
    HtmlSerializer ser = new HtmlSerializer();
    for (Node child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      ser.traverse(child);
    }
    return ser.getHtml();
  }
}
