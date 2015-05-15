# Prerequisites #

  * JDK 1.7
  * Apache Ant
  * `wget` (optional)

# Downloading and compiling #

`htmlmerge` is currently distributed in source form only.

  1. Clone the Htmlmerge Git repository as described [here](https://code.google.com/p/htmlmerge/source/checkout) or download a `.zip` snapshot [from here](https://code.google.com/p/htmlmerge/source/browse/)
  1. Change to the project root
```
    cd htmlmerge
```
  1. The set of libraries Htmlmerge depends (HTML parser, diff\_match\_patch,   Google Guava etc.) on is stored in the `contrib/` directory. The libraries are not in the Git repository, and need to be downloaded separately. The easiest way to do this is to run
```
  contrib/get_contrib.sh
```
> > If you cannot execute the script, simply download the URLs mentioned in the script manually.
  1. Build as follows (source is in `java/` and classes will be in `bin/`)
```
  ant
```
> > If you get compile errors related to `StandardCharsets` you are probably not using a recent enough JDK (1.7 or higher).

# Running tests #

There is a good set of tests in the `javatests/` directory. To run these just
```
ant test
```

# Merging HTML #

There is Ant target `merge` which you can use to merge HTML files using the included strawman HTML merge tool (i.e., this is just a library which does currently not have a full-featured frontend). The base (common ancestor) and edited files are passed as java properties `base`, `edit1` and `edit2` respectively.

Examples are included in the `examples/` directory. For instance, to run a the "simple" example which tests merging of insert, delete, and update operations, run
```
ant merge -Dbase=examples/simple/base.html -Dedit1=examples/simple/edit1.html -Dedit2=examples/simple/edit2.html
```

# Using the library #

The code is the documentation here -- for better and for worse :) To get started, look at the `com.google.documents.core.treesync.HtmlMerge` class. The main interface to the tree merging library is `com.google.documents.core.treesync.TreeMerger`. `com.google.documents.core.treesync.DomTreeMerger` is a specialization for HTML DOM trees. The merger outputs the generated editscript to an implementation of the `com.google.documents.core.treesync.EditHandler`. Similary, conflicts are procesed by a  `com.google.documents.core.treesync.ConflictHandler`

A lot can be learned from reading the tests. For merging, see `TreeMergerTest`, `DomTreeMergerTest`, `HtmlNodeMergerTest`; for edit scripts `EditScriptGeneratorTest`; and for HTML matching (when documents are not using id attributes) `TextAlignmentTreeMatcherTest`
