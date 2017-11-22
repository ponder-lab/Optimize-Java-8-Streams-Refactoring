package p;

import java.util.Collection;
import java.util.HashSet;

import edu.cuny.hunter.streamrefactoring.annotations.*;

public class A {
	@EntryPoint
	void m() {
		Collection collection1 = new HashSet<>();
		Collection collection2 = new HashSet<>();
		collection1.stream().count();
		collection2.stream();
	}
}