package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	static {
		Stream<Object> stream = new HashSet<>().parallelStream();
		stream.distinct().count();
	}
	
	@EntryPoint
	public static void main(String[] args) {
		new A();
	}
}
