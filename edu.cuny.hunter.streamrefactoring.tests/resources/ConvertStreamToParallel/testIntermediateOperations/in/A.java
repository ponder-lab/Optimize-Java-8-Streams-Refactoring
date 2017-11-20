package p;

import java.util.BitSet;
import java.util.stream.IntStream;

class A {
	@EntryPoint
	void m() {
		BitSet set = new BitSet();
		IntStream stream2 = set.stream();
		IntStream stream3 = stream2.distinct();
		stream2.average();
		stream3.average();
	}
}