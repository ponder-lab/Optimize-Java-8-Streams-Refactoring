package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {

	@EntryPoint
	void n() {
		new HashSet<>().stream().sorted().distinct().count();
	}
}