package p;

interface I {
	void m();
}

interface J {
	void m();
}

abstract class A implements I, J {
	@Override
	public void m() {
	}
}