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

import com.google.documents.core.treesync.TreeMergerTest.TestMerger;
import com.google.documents.core.treesync.TreeMergerTest.TestTree;

import junit.framework.TestCase;

/**
 * Test class for the edit script generator. 
 * 
 * @author tancred (Tancred Lindholm)
 */
public class EditScriptGeneratorTest extends TestCase {
  
  public void testUpdate() {
    runCase("a (b c)", "a (b C)"); // Single node update
    runCase("a (b c)", "A (b C)"); // Two node update, including root
    runCase("r (a (b c d))", "R (b (d C A))"); // Combined moves and updates
  }

  public void testInsert() {
    runCase("a (b c)", "a (b d c)"); // Insert at mid of child list
    runCase("a (b c)", "a (b c d)"); // Insert at end of child list
    runCase("a (b c)", "a (d b c)"); // Insert at start of child list
    runCase("a (b c)", "a (d e b f g c h i)"); // Multiple inserts
    runCase("a (b c)", "a (b (d) c)"); // New child
    runCase("a (b c)", "a (b (d e f) c)"); // New child list
    runCase("a", "a (b (c))"); // Simple new child of inserted node
    runCase("a", "a (b (d e f (g h (i j))) c)"); // More complex new subtree
  }

  public void testDelete() {
    reverseCase("a (b c)", "a (b d c)"); // Delete at mid of child list
    reverseCase("a (b c)", "a (b c d)"); // Delete at end of child list
    reverseCase("a (b c)", "a (d b c)"); // Delete at start of child list
    reverseCase("a (b c)", "a (d e b f g c h i)"); // Multiple Deletes
    reverseCase("a (b c)", "a (b (d) c)"); // Delete only child
    reverseCase("a (b c)", "a (b (d e f) c)"); // Delete child list
    reverseCase("a", "a (b (c))"); // Simple delete subtree
    reverseCase("a", "a (b (d e f (g h (i j))) c)"); // More complex delete subtrees
  }
  
  public void testInsDel() {
    // We test ins-del combinations to make sure child list position tracking works
    runCase("a (b c d)", "a (b i d)"); // Insert+delete in middle
    runCase("a (b c d)", "a (i c d)"); // Insert+delete at start
    runCase("a (b c d)", "a (b c i)"); // Insert+delete at end
    runCase("a (b c d)", "a (i b d)"); // Insert+delete not co-located 1
    runCase("a (b c d)", "a (c d i)"); // Insert+delete not co-located 2
    runCase("a (b c d e f g h i)", "a (j b d k e h l m n)"); // Lots of inserts,
                                                             // deletes in child list
    runCase("a (b (e (f g)) c d)", "a (c d i (j k (l)))"); // With subtrees
  }
  
  public void testLocalMove() {
    runCase("a (b c d e)", "a (b d c e)"); // Move in mid list
    runCase("a (b c d e)", "a (c d e b)"); // Move start->end of list
    runCase("a (b c d e)", "a (e b c d)"); // Move end->start of list    
    runCase("a (b c)", "a (c b)"); // Swap
    runCase("a (b c d e)", "a (d e c b)"); // 2 movers
    runCase("a (b c d e f g h i j k l m n)", "a (d j e b c i f g h k n l m)"); // Many movers
  }

  public void testFarMove() {
    runCase("a (g (b c d) h (e f))", "a (g (b d) h (e c f))"); // Move to mid list
    runCase("a (g (b c d) h (e f))", "a (g (b d) h (c e f))");  // Move to start of list
    runCase("a (g (b c d) h (e f))", "a (g (b d) h (e f c))");  // Move to end of list
    runCase("a (b (c) g (e f))", "a (b g (e c f))"); // Move complete 1-node child list
    runCase("a (b c (e f))", "a (b (e f c))"); // Move to increasing output order
    reverseCase("a (b c (e f))", "a (b (e f c))"); // Move to decreasing output order
    runCase("a (b c d e f g h i j)", "a (b (d (e f) c (g) h (i (j))))"); // "De-flatten"
  }
  
  public void testDelayedDelete() {
    // Deleting a subtree, some subtrees of which we move out, requires 
    // that deletion is executed after the move. This code tests this case
    runCase("a (b (k (l (m n) d)))", "a (b (d))"); // Move in increasing dfs order 
    runCase("a (b (k (l (m n) d)))", "a (d b)"); // Move in decreasing dfs order 
    runCase("a (b (k (l (m n) d)))", "a (b (i (d n) j (l (m))))"); // 3 dependencies for k 
  }
  
  public void testMixedOps() {
    runCase("a (b c d e)", "a (b d c i j)"); // local move, ins, del
    runCase("a (b c (e f))", "a (b (f e c))"); // local & far move
    // ins, del, far & local moves
    runCase("a (b (e (f g)) c m n o p d)", "a (m n c i (j k (l d)) p o)");
    // Same as above, with some updates
    runCase("a (b (e (f g)) c m n o p d)", "A (m n C i (J K (l d)) P o)");        
  }
  
