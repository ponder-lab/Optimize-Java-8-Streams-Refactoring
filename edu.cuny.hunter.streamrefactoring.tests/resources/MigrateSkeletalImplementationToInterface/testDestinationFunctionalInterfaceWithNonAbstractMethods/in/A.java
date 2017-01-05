package p;

@FunctionalInterface
interface I {
	void m();
	String toString();
}

abstract class A implements I {
	public void m() {
	}
}
