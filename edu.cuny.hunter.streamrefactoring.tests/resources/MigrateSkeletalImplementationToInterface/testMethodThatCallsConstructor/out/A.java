package p;

interface I {
	default void m() {
		new A();
	}
}

public class A implements I {
	
	A() {
	}
}
