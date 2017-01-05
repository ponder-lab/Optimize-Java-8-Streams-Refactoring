package p;

interface I {
	void m();
}

@Deprecated
abstract class A implements I {
	public void m() {
	}
}
