package p;

interface I {
	default void m() {
		n();
	}

	void n();
}

class B {
	public void n() {
		System.out.println("B.n()");
	}
}

abstract class A extends B implements I {
}