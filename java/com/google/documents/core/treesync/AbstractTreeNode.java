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
 * Generic node interface for tree nodes. A generic node has a unique key, a
 * parent, and an arbitrary content object. The content object should
 * ideally be immutable for maximum compatibility with the generic tree algorithms
 * in this package.
 * 
 * <p>Note that this class takes the parent node type as a parameter, thus allowing for
 * type-safe programming with custom node types. The downside of this is that the type signature
 * becomes a bit complicated, especially in the generic case. For reference, the type of a
 * generic node with known key an content types is
 * <code>AbstractTreeNode<C, K, ? extends AbstractTreeNode<C, K, ? >></code>. 
 * Similarly, with generic key and content types, the type becomes 
 * <code>AbstractTreeNode<? extends Object, ? extends Object, 
 * ? extends AbstractTreeNode<?, ?, ? >></code>. 
 *  
 * @author tancred (Tancred Lindholm)
 *
 * @param <C> type of content object
 * @param <K> type of key
 * @param <N> type of parent node
 */
public interface AbstractTreeNode<C, K, N extends AbstractTreeNode<C, K, N>> {

  /**
   * Get the content object of this node. 
   * 
   * @return node content, or <code>null</code> if none 
   */
  public C getContent();
  
  /**
   * Get the parent node of this node. The parent of the root is <code>null</code>.
   *  
   * @return parent node, or <code>null</code> if this is a root node.
   */
  public N getParent();
  
  /**
   * Get unique id of node.
   * 
   * @return Unique id of node, or <code>null</code> if the node has no id (detached node).
   */
  public K getId();
    
  /**
   * Get children of this node. 
   * <p>Note: Child node order is specific to the implementation.
   * 
   * @return children 
   */
  public Iterable<N> children();
  
}
