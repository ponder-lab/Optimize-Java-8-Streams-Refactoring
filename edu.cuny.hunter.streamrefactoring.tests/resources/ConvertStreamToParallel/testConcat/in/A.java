package p;

import static java.util.stream.Stream.concat;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m() {
		concat(new HashSet().parallelStream(),
				new HashSet().parallelStream()).count();
	}
}
