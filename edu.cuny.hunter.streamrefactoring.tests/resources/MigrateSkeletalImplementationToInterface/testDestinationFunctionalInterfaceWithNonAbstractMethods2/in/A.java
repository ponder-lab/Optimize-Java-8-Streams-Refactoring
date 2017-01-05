package p;

@FunctionalInterface
interface I {
	void m();
	default void n() {
	}
}

abstract class A implements I {
	public void m() {
	}
}
