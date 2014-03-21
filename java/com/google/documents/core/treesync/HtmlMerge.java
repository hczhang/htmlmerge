// Copyright 2013 Google Inc. All Rights Reserved.

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
import com.google.common.io.Files;
import com.google.documents.core.treesync.MergeDebug.DumpEdits;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Sample HTML merger that uses the DomTreeMerger.
 * <p>Usage: HtmlMerge base.html one.html two.html [out.html]
 *
 * @author tancred (Tancred Lindholm)
 */
public class HtmlMerge {
  
  public static void main(String[] args) throws IOException, SAXException {
    DomTreeMerger merger = new DomTreeMerger();
    ByteArrayOutputStream editScriptBuffer = new ByteArrayOutputStream();
    merger.setEditHandler(new DumpEdits(new PrintStream(editScriptBuffer)));
    Element baseDom = HtmlUtil.removeWhitespace(HtmlUtil.parseHtml(Files.toString(new File(args[0]), UTF_8)));
    Element firstDom = HtmlUtil.removeWhitespace(HtmlUtil.parseHtml(Files.toString(new File(args[1]), UTF_8)));
    Element secondDom = HtmlUtil.removeWhitespace(HtmlUtil.parseHtml(Files.toString(new File(args[2]), UTF_8)));
    Element merged = merger.runMerge(baseDom, firstDom, secondDom);
    System.out.println("Edit script:");
    System.out.write(editScriptBuffer.toByteArray());
    String mergedHtml = HtmlUtil.serialize(merged); 
    if (args.length > 3 && !Strings.isNullOrEmpty(args[3])) {
      Files.write(mergedHtml, new File(args[3]), UTF_8);
    } else {
      System.out.println(mergedHtml);
    }
  }    
}
