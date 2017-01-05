package p;

interface I extends J {
}

interface J {
	void m();
}

abstract class A implements I {
	public void m() {
	}
}
