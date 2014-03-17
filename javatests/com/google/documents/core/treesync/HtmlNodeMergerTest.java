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

import junit.framework.TestCase;

import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import java.util.Comparator;

/** 
 * Unit test for HtmlNodeMerger. 
 * 
 * @author tancred (Tancred Lindholm)
 */
public class HtmlNodeMergerTest extends TestCase {

  private static final Text TX1 = (Text) node("aaa");
  private static final Text TX2 = (Text) node("bbb");
  private static final Comment C1 = (Comment) node("<!--aaa-->");
  private static final Comment C2 = (Comment) node("<!--bbb-->");
  private static final Element T1 = (Element) node("<a>");
  private static final Element T11 = (Element) node("<a alt='aaa'></a>");
  private static final Element T111 = (Element) node("<a alt='bbb'></a>");
  private static final Element T12 = (Element) node("<a class='bbb'>");
  private static final Element T13 = (Element) node("<a alt='aaa' class='bbb'>");
  private static final Element T13E = (Element) node("<a class='bbb' alt='aaa'>");
  private static final Element T2 = (Element) node("<b>");

  private static final Text TEA1 = 
      (Text) node("Almost, but not quite, entirely unlike tea.");
  
  private static final Text TEA2 = 
      (Text) node("Definately, but not quite, entirely unlike tea.");
  
  private static final Text TEA2C = 
      (Text) node("Unfortunately, but not quite, entirely like tea.");

  private static final Text TEA3 = 
      (Text) node("Almost, but not quite, entirely like tea.");
  
  private static final Text TEAM = 
      (Text) node("Definately, but not quite, entirely like tea.");
  
  private static final Element TAG1 =
      (Element) node("<a id='id1' href='http://foo' class='link'>");
  private static final Element TAG2 =
      (Element) node("<a href='http://bar' id='id1' class='link'>");
  private static final Element TAG3 =
      (Element) node("<link href='http://foo' id='id1' alt='A link'>");
  private static final Element TAG3C =
      (Element) node("<link href='http://baz' id='id1' alt='A link'>");
  private static final Element TAGM =
      (Element) node("<link id='id1' href='http://bar' alt='A link'>");
  
  public void testTagMerge() throws ConflictException {
    Comparator<Node> c = (new HtmlNodeMerger()).getNodeComparatorForTest();
    HtmlNodeMerger nm = new HtmlNodeMerger();
    Node m = nm.mergeContent(TAG1, TAG2, TAG3);
    checkComparator(c, TAGM, m, 0);
    m = nm.mergeContent(TAG1, TAG3, TAG2);
    checkComparator(c, TAGM, m, 0);
    try {
      m = nm.mergeContent(TAG1, TAG2, TAG3C);
      fail("Unexpected success on merge");
    } catch (ConflictException e) {
      // Expected
    }
  }
  
  public void testTextMerge() throws ConflictException {
    Comparator<Node> c = (new HtmlNodeMerger()).getNodeComparatorForTest();
    HtmlNodeMerger nm = new HtmlNodeMerger();
    Node m = nm.mergeContent(TEA1, TEA2, TEA3);
    checkComparator(c, TEAM, m, 0);
    m = nm.mergeContent(TEA1, TEA3, TEA2);
    checkComparator(c, TEAM, m, 0);
    try {
      m = nm.mergeContent(TEA1, TEA2, TEA2C);
      fail("Unexpected success on merge; merge=" + HtmlUtil.serialize(m)); 
    } catch (ConflictException e) {
      // Expected
    }
  }
    
  public void testNodeComparator() {
    // Outer dimension = order, inner dimension = equivalent nodes
    Node[][] correctOrder = {
        {null}, {T1}, {T11}, {T111}, {T12}, {T13, T13E}, {T2},
        {TX1}, {TX2},
        {C1}, {C2}
      };
    Comparator<Node> c = (new HtmlNodeMerger()).getNodeComparatorForTest();
    // Check that results returned by the comparator corresponds to the 
    // ordering in correctOrder
    for (int i = 0; i < correctOrder.length; i++) {
      for (int j = 0; j < correctOrder[i].length; j++) {
        // Compare equality of all in class
        for (int k = 0; k < correctOrder[i].length; k++) {
         checkComparator(c, correctOrder[i][j], correctOrder[i][k], 0);
        }
        // Make sure all in this class are > all in any previous class
        // and that all in previous class are < this class
        // Note that we do not need to test with next class, because the next class
        // will test against members of this class
        for (int pi = 0; pi < i; pi++) {
          for (int k = 0; k < correctOrder[pi].length; k++) {
            checkComparator(c, correctOrder[pi][k], correctOrder[i][j], -1);
          }
        }
      }
    }
  }
  
  /** 
   * Check comparator result. Compares both l,r and r,l.
   * 
   * @param c comparator to use
   * @param l first node to compare
   * @param r second node to compare
   * @param expectedSignum expected result of the signum function for compare()
   */
  private void checkComparator(Comparator<Node> c, Node l, Node r, int expectedSignum) {
    assertEquals(buildMsg(expectedSignum, l, r), expectedSignum, sign(c.compare(l, r)));
    assertEquals(buildMsg(-expectedSignum, l, r), -expectedSignum, sign(c.compare(r, l)));
  }

  /** 
   * Build message on failed expected result of comparison. 
   * 
   * @param signum Expected signum of comparison, as defined by <code>compareTo</code> in the
   *    {@link Comparable} interface, i.e. -1 for l<r, 0 for l=r, and 1 for l>r.   
   * @param l first operand
   * @param r second operand
   * @return message.
   */
  private String buildMsg(int signum, Node l, Node r) {
    String[] plaintextSignum = {" < ", " == ", " > "};
    return "Did not get expected result: "  
      + (l != null ? HtmlUtil.serialize(l) : "(null)") + plaintextSignum[signum + 1]  
      + (r != null ? HtmlUtil.serialize(r) : "(null)");    
  }
  
  private int sign(int i) {
    return i > 0 ? 1 : (i < 0 ? -1 : 0);
  }
  
  private static Node node(String html) {
    return HtmlUtil.parseBody(html).getFirstChild();
  }
}
