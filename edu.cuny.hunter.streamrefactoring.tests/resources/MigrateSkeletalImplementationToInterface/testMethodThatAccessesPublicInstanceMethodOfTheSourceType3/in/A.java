package p;

interface I {
	void m();
}

class A implements I {
	void n() {
	}

	@Override
	public void m() {
		new A().n();
	}
}