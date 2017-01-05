package p;

class C {
}

class D extends C {
}

interface I {
	C m();
}

abstract class A implements I {
	@Override
	public D m() {
		return new D();
	}

	D n() {
		return m();
	}
}
