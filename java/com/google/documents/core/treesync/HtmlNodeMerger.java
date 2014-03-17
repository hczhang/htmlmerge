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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.documents.core.treesync.TreeMerger.NodeMerger;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Patch;

import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class for three-way merging of HTML nodes. In three-way merge, we have a base
 * value (an HTML node in this case) and two values with changes compared to the
 * base value. The three-way merge of these is a value that incorporates the
 * changed parts from both the changed values. For instance, if
 * <pre>
 *  base = &lt;span class="foo" id="bar"&gt;
 *  val1 = &lt;span class="quux" id="bar"&gt;
 *  val2 = &lt;i class="foo" id="bar"&gt;
 * </pre>
 * then the merge is
 * <pre>
 *  merge = &lt;i class="quux" id="bar"&gt;
 * </pre>
 * 
 * @author tancred (Tancred Lindholm)
 */
public class HtmlNodeMerger implements NodeMerger<Node> {
  
  private static final Comparator<Node> comparator = new HtmlNodeComparator();

  /** 
   * Merge the content of two nodes using three-way merge.
   * 
   * @param base base node. May not be <code>null</code>.
   * @param changeA node in first branch. May not be <code>null</code>.
   * @param changeB node in second branch. May not be <code>null</code>.
   * @return merged node
   * @throws ConflictException if a conflict occurs
   */
  public Node mergeContent(Node base, Node changeA, Node changeB) throws ConflictException {
    Preconditions.checkNotNull(base);
    Preconditions.checkNotNull(changeA);
    Preconditions.checkNotNull(changeB);
    boolean changeIn1 = !nodeEquals(base, changeA);
    boolean changeIn2 = !nodeEquals(base, changeB);
    if (!changeIn1 && !changeIn2) {
      return base;
    } else if (!changeIn1 && changeIn2) {
      return changeB;
    } else if (changeIn1 && !changeIn2) {
      return changeA;
    } else if (nodeEquals(changeA, changeB)) {
      // Identical changes in both, arbitrarily use n1
      return changeA;
    } else {
      // Both nodes have changed from n0, need to merge the actual node content
      if (base instanceof Text && changeA instanceof Text && changeB instanceof Text) {
        return mergeText((Text) base, (Text) changeA, (Text) changeB);
      } else if (base instanceof Element && changeA instanceof Element
          && changeB instanceof Element) {
        return mergeTag((Element) base, (Element) changeA, (Element) changeB);
      } else {
        // Nodes of mixed types (not all Tag or Text). We can't merge this case
        throw new ConflictException("Cannot merge mixed node types (e.g. Tag and Text)");
      }
    }
  }

  /** 
   * Perform a three-way merge of tags. Any changes in n1 and n2 compared to n0
   * are included in the merged tag. The attribute ordering in the merged tag is
   * determined by the order in n0, then n1, and finally n2.
   * 
   * @param n0 base tag
   * @param n1 tag in branch 1
   * @param n2 tag in branch 2
   * @return merged tag
   * 
   * @throws ConflictException if a conflicting update is detected
   */
  private Node mergeTag(Element n0, Element n1, Element n2) throws ConflictException {
    String mergedElementName =
        threeWayMergeValue(n0.getLocalName(), n1.getLocalName(), n2.getLocalName());
    Element mergedElement = n0.getOwnerDocument().createElement(mergedElementName);
    // The general idea behind attribute list merging:
    // 1) gather all attributes from n0, n1 and n2 
    // 2) loop over the attributes, and emit the merged value to the merged 
    //    attribute list (and accounting for deletions)
    // 
    // Note 1: linked hash set to keep n0, then n1, and finally n2 ordering
    // Note 2: estimated no of elements is attributes in n0 (i.e., no change)
    // Note 3: Use hash maps to do random access into the attribute lists
    Set<Node> attributes = Sets.newLinkedHashSet(); 
    NamedNodeMap n0Attributes = n0.getAttributes();
    NamedNodeMap n1Attributes = n1.getAttributes();
    NamedNodeMap n2Attributes = n2.getAttributes();
    // Add all involved attribute names
    initAttributeMerger(attributes, n0Attributes);
    initAttributeMerger(attributes, n1Attributes);
    initAttributeMerger(attributes, n2Attributes);
    List<Node> mergedAttributes = Lists.newArrayListWithExpectedSize(attributes.size()); 
    for (Node attribute : attributes) {
      // NOTE: due to the way the maps are set up (null -> missing value),
      // a merge value of null means that the attribute is deleted
      String name = attribute.getNodeName();
      Node at0 = n0Attributes.getNamedItem(name);
      Node at1 = n1Attributes.getNamedItem(name);
      Node at2 = n2Attributes.getNamedItem(name);
      String mergedValue = threeWayMergeValue(
          at0 != null ? at0.getNodeValue() : null,
          at1 != null ? at1.getNodeValue() : null,
          at2 != null ? at2.getNodeValue() : null);
      if (mergedValue != null) {
        mergedElement.setAttribute(name, mergedValue);
      }
    }
    return mergedElement;
  }

