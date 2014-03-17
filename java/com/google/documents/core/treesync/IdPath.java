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
 * Manipulation methods for IdPath strings. In the IdPath node naming scheme nodes
 * are identified by an id, followed by zero or more navigation steps.
 * The steps are<p>
 *  <code>*n</code> n siblings from current node. Positive n means next siblings, 
 *      negative n previous siblings.<p>
 *  <code>#n</code> child n of current node, where 0 is the first child<p> 
 *  <code>^n</code> n:th ancestor of current node, where 1 is the first ancestor
 *      (i.e., the parent node)<p>
 * 
 * The character indicating which direction to go (sibling, absolute child, parent)
 * is known as an <i>axis</i>. An IdPath with more than zero navigation steps is said 
 * to be <i>relative</i>.
 * 
 * @author tancred (Tancred Lindholm)
 */
public class IdPath {

  private IdPath() {
    // Singleton 
  } 
  
  /**
   * Character indicating navigation along the sibling axis.
   */
  public static final char SIBLING_AXIS = '*';
  
  /**
   * Character indicating navigation along the child axis.
   */
  public static final char CHILD_AXIS = '#';

  /**
   * Character indicating navigation along the parent axis.
   */
  public static final char PARENT_AXIS = '^';

  /**
   * Test if IdPath is relative. A relative path uses any of the *, #, or ^ operators. 
   * @param idPath path to test, may not be <code>null</code>
   * 
   * @return <code>true</code> if path is relative.
   */
  public static boolean isRelativePath(String idPath) {
    for (int i = 0; i < idPath.length(); i++) {
      switch (idPath.charAt(i)) {
        case SIBLING_AXIS:
        case CHILD_AXIS:
        case PARENT_AXIS:
          return true;
      }      
    }
    return false;
  }  
}
