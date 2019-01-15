#!/bin/bash
set -ex
git checkout master > /dev/null
mvn clean test -fn > output_orig.txt
git checkout optimized_streams > /dev/null
mvn test -fn > output_refact.txt
diff output_orig.txt output_refact.txt || true
ls -l output_orig.txt output_refact.txt
git checkout master > /dev/null
