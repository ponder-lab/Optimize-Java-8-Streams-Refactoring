package p;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class Widget {
	public enum Color {
		RED,
		BLUE,
		GREEN
	};

	Color color;
	double weight;

	Widget(Color color, double weight) {
		this.color = color;
		this.weight = weight;
	}

	public Color getColor() {
		return color;
	}

	public double getWeight() {
		return weight;
	}
}

class A {
	void m() {
		// an "unordered" collection of widgets.
		Collection<Widget> unorderedWidgets = new HashSet<>();
		// populate the collection ...

		// sort widgets by weight.
		List<Widget> sortedWidgets = unorderedWidgets.stream().sorted(Comparator.comparing(Widget::getWeight))
				.collect(Collectors.toList());

		// an "ordered" collection of widgets.
		Collection<Widget> orderedWidgets = new ArrayList<>();
		// populate the collection ...

		// collect widget weights over 43.2 into a set in
		// parallel.
		Set<Double> heavyWidgetWeightSet = orderedWidgets.parallelStream().map(Widget::getWeight).filter(w -> w > 43.2)
				.collect(Collectors.toSet());

		// sequentially skip the first 1000 widgets and
		// collect the remaining into a list.
		List<Widget> skippedWidgetList = orderedWidgets.stream().skip(1000).collect(Collectors.toList());
	}
}