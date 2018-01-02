package p;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import edu.cuny.hunter.streamrefactoring.annotations.*;
import p.A.Widget.Color;

public class A {

	static class Widget {
		enum Color {
			RED, BLUE, GREEN, PINK, ORANGE, YELLOW
		};

		public Color getColor() {
			return this.getColor();
		}
	}

	@EntryPoint
	void m() {
		Collection<Widget> orderedWidgets = new ArrayList<>();
		Map<Color, Set<Widget>> widgetsByColor = orderedWidgets.stream().collect(Collectors.groupingByConcurrent(
				Widget::getColor, ConcurrentHashMap::new, Collectors.toCollection(LinkedHashSet::new)));
	}
}
