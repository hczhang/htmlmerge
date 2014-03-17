// Copyright 2008 Google Inc. All Rights Reserved.

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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Test the DomTree class.
 * 
 * @author tancred (Tancred Lindholm)
 */
public class DomTreeTest extends AddressableTreeTest<DomTree, Node, String> {

  private Document domHelper;
  
  public Document getDomHelper() {
    if (domHelper == null) {
      try {
        domHelper = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      } catch (ParserConfigurationException e) {
        throw new RuntimeException(e);
      }
    }
    return domHelper;
  }
  
  @Override
  public DomTree getAbcdTree() {
    // Note: the DomTree default root node acts as the 'A' node in the Abcd tree.
    String abcdTree =
        "<div id='b' class='B'></div><div id='c' class='C'>"
        + "<div id='d' class='D'></div>";
    return new DomTree(HtmlUtil.parseBody(abcdTree));
  }

  @Override
  public Node toContent(String s) {
    Element e = getDomHelper().createElement("div");
    e.setAttribute("class", s);
    return e;
  }

  @Override
  public String toId(String s) {
    return "a".equals(s) ? DomTree.ROOT_ID : s;
  }

  @Override
  protected String toPrintable(Node content, String id) {
    if (DomTree.ROOT_ID.equals(id)) {
      return "a-A";
    } else {
      return id + "-" + ((Element) content).getAttribute("class");
    }
  }
}
