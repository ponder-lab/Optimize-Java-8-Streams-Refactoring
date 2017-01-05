# Migrate Skeletal Implementation to Interface Refactoring 

[![Build Status](https://travis-ci.org/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring.svg?branch=master)](https://travis-ci.org/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring) [![Coverage Status](https://coveralls.io/repos/github/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring/badge.svg?branch=master)](https://coveralls.io/github/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring?branch=master) [![GitHub license](https://img.shields.io/badge/license-Eclipse-blue.svg)](https://raw.githubusercontent.com/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring/master/LICENSE.txt)

## Screenshot

<img src="https://i2.wp.com/openlab.citytech.cuny.edu/interfacerefactoring/files/2011/06/Screen-Shot-2016-03-14-at-11.43.53-PM-e1458161353498.png?ssl=1" alt="Screenshot" width=75%/>

## Introduction

The *skeletal implementation pattern* is a software design pattern consisting of defining an abstract class that provides a partial interface implementation. However, since Java allows only single class inheritance, if implementers decide to extend a skeletal implementation, they will not be allowed to extend any other class. Also, discovering the skeletal implementation may require a global analysis. Java 8 enhanced interfaces alleviate these problems by allowing interfaces to contain (default) method implementations, which implementers inherit. Java classes are then free to extend a different class, and a separate abstract class is no longer needed; developers considering implementing an interface need only examine the interface itself. Both of these benefits improve software modularity.

This prototype refactoring plug-in for [Eclipse](http://eclipse.org) represents ongoing work in developing an automated refactoring tool that would assist developers in taking advantage of the enhanced interface feature for their legacy Java software.

## Usage

Currently, the prototype refactoring works only via the package explorer and the outline views (see [#2](https://github.com/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring/issues/2) and [#65](https://github.com/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring/issues/65)). You can either select a single method to migrate or select a class, package, or (multiple) projects. In the latter case, the tool will find methods in the enclosing item(s) that are eligible for migration.

### Installation for Usage

A beta version of our tool is available via an Eclipse update site at: https://raw.githubusercontent.com/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring/master/edu.cuny.hunter.streamrefactoring.updatesite. Please choose the latest version.

### Limitations

The research prototype refactoring is conservative. While tool should not produce any type-incorrect or semantic-inequivalent code, it may not refactor *all* code that may be safe to refactor.

## Contributing

We are currently seeking new collaborations. If you are interested in contributing, please see refer to our [wiki](https://github.com/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring/wiki) and [issues](https://github.com/khatchad/Migrate-Skeletal-Implementation-to-Interface-Refactoring/issues). Please also feel free to visit our [research page](https://openlab.citytech.cuny.edu/interfacerefactoring) to get in touch with the authors.

### Installation for Development

The project includes a maven configuration file using the tycho plug-in, which is part of the [maven eclipse plugin](http://www.eclipse.org/m2e/). Running `mvn install` will install all dependencies. Note that if you are not using maven, this plugin depends on https://github.com/khatchad/edu.cuny.citytech.refactoring.common, the **Eclipse SDK**, **Eclipse SDK tests**, and the **Eclipse testing framework**. The latter three can be installed from the "Install New Software..." menu option under "Help" in Eclipse.

### Running the Evaluator

The plug-in edu.cuny.hunter.streamrefactoring.eval is the evaluation plug-in. Note that it is not included in the standard update site as that it user focused. To run the evaluator, clone the repository and build and run the plug-in from within Eclipse. This will load the plug-in edu.cuny.hunter.streamrefactoring.eval (verify in "installation details.").

There is no UI menu options for the evaluator, however, there is an Eclipse command, which is available from the quick execution dialog in Eclipse. Please follow these steps:

1. Select a group of projects.
2. Press CMD-3 or CTRL-3 (command dialog).
3. Search for "evaluate." You'll see an option to run the migration evaluator. Choose it.
4. Once the evaluator completes, a set of `.csv` files will appear in the working directory.
