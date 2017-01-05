package p;

interface I {
	void m();
}

abstract class A implements I {
	@Deprecated
	public void m() {
	}
}
