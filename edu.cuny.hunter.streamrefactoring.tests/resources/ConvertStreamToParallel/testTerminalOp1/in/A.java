package p;

import java.util.Collection;
import java.util.HashSet;

public class A {
	
	public static class Widget {
		
		enum Color {RED, BLUE, GREEN, /*...*/};
		
		private Color color;
		private double weight;
		
		public Widget (Color color, double weight) {
			this.color = color;
			this.weight = weight;
		}
		
		public Color getColor() {return this.color;}
		public double getWeight() {return this.weight;}

	}
	
	void m() {
		Collection<Widget> collection1 = new HashSet<>();
		Collection<Widget> collection2 = new HashSet<>();
		collection1.stream().count();
		collection2.stream();
	}
}