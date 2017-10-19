package p;

import java.util.Collection;
import java.util.HashSet;

public class A {
	
	void m() {
		Collection collection1 = new HashSet<>();
		Collection collection2 = new HashSet<>();
		collection1.stream().count();
		collection2.stream();
	}
}