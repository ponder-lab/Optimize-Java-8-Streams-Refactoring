# Optimize Java 8 Streams Refactoring

[![Build Status](https://travis-ci.com/ponder-lab/Optimize-Java-8-Streams-Refactoring.svg?branch=master)](https://travis-ci.com/ponder-lab/Optimize-Java-8-Streams-Refactoring) [![Coverage Status](https://coveralls.io/repos/github/ponder-lab/Optimize-Java-8-Streams-Refactoring/badge.svg?branch=master)](https://coveralls.io/github/ponder-lab/Optimize-Java-8-Streams-Refactoring?branch=master) [![GitHub license](https://img.shields.io/badge/license-Eclipse-blue.svg)](https://github.com/khatchadourian-lab/Java-8-Stream-Refactoring/raw/master/LICENSE.txt) [![DOI](https://zenodo.org/badge/78147265.svg)](https://zenodo.org/badge/latestdoi/78147265)

## Introduction

<img src="https://raw.githubusercontent.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/master/edu.cuny.hunter.streamrefactoring.ui/icons/icon.png" alt="Icon" align="left" height=150px width=150px/> The Java 8 Stream API sets forth a promising new programming model that incorporates functional-like, MapReduce-style features into a mainstream programming language. However, using streams efficiently may involve subtle considerations. 

This tool consists of automated refactoring research prototype plug-ins for [Eclipse][eclipse] that assists developers in writing optimal stream client code in a semantics-preserving fashion. Refactoring preconditions and transformations for automatically determining when it is safe and possibly advantageous to convert a sequential stream to parallel and improve upon already parallel streams are included. The approach utilizes both [WALA][wala] and [SAFE][safe].

## Screenshot
![Screenshot](http://i2.wp.com/khatchad.commons.gc.cuny.edu/files/2018/03/Screenshot-from-2018-04-28-17-34-53.png)

## Usage

The refactoring can be run in two different ways:

1. As a command.
    1. Select a project.
    1. Select "Optimize Streams..." from the "Quick Access" dialog (CTRL-3).
1. As a menu item.
    1. Right-click on a project.
    1. Under "Refactor," choose "Optimize Streams..."

Currently, the refactoring works only via the package explorer and the outline views. You can either select a single project to optimize or select multiple projects. In each case, the tool will find streams in the enclosing projects to refactor.

## Installation

[This video][install] demonstrates the different ways that this tool can be installed.

### Update Site

An alpha version of our tool is available via an Eclipse update site at: https://raw.githubusercontent.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/master/edu.cuny.hunter.streamrefactoring.updatesite. Please choose the latest version of the "Optimize Stream Refactoring."

### Eclipse Marketplace

You may also install the tool via the [Eclipse Marketplace](https://marketplace.eclipse.org/content/optimize-java-8-streams-refactoring) by dragging this icon to your running Eclipse workspace: [![Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client](https://marketplace.eclipse.org/sites/all/themes/solstice/public/images/marketplace/btn-install.png)](http://marketplace.eclipse.org/marketplace-client-intro?mpc_install=4056021 "Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client")

### Dependencies

The refactoring has several dependencies as listed below. If you experience any trouble installing the plug-in using the above update site, you can manually install the dependencies. The latest version of the plug-ins should be installed. If installing SAFE, not that WALA must be installed first:

Dependency | Update Site
--- | ---
[WALA](https://github.com/ponder-lab/WALA) | https://raw.githubusercontent.com/ponder-lab/WALA/master/com.ibm.wala.updatesite
[SAFE](https://github.com/ponder-lab/safe) | https://raw.githubusercontent.com/ponder-lab/safe/master/com.ibm.safe.updatesite
[Common Eclipse Java Refactoring Framework](https://github.com/ponder-lab/Common-Eclipse-Java-Refactoring-Framework) | https://raw.githubusercontent.com/ponder-lab/Common-Eclipse-Java-Refactoring-Framework/master/edu.cuny.citytech.refactoring.common.updatesite

#### WALA

Please note that there is a special dependency on WALA. Currently, our refactoring requires **WALA version 1.3.10**. Although this version from the official WALA site would theoretically work, the plug-in has been tested with the WALA version whose update site is listed above. We highly recommend that this version of WALA be used with the plug-in, which may require uninstalling other WALA features from your current Eclipse installation. [Issue #192](https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/issues/192) has been opened to track the future integration.

## Marking Entry Points

Explicit entry points may be marked using the appropriate annotation found in the corresponding [annotation library][annotations]. They can also be marked using a text file named `entry_points.txt`. The processing of this file is recursive; it will search for this file in the same directory as the source code and will traverse up the directory structure until one is found. As such, the file may be placed in, for example, package directories, subproject directories, and project roots. The format of the file is simply a list of method signatures on each line.

[This video][entrypoints] explains more details on how entry points can be specified.

<!-- It is also possible to have the tool generate the file from the entry points that are being used (either implicit or explicit entry points). If enabled, the file will appear in the working directory. -->

## Limitations

There are currently some limitations with embedded streams (i.e., streams declared as part of lambda expressions sent as arguments to intermediate stream operations). This is due to model differences between the Eclipse JDT and WALA. See [#155](https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/155) for details.

In general, there is [an issue](https://github.com/wala/WALA/issues/281) with the mapping between the Eclipse DOM and WALA DOM, particuarly when using Anonymous Inner Classes (AICs). We are currently working with the WALA developers to resolve [this issue](https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/155).

## Contributing

For information on contributing, see [CONTRIBUTING.md][contrib].

## Further Information

See the [wiki][wiki] for further information.

[wiki]: https://github.com/ponder-lab/Java-8-Stream-Refactoring/wiki
[annotations]: https://github.com/ponder-lab/edu.cuny.hunter.streamrefactoring.annotations
[eclipse]: http://eclipse.org
[wala]: https://github.com/wala/WALA
[safe]: https://github.com/tech-srl/safe
[contrib]: https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/blob/master/CONTRIBUTING.md
[install]: https://youtu.be/On4xBzvFk1c
[entrypoints]: https://youtu.be/On4xBzvFk1c?t=2m6s
