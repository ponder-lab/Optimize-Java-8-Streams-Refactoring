package edu.cuny.hunter.streamrefactoring.core.analysis.exceptions;

public class NoniterablePossibleStreamSourceException extends Exception {

	public NoniterablePossibleStreamSourceException() {
		super();
	}

	public NoniterablePossibleStreamSourceException(String message) {
		super(message);
	}

	public NoniterablePossibleStreamSourceException(Throwable cause) {
		super(cause);
	}

	public NoniterablePossibleStreamSourceException(String message, Throwable cause) {
		super(message, cause);
	}

	public NoniterablePossibleStreamSourceException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
