package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Arrays;

import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction.Visitor;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.TypeReference;

public class StreamFindingVisitor extends Visitor {

	private IClassHierarchy classHierarchy;

	private boolean foundStream = false;

	public StreamFindingVisitor(IClassHierarchy classHierarchy) {
		this.classHierarchy = classHierarchy;
	}

	protected IClassHierarchy getClassHierarchy() {
		return this.classHierarchy;
	}

	public boolean hasFoundStream() {
		return this.foundStream;
	}

	private void processType(TypeReference type) {
		if (Util.implementsBaseStream(type, this.getClassHierarchy()))
			this.setFoundStream(true);
	}

	protected void setFoundStream(boolean foundStream) {
		this.foundStream = foundStream;
	}

	@Override
	public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
		this.processType(instruction.getElementType());
	}

	@Override
	public void visitArrayStore(SSAArrayStoreInstruction instruction) {
		this.processType(instruction.getElementType());
	}

	@Override
	public void visitCheckCast(SSACheckCastInstruction instruction) {
		Arrays.stream(instruction.getDeclaredResultTypes()).forEach(this::processType);
	}

	@Override
	public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
		this.processType(instruction.getType());
	}

	@Override
	public void visitConversion(SSAConversionInstruction instruction) {
		this.processType(instruction.getToType());
		this.processType(instruction.getFromType());
	}

	@Override
	public void visitGet(SSAGetInstruction instruction) {
		this.processType(instruction.getDeclaredFieldType());
	}

	@Override
	public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
	}

	@Override
	public void visitInstanceof(SSAInstanceofInstruction instruction) {
		this.processType(instruction.getCheckedType());
	}

	@Override
	public void visitInvoke(SSAInvokeInstruction instruction) {
		this.processType(instruction.getDeclaredResultType());
	}

	@Override
	public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
		this.processType(instruction.getType());
	}

	@Override
	public void visitNew(SSANewInstruction instruction) {
		this.processType(instruction.getConcreteType());
	}

	@Override
	public void visitPut(SSAPutInstruction instruction) {
		this.processType(instruction.getDeclaredFieldType());
	}
}
