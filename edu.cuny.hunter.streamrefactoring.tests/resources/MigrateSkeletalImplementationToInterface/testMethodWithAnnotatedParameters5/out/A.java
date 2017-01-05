package p;

import javax.annotation.Generated;

interface I {
	default void m(@Generated("hello") @SuppressWarnings("goodbye") int n) {
	}
}

abstract class A implements I {
}