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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utility functions to help debugging of tree-based merge.
 * 
 * @author tancred (Tancred Lindholm)
 */

public class MergeDebug {
  private MergeDebug() {
    // Singleton
  }
  
  // --- Merge tracing calls
  
  private static final Map<Object, Integer> tracedepths = new WeakHashMap<Object, Integer>();
  public static final Integer TRACE_DEPTH_CUTOFF = -1; // -1 disables all tracing
  

  public static final void enter() {
    enter(Thread.currentThread());
  }
  
  public static final void exit() {
    exit(Thread.currentThread());
  }
  
  public static final void trace( String msg, Object... params ) {
    trace(Thread.currentThread(), msg, params );
  }
  
  public static final void enter(Object handle) {
    Integer depth = tracedepths.get(handle);
    depth = depth == null ? 0 : depth;
    tracedepths.put(handle, depth + 1);
  }
  
  public static final void exit(Object handle) {
    Integer depth = tracedepths.get(handle);
    depth = depth == null ? 0 : depth;
    assert depth > 0 : "Exited block not entered";
    tracedepths.put(handle, depth - 1);    
  }
  
  public static final void trace(Object handle, String msg, Object... params ) {
    Integer depth = tracedepths.get(handle);
    depth = depth == null ? 0 : depth;
    if (depth > TRACE_DEPTH_CUTOFF)
      return;
    String formattedMessage =  "" + depth + ": " + msg;
    if (params.length == 0) {
      System.out.println(formattedMessage);
    } else if (params.length == 1) {
      System.out.println(formattedMessage + ": " + params[0]);
    } else {
      System.out.println(formattedMessage + ": " + Arrays.toString(params) );
    }
    System.out.flush();
  }        

  /**
   * Edit handler that prints a trace of edit operations. 
   */
  
  public static class DumpEdits implements EditHandler<Object, Object> {

    private PrintStream ps;
    
    /** 
     * Create a new edit handler.
     * 
     * @param ps Stream to print edit operations to
     */
    
    public DumpEdits(PrintStream ps) {
      this.ps = ps;
    }

    @Override
    public void delete(Object id, SourceTree tree) {
      ps.printf("%s -> del(%s)\n", tree, id);                  
    }

    @Override
    public void insert(AbstractTreeNode<? extends Object, ? extends Object, 
        ? extends AbstractTreeNode<? extends Object, ? extends Object, ?>> subTree,
        Object id, int pos, SourceTree tree) {
      ByteArrayOutputStream contentStream = new ByteArrayOutputStream();
      dumpTree(0, new PrintStream(contentStream), subTree, true);
      ps.printf("%s -> ins(%s%s%d, %s)\n", tree, id, IdPath.CHILD_AXIS, pos,
          new String(contentStream.toByteArray(), UTF_8));
    }

    @Override
    public void move(Object id, Object parentId, int pos, SourceTree tree) {
      ps.printf("%s -> mov(%s, %s%s%d)\n", tree, id, parentId, IdPath.CHILD_AXIS, pos);
    }

    @Override
    public void update(Object content, Object id, SourceTree tree) {
      ps.printf("%s -> upd(%s, %s)\n", tree, id, content.toString());   
    }    
  }

  public static void dumpTree(int level, PrintStream ps, AbstractTreeNode node,
      String... messages) {
    dumpTree(level, ps, node, false, messages);
  }

  public static void dumpTree(int level, PrintStream ps, AbstractTreeNode node,
      boolean contentOnly, String... messages) {
    if (level == 0 && messages != null && messages.length > 0) {
      ps.printf(messages[0] + "\n",
          Lists.newArrayList(messages).subList(1, messages.length).toArray());
    }
    Object content = node.getContent();
    String header = "";
    if (!contentOnly) {
      header = String.format("%s%s(%s): ", Strings.repeat("  " , level), 
          node.getId(), content instanceof Element ? 
          ((Element) node.getContent()).getAttribute("id") : "?");
    }
    ps.printf("%s%s\n", header, content);
    for (Object c: node.children()) {
      dumpTree(level + 1, ps, (AbstractTreeNode) c, contentOnly);
    }
  }
}
