package p;

interface I {
	default <E extends B> void m() {
		E e;
	}
}

class B {
}

public abstract class A implements I {
}
