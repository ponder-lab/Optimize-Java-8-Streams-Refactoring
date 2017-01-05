package p;

class C {
	public static void n() {
	}
}

interface I {
	default void m() {
		C.n();
	}
}

public abstract class A implements I {
}
