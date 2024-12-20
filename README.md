# Optimize Java 8 Streams Refactoring

[![Build Status](https://app.travis-ci.com/ponder-lab/Optimize-Java-8-Streams-Refactoring.svg?branch=master)](https://travis-ci.com/ponder-lab/Optimize-Java-8-Streams-Refactoring) [![Coverage Status](https://coveralls.io/repos/github/ponder-lab/Optimize-Java-8-Streams-Refactoring/badge.svg?branch=master)](https://coveralls.io/github/ponder-lab/Optimize-Java-8-Streams-Refactoring?branch=master) [![GitHub license](https://img.shields.io/badge/license-Eclipse-blue.svg)](https://github.com/khatchadourian-lab/Java-8-Stream-Refactoring/raw/master/LICENSE.txt) [![DOI](https://zenodo.org/badge/78147265.svg)](https://zenodo.org/badge/latestdoi/78147265) [![Java profiler](https://www.ej-technologies.com/images/product_banners/jprofiler_small.png)](https://www.ej-technologies.com/products/jprofiler/overview.html)

## Introduction

<img src="https://raw.githubusercontent.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/master/edu.cuny.hunter.streamrefactoring.ui/icons/icon.png" alt="Icon" align="left" height=150px width=150px/> The Java 8 Stream API sets forth a promising new programming model that incorporates functional-like, MapReduce-style features into a mainstream programming language. However, using streams efficiently may involve subtle considerations.

This tool consists of automated refactoring research prototype plug-ins for [Eclipse][eclipse] that assists developers in writing optimal stream client code in a semantics-preserving fashion. Refactoring preconditions and transformations for automatically determining when it is safe and possibly advantageous to convert a sequential stream to parallel and improve upon already parallel streams are included. The approach utilizes both [WALA][wala] and [SAFE][safe].

## Screenshot

![Screenshot](http://i2.wp.com/khatchad.commons.gc.cuny.edu/files/2018/03/Screenshot-from-2018-04-28-17-34-53.png)

## Demonstration

(click to view)

[![Video demo of refactoring tool](http://img.youtube.com/vi/YaSYH7n6y5s/0.jpg)](http://www.youtube.com/watch?v=YaSYH7n6y5s "Optimize Java 8 Stream Refactoring Tool Demonstration")

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

The latest release is [v0.18.0][v0.18.0]. It has been tested on Eclipse IDE for RCP and RAP Developers Version: 2019-03 (4.11.0), Build id: 20190314-1200 under OpenJDK Runtime Environment (build 1.8.0_212-8u212-b03-0ubuntu1.19.04.2-b03) and OpenJDK 64-Bit Server VM (build 25.212-b03, mixed mode) on Ubuntu 19.04.

[This video][install] demonstrates the different ways that this tool can be installed.

### Update Site

An alpha version of our tool is available via an Eclipse update site at: https://raw.githubusercontent.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/master/edu.cuny.hunter.streamrefactoring.updatesite. Please choose the latest version of the "Optimize Stream Refactoring."

### Eclipse Marketplace
 
You may also install the tool via the [Eclipse Marketplace](https://marketplace.eclipse.org/content/optimize-java-8-streams-refactoring) by dragging this icon to your running Eclipse workspace: [![Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client](https://marketplace.eclipse.org/sites/all/themes/solstice/public/images/marketplace/btn-install.png)](http://marketplace.eclipse.org/marketplace-client-intro?mpc_install=4056021 "Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client")

### Dependencies

The refactoring has several dependencies as listed below. If you experience any trouble installing the plug-in using the above update site, you can manually install the dependencies. The latest version of the plug-ins should be installed. If installing SAFE, note that WALA *must* be installed first:

Dependency | Update Site
--- | ---
[WALA](https://github.com/ponder-lab/WALA/tree/streams) | https://raw.githubusercontent.com/ponder-lab/WALA/streams/com.ibm.wala.updatesite
[SAFE](https://github.com/tech-srl/safe) | https://raw.githubusercontent.com/tech-srl/safe/master/com.ibm.safe.updatesite
[Common Eclipse Java Refactoring Framework](https://github.com/ponder-lab/Common-Eclipse-Java-Refactoring-Framework) | https://raw.githubusercontent.com/ponder-lab/Common-Eclipse-Java-Refactoring-Framework/master/edu.cuny.citytech.refactoring.common.updatesite

#### WALA

Please note that there is a special dependency on WALA. Currently, our refactoring requires **WALA version 1.3.10**. Although the version from the official WALA site would theoretically work, the plug-in has been tested with the WALA version whose update site is listed above. We highly recommend that this version of WALA be used with the plug-in, which may require uninstalling other WALA features from your current Eclipse installation. [Issue #192](https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/issues/192) has been opened to track the future integration.

## Marking Entry Points

Explicit entry points may be marked using the appropriate annotation found in the corresponding [annotation library][annotations]. They can also be marked using a text file named `entry_points.txt`. The processing of this file is recursive; it will search for this file in the same directory as the source code and will traverse up the directory structure until one is found. As such, the file may be placed in, for example, package directories, subproject directories, and project roots. The format of the file is simply a list of method signatures on each line.

[This video][entrypoints] explains more details on how entry points can be specified.

<!-- It is also possible to have the tool generate the file from the entry points that are being used (either implicit or explicit entry points). If enabled, the file will appear in the working directory. -->

## Limitations

There are currently some limitations with embedded streams (i.e., streams declared as part of lambda expressions sent as arguments to intermediate stream operations). This is due to model differences between the Eclipse JDT and WALA. See [#155](https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/155) for details.

In general, there is [an issue](https://github.com/wala/WALA/issues/2/edit81) with the mapping between the Eclipse DOM and WALA DOM, particularly when using Anonymous Inner Classes (AICs). We are currently working with the WALA developers to resolve [this issue](https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/155).

## Contributing

For information on contributing, see [CONTRIBUTING.md][contrib].

## Engineering Challenges and Solutions

[This wiki page][challenges] highlights the locations in our code that solve several engineering challenges outlined in our [SCAM 2018] [paper].

## Further Information

See the [wiki][wiki] for further information.

## Publications

<a name="Khatchadourian2019"></a>Raffi Khatchadourian, Yiming Tang, Mehdi Bagherzadeh, and Syed Ahmed. Safe automated refactoring for intelligent parallelization of Java 8 streams. In <em>International Conference on Software Engineering</em>, ICSE '19, pages 619--630, Piscataway, NJ, USA, May 2019. ACM/IEEE, IEEE Press. [ <a href="http://www.cs.hunter.cuny.edu/~Raffi.Khatchadourian99/all_bib.html#Khatchadourian2019">bib</a> | <a href="http://dx.doi.org/10.1109/ICSE.2019.00072">DOI</a> | <a href="http://www.slideshare.net/khatchad/safe-automated-refactoring-for-intelligent-parallelization-of-java-8-streams">slides</a> | <a href="http://academicworks.cuny.edu/hc_pubs/489">http</a> ]

<a name="Khatchadourian2018b"></a>Raffi Khatchadourian, Yiming Tang, Mehdi Bagherzadeh, and Syed Ahmed. A tool for optimizing Java 8 stream software via automated refactoring. In <em>International Working Conference on Source Code Analysis and Manipulation</em>, SCAM '18, pages 34--39. IEEE, IEEE Press, September 2018. Engineering Track. [ <a href="http://www.cs.hunter.cuny.edu/~Raffi.Khatchadourian99/all_bib.html#Khatchadourian2018b">bib</a> | <a href="http://dx.doi.org/10.1109/SCAM.2018.00011">DOI</a> | <a href="http://www.slideshare.net/khatchad/a-tool-for-optimizing-java-8-stream-softwarevia-automated-refactoring">slides</a> | <a href="http://academicworks.cuny.edu/hc_pubs/429">http</a> ]

## Citation

Please cite this work as follows:

```bibtex
@InProceedings{Khatchadourian2019,
  author       = {Raffi Khatchadourian and Yiming Tang and Mehdi Bagherzadeh and Syed Ahmed},
  booktitle    = {International Conference on Software Engineering},
  title        = {Safe Automated Refactoring for Intelligent Parallelization of {Java} 8 Streams},
  year         = {2019},
  address      = {Piscataway, NJ, USA},
  month        = may,
  organization = {ACM/IEEE},
  pages        = {619--630},
  publisher    = {{IEEE}},
  series       = {ICSE '19},
  acmid        = {3339586},
  doi          = {10.1109/icse.2019.00072},
  keywords     = {Java 8, automatic parallelization, refactoring, static analysis, streams, typestate analysis},
  location     = {Montr\'eal, QC, Canada},
  numpages     = {12},
  url          = {http://academicworks.cuny.edu/hc_pubs/489},
}

@InProceedings{Khatchadourian2018,
  author       = {Raffi Khatchadourian and Yiming Tang and Mehdi Bagherzadeh and Syed Ahmed},
  booktitle    = {International Working Conference on Source Code Analysis and Manipulation},
  title        = {A Tool for Optimizing {Java} 8 Stream Software via Automated Refactoring},
  year         = {2018},
  month        = sep,
  note         = {Engineering Track.},
  organization = {IEEE},
  pages        = {34--39},
  publisher    = {IEEE Press},
  series       = {IEEE SCAM '18},
  doi          = {10.1109/SCAM.2018.00011},
  issn         = {2470-6892},
  keywords     = {refactoring, automatic parallelization, typestate analysis, ordering, Java 8, streams, eclipse, WALA, SAFE},
  location     = {Madrid, Spain},
  numpages     = {6},
  url          = {http://academicworks.cuny.edu/hc_pubs/429},
}
```

[wiki]: https://github.com/ponder-lab/Java-8-Stream-Refactoring/wiki
[challenges]: https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/wiki/Solutions-to-Engineering-Challenges
[annotations]: https://github.com/ponder-lab/edu.cuny.hunter.streamrefactoring.annotations
[eclipse]: http://eclipse.org
[wala]: https://github.com/wala/WALA
[safe]: https://github.com/tech-srl/safe
[contrib]: https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/blob/master/CONTRIBUTING.md
[install]: https://youtu.be/On4xBzvFk1c
[entrypoints]: https://youtu.be/On4xBzvFk1c?t=2m6s
[SCAM 2018]: http://www.ieee-scam.org/2018
[paper]: https://khatchad.commons.gc.cuny.edu/research/publications/#Khatchadourian2018b
[v0.18.0]: https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/releases/tag/v0.18.0
