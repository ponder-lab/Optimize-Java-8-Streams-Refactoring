package edu.cuny.hunter.streamrefactoring.core.analysis;

public class CannotExtractSpliteratorException extends Exception {

	private static final long serialVersionUID = 5599211330376425600L;
	private Class<? extends Object> fromType;

	public CannotExtractSpliteratorException() {
	}

	public CannotExtractSpliteratorException(String message, Class<? extends Object> fromType) {
		super(message);
		this.fromType = fromType;
	}

	public CannotExtractSpliteratorException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public CannotExtractSpliteratorException(String message, Throwable cause, Class<? extends Object> fromType) {
		super(message, cause);
		this.fromType = fromType;
	}

	public CannotExtractSpliteratorException(Throwable cause) {
		super(cause);
	}

	public Class<? extends Object> getFromType() {
		return fromType;
	}
}
