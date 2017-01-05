package p;

interface I {
	int m();
}

abstract class A implements I {
	public int m() {
		return 0;
	}
}
