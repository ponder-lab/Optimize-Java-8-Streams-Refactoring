/**
 *
 */
package edu.cuny.hunter.streamrefactoring.core.messages;

import org.eclipse.osgi.util.NLS;

/**
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi Khatchadourian</a>
 */
public class Messages extends NLS {
	private static final String BUNDLE_NAME = "edu.cuny.hunter.streamrefactoring.core.messages.messages"; //$NON-NLS-1$
	public static String CategoryDescription;
	public static String CategoryName;
	public static String CheckingPreconditions;
	public static String CompilingSource;
	public static String CreatingChange;
	public static String CUContainsCompileErrors;
	public static String Name;
	public static String NoStreamsHavePassedThePreconditions;
	public static String NoStreamsToConvert;
	public static String PreconditionFailed;
	public static String RefactoringNotPossible;
	public static String StreamsNotSpecified;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
		super();
	}
}
