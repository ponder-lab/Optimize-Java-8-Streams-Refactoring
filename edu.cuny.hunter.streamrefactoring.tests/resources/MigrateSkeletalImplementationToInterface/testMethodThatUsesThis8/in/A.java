package p;

interface I {
	void m(A a); 
}

abstract class A implements I {
	@Override
	public void m(A a) {
		m(this);
	}
}