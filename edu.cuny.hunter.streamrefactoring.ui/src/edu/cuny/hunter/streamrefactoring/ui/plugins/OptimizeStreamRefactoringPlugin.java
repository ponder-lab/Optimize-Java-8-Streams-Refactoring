package edu.cuny.hunter.streamrefactoring.ui.plugins;

import org.osgi.framework.BundleContext;

import edu.cuny.citytech.refactoring.common.ui.RefactoringPlugin;
import edu.cuny.hunter.streamrefactoring.core.descriptors.OptimizeStreamRefactoringDescriptor;

public class OptimizeStreamRefactoringPlugin extends RefactoringPlugin {

	private static OptimizeStreamRefactoringPlugin plugin;

	public static RefactoringPlugin getDefault() {
		return plugin;
	}

	@Override
	protected String getRefactoringId() {
		return OptimizeStreamRefactoringDescriptor.REFACTORING_ID;
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
}