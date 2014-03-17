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

import java.util.HashSet;
import java.util.Set;

/**
 * Generic test class for traversable trees. If you implement a new traversable tree, write a
 * test class that extends this class and implements the abstract methods. Your implementation
 * will then be tested with an extensive array of available tree tests for this type! 
 * 
 * <p>You need to implement the method {@link #getAbcdTree()} which is used by the tests
 * to obtain a tree to run the tests on. 
 * Furthermore, you need to provide methods for handling ids and
 * content objects.
 * 
 * <p>In the common case that your tree supports Strings as both id and content object, you 
 * can implement content and id handling by providing dummy implementations for 
 * {@link #toId(String)} and {@link #toContent(String)}
 * that simply return their argument. In this case, the default 
 * {@link #toPrintable(Object, Object)} will also work, so you need not override it.  
 *
 * <p>If your tree implementation uses other types for id or content, so that you cannot
 * test with String id and content, you need to implement at mapping between the ids and content
 * objects that the tests use and those actually used in the tree. The tests use the 
 * Strings "a", "b", "c", ... for ids, and the Strings "A", "B", "C", ...
 * for contents. You need to provide implementations for the {@link #toId(String)} method
 * for mapping ids to the native id type of the tree and
 * the {@link #toContent(String)} method to map the test contents to the content type of your tree.
 * Furthermore, you need to implement {@link #toPrintable(Object, Object)} for your id and
 * content type. The purpose of this method is to produce a printable string for a given id and
 * content that the tests can use to verify a correct result.
 * See {@link DomTree} for an example of such a tree. 
 *   
 * <p><b>Example 1: </b>You have implemented MyTree with content type String and key type String. 
 * To test it, write the class
 * <pre>
 * public class MyTreeTest extends TraversableTreeTest<MyNode, String, String> {
 * 
 *    protected MyTree<Integer, String> getAbcdTree() {
 *       // Code to build the tree 
 *       //       a-A
 *       //      /   \
 *       //    b-B   c-C
 *       //           |
 *       //          d-D
 *     }
 *
 *     protected String toContent(String s) {
 *       return s;
 *     }
 *
 *     protected String toId(String s) {
 *       return s; 
 *     }
 *    
 * }
 * </pre>
 *
 * <p><b>Example 2: </b>You need to test type MyTree with hard-coded 
 * content type String and key type Integer. To test it, write the class
 * <pre>
 * public class MyTreeTest extends TraversableTreeTest<MyTree, String, Integer> {
 * 
 *    protected MyTree getAbcdTree() {
 *       // Code to build the tree 
 *       //       a-A
 *       //      /   \
 *       //    b-B   c-C
 *       //           |
 *       //          d-D
 *     }
 *
 *     protected String toContent(String s) {
 *       // Code to convert the strings "A", "B", "C", .. etc to a content object
 *       // In this case, we can return the same string
 *       return s;
 *     }
 *
 *     protected Integer toId(String s) {
 *       // Code to convert the strings "a", "b", "s", .. etc to an id
 *       // In this case, we convert "a"->0, "b"->1, etc
 *       return s.charAt(0)-'a'; 
 *     }
 *    
 *     protected String toPrintable(String c, Integer k) {
 *       // Build a string id-content using the unmapped id an content. That is,
 *       // we need to map the integer id back to "a", "b", ... etc.
 *       return (new StringBuilder()).append('a' + k).append('-').append(c).toString();
 *     }
 *    
 * }
 * </pre>
 *  
 * @author tancred (Tancred Lindholm)
 *
 * @param <T> Type of tree to test
 * @param <C> Type of content in tree used when testing
 * @param <K> Type of ids in tree used when testing
 */