  /**
   * Execute a test case. A test case consists of "from" and "to" trees,
   * henceforth Tf and Tt. We perform a three-way merge of these trees, where Tf
   * is used as the base and second edited tree, and Tt is used as the first
   * edited tree. The merge will then trivially be Tt.
   * 
   * <p>
   * The merge process invokes the edit script generator, which generates an
   * edit script that should transform Tf into the merged tree, i.e. Tt. To
   * verify that this is indeed the case, we execute the generated edit script
   * on a copy of Tf, and then verify that the end result is identical to Tt.
   * 
   * <p>
   * The input trees are expressed using a Lisp-like string notation for
   * convenience (i.e, test cases are quick to write). Each subtree s is written
   * as l (s1 s2 ...), where l is the label of the node, and s1,... are
   * subtrees. The first character of the label, lowercased, is used as node id. The
   * content of the node is a Text node with the label as text. 
   *
   * 
   * For instance, 
   * <pre>
   *   a
   *  / \ 
   * b   c 
   *     |
   *     d 
   * </pre>    
   * Becomes a (b c (d)). a (B c (d)) is the same tree, but with the content of node 
   * 'b' updated from 'b' to 'B'. See {@link TraversableTreeTest}} for a more detailed 
   * explanation of the notation.
   *
   * <p>
   * Note that we do not test for a particular edit script; only the end result
   * counts. Also note that we use the three-way merger to implicitly invoke edit
   * script generation rather than interfacing with the class directly. This has
   * the advantage that this test class also tests integration between the edit
   * script generator and the merger.
   * 
   * @param from initial tree
   * @param to target tree
   */
  private void runCase(String from, String to) {
    TestTree tb = new TestTree(from);
    TestTree t1 = new TestTree(to);
    TestTree t2 = tb;
    merge(tb, t1, t2, from, to);
  }

  /**
   * Run case in reverse. Convenience method to highlight the same cases
   * being run both forward and in reverse.
   *  
   * @param to target tree
   * @param from initial tree
   */
  private void reverseCase(String to, String from) {
    runCase(from, to);
  }
  
  private void merge(TestTree tb, TestTree t1, TestTree t2, String from, String expected) {
    TestTree t = new TestTree(from);
    TestMerger m = new TestMerger();
    try {
      m.setEditHandler(new ExecutingEditHandler(t));
      // Execute merge and mirror changes to tree t using the TestEditHandler
      m.merge(tb, t1, t2);
    } catch (ConflictException e) {
      fail("Unexpected conflict " + e);
    }
    // Check that string representation of result tree is identical to the expected tree
    assertEquals("Unexpected result tree", expected, t.toString());
  }
  
  /**
   * Edit handler that executes operations on a target mutable tree.
   */
  public static class ExecutingEditHandler implements EditHandler<String, String> {

    private MutableTree<String, String, ?> t;
    
    /**
     * Construct a new edit handler.
     * 
     * @param t target tree to edit
     */
    public ExecutingEditHandler(MutableTree<String, String, ?> t) {
      this.t = t;
    }

    @Override
    public void delete(String id, SourceTree tree) {
      log(String.format("delete(%s)", id));
      try {
        t.delete(id);
      } catch (NodeNotFoundException e) {
        fail("Unexpected exception " + e);
      }
    }

    @Override
    public void insert(AbstractTreeNode<? extends String, ? extends String, 
        ? extends AbstractTreeNode<? extends String, ? extends String, ?>> 
        insRoot, String parentId, int pos, SourceTree tree) {
      log(String.format("ins(%s, %s, %s, %d)", insRoot, insRoot.getId(), parentId, pos));
      try {
        t.insert(insRoot.getContent(), insRoot.getId(), parentId, pos);
        // Recurse insert operations for subtree
        for (AbstractTreeNode<? extends String, ? extends String, ?> child : insRoot.children()) {
          insert(child, insRoot.getId(), MutableTree.DEFAULT_POS, tree);
        }
      } catch (NodeNotFoundException e) {
        fail("Unexpected exception " + e);
      }
    }

    @Override
    public void move(String id, String parentId, int pos, SourceTree tree) {
      log(String.format("move(%s, %s, %s)", id, parentId, pos));
      try {
        t.move(id, parentId, pos);
      } catch (NodeNotFoundException e) {
        fail("Unexpected exception " + e);
      }      
    }

    @Override
    public void update(String content, String id, SourceTree tree) {
      log(String.format("update(%s, %s)", id, content));
      try {
        t.update(content, id);
      } catch (NodeNotFoundException e) {
        fail("Unexpected exception " + e);
      }      
    }

    // Hook for logging human-readable edits
    private void log(String edit) {
    }
    
  }
  
}