  /**
   * Gathers the attributes of a tag to supporting data structures.
   * 
   * @param allAttributes set, to which each attribute of the tag is added
   * @param tagAttributes map of attribute names and values, to which each 
   *        attribute of the tag is added
   *                        
   * @throws ConflictException if the tag has more than one attributes with the same name
   */
  private void initAttributeMerger(Set<Node> allAttributes,
      NamedNodeMap tagAttributes) throws ConflictException {
    Set<String> seenAttributes = Sets.newHashSet();
    for (int i = 0; i < tagAttributes.getLength(); i++) {
      Node attribute = tagAttributes.item(i);
      allAttributes.add(attribute);
      if (!seenAttributes.add(attribute.getLocalName())) {
        // We do not merge repeating attributes --> throw a conflict
        throw new ConflictException("Cannot merge tag with repeated attribute");
      }
    }
  }

  /**
   * Perform a three-way-merge of three values, based on their equality to the base
   * value. That is, the value that differs from the base value gets picked. Handles
   * null inputs as meaning "not present".<p>
   * 
   * Note: we are being conservative with allowed types here to avoid problems
   * with .equals() between sub- and supertypes. 
   * (? extends E would be the less conservative signature)
   * 
   * @param v0 base value
   * @param v1 value in first branch
   * @param v2 value in second branch
   */
  private <E> E threeWayMergeValue(E v0, E v1, E v2)
      throws ConflictException {
    boolean sameUpdate = v1 == v2 || (v1 != null && v1.equals(v2));
    if (sameUpdate) {
      return v1; // Same update in both v1 & v2 --> v1 (or v2) is the merge
    } else if (v0 == null) {
      // Handle case where we start from null (and remembering we took care of 
      // identical updates)
      if (v1 == null) {
        return v2;
      } else if (v2 == null) {
        return v1;
      }
      throw new ConflictException("Diverging content inserted at same location");
    } else if (v0.equals(v1)) {
      return v2;
    } else if (v0.equals(v2)) {
      return v1;
    }
    throw new ConflictException("Conflicting updates to existing content");
  }
  
  /** 
   * Three-way merge text node using text-based diff&patch. Null arguments are not
   * allowed.
   * 
   * @param n0 base text
   * @param n1 branch 1 text
   * @param n2 branch 2 text
   */
  protected Node mergeText(Text n0, Text n1, Text n2) throws ConflictException {
    if (n0 == null || n1 == null || n2 == null) {
      throw new IllegalArgumentException("Null arguments not allowed.");
    }
    diff_match_patch diffpatch = new diff_match_patch();
    // Tune dmp to allow 20% mismatches at 0 proximity and maximum
    // shift of 500 chars (at 0% mismatches)
    diffpatch.Match_Threshold = 0.2f;
    diffpatch.Match_Distance = 2500; // This is 500.0/0.2
    diffpatch.Patch_DeleteThreshold = 0.05f; // Require 95% match on deleted areas 
    String t0 = n0.getNodeValue();
    String t1 = n1.getNodeValue();
    String t2 = n2.getNodeValue();
    // Merge using the traditional diff + patch approach. We try both ways,
    // in case one works and the other does not
    String merge = threeWayMergeString(diffpatch, t0, t1, t2);
    if (merge == null) {
      merge = threeWayMergeString(diffpatch, t0, t2, t1);
    }
    if (merge == null) {
      throw new ConflictException("Conflicting updates to text node.");
    }
    return n0.getOwnerDocument().createTextNode(merge);
  }
  
