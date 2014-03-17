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

import org.w3c.dom.Node;

import java.math.BigInteger;
import java.util.Random;
import java.util.Set;

/**
 * Some general utilities for the Tree Merger.
 * @author tancred (Tancred Lindholm)
 */
public class Util {
  
  public static final String ID_ATTRIBUTE = "id";

  // Prefix shared by all generated ids so they can easily be recognized.
  // \u200b is "Zero width space", so it will not print.
  // TODO: Change this. It is likely to cause confusion.
  public static final String ID_PREFIX = "\u200b";
  
  private static Random random = new Random();
  
  public static String uid() {
    byte[] bytes = new byte[4];
    random.nextBytes(bytes);
    return ID_PREFIX + new BigInteger(1, bytes).toString(16);    
  }
  
  public static String uid(Set<String> existing) {
    return uid();
  }
  
  public static boolean isGeneratedId(String id) {
    return id != null && id.startsWith(ID_PREFIX);
  }
  
  abstract static class DomTraverser {
    
    /**
     * Traverse each node in the DOM.
     *
     * @param node the root node of the DOM
     */
    public final void traverse(Node node) {
      visit(node);
      if (node.hasChildNodes()) {
        Node nextChild = node.getFirstChild();
        while (nextChild != null) {
          Node thisChild = nextChild;
          nextChild = nextChild.getNextSibling();
          traverse(thisChild);
        }
      }
      postVisitNode(node);
    }

    protected abstract void visit(Node n);
    
    protected void postVisitNode(Node node) {
    }
  }
}