public abstract class TraversableTreeTest<T extends TraversableTree<C, K, 
  ? extends AbstractTreeNode<C, K, ? extends AbstractTreeNode<C, K, ? >>>, C, K> 
  extends TestCase {

  // Keys used in tests
  protected final K kA = toId("a"); 
  protected final K kB = toId("b");
  protected final K kC = toId("c");
  protected final K kD = toId("d"); 
  protected final K kZ = toId("z");
  
  // Contents used in tests
  protected final C cA = toContent("A");
  protected final C cB = toContent("B");
  protected final C cC = toContent("C"); 
  protected final C cD = toContent("D");
  protected final C cE = toContent("E");

  /**
   * Map ids used by test to the ids used by the actual tree. The methods should be
   * able to map the strings "a", "b", ... to ids. In the common case that you test
   * the actual tree with string ids, this methods could just return <i>s</i>.
   * 
   * @param s id to map
   * @return mapped id
   */
  public abstract K toId(String s);
  
  /**
   * Map contents used by test to the content objects used by the actual tree. The methods should be
   * able to map the strings "A", "B", ... to content objects. In the common case that you test
   * the actual tree with string contents, this methods could just return <i>s</i>.
   * 
   * @param s content to map
   * @return mapped content
   */  
  public abstract C toContent(String s);

  /** 
   * Build test tree. The test tree is 
   * <pre>
   *       a-A
   *      /   \
   *    b-B   c-C
   *           |
   *          d-D
   * </pre>
   * where nodes are written as id: (content)
   * 
   * @return test tree
   */
  public abstract T getAbcdTree();

  /** 
   * Make printable representation of content and key. The printable representation
   * should be <i>k</i> '-' <i>c</i>, where, <i>k</i> is the unmapped id (i.e. "a", "b", ...), and
   * <i>c</i> is the unmapped content (i.e., "A", "B",...).
   *  
   * <p>This default implementation uses the expression
   * <code>id.toString() +  "-" + content.toString()</code> to construct the printable
   * representation.
   *   
   * @param content
   * @param id
   * @return printable representation
   */
  protected String toPrintable(C content, K id) {
    return id.toString() +  "-" + content.toString();
  }
  
  public void testTraverse(){
    T tree = getAbcdTree();
    verifyTree(tree, "a-A (b-B c-C (d-D))");
  }
  
  protected Set<K> verifyTree(T tree, String expected) {
    StringBuilder treeSb = new StringBuilder();
    Set<K> index = new HashSet<K>();
    if (tree.getRoot() != null) {
      buildTreeStringAndIndex(tree.getRoot(), treeSb, index);
    }
    assertEquals(expected, treeSb.toString());
    return index;
  }

  /**
   *  Build string representation of tree and index of its nodes.
   *  
   * <p>Tree structures are tested against a simple string representation of the tree. The 
   * representation of a node <i>n</i> with children <i>n1</i>, <i>n2</i>, ... is 
   * <i>s</i> '(' <i>s1</i> ' ' <i>s2</i> ' ' ... ')', where <code>s = toPrintable(n)</code>,
   * <code>s1 = toPrintable(n1)</code>, etc. The purpose of {@link #toPrintable(Object, Object)}
   * is to map your custom id and content objects back to the test's ids and keys. For instance, 
   * using this notation, the tree (notation id-content)
   * <pre> 
   *       a-A
   *      /   \
   *    b-B   c-C
   *           |
   *          d-D
   * </pre>
   * becomes <code>a-A (b-B c-C (d-D))</code>.
   *    
   * @param node root of string representation
   * @param sb StringbBilder to store string representation
   * @param index set of node ids in subtree rooted at n
   */
  protected void buildTreeStringAndIndex(
      AbstractTreeNode<C, K, ? extends AbstractTreeNode<C, K, ?>> node,
      StringBuilder sb, Set<K> index) {
    K key = node.getId();
    assertTrue("Tree has duplicate id " + key, index.add(key));
    sb.append(toPrintable(node.getContent(), key));
    int childCount = 0;
    for (AbstractTreeNode<C, K, ?> c : node.children()) {
      sb.append(childCount++ == 0 ? " (" : " ");
      buildTreeStringAndIndex(c, sb, index);
    }
    if (childCount > 0) {
      sb.append(")");
    }
  }
  
}
