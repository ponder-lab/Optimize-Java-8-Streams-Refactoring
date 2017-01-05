package p;

interface I {
	void m();
}

interface J {
	default void m() {
	}
}

abstract class A implements I, J {
	@Override
	public void m() {
	}
}