  /**
   * Perform a three-way merger of strings. Strings are merged by computing the
   * diff between t0 and t1, and then patching t2 using {@link diff_match_patch}.
   * The method succeeds if all diffs were successfully patched.
   * 
   * @param diffpatch diff_match_patch object to use for diffing and patching
   * @param t0 base text
   * @param t1 first changed text
   * @param t2 second changed text
   * @return merged string, or <code>null</code> if the merge failed.
   */
  private String threeWayMergeString(diff_match_patch diffpatch, String t0,
      String t1, String t2) {
    LinkedList<Patch> patches = diffpatch.patch_make(t0, t1);
    Object[] result = diffpatch.patch_apply(patches, t2);
    boolean allApplied = true;
    for (boolean b : (boolean[]) result[1]) {
      allApplied &= b;
    }
    if (allApplied) {
      return (String) result[0];
    }
    return null;
  }

  /** 
   * Determine if two nodes are equal. Equality is in the sense that the nodes do not 
   * contain any changes compared to each other. This is typically a looser definition of
   * equality than normally. For instance, a change of attribute order may not be regarded as 
   * a change. 
   * 
   * @param n first node
   * @param m second nose 
   * @return <code>true</code> if no changes between the two nodes
   */
  @Override
  public boolean nodeEquals(Node n, Node m) {
    return comparator.compare(n, m) == 0;
  }
    
  /**
   * TESTING ONLY: Obtain current node comparator. 
   */
  Comparator<Node> getNodeComparatorForTest() {
    return comparator;
  }

  /** 
   * Comparator for HtmlDoument Nodes. The comparator orders Nodes by class
   * in the order Tag, EndTag, Text and Comment. Inside this group the ordering
   * is determined by name/string value. Finally, for tags, ordering is based on
   * attribute list length, and if equal, on the first differing value, as determined
   * by the attribute order of the first operand. The comparator considers tags with the
   * same attributes and values, but in a different order, equal. All string comparisons 
   * are case-sensitive.
   * 
   * <p>Note that this comparator is mainly used for determining equality. Also note 
   * that the Node classes have implementations of equals() based on object identity, so
   * those cannot be used (nor fixed, because that would break existing code that relies
   * on these semantics(!)).
   * 
   */
  protected static class HtmlNodeComparator implements Comparator<Node> {

    enum OrderingClass { UNDEFINED, NULL, TAG, TEXT, COMMENT;
    
      /**
       * Determine ordering class type for a node. Each node has an associated ordering
       * class associated with it, based on which nodes are ordered. The class order is
       * the order of the elements in the OrderingClass enumeration.
       * 
       * @param n Node the obtain the ordering class for
       */
      public static OrderingClass getOrderingClass(Node n) {
        if (n == null ) {
          return NULL;
        } else if (n instanceof Element) {
          return TAG;
        } else if (n instanceof Text) {
          return TEXT;
        } else if (n instanceof Comment) {
          return COMMENT;
        }
        return UNDEFINED;
      }
    }
    
    public int compare(Node a, Node b) {
      OrderingClass at = OrderingClass.getOrderingClass(a);
      OrderingClass bt = OrderingClass.getOrderingClass(b);
      if (at == OrderingClass.UNDEFINED || bt == OrderingClass.UNDEFINED) {
        throw new IllegalArgumentException("Cannot compare objects of these types.");
      } else if (at != bt) {
        return at.compareTo(bt);
      }
      assert at == bt;
      // Same class. Order by properties inside that class
      switch (at) {
        case NULL:
          return 0; // Both are null
        case TAG:          
          Element tagA = (Element) a;
          Element tagB = (Element) b;
          int nameComp = compareString(tagA.getNodeName(), tagB.getNodeName());
          if (nameComp != 0) {
            return nameComp;
          }
          return compareAttributeLists(tagA, tagB);
        case TEXT:
          Text textA = (Text) a;
          Text textB = (Text) b;
          return compareString(textA.getNodeValue(), textB.getNodeValue()); 
        case COMMENT:
          Comment commentA = (Comment) a;
          Comment commentB = (Comment) b;
          return compareString(commentA.getNodeValue(), commentB.getNodeValue());         
      }
      assert false : "Unreachable"; // The ifs above should go trough all types in classOrder
      return -1;
    }

