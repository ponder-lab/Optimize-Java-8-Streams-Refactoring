package edu.cuny.hunter.streamrefactoring.core.analysis;

public class NoniterableException extends Exception {

	/**
	 *
	 */
	private static final long serialVersionUID = 6340319816698688446L;

	public NoniterableException() {
		super();
	}

	public NoniterableException(String message) {
		super(message);
	}

	public NoniterableException(String message, Throwable cause) {
		super(message, cause);
	}

	public NoniterableException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public NoniterableException(Throwable cause) {
		super(cause);
	}
}
