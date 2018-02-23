package edu.cuny.hunter.streamrefactoring.core.analysis;

public class UnknownIfReduceOrderMattersException extends Exception {
	private static final long serialVersionUID = -3019170716585367027L;

	public UnknownIfReduceOrderMattersException() {
		super();
	}

	public UnknownIfReduceOrderMattersException(String message) {
		super(message);
	}

	public UnknownIfReduceOrderMattersException(String message, Throwable cause) {
		super(message, cause);
	}

	public UnknownIfReduceOrderMattersException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public UnknownIfReduceOrderMattersException(Throwable cause) {
		super(cause);
	}
}
