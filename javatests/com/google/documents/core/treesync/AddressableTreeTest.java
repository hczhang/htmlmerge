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

import java.util.Set;

/**
 * Generic test class for addressable trees. If you implement a new addressable tree, write a
 * test class that extends this class and implements the abstract methods. Your implementation
 * will then be tested with an extensive array of available tree tests for this type! 
 * 
 * This class is used just as {@link TraversableTreeTest}}, with the exception that the test
 * tree is an addressable tree.
 * 
 * @author tancred (Tancred Lindholm)
 *
 * @param <T> Type of tree to test
 * @param <C> Type of content in tree used when testing
 * @param <K> Type of ids in tree used when testing
 */

public abstract class AddressableTreeTest<T extends AddressableTree<C, K, 
    ? extends AbstractTreeNode<C, K, ? extends AbstractTreeNode<C, K, ?>>>, C, K>
      extends TraversableTreeTest<T, C, K> {

  public void testAddressability() {
    T t = getAbcdTree();
    verifyTree(t, "a-A (b-B c-C (d-D))"); // Overridden verify will test index as well
    assertNull("Node should not be in tree: " + kZ, t.getNode(kZ));
    assertNull("Node should not be in tree: " + null, t.getNode(null));
  }

  public void testGetParent() throws NodeNotFoundException {
    T t = getAbcdTree();
    verifyTree(t, "a-A (b-B c-C (d-D))"); // Overridden verify will test index as well
    assertEquals("Wrong parent", kC, t.getParent(kD));
    assertEquals("Wrong parent", kA, t.getParent(kC));
    assertEquals("Wrong parent", null, t.getParent(kA));
  }
  
  @Override
  protected Set<K> verifyTree(T t, String expected) {
    Set<K> index = super.verifyTree(t, expected);
    testNodeLookup(t, index, true);
    return index;
  }

  /**
   * Tests that nodes can or cannot be getNode()'d from the tree. The expectedPresence 
   * flag should be set to <code>true</code> if getNode() should succeed for each node
   * in ids, <code>false</code> if it should fail for each node. In the former case,
   * we verify that the id of each retrieved node matches the id used to look it up.  
   * 
   * @param t tree to test
   * @param ids ids to try to retrieve
   * @param expectedPresence see above description
   */
  protected void testNodeLookup(T t, Set<K> ids, boolean expectedPresence) {
    // Look up each node in the tree that we found during the above DFS (depth-first search) 
    // and verify that it has the same key as we used for lookup
    // A stronger test would be to check that the node is the same object (or .equals)
    // to the node object seen during DFS. However, the idea is that the trees
    // should be able to generate nodes on demand, and hence their identity may change
    // Using .equals() is just generally a bad idea because its semantics may vary.
    for (K key : ids) {
      AbstractTreeNode<C, K, ?> n = t.getNode(key);
      if (expectedPresence) {
        assertFalse("Node not found via getNode(): " + key, n == null);
        assertEquals("getNode(" + key + ") retuned node with key " + n.getId(), key, n.getId());
      } else {
        assertTrue("Deleted node found via getNode(): " + key, n == null);        
      }
    }
  }

}
