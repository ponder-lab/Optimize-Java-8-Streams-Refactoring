package p;

interface I {
	default I m() {
		return this;
	}
}

abstract class A implements I {
}