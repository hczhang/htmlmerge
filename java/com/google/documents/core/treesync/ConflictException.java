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
 * Merge conflict.
 * 
 * @author tancred (Tancred Lindholm)
 */

// This class needs to gather useful information about conflicts
// Currently, this has not yet stabilized, but it seems we want at least
// - Nodes involved in the conflict
// - Some high-level categorization (colliding insert, colliding update, etc...)
// - Whether reason is user or programming error (e.g. inconsistent node types)

public class ConflictException extends Exception {

  /**
   * Create conflict with unspecified reason.
   */
  public ConflictException() {
    this(null);
  }

  /**
   * Create conflict with specified reason.
   * @param reason Reason for conflict, suitable for logging (but not the end user).
   */
  public ConflictException(String reason) {
    super("Merging conflict: " + (reason == null ? "(no reason)" : reason));
  }
    
}