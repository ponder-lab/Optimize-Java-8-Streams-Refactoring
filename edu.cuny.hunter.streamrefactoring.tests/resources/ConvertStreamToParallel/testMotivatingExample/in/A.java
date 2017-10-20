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

import p.Widget.Color;

class Widget {
	public enum Color {
		RED, BLUE, GREEN
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

		Collection<Widget> orderedWidgets2 = orderedWidgets;

		// collect the first green widgets into a list.
		List<Widget> firstGreenList = orderedWidgets2.stream().filter(w -> w.getColor() == Color.GREEN).unordered()
				.limit(5).collect(Collectors.toList());

		Collection<Widget> orderedWidgets3 = orderedWidgets;

		// collect distinct widget weights into a set.
		Set<Double> distinctWeightSet = orderedWidgets3.stream().parallel().map(Widget::getWeight).distinct()
				.collect(Collectors.toCollection(TreeSet::new));

		// collect distinct widget colors into a HashSet.
		Set<Color> distinctColorSet = orderedWidgets2.parallelStream().map(Widget::getColor).distinct()
				.collect(HashSet::new, Set::add, Set::addAll);

		// collect widget colors matching a regex.
		Pattern pattern = Pattern.compile(".*e[a-z]");
		ArrayList<String> results = new ArrayList<>();

		Collection<Widget> orderedWidgets4 = orderedWidgets;

		orderedWidgets4.stream().map(w -> w.getColor()).map(c -> c.toString()).filter(s -> pattern.matcher(s).matches())
				.forEach(s -> results.add(s));
	}
}