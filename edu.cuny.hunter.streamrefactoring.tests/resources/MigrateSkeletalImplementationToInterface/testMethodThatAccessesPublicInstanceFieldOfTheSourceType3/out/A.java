package p;

interface I {
	default void m() {
		int g = new A().f;
	}
}

class A implements I {
	int f;
}