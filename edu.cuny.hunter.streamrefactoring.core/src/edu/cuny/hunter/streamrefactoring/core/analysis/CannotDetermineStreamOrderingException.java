package edu.cuny.hunter.streamrefactoring.core.analysis;

public class CannotDetermineStreamOrderingException extends Exception {

	private static final long serialVersionUID = -8186691314740466102L;
	private Class<?> sourceType;

	public CannotDetermineStreamOrderingException(Class<?> sourceType) {
		this.sourceType = sourceType;
	}

	public CannotDetermineStreamOrderingException(String message, Class<?> sourceType) {
		super(message);
	}

	public CannotDetermineStreamOrderingException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace, Class<?> sourceType) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public CannotDetermineStreamOrderingException(String message, Throwable cause, Class<?> sourceType) {
		super(message, cause);
	}

	public CannotDetermineStreamOrderingException(Throwable cause, Class<?> sourceType) {
		super(cause);
	}

	public Class<?> getSourceType() {
		return sourceType;
	}
}
