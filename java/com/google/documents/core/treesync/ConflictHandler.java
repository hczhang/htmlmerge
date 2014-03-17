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

import com.google.documents.core.treesync.TreeMerger.NodeHolder;
import com.google.documents.core.treesync.TreeMerger.TreeCursor;

/** 
 * Handler for conflicts that may arise during merge. Conflicts may be emitted by the
 * {@link TreeMerger tree merger} during the merge run.
 * 
 * @author tancred (Tancred Lindholm)
 *
 * @param <C> Content type for tree
 * @param <K> Key type for tree
 * @param <T> Tree type
 */
public interface ConflictHandler<C, K, T 
    extends AddressableTree<C, K, ? extends AbstractTreeNode<?, ?, ?>>> {

  /** 
   * Node has conflicting positions. This conflict occurs if a node appears in mutually
   * incompatible positions in the changed trees. For instance, a node has been moved to the end of 
   * a child list in one tree, and to the start of the same list in the other tree.
   *  
   * <p>Conflict handlers can resolve the conflict by manipulating the current triple of nodes
   * being merged (parameters nb, n1, and n2), and/or the current position in the child list,
   * stored in the tree cursors cb, c1, and c2.
   * 
   * @param nb Node in base tree or <code>null</code> if no base node involved
   * @param n1 Node in tree 1 or <code>null</code> if no node in tree 1 involved
   * @param n2 Node in tree 2 or <code>null</code> if no node in tree 1 involved
   * @param cb Tree cursor for base
   * @param c1 Tree cursor for tree 1
   * @param c2 Tree cursor for tree 2
   * @throws ConflictException if the conflict could not be resolved
   */
  public void conflictingPosition(NodeHolder<C, K> nb, NodeHolder<C, K> n1, NodeHolder<C, K> n2,
      TreeCursor<?, ?, T> cb, TreeCursor<?, ?, T> c1, TreeCursor<?, ?, T> c2) 
      throws ConflictException;

  /** 
   * New nodes were inserted at the same position from both trees. The nodes have different ids,
   * and are thus two separate entities.
   * 
   * <p>Conflict handlers can resolve the conflict by manipulating the inserted nodes
   * being merged (parameters n1, and n2), and/or the current position in the child list,
   * stored in the tree cursors c1 and c2.
   * 
   * @param n1 Node in tree 1
   * @param n2 Node in tree 2
   * @param c1 Tree cursor for tree 1
   * @param c2 Tree cursor for tree 2
   * @throws ConflictException if the conflict could not be resolved
   */
  public void collidingNode(NodeHolder<C, K> n1, NodeHolder<C, K> n2, TreeCursor<?, ?, T> c1, 
      TreeCursor<?, ?, T> c2) throws ConflictException;

  /** 
   * Conflicting insert of a node. A node was inserted in both trees. Note that the node
   * has the same id in both tree 1 and tree 2, which is how this case differs from
   * {@link #collidingNode(NodeHolder, NodeHolder, TreeCursor, TreeCursor) collidingNode}.  
   * 
   * @param c1 Content of node in tree 1
   * @param c2 Content of node in tree 2
   * @param id id of inserted node
   * @return reconciled content (must not be <code>null</code>)
   * @throws ConflictException if the conflict could not be resolved
   */  
  public C collidingContent(C c1, C c2, K id) throws ConflictException;

  /** 
   * Conflicting update to node. 
   * 
   * @param cb Content of node in base tree
   * @param c1 Content of node in tree 1
   * @param c2 Content of node in tree 2
   * @param id id of inserted node
   * @return reconciled content (must not be <code>null</code>)
   * @throws ConflictException if the conflict could not be resolved
   */
  public C conflictingContent(C cb, C c1, C c2, K id) throws ConflictException;

}
