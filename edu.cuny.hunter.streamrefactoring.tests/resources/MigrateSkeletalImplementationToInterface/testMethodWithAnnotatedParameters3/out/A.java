package p;

import javax.annotation.Generated;

interface I {
	default void m(@Generated("hello") int n, @Generated("goodbye") int q) {
	}
}

abstract class A implements I {
}