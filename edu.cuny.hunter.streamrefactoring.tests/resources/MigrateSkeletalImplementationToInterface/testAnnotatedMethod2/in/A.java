package p;

interface I {
	@Deprecated
	void m();
}

abstract class A implements I {
	@Deprecated
	public void m() {
	}
}