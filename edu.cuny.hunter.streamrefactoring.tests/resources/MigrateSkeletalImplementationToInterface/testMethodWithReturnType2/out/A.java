package p;

class C {
}

class D extends C {
}

interface I {
	default C m() {
		return new D();
	}
}

abstract class A implements I {
	C n() {
		return m();
	}
}
