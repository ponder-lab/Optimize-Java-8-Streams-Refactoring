package edu.cuny.hunter.streamrefactoring.ui.plugins;

import org.osgi.framework.BundleContext;

import edu.cuny.hunter.streamrefactoring.core.descriptors.ConvertStreamToParallelRefactoringDescriptor;
import edu.cuny.citytech.refactoring.common.ui.RefactoringPlugin;

public class OptimizeStreamRefactoringPlugin extends RefactoringPlugin {
	
	private static OptimizeStreamRefactoringPlugin plugin;
	
	public static RefactoringPlugin getDefault() {
		return plugin;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		plugin = this;
		super.start(context);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/* (non-Javadoc)
	 * @see edu.cuny.citytech.refactoring.common.ui.RefactoringPlugin#getRefactoringId()
	 */
	@Override
	protected String getRefactoringId() {
		return ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID;
	}
}