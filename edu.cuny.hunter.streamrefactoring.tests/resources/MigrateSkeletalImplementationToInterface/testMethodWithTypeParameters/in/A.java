package p;

interface I {
	<T> void m();
}

abstract class A implements I {
	public <T> void m() {
	}
}
