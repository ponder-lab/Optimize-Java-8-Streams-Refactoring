package p;

interface I {
	default A m() {
		return this;
	}
}

abstract class A implements I {
}