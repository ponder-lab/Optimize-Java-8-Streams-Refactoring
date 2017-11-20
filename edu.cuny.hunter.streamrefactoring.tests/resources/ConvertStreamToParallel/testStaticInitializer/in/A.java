package p;

import java.util.HashSet;
import java.util.stream.*;

class A {
	@EntryPoint
	static {
		Stream<Object> stream = new HashSet<>().parallelStream();
		stream.distinct().count();
	}
}