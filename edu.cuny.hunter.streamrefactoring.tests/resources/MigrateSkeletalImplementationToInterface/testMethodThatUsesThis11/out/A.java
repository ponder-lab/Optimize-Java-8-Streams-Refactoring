package p;

interface I {
	default void m() {
		new A(this);
	}
}

class A implements I {
	A(I i) {
	}
}