package p;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
		Collection<Widget> unorderedWidgets = new HashSet<>();

		Map<Color, List<Widget>> widgetsByColor = unorderedWidgets.stream()
				.collect(Collectors.groupingBy(Widget::getColor));
	}
}
