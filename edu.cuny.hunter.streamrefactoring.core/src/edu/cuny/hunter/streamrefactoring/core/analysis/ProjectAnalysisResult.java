package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Collection;
import java.util.HashSet;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;

public class ProjectAnalysisResult {

	private Collection<Entrypoint> usedEntryPoints = new HashSet<Entrypoint>();
	private Collection<CGNode> deadEntryPoints = new HashSet<CGNode>();

	public ProjectAnalysisResult(Collection<Entrypoint> usedEntryPoints, Collection<CGNode> deadEntryPoints) {
		this.usedEntryPoints = usedEntryPoints;
		this.deadEntryPoints = deadEntryPoints;
	}

	// Get used entry points.
	public Collection<Entrypoint> getUsedEntryPoints() {
		return this.usedEntryPoints;
	}

	// Get dead entry points.
	public Collection<CGNode> getDeadEntryPoints() {
		return this.deadEntryPoints;
	}

}
