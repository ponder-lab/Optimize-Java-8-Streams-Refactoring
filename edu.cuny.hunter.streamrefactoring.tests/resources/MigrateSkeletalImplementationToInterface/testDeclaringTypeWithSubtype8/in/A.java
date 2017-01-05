package p;

class S {
}

class T extends S {
}

interface I {
	S m();
}

abstract class A implements I {

	@Override
	public S m() {
		return null;
	}
}

interface J {
	T m();
}

abstract class C extends A implements J {
	@Override
	public T m() {
		return null;
	}
}