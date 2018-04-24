# Optimize Java 8 Streams Refactorings

[![Build Status](https://travis-ci.com/ponder-lab/Java-8-Stream-Refactoring.svg?token=ysqq4ZuxzD688KNytWSA&branch=master)](https://travis-ci.com/ponder-lab/Java-8-Stream-Refactoring) [![Coverage Status](https://coveralls.io/repos/github/ponder-lab/Java-8-Stream-Refactoring/badge.svg?branch=master&t=mM9zgy)](https://coveralls.io/github/ponder-lab/Java-8-Stream-Refactoring?branch=master) [![GitHub license](https://img.shields.io/badge/license-Eclipse-blue.svg)](https://github.com/khatchadourian-lab/Java-8-Stream-Refactoring/raw/master/LICENSE.txt) [![DOI](https://zenodo.org/badge/78147265.svg)](https://zenodo.org/badge/latestdoi/78147265)

<!-- TODO: ## Screenshot -->

## Introduction

<img src="https://upload.wikimedia.org/wikipedia/commons/thumb/0/01/Created_with_Matplotlib-logo.svg/128px-Created_with_Matplotlib-logo.svg.png" alt="Created with Matplotlib" align="left"/> The Java 8 Stream API sets forth a promising new programming model that incorporates functional-like, MapReduce-style features into a mainstream programming language. However, using streams efficiently may involve subtle considerations. 

This tool consists of automated refactoring research prototype plug-ins for [Eclipse](http://eclipse.org) that assists developers in writing optimal stream client code in a semantics-preserving fashion. Refactoring preconditions and transformations for automatically determining when it is safe and possibly advantageous to convert a sequential stream to parallel and improve upon already parallel streams are included. The approach utilizes both [WALA][wala] and [SAFE][safe].

## Usage

### Installation

An alpha version of our tool is available via an Eclipse update site at: https://raw.githubusercontent.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/master/edu.cuny.hunter.streamrefactoring.updatesite. Please choose the latest version of the "Optimize Stream Refactoring."

#### Dependencies

The refactoring has several dependencies whose update sites do not seem to always resolve correctly in Eclipse. If you experience any trouble installing the plug-in using the above update site, you can manually install the dependencies using the list of update sites below. The latest version of each plug-in should be installed in the order they are presented:

Dependency | Update Site
--- | ---
[WALA](https://github.com/ponder-lab/WALA) | https://raw.githubusercontent.com/ponder-lab/WALA/master/com.ibm.wala.updatesite
[SAFE](https://github.com/ponder-lab/safe) | https://raw.githubusercontent.com/ponder-lab/safe/master/com.ibm.safe.updatesite
[Common Eclipse Java Refactoring Framework](https://github.com/ponder-lab/Common-Eclipse-Java-Refactoring-Framework) | https://raw.githubusercontent.com/ponder-lab/Common-Eclipse-Java-Refactoring-Framework/master/edu.cuny.citytech.refactoring.common.updatesite

### Marking Entry Points

Explicit entry points may be marked using the appropriate annotation found in the corresponding [annotation library][annotations]. They can also be marked using a text file named `entry_points.txt`. The processing of this file is recursive; it will search for this file in the same directory as the source code and will traverse up the directory structure until one is found. As such, the file may be placed in, for example, package directories, subproject directories, and project roots. The format of the file is simply a list of method signatures on each line.

<!-- It is also possible to have the tool generate the file from the entry points that are being used (either implicit or explicit entry points). If enabled, the file will appear in the working directory. -->

### Limitations

There are currently some limitations with embedded streams (i.e., streams declared as part of lambda expressions sent as arguments to intermediate stream operations). This is due to model differences between the Eclipse JDT and WALA. See [#155](https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/155) for details.

In general, there is [an issue](https://github.com/wala/WALA/issues/281) with the mapping between the Eclipse DOM and WALA DOM, particuarly when using Anonymous Inner Classes (AICs). We are currently working with the WALA developers to resolve [this issue](https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/155).

## Further Information

See the [wiki][wiki] for further information. For information on contributing, see [CONTRIBUTING.md][contrib].

[wiki]: https://github.com/ponder-lab/Java-8-Stream-Refactoring/wiki
[annotations]: https://github.com/ponder-lab/edu.cuny.hunter.streamrefactoring.annotations
[wala]: https://github.com/wala/WALA
[safe]: https://github.com/tech-srl/safe
[contrib]: https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/blob/master/CONTRIBUTING.md
