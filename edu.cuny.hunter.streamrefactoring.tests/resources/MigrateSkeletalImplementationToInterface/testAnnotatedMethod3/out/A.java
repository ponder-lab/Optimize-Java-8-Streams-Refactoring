package p;

import javax.annotation.Generated;

interface I {
	@Generated("hello")
	default
	void m() {
	}
}

abstract class A implements I {
}