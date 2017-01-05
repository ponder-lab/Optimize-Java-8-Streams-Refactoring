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
	T m();
}

abstract class A extends C implements J {
	@Override
	public T m() {
		return null;
	}
}