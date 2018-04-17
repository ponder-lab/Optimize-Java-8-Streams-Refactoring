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

## Contributing

Please see [the wiki](wiki) for more information regarding development.

### Installation

The project includes a maven configuration file using the tycho plug-in, which is part of the [maven eclipse plugin](http://www.eclipse.org/m2e/). Running `mvn install` will install *most* dependencies. Note that if you are not using maven, this plugin depends on https://github.com/khatchad/edu.cuny.citytech.refactoring.common, the **Eclipse SDK**, **Eclipse SDK tests**, and the **Eclipse testing framework**. The latter three can be installed from the "Install New Software..." menu option under "Help" in Eclipse.

### Generating Entry Points Files

Each time we run the evaluation, a text file is generated in the working directory. Then, before the next time you run the evaluation on the same project, move or copy `entry_points.txt` into project directory or workspace directory of the project. While evaluating the project, if the file exists, the tool will ignore the explicit entry points that are added manually and recognize the explicit entry points through the file only.

### Dependencies

You should have the following projects in your workspace:

1. [WALA stream branch](https://github.com/ponder-lab/WALA/tree/streams). Though, not all projecst are necessary. You can close thee ones related to JavaScript and Android.
1. [SAFE](https://github.com/ponder-lab/safe).
1. [Common Eclipse Java Refactoring Framework](https://github.com/ponder-lab/Common-Eclipse-Java-Refactoring-Framework).

It's also possible just to use `mvn install` if you do not intend on changing any of the dependencies.

### Running the Evaluator

#### Configuring the Evaluation

A file named `eval.properties` can be placed at the project root. The following keys are available:

Key              | Value Type | Description
---------------- | ---------- | ----------
nToUseForStreams | Integer    | The value of N to use while building the nCFA for stream types.

### Further Information

See the [wiki][wiki] for further information.

[wiki]: https://github.com/ponder-lab/Java-8-Stream-Refactoring/wiki
[annotations]: https://github.com/ponder-lab/edu.cuny.hunter.streamrefactoring.annotations
[wala]: https://github.com/wala/WALA
[safe]: https://github.com/tech-srl/safe
