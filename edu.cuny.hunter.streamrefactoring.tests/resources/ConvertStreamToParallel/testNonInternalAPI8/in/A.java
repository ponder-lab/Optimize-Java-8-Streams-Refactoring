package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {

	static Stream<Object> m() {
		Stream<Object> stream = new HashSet<>().stream();
		return stream;
	}

	@EntryPoint
	public static void main(String[] args) {
		m().sorted().distinct().count();
	}
}