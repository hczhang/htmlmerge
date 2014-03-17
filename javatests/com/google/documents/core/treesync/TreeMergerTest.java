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

import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Comparator;
import java.util.List;

/**
 * Test tree merger.
 *
 * @author tancred (Tancred Lindholm)
 */
public class TreeMergerTest extends TestCase {

  // Concurrent inserts (i and j)
  public void testInsIns() {
    String baseDoc = "r (a (b) c)";
    String insDoc1 = "r (a (b) c j)";
    String insDoc2 = "r (a (i b) c)";
    String insMerge = "r (a (i b) c j)";
    merge(baseDoc, insDoc1, insDoc2, insMerge);
  }

  // Concurrent deletes (b and c)
  public void testDelDel() {
    String baseDoc = "r (a (b) c)";
    String delDoc1 = "r (a c)";
    String delDoc2 = "r (a (b))";
    String delMerge = "r (a)";
    merge(baseDoc, delDoc1, delDoc2, delMerge);
  }

  // Concurrent updates (to r and c)
  public void testUpdUpd() {
    String baseDoc = "r (a (b) c)";
    String updDoc1 = "R (a (b) c)";
    String updDoc2 = "r (a (b) C)";
    String updMerge = "R (a (b) C)";
    merge(baseDoc, updDoc1, updDoc2, updMerge);
  }

  // Concurrent moves (of c and s)
  public void testMovMov() {
    String base2Doc = "r (a (b d) c)";
    String movDoc1 = "r (a (d b) c)";
    String movDoc2 = "r (c a (b d))";
    String movMerge = "r (c a (d b))";
    merge(base2Doc, movDoc1, movDoc2, movMerge);
  }

  // Simple case combining all operations
  public void testSimpleAllOps() {
    String base2Doc = "r (a (b d) c)";
    String updmovDoc = "r (a (D b) c)";
    String insdelDoc = "r (i a (b d))";
    String dimuMerge = "r (i a (D b))";
    merge(base2Doc, insdelDoc, updmovDoc, dimuMerge);
  }

  public void testUpdateConflicts() {
    conflict("root (a (b) c)", "root2 (a (b) c)", "root3 (a (b) c)");
    conflict("r (a (bee) c)", "r (a (bee2) c)", "r (a (bee3) c)");
    conflict("r (a (b) cee)", "r (a (b) cee2)", "r (a (b) cee3)");
  }

  public void testUpdateDeleteConflicts() {
    conflict("r (a (b) c)", "r (c)", "r (A (b) c)");
    conflict("r (a (b) c)", "r (c)", "r (a (B) c)");
    conflict("r (a b c (d e) f g)", "r (a b g)", "r (a b C (d e) f g)");
    conflict("r (a b c (d e) f g)", "r (a b g)", "r (a b c (d e) F g)");
    conflict("r (a b c (d e) f g)", "r (a b g)", "r (a b c (D e) f g)");
    conflict("r (a b c (d e) f g)", "r (a b g)", "r (a b c (d E) f g)");
  }

  public void testInsertConflicts() {
    conflict("r (a (b) c)", "r (a i (b) c)", "r (a j (b) c)");
    conflict("r (a (b) c)", "r (i a (b) c)", "r (j a (b) c)");
    conflict("r (a (b) c)", "r (a (b) c i)", "r (a (b) c j)");
  }

  public void testInsertDeleteConflicts() {
    conflict("r (a b c (d e) f g)", "r (a b g)", "r (a b c i (d e) f g)");
    conflict("r (a b c (d e) f g)", "r (a b g)", "r (a b c (d e) f j g)");
    conflict("r (a b c (d e) f g)", "r (a b g)", "r (a b c (i d e) f g)");
    conflict("r (a b c (d e) f g)", "r (a b g)", "r (a b c (d e j) f g)");
  }

  public void testMoveConflicts() {
    conflict("r (a (b c d e f g) h)", "r (a (b g) h)", "r (a (b c e d f g) h)");
    conflict("r (a (b c d e f g) h)", "r (h)", "r (a (b c e d f g) h)");
    conflict("r (a (b c d e f g) h)", "r (h)", "r (a (c b d e f g) h)");
    conflict("r (a (b c d e f g) h)", "r (h)", "r (a (b c d e g f) h)");
    // Here a deleted node d is "saved" by being moved out of the deleted area
    // We do not allow this by default
    conflict("r (a (b c d e f g) h)", "r (a (b g) h)", "r (a (b c e f g) h d)");
    conflict("r (a (b c d e f g) h)", "r (h)", "r (a (b c e f g) h d)");
  }

  public void testEosConflicts() {
    // These conflicts involving ambiguity on the last node of the child list
    // exercise tricky edge cases, and are therefore included
    conflict("r (a b)", "r (a)", "r (a i)");
    conflict("r (a b c)", "r (b (a))", "r (b a)");
    conflict("r (a b c)", "r (a b)", "r (a b)");
  }

  public void testLoopDetection() {
    // This specially crafted pathological case
    // (see http://www.cs.hut.fi/~ctl/3dm/thesis.pdf)
    // edits the tree so that the "correct" merge is an infinite tree.
    // This test checks that such a loop is detected and a conflict emitted
    conflict("R (a (b (c (d))))", "R (a (c (b (d))))", "R (d (b (c (a))))");
  }

