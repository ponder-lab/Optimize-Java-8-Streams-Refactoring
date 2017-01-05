package p;

interface I {
	default void m() {
		A.super.getClass();
	}	
}

class A {

	class B implements I { 
	}
}
