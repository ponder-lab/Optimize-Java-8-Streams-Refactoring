package p;

import java.util.*;
import java.util.stream.*;
import java.util.concurrent.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;
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

	@EntryPoint
	void m() {
		Collection<Widget> orderedWidgets = new ArrayList<>();

		Map<Color, Set<Widget>> widgetsByColor2 = orderedWidgets.stream().collect(Collectors.groupingByConcurrent(
				Widget::getColor, ConcurrentHashMap::new, Collectors.toCollection(LinkedHashSet::new)));
	}
}
