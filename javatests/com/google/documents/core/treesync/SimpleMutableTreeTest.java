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
 * Test the SimpleMutableTree.
 * 
 * @author tancred (Tancred Lindholm)
 *
 */
public class SimpleMutableTreeTest 
    extends MutableTreeTest<SimpleMutableTree<String, String>, String, String> {

  @Override
  public SimpleMutableTree<String, String> createEmptyTree() {
    return new SimpleMutableTree<String, String>();
  }

  @Override
  public String toContent(String s) {
    return s;
  }

  @Override
  public String toId(String s) {
    return s;
  }
}
