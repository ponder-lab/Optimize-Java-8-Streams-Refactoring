package p;

interface I {
}

interface K extends I {
	default void m() {
	}
}

interface J {
	void m();
}

abstract class A implements I, J {
	@Override
	public void m() {
	}
}