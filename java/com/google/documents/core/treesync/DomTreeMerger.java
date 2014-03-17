// Copyright 2009 Google Inc. All Rights Reserved.

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
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.documents.core.treesync.Util.DomTraverser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tree Merger for DOM trees.
 *
 * @author tancred (Tancred Lindholm)
 */
public class DomTreeMerger extends TreeMerger<Node, String, DomTree> {

  private static final Logger logger = Logger.getLogger(TreeMerger.class.getName());

  /**
   * Merged tree. N.B. We do not necessarily need to build the result DOM,
   * we could also just serialize to normalized form on the fly
   * as merged nodes are emitted. For now, we just build the DOM though.
   */
  private Element merged = null;

  private Document document = null;

  /**
   * Map of id -> node for outstanding child lists. Gets emptied as
   * child lists are emitted.
   */
  private Map<String, Node> mergedListParents;

  public DomTreeMerger() {
    super(new HtmlNodeMerger());
  }

  public Element runMerge(Element ancestorBody, Element serverBody, Element clientBody) {
    document = Preconditions.checkNotNull(
        ancestorBody.getOwnerDocument(), "null document for: " + ancestorBody);
    if (!hasWellFormedIds(ancestorBody, ancestorBody, Sets.newHashSet()) 
        || !hasWellFormedIds(serverBody, serverBody, Sets.newHashSet())
        || !hasWellFormedIds(clientBody, clientBody, Sets.newHashSet())) {
      // We need to do tree matching to align the trees
      (new IdEnsurer()).traverse(serverBody);
      TextAlignmentTreeMatcher treeMatcher = new TextAlignmentTreeMatcher(); 
      treeMatcher.match(serverBody, ancestorBody);
      treeMatcher.match(ancestorBody, clientBody);
    }
    DomTree ancestorTree = new DomTree(ancestorBody);
    DomTree clientTree = new DomTree(clientBody);
    DomTree serverTree = new DomTree(serverBody);
    MergeDebug.dumpTree(0, System.out, ancestorTree.getRoot(), "Ancestor");
    MergeDebug.dumpTree(0, System.out, clientTree.getRoot(), "Client");
    MergeDebug.dumpTree(0, System.out, serverTree.getRoot(), "Server");

    try {
      merge(ancestorTree, clientTree, serverTree);
    } catch (ConflictException e) {
      logger.log(Level.INFO, "Tree merger yielded conflict: {0}", e.getMessage());
      return null;
    }
    logger.info("Tree merger succesful.");

    MergeDebug.dumpTree(0, System.out, new DomTree(merged).getRoot(), "Merged");
    return merged;
  }

  /**
   * Check if tree ids are well formed. Well-formed means that each element has
   * an id, and that id is unique among the element ids in the tree.
   * 
   * @param element root of tree
   * @param idAccumulator Set that accumulates seen ids
   * @return true iff well formed
   */
  private boolean hasWellFormedIds(Element element, Element bodyNode,
      HashSet<Object> idAccumulator) {
    if ((element == bodyNode) || element.hasAttribute(Util.ID_ATTRIBUTE)) {
      String id = element.getAttribute(Util.ID_ATTRIBUTE);
      if (idAccumulator.contains(id)) {
        // Abort on duplicate id.
        throw new RuntimeException("Duplicate id " + id); 
      } else if (id != null) {
        idAccumulator.add(id);
      }
      boolean wellFormed = true; // This node is well formed
      Node child = element.getFirstChild();      
      while (child != null && wellFormed) {
        if (child instanceof Element) {          
          wellFormed &= hasWellFormedIds((Element) child, bodyNode, idAccumulator);
        }
        child = child.getNextSibling();
      }
      return wellFormed; // Well-formedness of this node and subtrees
    } 
    return false; // Missing id
  }

  @Override
  protected DomTree emitChildList(AbstractTreeNode<Node, String, ?> parent,
      List<AbstractTreeNode<Node, String, ?>> children) {
    Node parentNode = null;
    if (parent == null) {
      Element parentElement = document.createElement(DomTree.ROOT_NAME);
      parentNode = parentElement;
      merged = parentElement;
      mergedListParents = Maps.newHashMap();
      mergedListParents.put(Iterables.getOnlyElement(children).getId(), parentNode);
    } else {
      parentNode = mergedListParents.remove(parent.getId());
      if (parentNode == null) {
        throw new IllegalStateException("Emitting child list of unknown parent " + parent.getId());
      }
      for (AbstractTreeNode<Node, String, ?> c : children) {
        // importNode() necessary because DOM does not allow shared nodes between trees
        Node mergedNode = document.importNode(c.getContent(), false);
        parentNode.appendChild(mergedNode);
        mergedListParents.put(c.getId(), mergedNode);
      }
    }
    return null; // Return value not used
  }
  
  private static class IdEnsurer extends DomTraverser {

    @Override
    protected void visit(Node n) {
      if (n instanceof Element) {
        Element e = (Element) n;
        if (Strings.isNullOrEmpty(e.getAttribute(Util.ID_ATTRIBUTE))) {
          e.setAttribute(Util.ID_ATTRIBUTE, Util.uid());
        }
      }
    }    
  }
}
