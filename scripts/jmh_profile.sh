#!/bin/bash
set -ex

BENCHMARKS=(
    "java-design-patterns/abstract-document"
    "java-design-patterns/dao"
    "java-design-patterns/double-dispatch"
    "java-design-patterns/specification"
    "java-design-patterns/thread-pool"
)

CMD="mvn clean install -Dcheckstyle.skip -Dpmd.skip -DskipTests=true"
MACHINE_OUTPUT="jmh-results"
MACHINE_EXT="csv"
MACHINE="$MACHINE_OUTPUT.$MACHINE_EXT"
HUMAN_OUTPUT="jmh-results"
HUMAN_EXT="txt"
HUMAN="$HUMAN_OUTPUT.$HUMAN_EXT"
MACHINE_ORIG="$MACHINE_OUTPUT-orig.$MACHINE_EXT"
HUMAN_ORIG="$HUMAN_OUTPUT-orig.$HUMAN_EXT"
MACHINE_REFACT="$MACHINE_OUTPUT-refact.$MACHINE_EXT"
HUMAN_REFACT="$HUMAN_OUTPUT-refact.$HUMAN_EXT"
ORIG_BRANCH="performance"
REFACT_BRANCH="performance_optimized_streams"
RESULTS_DIR="$HOME/performance"

for b in "${BENCHMARKS[@]}"
do
    cd $b
    git checkout $ORIG_BRANCH
    git pull
    $CMD
    rm -f $MACHINE_ORIG
    rm -f $HUMAN_ORIG
    mv $MACHINE $MACHINE_ORIG
    mv $HUMAN $HUMAN_ORIG
    git checkout $REFACT_BRANCH
    git pull
    $CMD
    rm -f $MACHINE_REFACT
    rm -f $HUMAN_REFACT
    mv $MACHINE $MACHINE_REFACT
    mv $HUMAN $HUMAN_REFACT
    diff $HUMAN_ORIG $HUMAN_REFACT || true
    ls -l $HUMAN_ORIG $HUMAN_REFACT
    git checkout $ORIG_BRANCH
    mkdir -p $RESULTS_DIR/$b
    cp jmh-results-* $RESULTS_DIR/$b
    cd -
    cd $RESULTS_DIR/$b
    git add .
    git commit -m "Adding performance results for $b."
    cd -
done
