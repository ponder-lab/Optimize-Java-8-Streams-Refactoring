package p;

import java.util.BitSet;
import java.util.OptionalDouble;

class A {
	@EntryPoint
	void m() {
		BitSet set = new BitSet();
		OptionalDouble average = set.stream().average();
	}
}