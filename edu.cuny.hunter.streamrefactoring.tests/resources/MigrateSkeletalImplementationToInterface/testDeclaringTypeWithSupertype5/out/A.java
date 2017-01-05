package p;

class S {
}

class T extends S {
}

interface I {
	S m();
}

abstract class C implements I {
	@Override
	public S m() {
		return null;
	}
}

interface J {
	default T m() {
		return null;
	}
}

abstract class A extends C implements J {
}