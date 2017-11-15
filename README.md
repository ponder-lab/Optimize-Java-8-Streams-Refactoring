# Java 8 Stream Optimization Refactorings
[![Build Status](https://travis-ci.com/ponder-lab/Java-8-Stream-Refactoring.svg?token=ysqq4ZuxzD688KNytWSA&branch=master)](https://travis-ci.com/ponder-lab/Java-8-Stream-Refactoring) [![Coverage Status](https://coveralls.io/repos/github/ponder-lab/Java-8-Stream-Refactoring/badge.svg?branch=master&t=mM9zgy)](https://coveralls.io/github/ponder-lab/Java-8-Stream-Refactoring?branch=master) [![GitHub license](https://img.shields.io/badge/license-Eclipse-blue.svg)](https://github.com/khatchadourian-lab/Java-8-Stream-Refactoring/raw/master/LICENSE.txt)

## Screenshot

## Introduction

This prototype refactoring plug-in for [Eclipse](http://eclipse.org) represents ongoing work in developing an automated refactoring tool that would assist developers in optimizing their Java 8 stream client code.

## Usage

### Installation for Usage

### Limitations

## Contributing

### Installation for Development

The project includes a maven configuration file using the tycho plug-in, which is part of the [maven eclipse plugin](http://www.eclipse.org/m2e/). Running `mvn install` will install *most* dependencies. Note that if you are not using maven, this plugin depends on https://github.com/khatchad/edu.cuny.citytech.refactoring.common, the **Eclipse SDK**, **Eclipse SDK tests**, and the **Eclipse testing framework**. The latter three can be installed from the "Install New Software..." menu option under "Help" in Eclipse.

### Dependencies

You should have the following projects in your workspace:

1. [WALA stream branch](https://github.com/ponder-lab/WALA/tree/streams). Though, not all projecst are necessary. You can close thee ones related to JavaScript and Android.
1. [SAFE](https://github.com/ponder-lab/safe)

### Running the Evaluator
