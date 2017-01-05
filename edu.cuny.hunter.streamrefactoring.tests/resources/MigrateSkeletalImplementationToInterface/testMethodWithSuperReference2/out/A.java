package p;

interface I {
    default void m() {
    	super.equals(null);
    }
}


abstract class A implements I {
}