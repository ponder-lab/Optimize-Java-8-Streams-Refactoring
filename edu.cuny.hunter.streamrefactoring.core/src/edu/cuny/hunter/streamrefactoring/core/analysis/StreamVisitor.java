package edu.cuny.hunter.streamrefactoring.core.analysis;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction.IVisitor;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

public class StreamVisitor implements IVisitor {

	private CGNode node;
	private EclipseProjectAnalysisEngine<InstanceKey> engine;
	private IClassHierarchy classHierarchy;
	private boolean isStreamNode = false;

	StreamVisitor(CGNode node, EclipseProjectAnalysisEngine<InstanceKey> engine) {
		this.node = node;
		this.engine = engine;
		this.classHierarchy = engine.getClassHierarchy();
	}

	@Override
	public void visitPut(SSAPutInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getDeclaredFieldType(), classHierarchy))
			isStreamNode = true;
	}

	@Override
	public void visitNew(SSANewInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getConcreteType(), classHierarchy))
			isStreamNode = true;

	}

	@Override
	public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getType(), classHierarchy))
			isStreamNode = true;
	}

	@Override
	public void visitInvoke(SSAInvokeInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getDeclaredResultType(), classHierarchy))
			isStreamNode = true;

		MethodReference methodReference = instruction.getDeclaredTarget();
		// check parameters
		// TODO: the code below cannot work well.
		// int numberOfParameters = methodReference.getNumberOfParameters();
		// for(int i = 0; i< numberOfParameters; ++ i) {
		// TypeReference typeReference = methodReference.getParameterType(i);
		// if (isStreamType(typeReference, classHierarchy)) {
		// isStreamNode = true;
		// return;
		// }
		//
		// }
	}

	@Override
	public void visitInstanceof(SSAInstanceofInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getCheckedType(), classHierarchy))
			isStreamNode = true;
	}

	@Override
	public void visitGet(SSAGetInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getDeclaredFieldType(), classHierarchy))
			isStreamNode = true;
	}

	@Override
	public void visitCheckCast(SSACheckCastInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getDeclaredResultType(), classHierarchy))
			isStreamNode = true;
	}

	@Override
	public void visitArrayStore(SSAArrayStoreInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getElementType(), classHierarchy))
			isStreamNode = true;
	}

	@Override
	public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
		if (Util.implementsBaseStream(instruction.getElementType(), classHierarchy))
			isStreamNode = true;
	}

	@Override
	public void visitGoto(SSAGotoInstruction instruction) {
	}

	@Override
	public void visitBinaryOp(SSABinaryOpInstruction instruction) {
	}

	@Override
	public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
	}

	@Override
	public void visitConversion(SSAConversionInstruction instruction) {
	}

	@Override
	public void visitComparison(SSAComparisonInstruction instruction) {
	}

	@Override
	public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
	}

	@Override
	public void visitSwitch(SSASwitchInstruction instruction) {
	}

	@Override
	public void visitReturn(SSAReturnInstruction instruction) {
		// TODO: the getPossibleType could never stop
		// why??
		// int numberOfUses = instruction.getNumberOfUses();
		// for (int i = 0; i < numberOfUses; ++i) {
		// int uses = instruction.getUse(i);
		// TypeInference inference = TypeInference.make(node.getIR(), false);
		// Collection<TypeAbstraction> returnTypes = Util.getPossibleTypes(uses,
		// inference);
		// for(TypeAbstraction typeAbstraction : returnTypes) {
		// TypeReference typeReference = typeAbstraction.getTypeReference();
		// if (typeReference == null) continue;
		// if (Util.implementsBaseStream(typeReference, classHierarchy)) {
		// isStreamNode = true;
		// return;
		// }
		// }
		// }
	}

	@Override
	public void visitArrayLength(SSAArrayLengthInstruction instruction) {
	}

	@Override
	public void visitThrow(SSAThrowInstruction instruction) {
	}

	@Override
	public void visitMonitor(SSAMonitorInstruction instruction) {
	}

	@Override
	public void visitPhi(SSAPhiInstruction instruction) {
	}

	@Override
	public void visitPi(SSAPiInstruction instruction) {
	}

	@Override
	public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
	}

	public boolean getIsStreamNode() {
		return this.isStreamNode;
	}

}
