package p;

import java.util.Collection;
import java.util.HashSet;

public class A {
	@EntryPoint
	void m() {
		Collection collection1 = new HashSet<>();
		Collection collection2 = new HashSet<>();
		collection1.stream();
		collection2.stream().count();
	}
}