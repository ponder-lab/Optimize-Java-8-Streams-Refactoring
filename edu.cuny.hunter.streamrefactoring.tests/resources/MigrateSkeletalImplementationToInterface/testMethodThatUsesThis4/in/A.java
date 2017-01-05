package p;

interface I {
	I m();
}

abstract class A implements I {
	@Override
	public I m() {
		return this;
	}
}