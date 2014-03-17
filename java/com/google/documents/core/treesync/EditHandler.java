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
 * Interface for handler of edit operations. The edit operations are emitted by the
 * {@link TreeMerger tree merger} during the merge run.
 * 
 * <p>Note that the parameters of the edit operations are compatible with those used
 * to change trees trough the {@link MutableTree} interface.
 *   
 * @author tancred (Tancred Lindholm)
 *
 * @param <C> Node content type
 * @param <K> Node key type
 */
public interface EditHandler<C, K> {
  
  /**
   * Source tree of the operation. 
   */
  public enum SourceTree { NONE, FIRST, SECOND, BOTH }
  
  /** 
   * A subtree insertion occurred. 
   * 
   * @param subTree the root of the subtree to insert
   * @param parentId Id of parent node
   * @param pos position of insert in child list
   * @param tree tree originating the insert
   */
  public void insert(AbstractTreeNode<? extends C, ? extends K,
      ? extends AbstractTreeNode<? extends C, ? extends  K, ?>> subTree,
      K parentId, int pos, SourceTree tree);

  /** 
   * A subtree deletion occurred.
   * @param id root of deleted subtree
   * @param tree tree in which the delete occurred
   */
  public void delete(K id, SourceTree tree);

  /** 
   * A node content update occurred.
   * 
   * @param content Updated content
   * @param id id of updated node
   * @param tree tree in which the update occurred
   */
  public void update(C content, K id, SourceTree tree);

  /** 
   * A node move occurred.
   * @param id id of moved node
   * @param parentId new parent of node
   * @param pos target position in child list of new parent 
   * @param tree in which the move occurred
   */
  public void move(K id, K parentId, int pos, SourceTree tree);
  
}
