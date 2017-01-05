package p;

interface I {
	void m();
}

interface J extends I {
}

abstract class A implements J {
	public void m() {
	}
}
