package edu.cuny.hunter.streamrefactoring.core.analysis;

public class NoniterableException extends Exception {

	public NoniterableException() {
		super();
	}

	public NoniterableException(String message) {
		super(message);
	}

	public NoniterableException(Throwable cause) {
		super(cause);
	}

	public NoniterableException(String message, Throwable cause) {
		super(message, cause);
	}

	public NoniterableException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
