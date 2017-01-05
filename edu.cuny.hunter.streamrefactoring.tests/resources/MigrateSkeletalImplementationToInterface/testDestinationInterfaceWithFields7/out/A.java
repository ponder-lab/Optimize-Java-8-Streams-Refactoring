package p;

interface I {
	static int f = 0;
	default void m() {
		System.out.println(f);
	}
}

abstract class A implements I {
}
