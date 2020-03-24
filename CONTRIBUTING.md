# Contributing

Please see [the wiki][wiki] for more information regarding development.

## Building

The project includes a maven configuration file using the tycho plug-in, which is part of the [maven eclipse plugin](http://www.eclipse.org/m2e/). Running `mvn install` will install *most* dependencies. Note that if you are not using maven, this plugin depends on https://github.com/khatchad/edu.cuny.citytech.refactoring.common, the **Eclipse SDK**, **Eclipse SDK tests**, the **Eclipse testing framework** (may also be called the **Eclipse Test Framework**), and [Metrics](http://metrics2.sourceforge.net). Some of these can be installed from the "Install New Software..." menu option under "Help" in Eclipse.

## Generating Entry Points Files

Each time we run the evaluation, a text file is generated in the working directory. Then, before the next time you run the evaluation on the same project, move or copy `entry_points.txt` into project directory or workspace directory of the project. While evaluating the project, if the file exists, the tool will ignore the explicit entry points that are added manually and recognize the explicit entry points through the file only.

## Dependencies

You should have the following projects in your workspace:

1. [WALA stream branch](https://github.com/ponder-lab/WALA/tree/streams). Though, not all projecst are necessary. You can close thee ones related to JavaScript and Android.
1. [SAFE](https://github.com/tech-srl/safe).
1. [Common Eclipse Java Refactoring Framework](https://github.com/ponder-lab/Common-Eclipse-Java-Refactoring-Framework).

It's also possible just to use `mvn install` if you do not intend on changing any of the dependencies.

## Running the Evaluator

### Configuring the Evaluation

A file named `eval.properties` can be placed at the project root. The following keys are available:

Key              | Value Type | Description
---------------- | ---------- | ----------
nToUseForStreams | Integer    | The value of N to use while building the nCFA for stream types.

More info can be found on [this wiki page](https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/wiki/Running-the-Evaluator).

[wiki]: https://github.com/ponder-lab/Optimize-Java-8-Streams-Refactoring/wiki
[annotations]: https://github.com/ponder-lab/edu.cuny.hunter.streamrefactoring.annotations
[wala]: https://github.com/wala/WALA
[safe]: https://github.com/tech-srl/safe
