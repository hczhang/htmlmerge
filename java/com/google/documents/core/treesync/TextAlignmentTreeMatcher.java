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

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.documents.core.treesync.Util.DomTraverser;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Heuristically match DOM trees by aligning text. This class takes two DOM
 * trees, and assigns id attribute values to the elements in the second tree so
 * that the "same" element in both trees will have the same id. The id values
 * are taken from the first tree, and thus in the first tree each element should
 * have an id attribute, and the attribute value should be unique. The first
 * tree is known as the "base" tree, and the second as the "changed" tree.
 * 
 * <p>As an example, consider the case where the base tree is<br>
 * {@code <p id=1>Hello <b id=2>world!</p>}<br>
 * and the changed tree is<br>
 * {@code <p>Hello <b>world!</p><p>Another paragraph</p>}<br>
 * 
 * After matching, the changed tree will be, e.g.,<br> 
 * {@code <p id=1>Hello <b id=2>world!</p><p id=l276>Another paragraph</p>}<br>
 * Note that the {@code <p>} and {@code <b>} tags present in the base document
 * have been assigned the same id that is in the base document, and that the new
 * {@code <p>} tag has been assigned a unique id not in the base document.
 * 
 * <p>The tree alignment algorithm is based on aligning textual representations
 * of the input tree HTML, and then using the {@link diff_match_patch
 * diff_match_patch} library to match those strings. Based on this alignment,
 * the matching of tags is then inferred. This simple heuristic should yield
 * quite good results when there is relatively much text in the input document.
 * 
 * @author tancred (Tancred Lindholm)
 */
public class TextAlignmentTreeMatcher {
  
  /**
   * Match DOM trees. Id attributes are added to the elements in the changed
   * tree so that the "same" element in the base and changed trees get the same
   * id attribute value. For a more detailed description of the tree matching,
   * see class documentation.
   * 
   * @see TextAlignmentTreeMatcher
   * @param base root of base tree
   * @param changed root of changed tree
   */
  public void match(Element base, Element changed) {
    Map<Integer, Element> baseTags = Maps.newHashMap();    
    String baseMatchText = DeTagger.deTag(base, baseTags);
    Map<Integer, Element> changedTags = Maps.newHashMap();
    String changedMatchText = DeTagger.deTag(changed, changedTags);
    diff_match_patch matcher = new diff_match_patch() {

      @Override
      public void diff_cleanupMerge(LinkedList<Diff> diffs) {
        // We do not want any diff cleanup, just the straight minimum edit
        // distance diff. Hence, we override this to a NOP method.
      }      
    };
    LinkedList<Diff> diff = matcher.diff_main(baseMatchText, changedMatchText, true);
    Function<Integer, Integer> changedToBase = 
      ChangedToBasePosition.createFromBaseToChangedDiff(diff);
    Map<Element, String> changedElementToBaseId = 
      buildElementToBaseIdMap(changedToBase, changedTags, baseTags);
    // Set ids according to matching (unmatched tags get set to empty)
    (new SetIdsFromMap(changedElementToBaseId)).traverse(changed);
  }
  
  /**
   * Build map from elements in the changed tree to ids in the base tree.
   * 
   * @param changedToBase matching function that maps positions in the 
   *    detagged change document to positions in the detagged base document
   * @param changedTags map from positions in the detagged changed document to
   *    original element present at that position
   * @param baseTags map from positions in the detagged base document to
   *    original element present at that position
   * @return a map from elements in the changed tree to ids in the base tree
   */
  private Map<Element, String> buildElementToBaseIdMap(Function<Integer, Integer> changedToBase,
      Map<Integer, Element> changedTags, Map<Integer, Element> baseTags) {
    Map<Element, String> changedElementToBaseId = Maps.newHashMap();
    for (Map.Entry<Integer, Element> changeTag : changedTags.entrySet()) {
      Integer basePosition = changedToBase.apply(changeTag.getKey());
      // Check if there is a matching position and if it is the position of an element
      if (basePosition != null && baseTags.containsKey(basePosition)) {
        String id = baseTags.get(basePosition).getAttribute(Util.ID_ATTRIBUTE);
        // Map current changed document position, if base document element has an id
        if (!Strings.isNullOrEmpty(id)) {          
          changedElementToBaseId.put(changeTag.getValue(), id);
        }
      }
    }
    return changedElementToBaseId;
  }
  
  /**
   * Class to convert DOM to a textual representation suitable for matching.
   * Also, a map between elements in the DOM and positions in the textual
   * representation is generated.
   * 
   * <p>Currently, the textual representation consists of the text nodes from the
   * input document, with special marker characters inserted where the elements
   * (opening tags) originally were. E.g., with $ denoting the marker character:<br>
   * {@code <p>Hello <b>world!</p><p>Another paragraph</p>}<br>
   * becomes<br>
   * {@code $Hello $world!$Another paragraph}<br>
   * {@code 01234567890123456789012345678901}<br>
   * {@code ..........1.........2.........3.}<br>
   * The generated map from positions to original elements
   * would contain {@code [(0,<p>),(7,<b>),(14,<p>)]}
   */
  static class DeTagger extends DomTraverser {

    /**
     * Special character used for tags. This should be some reasonably uncommon
     * character; we use the unicode replacement character #FFFD.
     */
    static final char TAG_MARKER = '\ufffd';

    /**
     * Start-of-document header to prepend to the detagged string to
     * help align the start of the document. We use the tag marker. 
     */
    static final String HEADER = String.valueOf(TAG_MARKER);

