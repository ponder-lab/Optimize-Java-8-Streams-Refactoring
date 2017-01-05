package p;

interface I extends J {
	void m();
}

interface J {
}

abstract class A implements I {
	public void m() {
	}
}
