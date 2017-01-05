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
	public C m() {
		return new D();
	}

	C n() {
		return m();
	}
}
