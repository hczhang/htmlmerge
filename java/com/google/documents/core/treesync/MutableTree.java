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

/**
 * Mutable tree. A mutable tree can be changed with insert, delete, update, and
 * move operations. 
 *  
 * @author tancred (Tancred Lindholm)
 *
 * @param <C> Type of content objects in nodes
 * @param <K> Type of ids
 * @param <N> Type of root node in tree
 */
public interface MutableTree<C, K, N extends AbstractTreeNode<C, K, N>> 
    extends AddressableTree<C, K, N> {

  /**
   * Constant signifying default position in child list. The default position is
   * implementation-dependent. It may for instance correspond to the end of the
   * child list, or to the position in the child list according to some child 
   * node sorting.
   */
  public static final int DEFAULT_POS = -1; 
  
  /**
   * Delete subtree. Deletes the subtree rooted at the given id.
   * 
   * @param id root of subtree to delete
   * @throws NodeNotFoundException if the node to delete is not in the tree
   */
  public void delete(K id) throws NodeNotFoundException;
  
  /**
   * Insert node.
   * 
   * @param c Content of new node
   * @param id Id of new node. Some implementations use automatic id assignment,
   * in which case this parameter may be set to <code>null</code>.
   * @param pid Id of parent of the new node. <code>null</code> indicates insertion of
   * a new root node in an otherwise empty tree.
   * @param pos Position in child list of the parent of the new node. {@link #DEFAULT_POS}
   * indicates that the default position should be used.
   * 
   * @return Id of inserted node. 
   * 
   * @throws NodeNotFoundException if the parent node is not in the tree
   */
  public K insert(C c, K id, K pid, int pos) throws NodeNotFoundException;
  
  /** 
   * Update content of node. Note that we provide an update operation in the
   * tree API to be able to capture update operations for further processing. That is,
   * if the nodes are updated with <code>tree.update(content,id)</code>, we may
   * process the update operation. If <code>tree.getNode(id).setSomeField()</code> is used,
   * the mutable tree interface gets bypassed.
   * 
   * @param content new content
   * @param id id of node to update
   * @throws NodeNotFoundException if the node to update is not in the tree
   */
  public void update(C content, K id) throws NodeNotFoundException;
  
  /**
   * Move node. A move operation will either succeed completely or fail 
   * with no changes to the tree. In the case that
   * the move would break the tree structure, an {@link IllegalArgumentException} is 
   * thrown. This happens, e.g., if a node is moved so that it becomes an ancestor or
   * descendant of itself.
   *  
   * <p>When moving a node to a new position in the same child list, the other children
   * of the node shift as if the node had first been removed and then inserted. That is,
   * when moving <code>a</code> in
   * <code>a b c</code> to position 1, the resulting list is <code>b a c</code>,
   * not <code>a b c</code>.
   * 
   * @param id id of node to move
   * @param pid id of new parent of node 
   * @param pos Position in child list of the parent of the moved node. {@link #DEFAULT_POS}
   *     indicates that the default position should be used.
   * @return id of moved node
   * 
   * @throws NodeNotFoundException if the node or parent node is not found.
   */
  public K move(K id, K pid, int pos) throws NodeNotFoundException;
}
