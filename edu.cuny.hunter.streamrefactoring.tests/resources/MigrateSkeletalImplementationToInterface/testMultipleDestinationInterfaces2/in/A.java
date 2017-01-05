package p;

interface I {
	void m();
}

interface J extends I {
	void m();
}

abstract class A implements I {
	public void m() {
	}
}
