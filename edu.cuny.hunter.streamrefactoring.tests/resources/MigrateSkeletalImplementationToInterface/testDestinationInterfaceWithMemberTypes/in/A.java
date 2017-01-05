package p;

interface I {
	void m();

	class B {
	}
}

abstract class A implements I {
	@Override
	public void m() {
	}
}
