package p;

interface I {
	default int m() {
		return 0;
	}
}

abstract class A implements I {
}
