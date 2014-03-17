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

import com.google.common.collect.ImmutableSet;

/**
 * Generic test class for mutable trees. If you implement a new mutable tree, write a
 * test class that extends this class and implements the abstract methods. Your implementation
 * will then be tested with an extensive array of available tree tests for this type! 
 * 
 * This class is used just as {@link TraversableTreeTest}}, with the exception that the test
 * tree is an mutable tree, and that you need not write the code for constructing the test tree
 * in {@link #getAbcdTree()}}.
 * 
 * @author tancred (Tancred Lindholm)
 *
 * @param <T> Type of tree to test
 * @param <C> Type of content in tree used when testing
 * @param <K> Type of ids in tree used when testing
 */

public abstract class MutableTreeTest<T extends MutableTree<C, K, ? 
      extends AbstractTreeNode<C, K, ? extends AbstractTreeNode<C, K, ? >>>, C, K> 
  extends AddressableTreeTest<T, C, K> {

  /** 
   * Construct an empty mutable tree.
   * 
   * @return a new tree.
   */
  public abstract T createEmptyTree();

  /**
   * Construct test tree. In this class, the test tree is constructed using the mutability
   * API. This procedure is an integral part of the tests, which is the reason that the 
   * method must not be overridden. 
   */
  @Override
  public final T getAbcdTree() {
    T t = createEmptyTree();
    try {
      t.insert(cA, kA, null, MutableTree.DEFAULT_POS);
      t.insert(cB, kB, kA, MutableTree.DEFAULT_POS);
      t.insert(cC, kC, kA, MutableTree.DEFAULT_POS);
      t.insert(cD, kD, kC, MutableTree.DEFAULT_POS);
    } catch (NodeNotFoundException e) {
      fail("Could not build test tree using inserts.");
    }
    return t;
  }
  
  public void testInsert() throws NodeNotFoundException {
    T t = getAbcdTree();
    verifyTree(t, "a-A (b-B c-C (d-D))");
    try {
      t.insert(cE, kZ, kZ, MutableTree.DEFAULT_POS);
      fail("Expected insert at non-existing node " + kZ + " to fail");
    } catch (NodeNotFoundException ex) {
      // Expected
    }    
    try {
      t.insert(cA, kA, kB, MutableTree.DEFAULT_POS);
      fail("Expected insert of duplicate node " + kA + " to fail");
    } catch (IllegalArgumentException ex) {
      // Expected
    }    
  }

  @SuppressWarnings("unchecked") // ImmutableSet.of(E ...) warning
  public void testDelete() throws NodeNotFoundException {
    T t = getAbcdTree();
    // Delete the single node b
    t.delete(kB);
    verifyTree(t, "a-A (c-C (d-D))");
    testNodeLookup(t, ImmutableSet.of(kB), false);
    // Delete subtree c-d
    t.delete(kC);
    testNodeLookup(t, ImmutableSet.of(kB, kC, kD), false);
    verifyTree(t, "a-A");
    // Delete root
    t.delete(kA);
    testNodeLookup(t, ImmutableSet.of(kB, kC, kD, kA), false);
    verifyTree(t, "");  
    t = getAbcdTree();
    try {
      t.delete(kZ);
      fail("Expected delete of non-existing node " + kZ + " to fail");
    } catch (NodeNotFoundException x) {
      // Expected
    }
  }
  
  public void testUpdate() throws NodeNotFoundException {
    T t = getAbcdTree();
    t.update(cE, kD);
    verifyTree(t, "a-A (b-B c-C (d-E))");
    t.update(cE, kA);
    verifyTree(t, "a-E (b-B c-C (d-E))");
    try {
      t.update(cE, kZ);
      fail("Expected update of non-existing node " + kZ + " to fail");
    } catch (NodeNotFoundException x) {
      // Expected
    }
  }
  
  public void testMove() throws NodeNotFoundException {
    T t = getAbcdTree();
    t.move(kD, kB, MutableTree.DEFAULT_POS);
    verifyTree(t, "a-A (b-B (d-D) c-C)");
    t.move(kC, kA, 0);
    verifyTree(t, "a-A (c-C b-B (d-D))");
    t.move(kD, kA, 2);
    verifyTree(t, "a-A (c-C b-B d-D)");
    t.move(kC, kA, 1);
    verifyTree(t, "a-A (b-B c-C d-D)");
    // Edge case: move to illegal last position in the same child list
    // The real last position is 2 due to detach/attach semantics of move
    // This should fail with the node still properly attached to the tree
    try {
      t.move(kC, kA, 3);
      fail("Expected move to illegal position in child list to fail");
    } catch (RuntimeException e) {
      // Expected
    }
    verifyTree(t, "a-A (b-B c-C d-D)"); // If this fails, the above move did not fail atomically
    // Try moving non-existing node
    t = getAbcdTree();
    try {
      t.move(kZ, kA, MutableTree.DEFAULT_POS);
      fail("Expected move of non-existing node to fail");
    } catch (NodeNotFoundException x) {
      // Expected
    }
    // Try moving C as a child of D. This should fail, since D is in the subtree of C
    try {
      t.move(kC, kD, MutableTree.DEFAULT_POS);
      fail("Expected move causing cycle in tree to fail.");
    } catch (RuntimeException x) {
      // Expected
    }
    // Try moving D as a child of D. 
    try {
      t.move(kD, kD, MutableTree.DEFAULT_POS);
      fail("Expected move causing cycle in tree to fail.");
    } catch (RuntimeException x) {
      // Expected
    }
  }
}
