package edu.cuny.hunter.streamrefactoring.core.analysis;

public class CannotDetermineStreamOrderingException extends Exception {

	private static final long serialVersionUID = -8186691314740466102L;

	public CannotDetermineStreamOrderingException() {
	}

	public CannotDetermineStreamOrderingException(String message) {
		super(message);
	}

	public CannotDetermineStreamOrderingException(Throwable cause) {
		super(cause);
	}

	public CannotDetermineStreamOrderingException(String message, Throwable cause) {
		super(message, cause);
	}

	public CannotDetermineStreamOrderingException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
