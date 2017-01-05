package p;

interface I {
	default void m() {
		int g = f;
	}
}

abstract class A implements I {
	int f;	
}