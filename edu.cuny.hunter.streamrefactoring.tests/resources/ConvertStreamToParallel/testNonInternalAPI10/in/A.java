package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {

	static void m(Stream<Object> s) {
		s.sorted().distinct().count();
	}

	@EntryPoint
	public static void main(String[] args) {
		m(new HashSet<Object>().stream());
	}
}