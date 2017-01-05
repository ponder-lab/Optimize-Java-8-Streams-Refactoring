package p;

interface I {
	void m(@Deprecated int n);
}

abstract class A implements I {
	public void m(@Deprecated int n) {
	}
}
