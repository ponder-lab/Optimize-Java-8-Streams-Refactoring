package p;

interface I {
	void m();
}

class A implements I {
	A(A a) {
	}

	@Override
	public void m() {
		new A(this);
	}
}