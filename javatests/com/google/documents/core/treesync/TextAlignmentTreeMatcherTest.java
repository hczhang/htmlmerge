// Copyright 2009 Google Inc. All Rights Reserved.

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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.documents.core.treesync.TextAlignmentTreeMatcher.ChangedToBasePosition;
import com.google.documents.core.treesync.TextAlignmentTreeMatcher.DeTagger;

import junit.framework.TestCase;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;

import org.w3c.dom.Element;

import java.util.LinkedList;
import java.util.Map;

/**
 * Tests for TextAlignmentTreeMatcher.
 * 
 * @author tancred (Tancred Lindholm)
 */
public class TextAlignmentTreeMatcherTest extends TestCase {

  /**
   * Test that matching the same doc with and without ids will
   * simply reinsert the ids.
   */
  public void testIdentityMatching() {
    String base = "<p id=1>Hello <b id=2>world</b></p>";
    String changed = "<p>Hello <b>world</b></p>";
    String expected = "<p id=1>Hello <b id=2>world</b></p>";
    assertMatching(base, changed, expected);
  }
  
  /**
   * Test that matching ids get correctly added in a simple case.
   */
  public void testSimpleMatching() {
    String base = "<p id=1>Hello <b id=2>world</b></p>";
    String changed = "<p>Hi, <b>world!</b></p>";
    String expected = "<p id=1>Hi, <b id=2>world!</b></p>";
    assertMatching(base, changed, expected);
  }
  
  /**
   * Test that matching ids get correctly added in a simple case
   * that includes insertion of new content.
   */
  public void testSimpleMatchingWithInsert() {
    String base = "<p id=1>Hello <b id=2>world</b></p>";
    String changed = "<p>Hi, <b>world!</b></p><p>Another paragraph</p>";
    // Note use of regexp to match any generated id
    String expected = "<p id=1>Hi, <b id=2>world!</b></p>"
        + "<p>Another paragraph</p>";
    assertMatching(base, changed, expected);
  }
  
  /**
   * Test the detagger (TextAlignmentTreeMatcher.DeTagger class)
   * by executing a simple detagging task, and then verifying the result.
   */
  public void testDetagger() {
    String doc = "<p>Hi, <b>world!</b></p><div>A div</div>";
    String expectedDeTag = DeTagger.HEADER 
        // 0123456789012345678901234567890123456789
        + "$Hi, $world!$A div" 
        + DeTagger.FOOTER;
    Element body = HtmlUtil.parseBody(doc);
    Element p = (Element) body.getFirstChild();
    Element b = (Element) body.getFirstChild().getFirstChild().getNextSibling();
    Element div = (Element) body.getFirstChild().getNextSibling();
    Map<Integer, Element> baseTags = Maps.newHashMap(); 
    String deTagged = DeTagger.deTag(body, baseTags);
    expectedDeTag = expectedDeTag.replace('$', DeTagger.TAG_MARKER);
    assertEquals("Wrong detagged document", expectedDeTag, deTagged);
    int offset = DeTagger.HEADER.length();
    // Lookup <p>
    assertEquals("Wrong tag in map", baseTags.get(offset + 0), p);
    // Lookup <b>
    assertEquals("Wrong tag in map", baseTags.get(offset + 5), b);
    // Lookup <div>
    assertEquals("Wrong tag in map", baseTags.get(offset + 12), div);    
  }

  /**
   * Test the functionality for adding ids 
   * (TextAlignmentTreeMatcher.SetIdsFromMap class) to the changed document by
   * executing a simple id adding task, and then verifying the result.
   */
  public void testSetIdsFromMap() {
    String doc = "<p>Hi, <b>world!</b></p><div>A div</div>";
    String expectedDoc = "<p id=p>Hi, <b id=b>world!</b></p><div id=div>A div</div>";
    Element body = HtmlUtil.parseBody(doc);
    Element p = (Element) body.getFirstChild();
    Element b = (Element) body.getFirstChild().getFirstChild().getNextSibling();
    Element div = (Element) body.getFirstChild().getNextSibling();
    Map<Element, String> map = ImmutableMap.of(p, "p", b, "b", div, "div");
    (new TextAlignmentTreeMatcher.SetIdsFromMap(map)).traverse(body);
    assertEquals("Wrong ids inserted",
        HtmlUtil.serializeBody(HtmlUtil.parseBody(expectedDoc)),
        HtmlUtil.serializeBody(body));
  }

  /**
   * Test the change to base position mapping function
   * (TextAlignmentTreeMatcher.ChangedToBasePosition) by generating 
   * a mapping function from a given diff, and then verifying the
   * mapping function. 
   */
  public void testChangedToBasePosition() {
    // The base and changed strings below will align as:
    // 01.2
    // ab.d
    // .bcd
    // .012
    String base = "abd";
    // Delta in diff_match_patch format encoding the above alignment
    String delta = "-1\t=1\t+c\t=1";     
    diff_match_patch dmp = new diff_match_patch();
    LinkedList<Diff> diff = dmp.diff_fromDelta(base, delta);
    ChangedToBasePosition mapFun = ChangedToBasePosition.createFromBaseToChangedDiff(diff);
    assertEquals("Wrong map for lower out of bounds.", null, mapFun.apply(-1));
    assertEquals("Wrong map for 'b'", 1, (int) mapFun.apply(0));
    assertEquals("Wrong map for 'c'", null, mapFun.apply(1));
    assertEquals("Wrong map for 'b'", 2, (int) mapFun.apply(2));
    assertEquals("Wrong map for upper out of bounds", null, mapFun.apply(3));
  }
  
  /**
   * Match changed to base, and verify the result.
   *  
   * @param base base document
   * @param changed document to match
   * @param expectedMatched The expected result, i.e., the changed document, 
   *     but with with id attributes indicating the matching to base.
   */
  private void assertMatching(String base, String changed, String expectedMatched) {
    Element baseContent = HtmlUtil.parseBody(base);
    Element changedContent = HtmlUtil.parseBody(changed);
    TextAlignmentTreeMatcher matcher = new TextAlignmentTreeMatcher();
    matcher.match(baseContent, changedContent);
    String normalizedExpected = HtmlUtil.serializeBody(HtmlUtil.parseBody(expectedMatched));
    String normalizedMatched = HtmlUtil.serializeBody(changedContent);
    assertEquals("Incorrect matching", normalizedExpected, normalizedMatched);
  }
}