    /**
     * Calculate order of attribute lists. The order determined by (highest priority first).
     * <ul>
     *  <li>Length (short &lt; long), null &lt; length(0) &lt; length(1) &lt; ...
     *  <li>First common attribute that has a differing value (first=as ordered by <i>ta</i>)
     *  <li>Node which has alphabetically smallest non-common attribute name
     *      (e.g. &lt;a c=b&gt; &lt; &lt;a d=b&gt;, because c &lt; d
     * </ul>
     * @param ta first attribute list
     * @param tb second attribute list
     * @return a negative value if <i>ta</i> precedes <i>tb</i>, 0 if attribute lists identical, and
     *          a positive value if <i>ta</i> succeeds <i>tb</i> 
     *          (this is the standard "a-b" comparison result)
     */
    private int compareAttributeLists(Element ta, Element tb) {
      int cmpCountA = ta.getAttributes() == null ? -1 : ta.getAttributes().getLength();
      int cmpCountB = tb.getAttributes() == null ? -1 : tb.getAttributes().getLength();
      int attCountCmp = cmpCountA - cmpCountB;
      if (attCountCmp != 0) {            
        return attCountCmp;
      }
      // Same number of attributes. Quick check for all equal in same-order (should be common)
      // Note: same count & one null -> other is null
      if (tb.getAttributes() == null || tb.getAttributes().equals(ta.getAttributes())) {
        return 0;
      }
      // Run order-insensitive comparison
      Map<String, Node> tbAtts = new HashMap<String, Node>();
      NamedNodeMap tbSrcAtts = tb.getAttributes();
      for (int i = 0; i < tbSrcAtts.getLength(); i++) {
        Node t = tbSrcAtts.item(i);
        tbAtts.put(t.getNodeName(), t);
      }
      int diff = 0;
      Node firstNotInTb = null; // Alphabetically first that is not in Tb
      NamedNodeMap taAttrs = ta.getAttributes();
      for (int i = 0; i < taAttrs.getLength(); i++) {
        Node attrA = taAttrs.item(i);  
        Node attrB = tbAtts.remove(attrA.getNodeName());
        if (attrB == null ) {
          if (firstNotInTb == null
              || firstNotInTb.getNodeName().compareTo(attrA.getNodeName()) > 0) { 
             firstNotInTb = attrA;
          }
        } else {
          diff = compareString(attrA.getNodeValue(), attrB.getNodeValue()); 
          if (diff != 0) {
            return diff;
          }
        }
      }          
      if (firstNotInTb != null ) {
        // Compare smallest attribute name not present in the other tag to
        // attribute names not present in tn. If it is smaller than all
        // unused attributes in tmatts, tn < tm, else tm > tn. Note that it is
        // impossible that tn = tm at this point
        for (String tbAtt : tbAtts.keySet() ) {
          if (firstNotInTb.getNodeName().compareTo(tbAtt) > 0) {
            // There is a smaller attribute name in tm -> tn>tm
            return 1;
          }
        }
        return -1;
      }
      return diff;
    }

    /**
     * Compares two strings using String.compareTo(). <code>null</code> is less than 
     * any non-null string.
     * 
     * @param a first string
     * @param b second string
     * @return a negative value if <i>a</i> precedes <i>b</i>, 0 if <i>a</i> and <i>b</i> are 
     *          identical, and a positive value if <i>a</i> succeeds <i>b</i> 
     *          (this is the standard "a-b" comparison result)
     */
    private int compareString(String a, String b) {
      return a == null ? (b == null ? 0 : -1) : a.compareTo(b);
    }
  }
}
