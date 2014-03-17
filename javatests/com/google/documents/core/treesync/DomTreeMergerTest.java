// Copyright 2007 Google Inc. All Rights Reserved.

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

import junit.framework.Assert;
import junit.framework.TestCase;

/** 
 * Unit test for three-way document merger.
 * 
 * @author tancred (Tancred Lindholm)
 */
public class DomTreeMergerTest extends TestCase {

  String baseDoc = "<div id=r><div id=a><div id=b></div></div><div id=c></div></div>";
  
  // Concurrent inserts (i and j)
  String insDoc1 =
      "<div id=r><div id=a><div id=b></div></div><div id=c></div><div id=j></div></div>";
  String insDoc2 =
      "<div id=r><div id=a><div id=i></div><div id=b></div></div><div id=c></div></div>";
  String insMerge =
      "<div id=r><div id=a><div id=i></div><div id=b></div></div><div id=c></div>"
          + "<div id=j></div></div>";

  // Concurrent deletes (b and c)
  String delDoc1 = "<div id=r><div id=a></div><div id=c></div></div>";
  String delDoc2 = "<div id=r><div id=a><div id=b></div></div></div>";
  String delMerge = "<div id=r><div id=a></div></div>";

  // Concurrent updates (to r and c)  
  String updDoc1 = "<div class=u id=r><div id=a><div id=b></div></div><div id=c></div></div>";
  String updDoc2 = "<div id=r><div id=a><div id=b></div></div><div class=u id=c></div></div>";
  String updMerge = "<div class=u id=r><div id=a><div id=b></div></div>"
      + "<div class=u id=c></div></div>";

  // Concurrent moves (of c and s)  
  String base2Doc =
      "<div id=r><div id=a><div id=b></div><div id=d></div></div><div id=c></div></div>";
  String movDoc1 =
      "<div id=r><div id=a><div id=d></div><div id=b></div></div><div id=c></div></div>";
  String movDoc2 =
      "<div id=r><div id=c></div><div id=a><div id=b></div><div id=d></div></div></div>";
  String movMerge =
      "<div id=r><div id=c></div><div id=a><div id=d></div><div id=b></div></div></div>";

  // Simple case combining all operations  
  String updmovDoc =
      "<div id=r><div id=a><div class=u id=d></div><div id=b></div></div><div id=c></div></div>";
  String insdelDoc =
      "<div id=r><div id=i></div><div id=a><div id=b></div><div id=d></div></div></div>";
  String dimuMerge =
      "<div id=r><div id=i></div><div id=a><div class=u id=d></div><div id=b></div></div></div>";

  // Case with concurrent changes to same node
  private static final String DOCB = 
      "<h2 id=1>Drescher and the toaster</h2>\n"
      + "A disciple of another sect once came to Drescher as he was eating his "
      + "morning meal.<br id=2>" 
      + "I would like to give you this personality test, <br id=3 style=foo>said the outsider, " 
      + "because I want you to be happy. Drescher took the papr that was offered him and put " 
      + "it into the toaster, saying: I wish the toaster to be happy, too.";
    
  // <h2 id=1> -> <h2 id=1 class=heading>; sect once came -> sect came; 
  // I wish .. happy, too -> "I wish .. happy, too" 
  private static final String DOC1 = 
      "<h2 id=1 class=heading>Drescher and the toaster</h2>\n"
      + "A disciple of another sect came to Drescher as he was eating his "
      + "morning meal.<br id=2>" 
      + "I would like to give you this personality test, <br id=3 style=foo>said the outsider, " 
      + "because I want you to be happy. Drescher took the papr that was offered him and put " 
      + "it into the toaster, saying: \"I wish the toaster to be happy, too.\"";

  // <h2 id=1> -> <h2 id=1  >;  I would ... test -> "I would ... test"; 
  // <br id=3 style=foo> -> <br id=3>; papr->paper
  private static final String DOC2 = 
      "<h2 id=1  >Drescher and the toaster</h2>\n"
      + "A disciple of another sect once came to Drescher as he was eating his "
      + "morning meal.<br id=2>" 
      + "\"I would like to give you this personality test\", <br id=3>said the outsider, " 
      + "because I want you to be happy. Drescher took the paper that was offered him and put " 
      + "it into the toaster, saying: I wish the toaster to be happy, too.";

  private static final String DOCM = 
      "<h2 id=1 class=heading>Drescher and the toaster</h2>\n"
      + "A disciple of another sect came to Drescher as he was eating his "
      + "morning meal.<br id=2>" 
      + "\"I would like to give you this personality test\", <br id=3>said the outsider, " 
      + "because I want you to be happy. Drescher took the paper that was offered him and put " 
      + "it into the toaster, saying: \"I wish the toaster to be happy, too.\"";

  
  public void testInsIns() {
    String merge = merge(baseDoc, insDoc1, insDoc2);
    htmlEquals("Failed to merger concurrent inserts", merge, insMerge);
  }

  public void testDelDel() {
    String merge = merge(baseDoc, delDoc1, delDoc2);
    htmlEquals("Failed to merger concurrent deletes", merge, delMerge);
  }
  
  public void testUpdUpd() {
    String merge = merge(baseDoc, updDoc1, updDoc2);
    htmlEquals("Failed to merger concurrent updates", merge, updMerge);
  }

  public void testMovMov() {
    String merge = merge(base2Doc, movDoc1, movDoc2);
    htmlEquals("Failed to merger concurrent moves", merge, movMerge);
  }
  
  public void testSimpleAllOps() {
    String merge = merge(base2Doc, insdelDoc, updmovDoc);
    htmlEquals("Failed to merger concurrent moves", merge, dimuMerge);
  }

  public void testNodeMerging() {
    String merge = merge(DOCB, DOC1, DOC2);
    htmlEquals("Failed to merge concurrent edits to same node(s)", merge, DOCM);
  }

  private String merge(String base, String one, String two) {
    DomTreeMerger m = new DomTreeMerger();
    String merge12 = HtmlUtil.serialize(
        m.runMerge(HtmlUtil.parseBody(base), HtmlUtil.parseBody(one), HtmlUtil.parseBody(two)));
    String merge21 = HtmlUtil.serialize(
        m.runMerge(HtmlUtil.parseBody(base), HtmlUtil.parseBody(two), HtmlUtil.parseBody(one)));
    Assert.assertEquals("Merge failed symmetry test", merge12, merge21);
    return merge12;
  }

  private void htmlEquals(String msg, String h1, String expected) {
    String nh1 = h1 != null ? HtmlUtil.serialize(HtmlUtil.parseBody(h1)) : null;
    String nh2 = expected != null ? HtmlUtil.serialize(HtmlUtil.parseBody(expected)) : null;
    Assert.assertEquals(msg, nh2, nh1);
  }
}
