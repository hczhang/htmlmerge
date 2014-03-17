#!/bin/bash
# Downloads necessary 3rd party contrib libraries
cd `dirname $0`
DIFF_MATCH_PATCH=diff_match_patch_20121119
wget "https://google-diff-match-patch.googlecode.com/files/${DIFF_MATCH_PATCH}.zip"
unzip -j ${DIFF_MATCH_PATCH}.zip ${DIFF_MATCH_PATCH}/java/diff_match_patch.java
wget 'https://org-json-java.googlecode.com/files/org.json-20120521.jar'
wget 'http://search.maven.org/remotecontent?filepath=com/google/guava/guava/16.0.1/guava-16.0.1.jar' -O guava-16.0.1.jar
wget 'http://search.maven.org/remotecontent?filepath=nu/validator/htmlparser/htmlparser/1.4/htmlparser-1.4.jar' -O htmlparser-1.4.jar
wget 'http://search.maven.org/remotecontent?filepath=junit/junit/4.11/junit-4.11.jar' -O junit-4.11.jar
wget 'http://search.maven.org/remotecontent?filepath=org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar' -O hamcrest-core-1.3.jar
