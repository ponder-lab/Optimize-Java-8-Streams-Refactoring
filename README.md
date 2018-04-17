# Optimize Java 8 Streams Refactorings
[![Build Status](https://travis-ci.com/ponder-lab/Java-8-Stream-Refactoring.svg?token=ysqq4ZuxzD688KNytWSA&branch=master)](https://travis-ci.com/ponder-lab/Java-8-Stream-Refactoring) [![Coverage Status](https://coveralls.io/repos/github/ponder-lab/Java-8-Stream-Refactoring/badge.svg?branch=master&t=mM9zgy)](https://coveralls.io/github/ponder-lab/Java-8-Stream-Refactoring?branch=master) [![GitHub license](https://img.shields.io/badge/license-Eclipse-blue.svg)](https://github.com/khatchadourian-lab/Java-8-Stream-Refactoring/raw/master/LICENSE.txt)

<!-- TODO: ## Screenshot -->

## Introduction

The Java 8 Stream API sets forth a promising new programming model that incorporates functional-like, MapReduce-style features into a mainstream programming language. However, using streams efficiently may involve subtle considerations. 

This tool consists of automated refactoring research prototype plug-ins for [Eclipse](http://eclipse.org) that assists developers in writing optimal stream client code in a semantics-preserving fashion. Refactoring preconditions and transformations for automatically determining when it is safe and possibly advantageous to convert a sequential stream to parallel and improve upon already parallel streams are included. The approach utilizes both [WALA](wala) and [SAFE](safe).

## Usage

<!-- TODO: ### Installation -->

### Marking Entry Points

Explicit entry points may be marked using the appropriate annotation found in the corresponding [annotation library][annotations]. They can also be marked using a text file named `entry_points.txt`. The processing of this file is recursive; it will search for this file in the same directory as the source code and will traverse up the directory structure until one is found. As such, the file may be placed in, for example, package directories, subproject directories, and project roots. The format of the file is simply a list of method signatures on each line.

<!-- It is also possible to have the tool generate the file from the entry points that are being used (either implicit or explicit entry points). If enabled, the file will appear in the working directory. -->

### Limitations

There are currently some limitations with embedded streams (i.e., streams declared as part of lambda expressions sent as arguments to intermediate stream operations). This is due to model differences between the Eclipse JDT and WALA. See [#155](https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/155) for details.

In general, there is [an issue](https://github.com/wala/WALA/issues/281) with the mapping between the Eclipse DOM and WALA DOM, particuarly when using Anonymous Inner Classes (AICs). We are currently working with the WALA developers to resolve [this issue](https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/155).

## Further Information

See the [wiki][wiki] for further information.

[wiki]: https://github.com/ponder-lab/Java-8-Stream-Refactoring/wiki
[annotations]: https://github.com/ponder-lab/edu.cuny.hunter.streamrefactoring.annotations
[wala]: https://github.com/wala/WALA
[safe]: https://github.com/tech-srl/safe
