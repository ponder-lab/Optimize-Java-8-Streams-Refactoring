package p;

interface I {
	A m(); 
}

abstract class A implements I {
	@Override
	public A m() {
		return this;
	}
}