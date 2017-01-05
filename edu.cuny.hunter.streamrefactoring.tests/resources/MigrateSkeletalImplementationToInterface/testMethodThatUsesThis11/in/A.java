package p;

interface I {
	void m();
}

class A implements I {
	A(I i) {
	}

	@Override
	public void m() {
		new A(this);
	}
}