package p;

interface J {
}

interface I extends J {
	void m();
}

abstract class A implements I {
	public void m() {
	}
}
