package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {

	static Stream<Object> m() {
		return new HashSet<>().stream().sorted();
	}

	@EntryPoint
	public static void main(String[] args) {
		m().distinct().count();
	}
}