package edu.cuny.streamrefactoring.core.plugin;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Logger;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

public class StreamRefactorCorePlugin extends Plugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "edu.cuny.streamrefactoring.core"; //$NON-NLS-1$

	// The shared instance
	private static StreamRefactorCorePlugin plugin;

	private static URI jvmLocalVersionUri = null;

	/**
	 * The constructor
	 */
	public StreamRefactorCorePlugin() {
	}

	/** {@inheritDoc} */
	public final void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/** {@inheritDoc} */
	public final void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static StreamRefactorCorePlugin getDefault() {
		return plugin;
	}


	private static final Logger log = Logger.getLogger(PLUGIN_ID); 

	public static URI getJVMLocalVersionURI() throws IOException {
		if (jvmLocalVersionUri == null) {
			String relativePath = "jre/";
			URL toolff = getDefault().getBundle().getResource(relativePath);
			if (toolff == null) {
				log.severe("unable to find a folder for JRE in path " + relativePath);
				throw new IOException("Unable to find the embedded JVM folder " + relativePath + " within bundle "+ getDefault().getBundle());
			}
			URL tmpURL = FileLocator.toFileURL(toolff);

			// use of the multi-argument constructor for URI in order to escape appropriately illegal characters
			URI uri;
			try {
				uri = new URI(tmpURL.getProtocol(), tmpURL.getPath(), null);
			} catch (URISyntaxException e) {
				throw new IOException("Could not create a URI to access the binary tool :", e);
			}
			jvmLocalVersionUri = uri;
			log.fine("Location of the JRE libs : " + jvmLocalVersionUri);
		}
		return jvmLocalVersionUri;
	}
}
