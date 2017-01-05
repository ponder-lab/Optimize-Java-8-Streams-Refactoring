package p;

interface I {
	default void m() {
		B.super.getClass();
	}	
}

class A {

	class B implements I { 
	}
}
