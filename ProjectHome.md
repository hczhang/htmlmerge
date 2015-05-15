# `htmlmerge` library #

This is a Java library that provides

  * three way merging of HTML or other ordered tree structure data where an ancestor and two modified documents are combined into a merged document

  * merge edit script generation, where the generated edit script transforms the common merge ancestor document into the merged document.

In addition to the common insert, update, and delete operations the library also supports moves. That is, it is able to reconcile move operations with e.g. updates to the moved HTML fragment.

To start using the library, see GettingStarted. The library implements the tree merging algorithm described in [A three-way merge for XML documents](http://dx.doi.org/10.1145/1030397.1030399) presented at DocEng 2004.

A crucial part of merging is having a good matching between the input documents, i.e., knowing which parts (HTML elements and text nodes) are the "same" across documents. For this the HTML id attribute is used: two elements are the same across documents if they have matching id attribute values. However, in practice it happens that ids are not always available, and for such cases an HTML matching algorithm based on the
[Google diff match patch](https://code.google.com/p/google-diff-match-patch/) library is used to match those elements that do not have an id.

The current status of the library is
  * core tree merging algorithms are production ready
  * edit script generation is production ready, but the generated script can be optimized
  * core HTML merging is implemented but the pre- and postprocessing needed to merge "any" HTML file is lacking. This includes whitespace normalization (unless you want to actually merge whitespace nodes), preserving indentation, DTDs, resilience to Comments, CDATA, etc.