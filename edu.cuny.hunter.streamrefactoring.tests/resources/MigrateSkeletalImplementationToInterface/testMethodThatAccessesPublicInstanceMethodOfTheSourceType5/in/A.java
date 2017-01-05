package p;

interface I {
	void m();
}

abstract class A implements I {
	void n() {
	}

	public void m() {
		A a = this;
		a.n();
	}
}
