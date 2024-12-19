/**
 *
 */
package edu.cuny.hunter.streamrefactoring.ui.messages;

import org.eclipse.osgi.util.NLS;

/**
 * @author raffi
 *
 */
public class Messages extends NLS {
	private static final String BUNDLE_NAME = "edu.cuny.hunter.streamrefactoring.ui.messages.messages"; //$NON-NLS-1$
	public static String Name;
	public static String NoProjects;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
