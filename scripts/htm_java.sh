#!/bin/bash
git checkout master
./gradlew check
cd build/reports
rm -rf jmh_orig
mv jmh jmh_orig
cd -
git checkout optimized_streams
./gradlew check
cd build/reports
rm -rf jmh_refact
mv jmh jmh_refact
cd -
diff build/reports/jmh_orig/human.txt build/reports/jmh_refact/human.txt
ls -l build/reports/jmh_orig/human.txt build/reports/jmh_refact/human.txt
git checkout master
