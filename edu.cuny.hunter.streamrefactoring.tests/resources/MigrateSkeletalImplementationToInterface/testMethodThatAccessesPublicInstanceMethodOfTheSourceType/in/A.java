package p;

interface I {
	void m();
}

abstract class A implements I {
	void n() {
	}

	@Override
	public void m() {
		n();
	}
}