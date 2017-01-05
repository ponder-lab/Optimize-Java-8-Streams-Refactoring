package p;

interface I {
	default void m() {
		B.n();
	}
}

class B {
	public static void n() {
	}
}

abstract class A extends B implements I {
}