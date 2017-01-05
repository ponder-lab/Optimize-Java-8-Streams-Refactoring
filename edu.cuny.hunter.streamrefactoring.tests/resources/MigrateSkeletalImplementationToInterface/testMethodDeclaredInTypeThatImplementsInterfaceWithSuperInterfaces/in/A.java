package p;

interface I {
}

interface J extends I {
	void m();
}

abstract class A implements J {
	public void m() {
	}
}
