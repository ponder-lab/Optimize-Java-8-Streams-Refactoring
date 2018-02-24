package edu.cuny.hunter.streamrefactoring.core.analysis;

import com.ibm.wala.types.MethodReference;

public class NoEnclosingMethodNodeFoundException extends Exception {

	private static final long serialVersionUID = -6260365320836884180L;
	private MethodReference methodReference;

	public NoEnclosingMethodNodeFoundException(MethodReference reference) {
		super("No enclosing method node found for method reference: " + reference);
		this.methodReference = reference;
	}

	public NoEnclosingMethodNodeFoundException(String message) {
		super(message);
	}

	public NoEnclosingMethodNodeFoundException(Throwable cause) {
		super(cause);
	}

	public NoEnclosingMethodNodeFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public NoEnclosingMethodNodeFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public MethodReference getMethodReference() {
		return this.methodReference;
	}
}