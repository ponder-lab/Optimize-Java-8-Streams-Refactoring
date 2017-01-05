package p;

interface I {
}

interface J {
	void m();
}

abstract class A implements I, J {
	@Override
	public void m() {
	}
}