  private void conflict(String base, String one, String two) {
    TestMerger m = new TestMerger();
    TestTree tb = new TestTree(base);
    TestTree t1 = new TestTree(one);
    TestTree t2 = new TestTree(two);
    try {
      m.merge(tb, t1, t2).toString();
      fail("Conflict expected");
    } catch (ConflictException ex) {
      // Expected result
    }
    // Try the other way as well
    try {
      m.merge(tb, t2, t1).toString();
      fail("Conflict expected also in mirrored case");
    } catch (ConflictException ex) {
      // Expected result
    }

  }

  private void merge(String base, String one, String two, String merge) {
    TestMerger m = new TestMerger();
    TestTree tb = new TestTree(base);
    TestTree t1 = new TestTree(one);
    TestTree t2 = new TestTree(two);
    try {
      String merge12 = m.merge(tb, t1, t2).toString();
      String merge21 = m.merge(tb, t2, t1).toString();
      Assert.assertEquals("Merge failed symmetry test", merge12, merge21);
      Assert.assertEquals("Merge incorrect", merge, merge21);
    } catch (ConflictException ex) {
      throw new RuntimeException(ex); // Fail
    }
  }

  /**
   * Simple tree merger for testing purposes. Keys and content are strings,
   * and the merger uses {@link TestTree}s for convenience.
   */
  public static class TestMerger extends TreeMerger<String, String, TestTree> {

    private TestTree merged;

    protected TestMerger() {
      super(new TreeMerger.NullNodeMerger<String>(new Comparator<String>() {

        @Override
        public int compare(String s1, String s2) {
          return s1.compareTo(s2);
        }

      }));
    }

    @Override
    protected TestTree emitChildList(AbstractTreeNode<String, String, ?> parent,
        List<AbstractTreeNode<String, String, ?>> children) {
      String parentId = parent == null ? null : parent.getId();
      if (parent == null) {
        merged = new TestTree();
      }
      try {
        for (AbstractTreeNode<String, String, ?> child : children) {
          merged.insert(child.getContent(), child.getId(), parentId, MutableTree.DEFAULT_POS);
        }
      } catch (NodeNotFoundException ex) {
        throw new RuntimeException(ex); // Cause test to fail
      }
      return merged;
    }
  }

  /**
   * Mutable tree to hold test trees. Test trees are expressed using a Lisp-like
   * string notation for brevity. Each subtree s is written as l (s1 s2 ...),
   * where l is the label of the node, and s1,... are subtrees. For instance,
   *
   *  a
   * / \
   * b  c
   *    |
   *    d
   * Becomes a (b c (d)).
   * In the test tree nodes are assigned ids using the lower-case first two
   * letters of the label l, and the content of nodes becomes l. For instance,
   * the (id:content) of the nodes in the tree "r (AB Hello)" are
   * (r:r), (ab:AB), and (he:Hello).
   */
  public static class TestTree extends SimpleMutableTree<String, String> {

    public TestTree() {
    }

    public TestTree(String from) {
      buildTree(from);
    }

    /**
     * Return lisp-like string of tree.
     */
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      toString(getRoot(), sb);
      return sb.toString();
    }

    private void toString(SimpleNode<String, String> n, StringBuilder sb) {
      sb.append(n.getContent() != null ? n.getContent() : "(null)");
      int childCount = 0;
      for (SimpleNode<String, String> c : n.children()) {
        sb.append(childCount++ == 0 ? " (" : " ");
        toString(c, sb);
      }
      if (childCount > 0) {
        sb.append(")");
      }
    }

    // Build tree from description. See above for the notation
    private void buildTree(String descr) {
      // Canonicalize: atoms, parenthesis separated by exactly 1 space on both sides
      descr = descr.replace("(", " ( ").replace(")", " ) ").replaceAll("\\s+", " ");
      try {
        buildTree(descr, null, 0);
      } catch (NodeNotFoundException e) {
        fail("Unexpected exception in test setup " + e);
      }
    }

    private void buildTree(String t, String pid, int pos)
        throws NodeNotFoundException {
      int startOfToken = 0;
      String node = null, nodeId = null;
      while (startOfToken < t.length()) {
        // Read a token: either non-space chars or (....), where the parenthesis are matched
        int depth = 0;
        int endOfToken = startOfToken;
        while (endOfToken < t.length()) {
          char ch = t.charAt(endOfToken);
          if (ch == '(') {
            depth++;
          } else if (ch == ')') {
            depth--;
          } else if (ch == ' ' && depth == 0) {
            break;
          }
          endOfToken++;
        }
        String token = t.substring(startOfToken, endOfToken);
        if (token.startsWith("(")) {
          // Recurse for subtree. The 2 and -2 offsets below strip the '(' and ')'
          buildTree(token.substring(2, token.length() - 2), nodeId, 0);
        } else {
          node = token;
          String str = node.length() > 1 ? node.substring(0, 2) : node;
          nodeId = str.toLowerCase();
          insert(node, nodeId, pid, pos++);
        }
        startOfToken = endOfToken + 1;
      }
    }
  }
  
}

