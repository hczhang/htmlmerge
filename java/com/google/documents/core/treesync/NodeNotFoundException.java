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
 * Exception signaling that a required node was not found.
 * 
 * @author tancred (Tancred Lindholm)
 */
public class NodeNotFoundException extends Exception {

  // Trivia: did you know that Java forbids generics in Throwable subclasses?
  // Therefore we have to settle to use Object as the id type.
  final private Object id;

  /**
   * Construct a new object.
   * 
   * @param id id of node not found
   */
  public NodeNotFoundException(Object id) {
    super("No node with key " + id);
    this.id = id;
  }

  /**
   * Get id of node not found.
   * 
   * @return id of node not found
   */
  public Object getId() {
    return id;
  }
}
