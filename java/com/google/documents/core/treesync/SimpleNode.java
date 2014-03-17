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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Nodes in the SimpleMutableTree.
 * 
 * @author tancred (Tancred Lindholm)
 *
 * @param <C> Content type
 * @param <K> Id type
 */

public class SimpleNode<C, K> implements AbstractTreeNode<C, K, SimpleNode<C, K>> {

  // Allocated on-demand to save a list instantiation per leaf node
  private List<SimpleNode<C, K>> children = null; 

  private C content = null;
  private K id = null;
  private SimpleNode<C, K> parent = null;
  
  /**
   * Create a new node.
   * 
   * @param content content object
   * @param id node id
   */
  public SimpleNode(C content, K id) {
    this.content = content;
    this.id = id;
  }

  @Override
  public C getContent() {
    return content;
  }

  @Override
  public K getId() {
    return id;
  }

  @Override
  public SimpleNode<C, K> getParent() {
    return parent;
  }

  /**
   * Detach this node from its parent.
   */
  public void detach() {
    Preconditions.checkState(parent != null, "Node already detached.");
    parent.children.remove(this);
    this.parent = null;
  }
  
  /**
   * Attach the node as a child node.
   * 
   * @param toParent node that become parent node of this node
   * @param pos position in child list of the parent
   */
  public void attach(SimpleNode<C, K> toParent, int pos) {
    Preconditions.checkArgument(toParent != null, "Null parent illegal");
    Preconditions.checkState(parent == null, "Node is already attached");
    if (pos == MutableTree.DEFAULT_POS) {
      toParent.allocChildList().add(this);
    } else {
      toParent.allocChildList().add(pos, this);
    }
    this.parent = toParent;
  }
  
  @Override
  public Iterable<SimpleNode<C, K>> children() {
    return safeChildList();
  }
  
  /**
   * Get child at position.
   * 
   * @param pos position in child list
   * @return child at position <code>pos</code>
   */
  public SimpleNode<C, K> getChild(int pos) {
    return safeChildList().get(pos); 
  }
  
  /**
   * Get number of children.
   * 
   * @return number of children
   */
  public int getChildCount() {
    return safeChildList().size();
  }
  
  /**
   * Set content of node.
   * 
   * @param newContent new content, or <code>null</code> if no content
   * @return old content, or <code>null</code> if none
   */
  protected C setContent(C newContent) {
    C oldContent = this.content;
    this.content = newContent;
    return oldContent;
  }

  final List<SimpleNode<C, K>> safeChildList() {
    return children == null ? ImmutableList.<SimpleNode<C, K>>of() : children;
  }

  private final List<SimpleNode<C, K>> allocChildList() {
    return children == null ? children = Lists.newArrayList() : children;    
  }

  /**
   * Determine node equality. Node equality is determined by object identity.
   * <p>
   * Note that this must not be changed due to implementation concerns (Java
   * list lookups may return the "wrong" node). In particular, consider what
   * happens if we have a list <i>l</i> of two nodes <i>a</i> and <i>b</i>,
   * <code>l={a,b}</code>. If <code>a.equals(b)</code>, and we try to
   * <code>l.remove(b)</code>, <i>a</i> will get removed instead! Hence, we
   * only allow equality by object identity.
   */
  @Override
  public final boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    String longContent = String.valueOf(content);
    return "Node(" + id + ": " 
        + longContent.substring(0, Math.min(80, longContent.length())) + ")";
  }

  protected boolean isAncestorOf(SimpleNode<C, K> n) {
    for (SimpleNode<C, K> parentAncestor = n; parentAncestor != null;
        parentAncestor = parentAncestor.getParent()) {
      if (parentAncestor == this) {
        return true;
      }      
    }
    return false;
  }
}  
