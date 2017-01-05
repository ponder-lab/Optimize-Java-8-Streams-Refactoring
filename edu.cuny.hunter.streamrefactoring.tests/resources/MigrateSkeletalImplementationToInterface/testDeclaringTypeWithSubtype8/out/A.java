package p;

class S {
}

class T extends S {
}

interface I {
	default S m() {
		return null;
	}
}

abstract class A implements I {
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