#!/bin/bash
set -ev
function write_visual_bells() {
  for i in `seq 1 40`; do
    echo -en "\a"
    sleep 1m
  done
}
write_visual_bells&
mvn install
