package p;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.Set;

import edu.cuny.hunter.streamrefactoring.annotations.EntryPoint;

import p.A.Widget.Color;

public class A {

	static class Widget {
		enum Color {
			RED,
			BLUE,
			GREEN,
			PINK,
			ORANGE,
			YELLOW
		};

		public Color getColor() {
			return this.getColor();
		}
	}

	/**
	 * P6 in table 3
	 */
	@EntryPoint
	void m() {
		Collection<Widget> orderedWidgets = new ArrayList<>();

		Map<Color, Set<Widget>> widgetsByColor2 = orderedWidgets.stream().collect(Collectors.groupingByConcurrent(
				Widget::getColor, ConcurrentSkipListMap::new, Collectors.toCollection(LinkedHashSet::new)));
	}
}
