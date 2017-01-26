package edu.cuny.hunter.streamrefactoring.core.analysis;

public class NoninstantiablePossibleStreamSourceException extends Exception {

	private static final long serialVersionUID = 2364202699579315467L;
	private Object sourceType;

	public NoninstantiablePossibleStreamSourceException(String string, Class<?> sourceType) {
		super(string);
		this.sourceType = sourceType;
	}

	public NoninstantiablePossibleStreamSourceException(String string, Throwable cause, Class<?> sourceType) {
		super(string, cause);
		this.sourceType = sourceType;
	}

	public Object getSourceType() {
		return sourceType;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString() + ", ");
		builder.append("NoninstantiablePossibleStreamSourceException [sourceType=");
		builder.append(sourceType);
		builder.append("]");
		return builder.toString();
	}

}
