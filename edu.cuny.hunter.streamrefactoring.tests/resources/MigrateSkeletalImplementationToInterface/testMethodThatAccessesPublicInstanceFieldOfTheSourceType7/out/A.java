package p;

interface I {
	default void m() {
		new A().f = 5;
	}
}

class A implements I {
	int f;
}