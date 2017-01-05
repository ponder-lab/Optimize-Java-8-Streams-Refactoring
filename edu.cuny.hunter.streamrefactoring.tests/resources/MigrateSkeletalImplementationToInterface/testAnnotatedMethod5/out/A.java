package p;

import javax.annotation.Generated;

interface I {
	@Deprecated
	@Generated("hello")
	default
	void m() {
	}
}

abstract class A implements I {
}