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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.documents.core.treesync.EditHandler.SourceTree;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Tree-based three-way merger. See the following reference for a description of
 * the algorithm: T. Lindholm. A Three-way Merge for XML Documents. In ACM 
 * Symposium on Document Engineering, ACM Press, October 2004 [DocEng2004]
 * 
 * @author tancred (Tancred Lindholm)
 * 
 * @param <C> Content type of tree
 * @param <K> Key type of tree
 * @param <T> Tree type to merge
 */

/* 
 * There is some terminology associated with the algorithm that the reader
 * should be familiar with.
 * 
 * base     the common ancestor data, i.e., the common ancestor tree
 *          is the base tree, and nodes in it are referred to as base nodes
 * branch   the changed data. The changed tree are branch trees, and their
 *          nodes are branch nodes
 *
 * To reduce clutter, the following short mnemonic variable names are used
 * 
 * t,n       tree and node of unspecified origin 
 * tb,t1,t2  base and branch 1 and 2 trees
 * nb,n1,n2  nodes in tb,t1 and t2   
 * 
 * Naturally, to really understand what's going on, it helps to know the
 * underlying algorithm, so please read the associated publication
 * (http://dx.doi.org/10.1145/1030397.1030399)
 */
public abstract class TreeMerger<C, K, T extends AddressableTree<C, K, ? 
    extends AbstractTreeNode<C, K, ?>>> {
    
  private static final Logger logger = Logger.getLogger(TreeMerger.class.getName());
    
  /** Default edit handler. The default edit handler does nothing. */
  public static final EditHandler<Object, Object> DEFAULT_EDIT_HANDLER = new NullEditHandler();
  
  /** Sentinel node indicating absence of a node. */
  public static final AbstractTreeNode<?, Object, ?> DELETIA = DeletiaNode.INSTANCE;
  
  /** Sentinel node (abbreviation EOS) that marks the end of all child lists. */
  public static final AbstractTreeNode<?, Object, ?> END_OF_SEQUENCE = EndOfSequenceNode.INSTANCE;  

  /** Merger for content. */
  protected NodeMerger<C> contentMerger;
  
  protected TreeMerger(NodeMerger<C> contentMerger) {
    this.contentMerger = contentMerger;
  }

  /** 
   * Default conflict handler. The default conflict handler throws a 
   * {@link ConflictException} on any conflict.
   */
  private ConflictHandler<C, K, T> conflictHandler = new NullConflictHandler<C, K, T>();
  
  private EditHandler<? super C, ? super K> editHandler = DEFAULT_EDIT_HANDLER;
  
  /**
   * Merge trees.
   * 
   * @param base base tree
   * @param first first modified tree
   * @param second second modified tree
   * @return merged tree
   * @throws ConflictException if a conflict occurs
   */
  protected T merge(T base, T first, T second) throws ConflictException {
    // Implement the algorithm described in [DocEng2004]. We compute the
    // PCS relations (both edits and changes) on the fly from the input trees.
    AbstractTreeNode<C, K, ?> baseRoot = base.getRoot();
    AbstractTreeNode<C, K, ?> firstRoot = first.getRoot();
    AbstractTreeNode<C, K, ?> secondRoot = second.getRoot();
    EditScriptGenerator<C, K> scriptGenerator =
        new EditScriptGenerator<C, K>(editHandler, base, first, second);
    AbstractTreeNode<C, K, ?> mergeRoot =
        mergeNode(baseRoot, firstRoot, secondRoot, scriptGenerator);
    scriptGenerator.emitEdits(null,
        Collections.<AbstractTreeNode<C, K, ?>> singletonList(baseRoot), 
        Collections.<AbstractTreeNode<C, K, ?>> singletonList(mergeRoot));
    T mergedTree = emitChildList(null, ImmutableList.<AbstractTreeNode<C, K, ?>> of(mergeRoot));
    merge(mergeRoot, baseRoot, firstRoot, secondRoot, base, first, second, scriptGenerator,
        Sets.<K>newHashSet());
    return mergedTree;
  }
 
  /**
   * Called during merging to emit the merged tree.
   * 
   * @param parent parent of child list, or <code>null</code> if emitting root
   * @param children children to add (exactly 1 when parentId is <code>null</code>)
   * @return merged tree object.
   */
  protected abstract T emitChildList(AbstractTreeNode<C, K, ?> parent,
      List<AbstractTreeNode<C, K, ?>> children);

  /**
   * Recursively merge trees using the [DocEng2004] algorithm.
   * 
   * @param parent parent node in merged tree to which the merge subtree is added
   * @param pnb root node in base tree 
   * @param pn1 root node in first branch tree
   * @param pn2 root node in second branch tree
   * @param tb base tree
   * @param t1 first branch tree
   * @param t2 second branch tree
   * @param esg edit script generator
   * 
   * @throws ConflictException if a conflict occurs.
   */
  private void merge(AbstractTreeNode<C, K, ?> parent, AbstractTreeNode<?, ?, ?> pnb,
          AbstractTreeNode<?, ?, ?> pn1, AbstractTreeNode<?, ?, ?> pn2, T tb, T t1, T t2, 
          EditScriptGenerator<C, K> esg, Set<K> emittedIds) throws ConflictException {
    // The possible states of parent node deletion are tabulated below where Y means deleted, 
    // and - not deleted. Only some are valid states, as explained by the table. The 
    // subsequent assert verify that the state is correct on entering.
    //
    // pnb pn1 pn2       
    //  -   -   -  OK (all nodes exist)
    //  -   -   Y  Invalid (the parent merge should be DELETIA)
    //  -   Y   -  Invalid (the parent merge should be DELETIA)
    //      Y   Y  Invalid (node deleted in both branches, the parent merge should be DELETIA)
    //  Y   -   -  Invalid (parent found co-located inserts, should be conflict
    //             or be split into cases Y - Y and Y Y - )
    //  Y   -   Y  OK (inserting from T1)
    //  Y   Y   -  OK (inserting from T2)
    //  Y   Y   Y  Invalid (merging an infinite tree of deletia...not good)  

    assert (pnb != DELETIA && pn1 != DELETIA && pn2 != DELETIA) ||
           (pnb == DELETIA && pn1 != DELETIA && pn2 == DELETIA) ||
           (pnb == DELETIA && pn1 == DELETIA && pn2 != DELETIA) :
           "Invalid deletion state on merge enter";  
    TreeCursor<C, K, T> cb = new TreeCursor<C, K, T>(tb, pnb),
      c1 = new TreeCursor<C, K, T>(t1, pn1),
      c2 = new TreeCursor<C, K, T>(t2, pn2);

    MergeDebug.enter();
    MergeDebug.trace("At node " + parent);
        
    AbstractTreeNode<?, ?, ?> n0 = cb.next(), n1 = c1.next(), n2 = c2.next();
    List<AbstractTreeNode<C, K, ?>> mergedChildren = null;
    // List that will hold arguments for recursive calls
    List<MergeCallArguments> mergeCallArgumentList = null;
    for (boolean atEnd = false;;) {
      // In this loop, we traverse the three child lists in sync, i.e.,
      // the cursors are positioned at the corresponding nodes (or at
      // DELETIA if no such node exists). The first step is to sync up the
      // node cursors to the node that differs from the base (if any), because
      // that node represents a change in order/insert with respect to the 
      // base tree.
      MergeDebug.trace("Pre-merge position is (" + n0 + "," + n1 + "," + n2 + ")");
      if (n1.getId().equals(n2.getId())) {
        // Most common case, i.e., both branches agree on successor.
        // This is either no edit (then succ0=succ1=succ2), or the same edit
        // in both branches
        atEnd = !c1.hasNext();
        if (!atEnd && !n1.getId().equals(n0.getId())) {
          // Same edit on both
          // Case safe because n1 is not DEL or EOS (by !atEnd above)
          esg.setReorderOrigin(notSentinel(n1), SourceTree.BOTH);
          n0 = cb.seek(n1); // Remember to seek i0 to current position, though!
        }
      } else if (!n1.getId().equals(n0.getId()) && !n2.getId().equals(n0.getId())) {
        // Conflict order case (not so common, but prettier code if we test here)
        if (!hasNode(tb, n1) && !hasNode(tb, n2) && 
            !(n1 instanceof SentinelNode) && !(n2 instanceof SentinelNode)) {
          // Colliding inserts case; casts are safe since n1, n2 are real nodes heres
          NodeHolder<C, K> n1h = NodeHolder.newHolder(notSentinel(n1));
          NodeHolder<C, K> n2h = NodeHolder.newHolder(notSentinel(n2)); 
          conflictHandler.collidingNode(n1h, n2h, c1, c2);
          n0 = DELETIA;
          n1 = n1h.getNode();
          n2 = n2h.getNode();
        } else {
          // Move/move conflict, which may include a conflict with special nodes,
          // e.g., if one tree has a shorter child list and the other tree inserts
          // at the end of the child list. 
          NodeHolder<C, K> n0h = NodeHolder.newHolder(nullSentinel(n0)); 
          NodeHolder<C, K> n1h = NodeHolder.newHolder(nullSentinel(n1));
          NodeHolder<C, K> n2h = NodeHolder.newHolder(nullSentinel(n2));
          conflictHandler.conflictingPosition(n0h, n1h, n2h, cb, c1, c2);
          n0 = n0h.getNode();
          n1 = n1h.getNode();
          n2 = n2h.getNode();
        }
        atEnd = !cb.hasNext() || !c1.hasNext() || !c2.hasNext();
      } else if (!n1.getId().equals(n0.getId())) {
        // Change is in t1 (we know t2 has no change, because that would have
        // been a conflict)
        // Re-align i0, i2 to point at succ1
        n0 = cb.seek(n1);
        n2 = c2.seek(n1);
        atEnd = !c1.hasNext();
        if (!atEnd) {
          esg.setReorderOrigin(notSentinel(n1), SourceTree.FIRST);
        }
      } else {
        // Change is in t2
        // Re-align i0, i1 to point at succ2
        n0 = cb.seek(n2);
        n1 = c1.seek(n2);
        atEnd = !c2.hasNext();
        if (!atEnd) {
          esg.setReorderOrigin(notSentinel(n2), SourceTree.SECOND);
        }
      }
      MergeDebug.trace("Reconciled position is (" + n0 + "," + n1 + "," + n2 + ")");
      if (atEnd) {
        break; // Reached end of merged child list -> we're done!
      }
      if (mergedChildren == null) {
        // Lazily instantiated to save time & space in common case of leaf node
        // (which has no children, and thus won't need this object)
        mergedChildren = Lists.newLinkedList(); 
        mergeCallArgumentList = Lists.newLinkedList();
      }        
      AbstractTreeNode<C, K, ?> childNode = mergeNode(n0, n1, n2, esg);
      MergeDebug.trace("Merged node is", childNode);
      if (!emittedIds.contains(childNode.getId())) {
        mergedChildren.add(childNode);
        emittedIds.add(childNode.getId());
      } else {
        throw new ConflictException("Cyclic merged tree. Cycle starts at "
            + childNode.getId()); 
      }
      
      mergeCallArgumentList.add(new MergeCallArguments(n0, n1, n2)); 
      // Store arguments for recursive call
      // We do not recurse directly here, as we want to emit the edits for this child list first
      // The reason for that is that we need to emit inserted nodes at level n to be able to
      // insert any children of the inserted node at level n + 1
      
      n0 = cb.next(); 
      n1 = c1.next(); 
      n2 = c2.next();
    }
    
    // Merged child list is done, compute edits on it:
    // - First, set up child lists required by the ESG, accounting for special semantics
    //   of null mergedChildren and DELETIA parent in T0
    Iterable<AbstractTreeNode<C, K, ?>> mergedForEdScriptGen = 
        mergedChildren == null ? ImmutableList.<AbstractTreeNode<C, K, ?>>of() : mergedChildren;
    checkForDeletedNodes(sentinelSafeChildren(pnb), sentinelSafeChildren(pn1), tb, t1, t2);
    checkForDeletedNodes(sentinelSafeChildren(pnb), sentinelSafeChildren(pn2), tb, t2, t1);
    checkForMissingInserts(mergedForEdScriptGen, Iterables.concat(sentinelSafeChildren(pn1),
        sentinelSafeChildren(pn2)), tb);
    // - Compute edits
    esg.emitEdits(parent.getId(), sentinelSafeChildren(pnb), mergedForEdScriptGen);
    
    // Set children and recurse
    if (mergedChildren != null) {
      emitChildList(parent, mergedChildren);
      Iterator<MergeCallArguments> argIter = mergeCallArgumentList.iterator();
      for (AbstractTreeNode<C, K, ?> child : mergedChildren) {
        MergeCallArguments mergeArgs = argIter.next();
        merge(child, mergeArgs.getN0(), mergeArgs.getN1(), mergeArgs.getN2(), tb, t1, t2, esg,
            emittedIds);
      }
    }
    MergeDebug.exit();
  }
    
  private AbstractTreeNode<C, K, ?> mergeNode(AbstractTreeNode<?, ?, ?> n0, 
      AbstractTreeNode<?, ?, ?> n1, AbstractTreeNode<?, ?, ?> n2, EditScriptGenerator<C, K> esg)
      throws ConflictException {
    // Check that we are not merging EOS nodes; this is an error
    // If all nodes are EOS, we should not go here. If >0 nodes are
    // not EOS, the other nodes should be seek'd to that non-EOS node
    // In either case, we should never see EOS here!
    assert n0 != END_OF_SEQUENCE && n1 != END_OF_SEQUENCE && n2 != END_OF_SEQUENCE;
    boolean is0Deleted = n0 == DELETIA; 
    boolean is1Deleted = n1 == DELETIA; 
    boolean is2Deleted = n2 == DELETIA;
    C contentMerge;
    K idMerge;
    SourceTree updateOrigin = SourceTree.NONE;
    MergeDebug.trace("Merging content of (" + n0 + "," + n1 + "," + n2 + ")");
    // Possible states of node deletion. These are implemented in the if clauses below
    // Tb T1 T2     
    // - - - Normal merge
    // - - Y Deleted in a subtree in T2, but moved to different location in T1
    // - Y - As above, but vice versa
    // - Y Y Invalid
    // Y - - Co-located insert from both branches
    // Y - Y Insert from T1
    // Y Y - Insert from T2
    // Y Y Y Invalid
    if (!is0Deleted && !is1Deleted && !is2Deleted) {
      C c0 = notSentinel(n0).getContent();
      C c1 = notSentinel(n1).getContent();
      C c2 = notSentinel(n2).getContent(); 
      idMerge = notSentinel(n0).getId();
      try {
        contentMerge = contentMerger.mergeContent(c0, c1, c2);        
      } catch (ConflictException c) {
        contentMerge = conflictHandler.conflictingContent(c0, c1, c2, notSentinel(n0).getId());
      }
      if (contentMerge != c0) {
        updateOrigin = contentMerge == c1 ? SourceTree.FIRST 
            : (contentMerge == c2 ? SourceTree.SECOND : SourceTree.BOTH);
      }
    } else if (is0Deleted && !is1Deleted && is2Deleted) {
      // Insert from T1
      idMerge = notSentinel(n1).getId();
      contentMerge = notSentinel(n1).getContent();
      esg.setInsertOrigin(notSentinel(n1), SourceTree.FIRST);
    } else if (is0Deleted && is1Deleted && !is2Deleted) {
      // Insert from T2
      idMerge = notSentinel(n2).getId();
      contentMerge = notSentinel(n2).getContent();
      esg.setInsertOrigin(notSentinel(n2), SourceTree.SECOND);
    } else if (is0Deleted && !is1Deleted && !is2Deleted) {
      // Insert from both
      idMerge = notSentinel(n1).getId();
      contentMerge = conflictHandler.collidingContent(notSentinel(n1).getContent(), 
          notSentinel(n2).getContent(), notSentinel(n1).getId());
      esg.setInsertOrigin(notSentinel(n1), SourceTree.BOTH);
    } else if (!is0Deleted && is1Deleted && !is2Deleted) {
      // Node is deleted from T1 and has been moved to a new location in T2
      // The natural action here seems to be emitting a conflict, since otherwise
      // we break the really fundamental property that says 
      // "if a node is not in either branch tree, it is deleted." 
      throw new ConflictException("Delete/Move conflict for " + n0.getId());      
    } else if (!is0Deleted && !is1Deleted && is2Deleted) {
      // Node is deleted from T2 and has been moved to a new location in T1
      throw new ConflictException("Delete/Move conflict for " + n0.getId());      
    } else {
      throw new IllegalStateException("Illegal algorithm state." +
          "deleted(base,t1,t2)=(" + is0Deleted + "," + is1Deleted + "," + is2Deleted + ")");
    }
    SimpleNode<C, K> merged = new SimpleNode<C, K>(contentMerge, idMerge);
    if (updateOrigin != SourceTree.NONE) {
      esg.setUpdateOrigin(merged, updateOrigin);
    }
    return merged;
  }
  
  /**
   * Check that all inserts in branch child lists are present.
   * 
   * @param mergedChildren children of a merged node n
   * @param children children of n in branch child lists
   * @param tb base tree
   * @throws ConflictException if some inserted node in children is not in mergedChildren  
   */
  private void checkForMissingInserts(Iterable<AbstractTreeNode<C, K, ?>> mergedChildren,
      Iterable<AbstractTreeNode<C, K, ?>> children, T tb) throws ConflictException {
    // Set of keys in mergedChildren. Lazy initialized on first insert,
    // as in most child lists there will be no inserts at all
    Set<K> mergedNodes = null; 
    for (AbstractTreeNode<?, ?, ?> n : children) {
      if (!hasNode(tb, n)) {
        // Found insert of n
        if (mergedNodes == null) {          
          mergedNodes = Sets.newHashSet();
          for (AbstractTreeNode<?, K, ?> mergedNode : mergedChildren) {
            mergedNodes.add(mergedNode.getId());
          }
        }
        if (!mergedNodes.contains(n.getId())) {      
          throw new ConflictException(String.format("Inserted node %s was deleted", n.getId()));
        }
      }
    }
  }

  /**
   * Check child list in modified (branch) tree for deleted nodes that have
   * changed. If a changed and deleted node is found, a conflict is raised.
   * 
   * @param baseChildren children of a node n in the base tree 
   * @param branchChildren children of a node n in the branched tree
   * @param tb base tree
   * @param t branched tree in which branchChildren are 
   * @param otherT the other branched tree (i.e., T2 if t is T1)
   * @throws ConflictException if a delete/change conflict is found
   */
  private void checkForDeletedNodes(Iterable<AbstractTreeNode<C, K, ?>> baseChildren,
      Iterable<AbstractTreeNode<C, K, ?>> branchChildren, T tb, T t, T otherT)
      throws ConflictException {
    // Map of branch node predecessors and successors; nulls are used for "no previous"
    // (at start-of-list)
    // The map is lazily initialized on first delete (since a lot of
    // child lists will have no deletes)
    Map<Object, AdjacentNodeIds> branchOrderMap = null;
    Object prevBaseNodeId = null;
    AbstractTreeNode<?, ?, ?> baseNode = StartOfSequenceNode.INSTANCE;
    for (AbstractTreeNode<?, ?, ?> node : Iterables.concat(baseChildren,
        ImmutableList.of(END_OF_SEQUENCE))) {
      Object nextBaseNodeId = node.getId();
      if (baseNode != StartOfSequenceNode.INSTANCE && !hasNode(otherT, baseNode)) {
        // We have a delete; first initialize order map if needed
        if (branchOrderMap == null) {
          branchOrderMap = Maps.newHashMap();
          AbstractTreeNode<?, ? extends Object, ?> prev = null;
          AbstractTreeNode<?, ? extends Object, ?> m = StartOfSequenceNode.INSTANCE;
          for (AbstractTreeNode<?, ? extends Object, ?> next : Iterables.concat(branchChildren,
              ImmutableList.of(END_OF_SEQUENCE))) {
            if (m != StartOfSequenceNode.INSTANCE) {
              branchOrderMap.put(m.getId(), new AdjacentNodeIds(prev.getId(), next.getId()));
            }
            prev = m;
            m = next;
          }
        }
        // Check for changed predecessor or successor of node
        AdjacentNodeIds branchPrevNext = branchOrderMap.get(baseNode.getId());
        if (branchPrevNext == null // not in this child list -> node moved to other parent
            || !Objects.equal(prevBaseNodeId, branchPrevNext.predecessorId)
            || !Objects.equal(nextBaseNodeId, branchPrevNext.successorId)) {
          throw new ConflictException(String.format("Repositioning of %s ", baseNode.getId()));
        }
        // Check for deletes inside the deleted subtree
        checkDeletedSubtree(notSentinel(baseNode), tb, t, otherT);
      }
      prevBaseNodeId = baseNode != null ? baseNode.getId() : null;
      baseNode = node;
    }
  }
  
  /**
   * Check deleted subtree for changes.
   *   
   * @param delBase root node of deleted subtree
   * @param tb base tree
   * @param t tree which contains delBase (the branch tree in which delBase *was not* deleted)
   * @param otherT tree which does not contain delBase (the branch tree in which delBase 
   *                *was* deleted)
   * @throws ConflictException
   */
  private void checkDeletedSubtree(AbstractTreeNode<C, K, ?> delBase, T tb, T t, T otherT)
      throws ConflictException {
    K id = delBase.getId();
    AbstractTreeNode<C, K, ?> delBranch = t.getNode(id);
    Preconditions.checkNotNull(delBranch, "Call contract specifies that node must exist in t"); 
    C baseContent = delBase.getContent();
    C branchContent = delBranch.getContent();
    if (!contentMerger.nodeEquals(baseContent, branchContent)) {
      throw new ConflictException("Delete/Change conflict for " + id);
    }
    TreeCursor<C, K, T> baseCursor = new TreeCursor<C, K, T>(tb, delBase);
    TreeCursor<C, K, T> branchCursor = new TreeCursor<C, K, T>(t, delBranch);
    do {
      AbstractTreeNode<?, ?, ?> baseNode = baseCursor.next();
      AbstractTreeNode<?, ?, ?> branchNode = branchCursor.next();
      if (!baseNode.getId().equals(branchNode.getId())) {
        // Nodes in base and changed tree do not align
        if (hasNode(tb, branchNode)) {
          throw new ConflictException(String.format("Moved node %s in deleted subtree", 
              String.valueOf(branchNode.getId())));          
        } else {
          throw new ConflictException(String.format("Inserted node %s in deleted subtree", 
              String.valueOf(branchNode.getId())));          
        }
      }
      // Recurse for any nodes that are still deleted
      if (baseNode != END_OF_SEQUENCE && !hasNode(otherT, baseNode)) {
        checkDeletedSubtree(notSentinel(baseNode), tb, t, otherT);
      }
    } while (baseCursor.hasNext() && branchCursor.hasNext()); 
    // N.B. EOS symbols catch changes at end of list in the loop above
  }
  
  /** 
   * Set handler for conflicts detected during merge.
   * 
   * @param aCh new conflict handler
   */
  public void setConflictHandler(ConflictHandler<C, K, T> aCh) {
    this.conflictHandler = aCh; 
  }

  /** 
   * Set handler for edits detected during merge. 
   * 
   * @param aEh
   */
  public void setEditHandler(EditHandler<? super C, ? super K> aEh) {
    this.editHandler = aEh;
  }


  @SuppressWarnings("unchecked")
  private final <K, T extends AddressableTree<?, K, ?>> boolean hasNode(T t,
      AbstractTreeNode<?, ?, ?> n) {
    if (n == DELETIA || n == END_OF_SEQUENCE || n == StartOfSequenceNode.INSTANCE) {
      return false;
    }
    // Cast is safe, because we eliminated DELETIA / EOS
    return n != null && t.getNode((K) n.getId()) != null;
  }

  /**
   * Return existing children of node, unless node is a sentinel node, in which
   * case the empty list is returned.  
   * @param n node  
   * @return child list of n, or the empty iterable for sentinel nodes.
   */
  @SuppressWarnings("unchecked")
  private Iterable<AbstractTreeNode<C, K, ?>> sentinelSafeChildren(AbstractTreeNode<?, ?, ?> n) {
    if (n instanceof SentinelNode) {
      return ImmutableList.of();
    } else {
      return (Iterable<AbstractTreeNode<C, K, ?>>) n.children();
    }
  }
  
  @SuppressWarnings({"unchecked"})
  private final AbstractTreeNode<C, K, ? extends AbstractTreeNode<C, K, ?>> 
    notSentinel(AbstractTreeNode n) {
    if (n instanceof SentinelNode) {
      throw new IllegalStateException("Internal error: sentinel node not allowed here");      
    }
    return n;
  }

  @SuppressWarnings({"unchecked"})
  private final AbstractTreeNode<C, K, ? extends AbstractTreeNode<C, K, ?>> 
    nullSentinel(AbstractTreeNode n) {
    if (n instanceof SentinelNode) {
      return null; 
    }
    return n;
  }
 
  /** 
   * Cursor for child list traversal used by the merge algorithm. The cursor allows iteration 
   * over children of a node, and seeking the cursor to any node in its tree. Child list iteration
   * has the following special properties:
   * <ul>
   *  <li>The last node of any child list (in particular, empty ones) is 
   *  {@link TreeMerger#END_OF_SEQUENCE END_OF_SEQUENCE}.
   *  <li>The next node of {@link TreeMerger#DELETIA DELETIA} is 
   *    {@link TreeMerger#DELETIA DELETIA}.
   * </ul>
   */
  public static class TreeCursor<C, K, T extends AddressableTree<C, K, 
      ? extends AbstractTreeNode<C, K, ?>>> implements Iterator<AbstractTreeNode<?, ?, ?>> {

    private T tree;
    private AbstractTreeNode<?, ?, ?> currentNode; // Node to return on call to next()
    private AbstractTreeNode<?, ?, ?> previousNode = null; // Last node returned on next()
    private boolean exhausted = false;
    private Iterator<AbstractTreeNode<?, ?, ?>> nodes; // Cursor is positioned at first child of n
    
    @SuppressWarnings("unchecked")
    protected TreeCursor(T tree, AbstractTreeNode<?, ?, ?> n) {
      this.tree = tree;
      nodes = (Iterator<AbstractTreeNode<?, ?, ?>>) n.children().iterator();
      if (n == DELETIA) {
        this.currentNode = DELETIA;
      } else if (!nodes.hasNext()) {        
        this.currentNode = null;
      } else {
        this.currentNode = nodes.next(); 
      }
    }

    @Override
    public boolean hasNext() {
      return !exhausted;
    }

    @Override
    public AbstractTreeNode<?, ?, ?> next() {
      if (currentNode == DELETIA) {
        previousNode = currentNode;
      } else if (currentNode != null) {
        AbstractTreeNode<?, ?, ?> m = currentNode;
        currentNode = nodes.hasNext() ? nodes.next() : null;
        previousNode = m;
      } else if (!exhausted) {
        exhausted = true;
        previousNode = END_OF_SEQUENCE;
      } else {
        throw new NoSuchElementException();
      }
      return previousNode;
    }

    /** 
     * Seek to a node in the tree. After seek, the node following the target node <code>m</code> 
     * will be the first node returned by next. 
     * Seeking to {@link TreeMerger#DELETIA DELETIA} and  
     * {@link TreeMerger#END_OF_SEQUENCE END_OF_SEQUENCE} is allowed.
     * 
     * @param m Node to seek to.
     * @return m
     */
    @SuppressWarnings("unchecked")
    public AbstractTreeNode<?, ?, ?> seek(AbstractTreeNode<?, ?, ?> m) {
      if (m != currentNode && (m == DELETIA || m == END_OF_SEQUENCE)) {
        // Handle seek to DEL or EOS
        currentNode = m == DELETIA ? m : null;
      } else if (m == previousNode 
          || (previousNode != null && m.getId().equals(previousNode.getId()))) {
        // Handle NOP seek
        return previousNode; 
      }
      // Handle other seeks. Cast is safe, because DEL/EOS seeks already handled
      K mKey = (K) m.getId();
      currentNode = tree.getNode(mKey);
      currentNode = currentNode == null ? DELETIA : currentNode;
      // Seek child iterator to correct position
      // TODO: Ideally, we should provide a seekable iterator etc. to avoid
      // scanning child lists when moves occur
      if (currentNode.getParent() != null) {
        nodes = (Iterator<AbstractTreeNode<?, ?, ?>>) 
            currentNode.getParent().children().iterator();
        do {
          currentNode = nodes.next();
        } while (!mKey.equals(currentNode.getId()));
      }
      exhausted = false;
      return next();
    }
    
    /**
     *  Remove node at current position. <b>Not supported.</b>
     */   
    public void remove() {
      throw new UnsupportedOperationException();
    }
    
  }

  /**
   * Interface for three-way merger of node content. Should return merged
   * content. May be called when no merge is needed, e.g., when all nodes equal,
   * or just one is modified.
   *  
   * @param <C> Content type
   */
  public interface NodeMerger<C> {
    C mergeContent(C base, C changeA, C changeB) throws ConflictException;
    
    boolean nodeEquals(C c1, C c2);
    
  }

  /**
   * Null content merger. Simply generates a conflict if content is changed
   * in both branches.
   * 
   * @param <C> Content type
   */
  public static class NullNodeMerger<C> implements NodeMerger<C> {

    private Comparator<C> compare;
    
    public NullNodeMerger(Comparator<C> compare) {
      this.compare = compare;
    }

    @Override
    public C mergeContent(C base, C changeA, C changeB) throws ConflictException {
      boolean changeInA = compare.compare(base, changeA) != 0;
      boolean changeInB = compare.compare(base, changeB) != 0;
      if (changeInA && changeInB) {
        throw new ConflictException();
      } else if (changeInA) {
        return changeA;
      } else if (changeInB) {
        return changeB;
      }
      return base;
    }

    @Override
    public boolean nodeEquals(C c1, C c2) {
      return compare.compare(c1, c2) == 0;
    }
    
  }
  
  private abstract static class SentinelNode implements 
    AbstractTreeNode<Object, Object, SentinelNode> {

    @Override
    public Iterable<SentinelNode> children() {
      return ImmutableList.of();
    }

    @Override
    public Object getContent() {
      return null;
    }

    @Override
    public SentinelNode getParent() {
      return null;
    }

  }
  
  private static class DeletiaNode extends SentinelNode {
    
    public static final DeletiaNode INSTANCE = new DeletiaNode();
    
    @Override
    public Object getId() {
      // Id with 128 random bits to ensure no collision with input tree ids 
      return "DEL-\u38d5\ue04c\u3b68\u0d42\u8407\u4f2b\u0dea\u343e\u773d\ue5f4";
    }

    @Override
    public String toString() {
      return "DEL()";
    }
    
  }

  private static class EndOfSequenceNode extends SentinelNode {
    
    public static final EndOfSequenceNode INSTANCE = new EndOfSequenceNode();

    @Override
    public Object getId() {
      // Id with 128 random bits to ensure no collision with input tree ids 
      return "EOS-\uaee7\u803f\uead8\ua8d9\ub3cd\u9be5\u097d\u5052\u7dcb\u7825";
    }

    @Override
    public String toString() {
      return "EOS()";
    }
    
  }
  
  private static class StartOfSequenceNode extends SentinelNode {
    
    public static final StartOfSequenceNode INSTANCE = new StartOfSequenceNode();

    @Override
    public Object getId() {      
      // Id with 128 random bits to ensure no collision with input tree ids 
      return "SOS-\u4a7d\u27ba\u1371\u4788\u9c17\uf29e\ud433\u89b6";
    }

    @Override
    public String toString() {
      return "SOS()";
    }
    
  }
  
  /** 
   * Holder object for nodes.
   */

  public static class NodeHolder<C, K> {

    private AbstractTreeNode<C, K, ?> n;

    NodeHolder(AbstractTreeNode<C, K, ?> n) {
      this.n = n;
    }

    /** 
     * Get node.
     * 
     * @return node.
     */
    public AbstractTreeNode<C, K, ?> getNode() {
      return n;
    }
 
    /** 
     * Set node.
     * 
     * @param an
     */
    public void setNode(AbstractTreeNode<C, K, ?> an) {
      this.n = an;
    }
  
    /**
     * Create new holder for normal nodes. Must not be called with DELETIA
     * or END_OF_SEQUENCE nodes.
     * @param <C>
     * @param <K>
     * @param n
     * @return new node
     */
    static <C, K> NodeHolder<C, K> newHolder(AbstractTreeNode<C, K, ?> n) {
      return new NodeHolder<C, K>(n);
    }
    
  }
  
  /**
   * Container for arguments used when recursing in 
   * {@link TreeMerger#merge}.
   */
  private static final class MergeCallArguments {
    
    private AbstractTreeNode<?, ?, ?> n0, n1, n2;

    public MergeCallArguments(AbstractTreeNode<?, ?, ?> n0, AbstractTreeNode<?, ?, ?> n1,
        AbstractTreeNode<?, ?, ?> n2) {
      super();
      this.n0 = n0;
      this.n1 = n1;
      this.n2 = n2;
    }

    public AbstractTreeNode<?, ?, ?> getN0() {
      return n0;
    }

    public AbstractTreeNode<?, ?, ?> getN1() {
      return n1;
    }

    public AbstractTreeNode<?, ?, ?> getN2() {
      return n2;
    }
    
  }
  
  private static final class NullEditHandler implements EditHandler<Object, Object> {

    @Override
    public void delete(Object id, SourceTree tree) {
      // Deliberately empty      
    }

    @Override
    public void insert(AbstractTreeNode<? extends Object, ? extends Object, 
        ? extends AbstractTreeNode<? extends Object, ? extends Object, ?>> subTree,
        Object parentId, int pos, SourceTree tree) {
      // Deliberately empty            
    }

    @Override
    public void move(Object id, Object parentId, int pos, SourceTree tree) {
      // Deliberately empty      
    }

    @Override
    public void update(Object content, Object id, SourceTree tree) {
      // Deliberately empty      
    }

  }

  private static final class NullConflictHandler<C, K, T extends 
    AddressableTree<C, K, ? extends AbstractTreeNode<?, ?, ?>>> 
    implements ConflictHandler<C, K, T> {

    @Override
    public C collidingContent(C content1, C content2, K id) throws ConflictException {
      throw new ConflictException();
    }

    @Override
    public void collidingNode(NodeHolder<C, K> n1, NodeHolder<C, K> n2, TreeCursor<?, ?, T> c1, 
        TreeCursor<?, ?, T> c2) throws ConflictException {
      throw new ConflictException();      
    }

    @Override
    public C conflictingContent(C contentBase, C content1, C content2, K id) 
        throws ConflictException {
      throw new ConflictException();      
    }

    @Override
    public void conflictingPosition(NodeHolder<C, K> nb, NodeHolder<C, K> n1, 
        NodeHolder<C, K> n2, TreeCursor<?, ?, T> cb, TreeCursor<?, ?, T> c1,
        TreeCursor<?, ?, T> c2) throws ConflictException {
      throw new ConflictException();            
    }

  }

  /**
   * Ids of predecessor and successor of a node. 
   */
  private static class AdjacentNodeIds {

    public Object predecessorId;
    public Object successorId;

    public AdjacentNodeIds(Object predecessorId, Object successorId) {
      this.predecessorId = predecessorId;
      this.successorId = successorId;
    }
  }
}
