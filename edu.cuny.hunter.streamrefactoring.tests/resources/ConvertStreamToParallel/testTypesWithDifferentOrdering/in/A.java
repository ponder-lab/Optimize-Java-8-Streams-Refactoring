package p;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m(int n) {
		Collection col = null;

		if (n > 0)
			col = new ArrayList();
		else
			col = Collections.emptyList();

		Stream<Object> stream = col.parallelStream();
		stream.distinct().count();
	}
}