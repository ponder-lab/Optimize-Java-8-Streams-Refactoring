package p;

interface I {
	default void m() {
		f = 5;
	}
}

abstract class A implements I {
	int f;	
}