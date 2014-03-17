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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Lightweight addressable tree on top of DOM. Provides access to any node by
 * node id and a fake root node. Nodes ids are assigned using {@link IdPath}
 * naming based on existing ids in the document. Unidentified element nodes
 * get random unique ids. This tree generates the addressable tree nodes that wrap DOM
 * nodes on demand and without references to each other, i.e., the nodes are
 * transient objects immediately eligible for garbage collection.
 *
 * @author tancred (Tancred Lindholm)
 */
public class DomTree implements AddressableTree<Node, String, DomTree.DomTreeNode> {

  private static final Logger logger = Logger.getLogger(DomTree.class.getName());

  /**
   * Name of attribute storing element ids.
   */
  public static final String ID_NAME = "id";

  /**
   * Well-known id for the root node.
   */
  public static final String ROOT_ID = "morot-2375c3f5-cf32-4dc9-9a56-54c4cb334a89";

  public static final String ROOT_NAME = "html";
  private Element rootContent;
  private NodeList bodyList;
  private DomTreeNode root;

  /**
   * Bidirectional map between DOM nodes and ids.
   */
  private BiMap<Node, String> ids = HashBiMap.create();

  /**
   * Ids of nodes on the first level of the tree. Used to detect the
   * boundary between the fake root node and its children.
   */
  private Set<String> level1Ids = Sets.newHashSet();

  /**
   * Create a new tree on top of a DOM tree. Note: The DOM tree passed to
   * the constructor will be normalized.
   *
   * @param body root node. This subtree will be normalized as a side effect.
   */
  public DomTree(Element body) {
    this(body, true);
  }
  
  /**
   * Create a new tree on top of a DOM tree. 
   * 
   * @param body root node.
   * @param normalize set if the body node should be normalized
   */
  DomTree(Element body, boolean normalize) {
    // Start by normalizing the input. We do this to put the tree in a canonical
    // form
    if (normalize) {
      body.normalize();
    }
    bodyList = body.getChildNodes();
    rootContent = body.getOwnerDocument().createElement(ROOT_NAME);
    root = new RootNode();
    identify(bodyList.item(0), 1, level1Ids, ids);
  }

  /**
   * Identify nodes in a node list.
   *
   * @param firstNode first node in node list
   * @param level current tree level
   * @param level1IdSet all ids on level 1 are added to this set
   * @param idBiMap Bidirectional map between DOM node and its id
   */
  private void identify(Node firstNode, int level, Set<String> level1IdSet,
      BiMap<Node, String> idBiMap) {
    if (firstNode == null) {
      return;
    }

    // First, establish element node ids
    for (Node n = firstNode; n != null; n = n.getNextSibling()) {
      if (n.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      String id = null;
      NamedNodeMap attributes = n.getAttributes();
      if (attributes != null) {
        Node attrNode = attributes.getNamedItem(ID_NAME);
        id = attrNode != null ? attrNode.getNodeValue() : null;
        if (idBiMap.containsValue(id)) {
          id = null; // reject as duplicate
        }
      }
      if (id == null) {
        id = Util.uid(idBiMap.values());
      }
      idBiMap.put(n, id);
      if (level < 2) {
        level1IdSet.add(id);
      }
    }

    // Then, establish ids for text nodes (which are anchored to siblings)
    for (Node n = firstNode; n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.ELEMENT_NODE) {
        continue;
      }
      if (n.getNodeType() != Node.TEXT_NODE) {
        throw new IllegalArgumentException("Input tree has non text/element data");
      }
      String id = null;
      if (n.getPreviousSibling() == null) {
        // First child
        id = idBiMap.get(n.getParentNode()) + IdPath.CHILD_AXIS + "0";
      } else {
        // Scan back and forwards for element node
        int distance = 1;
        Node prev = n;
        Node next = n;
        do {
          prev = (prev != null) ? prev.getPreviousSibling() : null;
          next = (next != null) ? next.getNextSibling() : null;
          if (prev != null && prev.getNodeType() == Node.ELEMENT_NODE) {
            id = idBiMap.get(prev) + IdPath.SIBLING_AXIS + distance;
          } else if (next != null && next.getNodeType() == Node.ELEMENT_NODE) {
            id = idBiMap.get(next) + IdPath.SIBLING_AXIS + (-distance);
          }
          distance++;
        } while (id == null && !(prev == null && next == null));
      }
      if (id == null || idBiMap.containsValue(id)) {
        // This should not really happen
        // If the tree is not normalized (constructor should ensure that), it
        // might be we got here because of an element node which has > 1 text 
        // nodes as first children. 
        logger.warning("Unexpected allocation failure for text node id: "
            + id + ", duplicate=" + idBiMap.containsValue(id));
        id = Util.uid(idBiMap.values());
      }
      idBiMap.put(n, id);
      if (level < 2) {
        level1IdSet.add(id);
      }
    }
    // Finally, recurse (could have been done in previous loops)
    for (Node n = firstNode; n != null; n = n.getNextSibling()) {
      identify(n.getFirstChild(), level + 1, level1IdSet, idBiMap);
    }
  }

  @Override
  public DomTreeNode getNode(String id) {
    if (ROOT_ID.equals(id)) {
      return root;
    }
    Node dn = ids.inverse().get(id);
    return dn == null ? null : new ProxiedNode(dn);
  }

  @Override
  public String getParent(String id) throws NodeNotFoundException {
    DomTreeNode n = getNode(id);
    if (n == null) {
      throw new NodeNotFoundException(id);
    }
    n = n.getParent();
    return n == null ? null : n.getId();
  }

  @Override
  public DomTreeNode getRoot() {
    return root;
  }

  /**
   * Base class for nodes in this tree.
   */
  public abstract class DomTreeNode implements AbstractTreeNode<Node, String, DomTreeNode> {

    /**
     * Make iterator returning proxied nodes for a node list.
     * @param list node list
     * @return child iterator
     */
    protected Iterable<DomTreeNode> makeIterable(final NodeList list) {
      return new Iterable<DomTreeNode>() {
        public Iterator<DomTreeNode> iterator() {
          return new Iterator<DomTreeNode>() {

            int next = 0;

            @Override
            public boolean hasNext() {
              return list.getLength() > next;
            }

            @Override
            public DomTreeNode next() {
              return new ProxiedNode(list.item(next++));
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException();
            }
          };
        }
      };
    }
  }

  /**
   * The root node in a DomTree. Since some HTML documents do not have a
   * "real" root, we use this fake root node.
   */
  private class RootNode extends DomTreeNode {

    @Override
    public Iterable<DomTreeNode> children() {
      return makeIterable(bodyList);
    }

    @Override
    public Node getContent() {
      return rootContent;
    }

    @Override
    public String getId() {
      return ROOT_ID;
    }

    @Override
    public DomTreeNode getParent() {
      return null;
    }
  }

  /**
   * Proxy for a DOM Node.
   */
  private class ProxiedNode extends DomTreeNode {

    private Node wrapped;

    public ProxiedNode(Node wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public Iterable<DomTreeNode> children() {
      return makeIterable(wrapped.getChildNodes());
    }

    @Override
    public Node getContent() {
      return wrapped;
    }

    @Override
    public String getId() {
      return ids.get(wrapped);
    }

    @Override
    public DomTreeNode getParent() {
      return level1Ids.contains(getId()) ? root : new ProxiedNode(wrapped.getParentNode());
    }
  }
}
