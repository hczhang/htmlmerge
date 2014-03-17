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
 * A tree that supports traversal by following child and parent relationships.
 * 
 * @author tancred (Tancred Lindholm)
 *
 * @param <C> Type of content objects in nodes
 * @param <K> Type of ids
 * @param <N> Type of root node in tree
 */
public interface TraversableTree<C, K, N extends AbstractTreeNode<C, K, N>> {
  
  /**
   * Get tree root node. 
   * @return root node, or <code>null</code> if the tree is empty.
   */
  public N getRoot();
}
