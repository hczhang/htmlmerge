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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.LinkedList;
import java.util.Map;

/**
 * Simple implementation of mutable tree. 
 * 
 * @author tancred (Tancred Lindholm)
 *
 * @param <C> Content type
 * @param <K> Id type
 */
public class SimpleMutableTree<C, K> implements MutableTree<C, K, SimpleNode<C, K>> {

  protected SimpleNode<C, K> root = null;
  protected Map<K, SimpleNode<C, K>> index = Maps.newHashMap(); 

  // TraversableTree interface

  @Override
  public SimpleNode<C, K> getRoot() {
    return root;
  }

  // AdressableTree interface
  
  @Override
  public SimpleNode<C, K> getNode(K key) {
    return index.get(key);
  }


  @Override
  public K getParent(K id) throws NodeNotFoundException {
    SimpleNode<C, K> node = getNode(id);
    if (node == null) {
      throw new NodeNotFoundException(id);
    }
    SimpleNode<C, K> parent = node.getParent();
    return parent == null ? null : parent.getId();
  }
  
  // MutableTree interface
  
  @Override
  public void delete(K id) throws NodeNotFoundException {
    SimpleNode<C, K> delRoot = ensureNode(id);
    if (delRoot == root) {
      root = null; // Deleted root
    } else {
      delRoot.detach();
    }
    // Clean out deleted nodes from the index
    LinkedList<SimpleNode<C, K>> delQueue = Lists.newLinkedList();
    delQueue.add(delRoot);
    while (!delQueue.isEmpty()) {
      SimpleNode<C, K> n = delQueue.removeFirst();
      index.remove(n.getId());
      delQueue.addAll(n.safeChildList());
    }
  }

  @Override
  public K insert(C n, K id, K pid, int pos) throws NodeNotFoundException {
    SimpleNode<C, K> newNode = new SimpleNode<C, K>(n, id);
    if (pid == null) {
      // Inserting new root
      Preconditions.checkArgument(root == null, "Tree already has a root");
      root = newNode;
    } else {
      SimpleNode<C, K> parent = ensureNode(pid);
      newNode.attach(parent, pos);
      if (index.containsKey(newNode.getId()))  {
        newNode.detach();
        throw new IllegalArgumentException("Tree already contains node " + newNode.getId());
      }
    }
    index.put(newNode.getId(), newNode);
    return newNode.getId();
  }

  @Override
  public K move(K id, K pid, int pos) throws NodeNotFoundException {
    SimpleNode<C, K> mover = ensureNode(id);
    SimpleNode<C, K> parent = ensureNode(pid);
    // Sanity checks before move
    if (parent == null || mover.getParent() == null) {
      throw new IllegalArgumentException("Trying to move root, or destination is root.");
    }
    if (mover.isAncestorOf(parent)) {    
      throw new IllegalArgumentException(
        "Node to move node would become ancestor of its new parent");
    }
    if (pos < DEFAULT_POS 
        || pos > parent.getChildCount() - (parent == mover.getParent() ? 1 : 0)) {
      // Note that we need to decrease the allowed upper bound by 1 if we're
      // moving inside the child list of the same node (because of the
      // detach and then attach semantics of the target position)
      throw new IndexOutOfBoundsException(); // Illegal target index
    }
    // NOTE: if detach succeeds, but attach fails, the node gets deleted.
    // The checks above should ensure this won't happen, though 
    mover.detach();
    mover.attach(parent, pos);
    return mover.getId();
  }

  @Override
  public void update(C content, K id) throws NodeNotFoundException  {
    SimpleNode<C, K> n = ensureNode(id);
    n.setContent(content);    
  }

  private final SimpleNode<C, K> ensureNode(K id) throws NodeNotFoundException {
    SimpleNode<C, K> n = index.get(id);
    if (n == null) {
      throw new NodeNotFoundException(id);
    }
    return n;
  }
}
