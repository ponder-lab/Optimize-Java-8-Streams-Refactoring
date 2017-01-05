package p;

class C {
	public static void n() {
	}
}

interface I {
	void m();
}

public abstract class A implements I {

	@Override
	public void m() {
		C.n();
	}
}
