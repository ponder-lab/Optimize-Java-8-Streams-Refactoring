package p;

interface I {
	void m(I i);
}

abstract class A implements I {
	@Override
	public void m(I i) {
		m(this);
	}
}