    /**
     * End-of-document footer to append to the detagged string to
     * help align the end of the document. We use the tag marker. 
     */
    static final String FOOTER = String.valueOf(TAG_MARKER);
    
    /**
     * Map from positions in the detagged string to original elements. 
     */
    private Map<Integer, Element> tagPositions;
    private StringBuilder deTagBuilder;
    Node root;
    
    private DeTagger(Map<Integer, Element> tagPositions) {
      super();
      deTagBuilder = new StringBuilder();
      this.tagPositions = tagPositions;
    }

    /**
     * Detag document. See class documentation for a description of detagging.
     * 
     * @param base root of document to detag
     * @param baseTags map to store pairs (position,original element), where
     *        position is the position of the original element in the detagged
     *        document.
     * @return detagged document
     */
    public static String deTag(Element base, Map<Integer, Element> baseTags) {
      DeTagger deTagger = new DeTagger(baseTags); 
      return deTagger.deTag(base);
    }

    /**
     * Detag document.
     * 
     * @see DeTagger#deTag(Element, Map)
     */
    private String deTag(Element base) {
      deTagBuilder.append(HEADER);
      root = base;
      traverse(base);
      deTagBuilder.append(FOOTER);
      return deTagBuilder.toString();
    }
    
    /**
     * Output text nodes and elements as marker symbols to the detagged
     * document, while updating the tag position map.
     */
    @Override
    protected void visit(Node node) {
      if (node instanceof Element && node != root) {
        tagPositions.put(deTagBuilder.length(), (Element) node);
        deTagBuilder.append(TAG_MARKER);
      } else if (node instanceof Text) {
        deTagBuilder.append(node.getNodeValue());
      }
    }
  }
  
  /**
   * Set id attributes of element according to map of matching ids.
   */
  static class SetIdsFromMap extends DomTraverser {
    
    private Map<Element, String> elementToBaseId = null;

    /**
     * Create.
     * 
     * @param elementToBaseId map from elements in the  document to values for id attributes.
     */
    public SetIdsFromMap(Map<Element, String> elementToBaseId) {
      super();
      this.elementToBaseId = elementToBaseId;
    }

    /**
     * Update element id. The new id is obtained by looking up the
     * element in the {@link #elementToBaseId} map. If the element is not
     * in the map, or the id is null, the id attribute is set to a
     * a random string. 
     */
    @Override
    protected void visit(Node node) {
      if (node instanceof Element) {
        Element element = (Element) node;
        String id = elementToBaseId.get(element);
        if (Strings.isNullOrEmpty(id)) {
          id = Util.uid();
        }
        element.setAttribute(Util.ID_ATTRIBUTE, id);
      }
    }    
  }

  /**
   * Function that maps positions in the changed document to the base document.
   */
  static class ChangedToBasePosition implements Function<Integer, Integer> {

    /**
     * Map of corresponding intervals. Let A=[alow,ahigh] and B=[blow, bhigh]
     * be two intervals of equal length. The mapping from interval A to B
     * is stored as the map entries (alow-1,x) and (ahigh,bhigh), where x is some
     * entity. A lookup using the tree map's celiningEntry() method for any 
     * number y between alow and a high will now return the interval (blow,bhigh), 
     * and the difference between y and ahigh gives the position in the interval.
     * 
     * <p>Note that when encoding consecutive intervals, the entry (alow-1,x)
     * will correspond to the high point of another interval. To limit a interval 
     * from below, a mapping (alow-1,null) is used. 
     */
    private TreeMap<Integer, Integer> changedToBaseIntervals;    
    
    private ChangedToBasePosition(TreeMap<Integer, Integer> changedToBase) {
      this.changedToBaseIntervals = changedToBase;
    }

    /**
     * Create position mapping function from diff. A diff implicitly encodes a
     * correspondence between characters in the base and changed documents. This
     * method creates an explicit mapping function with the same correspondence.
     * 
     * @param baseToChanged diff from base to changed document
     * @return position mapping function that maps positions in the changed
     *         document to positions in the base document.
     */
    public static ChangedToBasePosition createFromBaseToChangedDiff(List<Diff> baseToChanged) {     
      TreeMap<Integer, Integer> changedToBase = Maps.newTreeMap();
      int basePos = -1; // Current position in base
      int changedPos = -1; // Current position in changed
      changedToBase.put(-1, null); // Leave negative positions unmapped.
      for (Diff diff : baseToChanged) {
        int length = diff.text.length();
        switch (diff.operation) {
          case EQUAL:
            basePos += length;
            changedPos += length;
            changedToBase.put(changedPos, basePos);
          break;
          case INSERT:
            changedPos += length;
            changedToBase.put(changedPos, null); // Not matched
          break;
          case DELETE:
            basePos += length;
          break;
          default:
            // This cannot happen unless the diff_match_patch library changes
            throw new IllegalStateException("Unknwon diff operation");
        }
      }
      return new ChangedToBasePosition(changedToBase);
    }
    
    /**
     * Map position in changed document to base document position.
     * 
     * @return mapped position, or null if position has no correspondence in the
     *         base document
     */
    @Override
    public Integer apply(Integer from) {
      Entry<Integer, Integer> entry = changedToBaseIntervals.ceilingEntry(from);
      if ((entry == null) || (entry.getValue() == null)) {
        // Above highest mapped position, or position mapped to null (i.e., unmatched).
        return null;
      }
      int offset = from - entry.getKey();
      return entry.getValue() + offset;
    }    
  }
}
