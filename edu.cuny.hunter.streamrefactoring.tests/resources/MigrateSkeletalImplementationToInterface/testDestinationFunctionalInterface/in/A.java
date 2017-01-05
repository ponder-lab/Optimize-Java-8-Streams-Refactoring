package p;

@FunctionalInterface
interface I {
	void m();
}

abstract class A implements I {
	public void m() {
	}
}