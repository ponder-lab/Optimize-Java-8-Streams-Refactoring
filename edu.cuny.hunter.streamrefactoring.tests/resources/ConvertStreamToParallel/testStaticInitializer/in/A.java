package p;

import java.util.HashSet;
import java.util.stream.*;

class A {
	static {
		Stream<Object> stream = new HashSet<>().parallelStream();
		stream.distinct().count();
	}
}