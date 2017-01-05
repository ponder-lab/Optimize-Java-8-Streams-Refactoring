package p;

interface I {
	void m();
}

interface J extends I {
}

abstract class A implements I {
	public void m() {
	}
}
