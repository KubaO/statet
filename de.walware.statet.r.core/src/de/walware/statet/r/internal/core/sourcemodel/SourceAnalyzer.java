/*******************************************************************************
 * Copyright (c) 2008-2009 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.core.sourcemodel;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.OperationCanceledException;

import de.walware.ecommons.ltk.AstInfo;
import de.walware.ecommons.ltk.ISourceStructElement;

import de.walware.statet.r.core.model.ArgsBuilder;
import de.walware.statet.r.core.model.ArgsDefinition;
import de.walware.statet.r.core.model.IEnvirInSource;
import de.walware.statet.r.core.model.IRLangElement;
import de.walware.statet.r.core.model.IRModelInfo;
import de.walware.statet.r.core.model.IRSourceUnit;
import de.walware.statet.r.core.model.RCoreFunctions;
import de.walware.statet.r.core.model.RElementName;
import de.walware.statet.r.core.rlang.RTerminal;
import de.walware.statet.r.core.rsource.IRSourceConstants;
import de.walware.statet.r.core.rsource.ast.Arithmetic;
import de.walware.statet.r.core.rsource.ast.Assignment;
import de.walware.statet.r.core.rsource.ast.CForLoop;
import de.walware.statet.r.core.rsource.ast.CIfElse;
import de.walware.statet.r.core.rsource.ast.CLoopCommand;
import de.walware.statet.r.core.rsource.ast.CRepeatLoop;
import de.walware.statet.r.core.rsource.ast.CWhileLoop;
import de.walware.statet.r.core.rsource.ast.Dummy;
import de.walware.statet.r.core.rsource.ast.FCall;
import de.walware.statet.r.core.rsource.ast.FDef;
import de.walware.statet.r.core.rsource.ast.Help;
import de.walware.statet.r.core.rsource.ast.Logical;
import de.walware.statet.r.core.rsource.ast.Model;
import de.walware.statet.r.core.rsource.ast.NSGet;
import de.walware.statet.r.core.rsource.ast.NodeType;
import de.walware.statet.r.core.rsource.ast.NullConst;
import de.walware.statet.r.core.rsource.ast.NumberConst;
import de.walware.statet.r.core.rsource.ast.Power;
import de.walware.statet.r.core.rsource.ast.RAst;
import de.walware.statet.r.core.rsource.ast.RAstNode;
import de.walware.statet.r.core.rsource.ast.RAstVisitor;
import de.walware.statet.r.core.rsource.ast.Relational;
import de.walware.statet.r.core.rsource.ast.Seq;
import de.walware.statet.r.core.rsource.ast.Sign;
import de.walware.statet.r.core.rsource.ast.SourceComponent;
import de.walware.statet.r.core.rsource.ast.Special;
import de.walware.statet.r.core.rsource.ast.StringConst;
import de.walware.statet.r.core.rsource.ast.SubIndexed;
import de.walware.statet.r.core.rsource.ast.SubNamed;
import de.walware.statet.r.core.rsource.ast.Symbol;
import de.walware.statet.r.core.rsource.ast.RAst.ReadedFCallArgs;
import de.walware.statet.r.internal.core.sourcemodel.RSourceElementByElementAccess.RClass;
import de.walware.statet.r.internal.core.sourcemodel.RSourceElementByElementAccess.RClassExt;
import de.walware.statet.r.internal.core.sourcemodel.RSourceElementByElementAccess.RMethod;
import de.walware.statet.r.internal.core.sourcemodel.RSourceElementByElementAccess.RPkgImport;
import de.walware.statet.r.internal.core.sourcemodel.RSourceElementByElementAccess.RSlot;


/**
 * Walks through the AST, analyzes element access, ...
 * 
 * Saves the information in {@link IRModelInfo}
 */
public class SourceAnalyzer extends RAstVisitor {
	
	
	private static final int S_GLOBAL = 0;
	private static final int S_LOCAL = 1;
	private static final int S_SEARCH = 2;
	
	private static final int RETURN_SOURCE_CONTAINTER = 1;
	private static final int RETURN_METHOD_SIGNATURE = 2;
	private static final int RETURN_STRING_ARRAY = 3;
	private static final int REG_CLASS_REPRESENTATION = 4;
	private static final int REG_CLASS_PROTOTYPE = 5;
	
	private static final int[] NO_REQUESTS = { };
	private static final int[] STRING_ARRAY_REQUEST = {
		RETURN_STRING_ARRAY };
	private static final int[] SIGNATURE_REQUESTS = {
		RETURN_METHOD_SIGNATURE, RETURN_STRING_ARRAY };
	private static final int[] REPRESENTATION_REQUEST = {
		REG_CLASS_REPRESENTATION };
	private static final int[] PROTOTYPE_REQUEST = {
		REG_CLASS_PROTOTYPE };
	
	
	private static final Comparator<ISourceStructElement> SOURCEELEMENT_SORTER = new Comparator<ISourceStructElement>() {
		public int compare(final ISourceStructElement e1, final ISourceStructElement e2) {
			return (e1.getSourceRange().getOffset() - e2.getSourceRange().getOffset());
		}
	};
	
	
	private static class ReturnValue {
		
		final int returnType;
		
		ReturnValue(final int returnType) {
			this.returnType = returnType;
		}
	}
	
	private static class NodeArray extends ReturnValue {
		
		final RAstNode[] array;
		
		NodeArray(final int returnType, final RAstNode[] array) {
			super(returnType);
			this.array = array;
		}
	}
	
	
	private static class SourceElementBuilder extends ReturnValue {
		
		private final SourceElementBuilder parent;
		private final AbstractRModelElement element;
		private final List<RSourceElementByElementAccess> children;
		
		private final List<ElementAccess> toCheck;
		private final Envir envir;
		
		SourceElementBuilder(final AbstractRModelElement element, final SourceElementBuilder parent, final Envir envir) {
			super(RETURN_SOURCE_CONTAINTER);
			this.element = element;
			this.parent = parent;
			this.children = new ArrayList<RSourceElementByElementAccess>();
			this.toCheck = new ArrayList<ElementAccess>();
			this.envir = envir;
		}
		
	}
	
	private static class Signature extends ReturnValue {
		
		private final String[] argNames;
		private final String[] classNames;
		
		Signature(final String[] argNames, final String[] classNames) {
			super(RETURN_METHOD_SIGNATURE);
			this.argNames = argNames;
			this.classNames = classNames;
		}
		
	}
	
	
	private IRSourceUnit fCurrentUnit;
	private int fAnonymCount;
	private final ArrayList<String> fIdComponents = new ArrayList<String>();
	private LinkedHashMap<String, Envir> fEnvironments;
	private Map<String, Envir> fDependencyEnvironments;
	private final ArrayList<Envir> fCurrentEnvironments = new ArrayList<Envir>();
	private Envir fGlobalEnvir;
	private Envir fGenericDefaultEnvir;
	private Envir fTopLevelEnvir;
	private Envir fTopScope;
	private Envir fPkgEnvir;
	
	private final LinkedList<RAstNode> fArgValueToIgnore = new LinkedList<RAstNode>();
	private int[] fRequest = NO_REQUESTS;
	private Object fReturnValue;
	
	private final ArrayList<SourceElementBuilder> fSourceContainerBuilders = new ArrayList<SourceElementBuilder>();
	private SourceElementBuilder fCurrentSourceContainerBuilder;
	
	private RCoreFunctions fConfiguredRDef;
	private final Map<String, IFCallAnalyzer> fFCallAnalyzers = new HashMap<String, IFCallAnalyzer>();
	private IFCallAnalyzer fFCallFallback;
	private final IFCallAnalyzer fFCallNoAnalysis = new IFCallAnalyzer() {
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
	};
	
	
	public SourceAnalyzer() {
		
		configure(RCoreFunctions.DEFAULT);
	}
	
	public void configure(final RCoreFunctions rdef) {
		fConfiguredRDef = rdef;
		fFCallAnalyzers.clear();
		IFCallAnalyzer analyzer;
		
		fFCallAnalyzers.put(RCoreFunctions.BASE_ASSIGN_NAME, new BaseAssign(rdef));
		analyzer = new BaseRemove(rdef);
		fFCallAnalyzers.put(RCoreFunctions.BASE_REMOVE_NAME, analyzer);
		fFCallAnalyzers.put(RCoreFunctions.BASE_REMOVE_ALIAS_RM, analyzer);
		fFCallAnalyzers.put(RCoreFunctions.BASE_EXISTS_NAME,
				new BaseExists(rdef));
		fFCallAnalyzers.put(RCoreFunctions.BASE_GET_NAME, 
				new BaseGet(rdef));
		fFCallAnalyzers.put(RCoreFunctions.BASE_SAVE_NAME,
				new BaseSave(rdef));
		fFCallAnalyzers.put(RCoreFunctions.BASE_CALL_NAME,
				new BaseCall(rdef));
		fFCallAnalyzers.put(RCoreFunctions.BASE_LIBRARY_NAME,
				new BaseLibrary(rdef));
		fFCallAnalyzers.put(RCoreFunctions.BASE_REQUIRE_NAME,
				new BaseRequire(rdef));
		fFCallAnalyzers.put(RCoreFunctions.BASE_GLOBALENV_NAME,
				new BaseGlobalenv(rdef));
		fFCallAnalyzers.put(RCoreFunctions.BASE_TOPENV_NAME,
				new BaseTopenv(rdef));
		fFCallAnalyzers.put(RCoreFunctions.BASE_C_NAME,
				new BaseC(rdef));
		
		fFCallAnalyzers.put(RCoreFunctions.BASE_USEMETHOD_NAME,
				new CommonDefBased(rdef.BASE_USEMETHOD_args));
		fFCallAnalyzers.put(RCoreFunctions.BASE_NEXTMETHOD_NAME,
				new CommonDefBased(rdef.BASE_NEXTMETHOD_args));
		fFCallAnalyzers.put(RCoreFunctions.UTILS_METHODS_NAME,
				new CommonDefBased(rdef.UTILS_METHODS_args));
		
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SETGENERIC_NAME,
				new MethodsSetGeneric(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SETGROUPGENERIC_NAME,
				new MethodsSetGroupGeneric(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_REMOVEGENERIC_NAME,
				new MethodsRemoveGeneric(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_ISGENERIC_NAME,
				new CommonDefBased(rdef.METHODS_ISGENERIC_args));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_ISGROUP_NAME,
				new CommonDefBased(rdef.METHODS_ISGROUP_args));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SIGNATURE_NAME,
				new MethodsSignature(rdef));
		
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SETCLASS_NAME,
				new MethodsSetClass(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SETCLASSUNION_NAME,
				new MethodsSetClassUnion(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_REPRESENTATION_NAME,
				new MethodsRepresentation(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_PROTOTYPE_NAME,
				new MethodsPrototype(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SETIS_NAME,
				new MethodsSetIs(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_REMOVECLASS_NAME,
				new MethodsRemoveClass(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_RESETCLASS_NAME,
				fFCallNoAnalysis);
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SETAS_NAME,
				new MethodsSetAs(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SETVALIDITY_NAME,
				new MethodsSetValidity(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_ISCLASS_NAME,
				new CommonDefBased(rdef.METHODS_ISCLASS_args));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_GETCLASS_NAME,
				new MethodsGetClass(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_GETCLASSDEF_NAME,
				new MethodsGetClassDef(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_FINDCLASS_NAME,
				new MethodsFindClass(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_EXTENDS_NAME,
				new CommonDefBased(rdef.METHODS_EXTENDS_args));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_IS_NAME,
				new CommonDefBased(rdef.METHODS_IS_args));
		
		fFCallAnalyzers.put(RCoreFunctions.METHODS_NEW_NAME,
				new MethodsNew(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_AS_NAME,
				new MethodsAs(rdef));
		
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SETMETHOD_NAME,
				new MethodsSetMethod(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_REMOVEMETHOD_NAME,
				new MethodsRemoveMethod(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_REMOVEMETHODS_NAME,
				new MethodsRemoveMethods(rdef));
		
		fFCallAnalyzers.put(RCoreFunctions.METHODS_HASMETHOD_NAME,
				new CommonDefBased(rdef.METHODS_HASMETHOD_args));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_EXISTSMETHOD_NAME,
				new CommonDefBased(rdef.METHODS_EXISTSMETHOD_args));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_GETMETHOD_NAME,
				new MethodsGetMethod(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SELECTMETHOD_NAME,
				new MethodsSelectMethod(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_GETMETHODS_NAME,
				new MethodsGetMethods(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_FINDMETHOD_NAME,
				new MethodsFindMethod(rdef));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_DUMPMETHOD_NAME,
				new CommonDefBased(rdef.METHODS_DUMPMETHOD_args));
		fFCallAnalyzers.put(RCoreFunctions.METHODS_DUMPMETHODS_NAME,
				new CommonDefBased(rdef.METHODS_DUMPMETHOD_args));
		
		fFCallAnalyzers.put(RCoreFunctions.METHODS_SLOT_NAME,
				new MethodsSlot(rdef));
		
		// DEBUG
		if (false) {
			final Set<String> test = new HashSet<String>();
			test.addAll(rdef.getKnownFunctions());
			test.removeAll(fFCallAnalyzers.keySet());
			System.out.println("nonregistered RCoreFunctions: " + test.toString());
		}
		
		fFCallFallback = new NoDefFallback();
	}
	
	
	public IRModelInfo update(final IRSourceUnit u, final AstInfo<RAstNode> ast) {
		fAnonymCount = 0;
		fCurrentUnit = u;
		fEnvironments = new LinkedHashMap<String, Envir>();
		fDependencyEnvironments = new HashMap<String, Envir>();
		final IResource res = u.getResource();
		final String projId = (res != null) ? res.getProject().getName() : "<noproject:"+u.getElementName(); //$NON-NLS-1$
		
		final Envir fileEnvir = new Envir.DefScope(IEnvirInSource.T_PROJ, Envir.createId(IEnvirInSource.T_PROJ, projId, 0), new Envir[0]); // ref projects
		fCurrentEnvironments.add(fileEnvir);
		fEnvironments.put(fileEnvir.getId(), fileEnvir);
		fGenericDefaultEnvir = fTopLevelEnvir = fGlobalEnvir = fileEnvir;
		fPkgEnvir = new Envir.DefScope(0, "pkgAccessInUnit", new Envir[0]);
		fTopScope = fCurrentEnvironments.get(fCurrentEnvironments.size()-1);
		
		fIdComponents.add(projId);
		
		final RSourceUnitElement fileElement = new RSourceUnitElement(u, ast.root);
		
		try {
			ast.root.addAttachment(fileElement);
			enterElement(fileElement, fileEnvir);
			ast.root.acceptInR(this);
			leaveElement();
			
			for (final Envir si : fDependencyEnvironments.values()) {
				si.runLateResolve(false);
			}
			fTopLevelEnvir.fParents.addAll(0, fDependencyEnvironments.values());
			for (final Envir si : fEnvironments.values()) {
				si.runLateResolve(false);
			}
			final AstInfo<RAstNode> newAst = new AstInfo<RAstNode>(RAst.LEVEL_MODEL_DEFAULT, ast.stamp);
			newAst.root = ast.root;
			final RSourceInfo model = new RSourceInfo(newAst, fEnvironments, fileElement);
			
			fEnvironments = null;
			fDependencyEnvironments = null;
			
			final HashMap<String, Integer> commonNames = new HashMap<String, Integer>();
			final HashMap<String, Integer> classNames = new HashMap<String, Integer>();
			final HashMap<String, Integer> importNames = new HashMap<String, Integer>();
			for (final SourceElementBuilder seb : fSourceContainerBuilders) {
				for (final RSourceElementByElementAccess element : seb.children) {
					final String name = element.getElementName().getDisplayName();
					final HashMap<String, Integer> names;
					switch (element.fType & IRLangElement.MASK_C1) {
					case IRLangElement.C1_CLASS:
						names = classNames;
						break;
					case IRLangElement.C1_IMPORT:
						names = importNames;
						break;
					default:
						names = commonNames;
						break;
					}
					final Integer count = names.get(name);
					names.put(name, (element.fOccurenceCount = (count != null) ? count.intValue()+1 : 0) );
				}
				for (final ElementAccess access : seb.toCheck) {
					if (seb.envir == access.getFrame()) {
						if (commonNames.containsKey(access.getSegmentName())) {
							continue;
						}
						commonNames.put(access.getSegmentName(), null);
						seb.children.add(new RSourceElementByElementAccess.RVariable(seb.element,
								(seb.envir != fTopLevelEnvir) ? IRLangElement.R_GENERAL_LOCAL_VARIABLE : IRLangElement.R_GENERAL_VARIABLE, access));
					}
					else {
//						seb.children.add(new RSourceElementFromElementAccess.RVariable(seb.element,
//								IRLangElement.R_COMMON_VARIABLE, access));
					}
				}
				
				final RSourceElementByElementAccess[] finalChildren = seb.children.toArray(new RSourceElementByElementAccess[seb.children.size()]);
				Arrays.sort(finalChildren, SOURCEELEMENT_SORTER);
				seb.element.fChildrenProtected = Arrays.asList(finalChildren);
				commonNames.clear();
				classNames.clear();
				importNames.clear();
			}
			
			return model;
		}
		catch (final OperationCanceledException e) {}
		catch (final InvocationTargetException e) {}
		finally {
			cleanup();
		}
		return null;
	}
	
	
	private void cleanup() {
		clean(fCurrentEnvironments);
		clean(fIdComponents);
		fArgValueToIgnore.clear();
		clean(fSourceContainerBuilders);
		
		fGenericDefaultEnvir = null;
		fGlobalEnvir = null;
		fPkgEnvir = null;
		fTopLevelEnvir = null;
		
		fReturnValue = null;
		fCurrentUnit = null;
		fCurrentSourceContainerBuilder = null;
	}
	
	private void clean(final ArrayList<?> list) {
		if (list.size() > 1024) {
			list.clear();
			list.trimToSize();
			list.ensureCapacity(128);
		}
		else {
			list.clear();
		}
	}
	
	
	private Envir getPkgEnvir(final String name) {
		final String id = Envir.createId(IEnvirInSource.T_PKG, name, ++fAnonymCount);
		Envir envir = fDependencyEnvironments.get(id);
		if (envir == null) {
			envir = new Envir.DefScope(IEnvirInSource.T_PKG, id, new Envir[0]);
			fDependencyEnvironments.put(id, envir);
		}
		return envir;
	}
	
	private void registerInEnvir(final int search, final String name, final ElementAccess access) {
		if (access.fShared != null) {
			return;
		}
		switch (search) {
		case S_LOCAL:
			fTopScope.add(name, access);
			return;
		case S_GLOBAL:
			fGlobalEnvir.add(name, access);
			return;
		case S_SEARCH:
			fTopScope.addLateResolve(name, access);
			return;
		default:
			throw new IllegalArgumentException("Illegal mode"); //$NON-NLS-1$
		}
	}
	
	private ElementAccess registerSimpleClassAccessInEnvir(final RAstNode refNode, final RAstNode nameNode) {
		final ElementAccess access = new ElementAccess.Class(refNode);
		access.fFlags = ElementAccess.A_READ;
		access.fNameNode = nameNode;
		fGenericDefaultEnvir.addClass(nameNode.getText(), access);
		
		return access;
	}
	
	protected final void enterElement(final AbstractRModelElement element, final Envir envir) {
		fCurrentSourceContainerBuilder = new SourceElementBuilder(element, fCurrentSourceContainerBuilder, envir);
		envir.setModelElement(element);
		fSourceContainerBuilders.add(fCurrentSourceContainerBuilder);
	}
	
	protected final void leaveElement() {
		fCurrentSourceContainerBuilder = fCurrentSourceContainerBuilder.parent;
	}
	
	private Object registerSourceElement(final Object value, final ElementAccess access) {
		if (value instanceof RSourceElementByElementAccess) {
			final RSourceElementByElementAccess element = (RSourceElementByElementAccess) value;
			if ((element.getElementType() & IRLangElement.MASK_C1) == IRLangElement.C1_METHOD) {
				registerFunctionElement((RMethod) value, element.getElementType(), access, null);
				return null;
			}
			
			element.fAccess = access;
			fCurrentSourceContainerBuilder.children.add(element);
			access.getNode().addAttachment(element);
			return null;
		}
		else if (access.getSegmentName() != null && access.getType() == RElementName.MAIN_DEFAULT && access.fNextSegment == null) {
			fCurrentSourceContainerBuilder.toCheck.add(access);
		}
		return value;
	}
	
	private void registerFunctionElement(final RMethod rMethod, int type,
			final ElementAccess access, final Signature sig) {
		if (rMethod.getElementType() == IRLangElement.R_COMMON_FUNCTION) {
			final IEnvirInSource frame = access.getFrame();
			if (frame != null && (frame.getType() == IEnvirInSource.T_FUNCTION || frame.getType() == IEnvirInSource.T_CLASS)) {
				// make sure it is marked as local
				type |= 0x1;
			}
		}
		rMethod.complete(type, access, createMethodArgDef(rMethod.getFDefNode(), sig));
		
		access.fFlags |= ElementAccess.A_FUNC;
		fCurrentSourceContainerBuilder.children.add(rMethod);
		access.getNode().addAttachment(rMethod);
	}
	
	private void registerFunctionElement(final RMethod rMethod) {
		fCurrentSourceContainerBuilder.children.add(rMethod);
		rMethod.getAccess().getNode().addAttachment(rMethod);
	}
	
	
	private void registerClassElement(final RClass rClass) {
		fCurrentSourceContainerBuilder.children.add(rClass);
		rClass.getAccess().getNode().addAttachment(rClass);
	}
	
	private void registerClassExtElement(final RClassExt rClassExt) {
		fCurrentSourceContainerBuilder.children.add(rClassExt);
		rClassExt.getAccess().getNode().addAttachment(rClassExt);
	}
	
	private boolean isRequested(final int requestId) {
		for (int i = 0; i < fRequest.length; i++) {
			if (fRequest[i] == requestId) {
				return true;
			}
		}
		return false;
	}
	
	
	@Override
	public void visit(final SourceComponent node) throws InvocationTargetException {
		node.addAttachment(fTopScope);
		node.acceptInRChildren(this);
	}
	
	@Override
	public void visit(final Assignment node) throws InvocationTargetException {
		// Value
		fReturnValue = null;
		node.getSourceChild().acceptInR(this);
		final Object returnValue = fReturnValue;
		
		final RAstNode target = node.getTargetChild();
		final ElementAccess access = new ElementAccess.Default(node);
		access.fFlags = ElementAccess.A_WRITE;
		
		final String name = resolveElementName(target, access, true);
		
		// Resolve
		int mode;
		if (access.getNextSegment() == null) {
			switch (node.getNodeType()) {
			case A_LEFT_D:
			case A_RIGHT_D:
				mode = S_SEARCH;
				break;
			default:
				mode = S_LOCAL;
				break;
			}
		}
		else {
			mode = S_SEARCH;
		}
		registerInEnvir(mode, name, access);
		
		fReturnValue = registerSourceElement(returnValue, access);
	}
	
	@Override
	public void visit(final CForLoop node) throws InvocationTargetException {
		final Symbol symbol = node.getVarChild();
		final ElementAccess access = new ElementAccess.Default(symbol);
		access.fFlags = ElementAccess.A_WRITE;
		final String name = resolveElementName(symbol, access, false);
		if (name != null) {
			registerInEnvir(S_LOCAL, name, access);
		}
		fRequest = NO_REQUESTS;
		node.getCondChild().acceptInR(this);
		node.getContChild().acceptInR(this);
		
		fReturnValue = null;
	}
	
	
	@Override
	public void visit(final FDef node) throws InvocationTargetException {
		final Envir envir = new Envir.DefScope(IEnvirInSource.T_FUNCTION, Envir.createId(IEnvirInSource.T_FUNCTION, null, ++fAnonymCount),
				new Envir[] { fTopScope });
		fCurrentEnvironments.add(envir);
		fTopScope = envir;
		fEnvironments.put(envir.getId(), envir);
		node.addAttachment(envir);
		
		final RMethod rMethod = new RMethod(fCurrentSourceContainerBuilder.element, envir, node);
		enterElement(rMethod, envir);
		
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		leaveElement();
		
		fCurrentEnvironments.remove(envir);
		fTopScope = fCurrentEnvironments.get(fCurrentEnvironments.size()-1);
		
		fReturnValue = rMethod;
	}
	
	@Override
	public void visit(final FDef.Arg node) throws InvocationTargetException {
		final RAstNode nameNode = node.getNameChild();
		if ((nameNode.getStatusCode() & IRSourceConstants.STATUSFLAG_REAL_ERROR) == 0) {
			final ElementAccess access = new ElementAccess.Default(node);
			access.fFlags = ElementAccess.A_WRITE | ElementAccess.A_ARG;
			access.fNameNode = nameNode;
			registerInEnvir(S_LOCAL, nameNode.getText(), access);
			
			fCurrentSourceContainerBuilder.children.add(new RSourceElementByElementAccess.RVariable(
					fCurrentSourceContainerBuilder.element, IRLangElement.R_ARGUMENT, access));
		}
		
		if (node.hasDefault()) {
			node.getDefaultChild().acceptInR(this);
		}
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final FCall node) throws InvocationTargetException {
		// Resolve
		final RAstNode ref = node.getRefChild();
		final ElementAccess access = new ElementAccess.Default(ref);
		access.fFlags = ElementAccess.A_READ | ElementAccess.A_FUNC;
		
		final String name = resolveElementName(node.getRefChild(), access, true);
		if (name != null) {
			registerInEnvir(S_SEARCH, name, access);
		}
		final boolean write;
		final RAstNode parent = node.getRParent();
		if (parent instanceof Assignment) {
			write = (((Assignment) parent).getTargetChild() == node);
		}
		else {
			write = false;
		}
		
		IFCallAnalyzer specialist = null;
		if (name != null) {
			specialist = fFCallAnalyzers.get(name);
		}
		if (specialist == null) {
			specialist = fFCallFallback;
		}
		specialist.visit(node, write);
	}
	
	@Override
	public void visit(final FCall.Arg node) throws InvocationTargetException {
		final RAstNode valueNode = node.getValueChild();
		if (valueNode != null) {
			if (!fArgValueToIgnore.remove(valueNode)) {
				valueNode.acceptInR(this);
			}
		}
		
		fReturnValue = null;
	}
	
	public RMethod visitAndCheckValue(final FCall.Arg node, final String name) throws InvocationTargetException {
		assert (name != null);
		if (node != null) {
			final RAstNode valueNode = node.getValueChild();
			if (valueNode != null) {
				valueNode.acceptInR(this);
				fArgValueToIgnore.add(valueNode);
				if (fReturnValue instanceof RMethod) {
					final RMethod rMethod = (RMethod) fReturnValue;
					
					final ElementAccess access = new ElementAccess.Default(node);
					access.fFlags = ElementAccess.A_WRITE | ElementAccess.A_FUNC;
					access.fNameNode = node.getNameChild();
					fCurrentSourceContainerBuilder.envir.addRunResolve(name, access);
					
					registerFunctionElement(rMethod, IRLangElement.R_COMMON_LOCAL_FUNCTION, access, null);
					fReturnValue = null;
					return rMethod;
				}
				else {
					fReturnValue = null;
				}
			}
		}
		return null;
	}
	
	@Override
	public void visit(final SubIndexed.Arg node) throws InvocationTargetException {
		final RAstNode valueNode = node.getValueChild();
		if (valueNode != null) {
			valueNode.acceptInR(this);
		}
	}
	
	@Override
	public void visit(final NSGet node) throws InvocationTargetException {
		final ElementAccess access = new ElementAccess.Default(node);
		access.fFlags = ElementAccess.A_READ;
		final String name = resolveElementName(node, access);
		if (name != null) {
			registerInEnvir(S_LOCAL, name, access);
		}
		
		fReturnValue = access;
	}
	
	@Override
	public void visit(final Symbol node) throws InvocationTargetException {
		final ElementAccess access = new ElementAccess.Default(node);
		access.fFlags = ElementAccess.A_READ;
		final String name = resolveElementName(node, access);
		if (name != null) {
			registerInEnvir(S_SEARCH, name, access);
			
			if (name.equals(".GlobalEnv")) {
				fReturnValue = fGlobalEnvir;
				return;
			}
		}
		
		fReturnValue = access;
	}
	
	@Override
	public void visit(final SubNamed node) throws InvocationTargetException {
		final ElementAccess access = new ElementAccess.Default(node);
		access.fFlags = ElementAccess.A_READ;
		final String name = resolvePartName(node, access);
		if (name != null) {
			registerInEnvir(S_SEARCH, name, access);
		}
		
		fReturnValue = access;
	}
	
	@Override
	public void visit(final SubIndexed node) throws InvocationTargetException {
		final ElementAccess access = new ElementAccess.Default(node);
		access.fFlags = ElementAccess.A_READ;
		final String name = resolveElementName(node, access);
		if (name != null) {
			registerInEnvir(S_SEARCH, name, access);
		}
		
		fReturnValue = access;
	}
	
	@Override
	public void visit(final Model node) throws InvocationTargetException {
		node.acceptInRChildren(this);
		fReturnValue = node;
	}
	
	
	@Override
	public void visit(final Help node) throws InvocationTargetException {
		fReturnValue = null;
	}
	
	@Override
	public void visit(final StringConst node) throws InvocationTargetException {
		for (int i = 0; i < fRequest.length; i++) {
			if (fRequest[i] == RETURN_STRING_ARRAY) {
				fReturnValue = new NodeArray(RETURN_STRING_ARRAY, new StringConst[] { node });
				return;
			}
		}
		fReturnValue = node;
	}
	
	@Override
	public void visit(final NumberConst node) throws InvocationTargetException {
		fReturnValue = null;
	}
	
	@Override
	public void visit(final NullConst node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	
	@Override
	public void visit(final Special node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final Sign node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final Power node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final Arithmetic node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final Seq node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final Relational node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final Logical node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final CIfElse node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final CRepeatLoop node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final CWhileLoop node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final CLoopCommand node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	@Override
	public void visit(final Dummy node) throws InvocationTargetException {
		fRequest = NO_REQUESTS;
		node.acceptInRChildren(this);
		
		fReturnValue = null;
	}
	
	
	private String resolveElementName(final RAstNode node, final ElementAccess access, final boolean allowString) throws InvocationTargetException {
		switch (node.getNodeType()) {
		case SYMBOL:
			return resolveElementName((Symbol) node, access);
		case STRING_CONST:
			if (allowString && ((node.getStatusCode() & IRSourceConstants.STATUSFLAG_REAL_ERROR) == 0)) {
				access.fNameNode = node;
				return node.getText();
			}
			return null;
		case SUB_INDEXED_S:
		case SUB_INDEXED_D:
			return resolveElementName((SubIndexed) node, access);
		case SUB_NAMED_PART:
			return resolvePartName((SubNamed) node, access);
		case SUB_NAMED_SLOT:
			return resolveSlotName((SubNamed) node, access);
		case NS_GET:
		case NS_GET_INT:
			return resolveElementName((NSGet) node, access);
		}
		if (node == access.fFullNode) {
			node.acceptInRChildren(this);
		}
		else {
			node.acceptInR(this);
		}
		return null;
	}
	
	private String resolveElementName(final Symbol node, final ElementAccess access) {
		if ((node.getStatusCode() & IRSourceConstants.STATUSFLAG_REAL_ERROR) == 0) {
			access.fNameNode = node;
			return node.getText();
		}
		return null;
	}
	
	private String resolveElementName(final SubIndexed node, final ElementAccess access) throws InvocationTargetException {
		final RAstNode child = node.getRefChild();
		final String name = resolveElementName(child, access, false);
		node.getArgsChild().acceptInR(this);
		if (name != null) {
			access.fFlags |= ElementAccess.A_SUB;
			access.appendSubElement((node.getOperator(0) == RTerminal.SUB_INDEXED_D_OPEN) ? 
					new SubIndexedDElementAccess(access, node) : new SubIndexedSElementAccess(access, node));
			return name;
		}
		return null;
	}
	
	private String resolvePartName(final SubNamed node, final ElementAccess access) throws InvocationTargetException {
		final RAstNode child = node.getRefChild();
		final String name = resolveElementName(child, access, false);
		if (name != null) {
			access.fFlags |= ElementAccess.A_SUB;
			access.appendSubElement(new SubNamedPartSyntacticElementAccess(access, node));
			return name;
		}
		return null;
	}
	
	private String resolveSlotName(final SubNamed node, final ElementAccess access) throws InvocationTargetException {
		final RAstNode child = node.getRefChild();
		final String name = resolveElementName(child, access, false);
		if (name != null) {
			access.fFlags |= ElementAccess.A_SUB;
			access.appendSubElement(new SubNamedSlotSyntacticElementAccess(access, node));
			return name;
		}
		return null;
	}
	
	private String resolveElementName(final NSGet node, final ElementAccess access) {
		final RAstNode namespaceChild = node.getNamespaceChild();
		String namespaceName = null;
		if (isValidPackageName(namespaceChild)) {
			namespaceName = namespaceChild.getText();
			final ElementAccess packageAccess = new ElementAccess.Package(access.fFullNode, namespaceChild);
			fPkgEnvir.add(namespaceName, packageAccess);
		}
		// register explicit
		Envir envir;
		if (namespaceName != null &&
				((node.getElementChild().getStatusCode() & IRSourceConstants.STATUSFLAG_REAL_ERROR) == 0)) {
			envir = getPkgEnvir(namespaceName);
		}
		else {
			envir = fTopScope;
		}
		access.fNameNode = node.getElementChild();
		final String name = access.fNameNode.getText();
		envir.add(name, access);
		return name;
	}
	
	private boolean isValidPackageName(final RAstNode node) {
		switch (node.getNodeType()) {
		case SYMBOL:
			return ((node.getStatusCode() & IRSourceConstants.STATUSFLAG_REAL_ERROR) == 0);
		case STRING_CONST:
			// TODO check
			return true;
		}
		return false;
	}
	
	
	protected static interface IFCallAnalyzer {
		
		public void visit(FCall node, boolean assignment) throws InvocationTargetException;
		
	}
	
	protected class CommonVarNamedRead implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_name;
		private final int fArgIdx_scope;
		
		protected CommonVarNamedRead(final ArgsDefinition argsDef, final String nameArgName, final String scopeArgName) {
			fArgsDef = argsDef;
			fArgIdx_name = fArgsDef.indexOf(nameArgName);
			fArgIdx_scope = fArgsDef.indexOf(scopeArgName);
		}
		
		public final void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode nameValue = args.getArgValueNode(fArgIdx_name);
			
			if (nameValue != null && nameValue.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_READ;
				access.fNameNode = nameValue;
				final Envir envir = readScopeArgs(args.getArgValueNode(fArgIdx_scope), fTopScope);
				if (evalBoolean(args.getArgValueNode("inherits"), false)) {
					envir.addLateResolve(nameValue.getText(), access);
				}
				else {
					envir.add(nameValue.getText(), access);
				}
				
				fArgValueToIgnore.add(nameValue);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class CommonDefBased implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		
		public CommonDefBased(final ArgsDefinition argsDef) {
			fArgsDef = argsDef;
		}
		
		public final void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			
			ITER_ARGS: for (int i = 0; i < args.allocatedArgs.length; i++) {
				final RAstNode argValue = args.getArgValueNode(i);
				if (argValue != null) {
					if ((fArgsDef.get(i).type & ArgsDefinition.METHOD_NAME) != 0
							&& argValue.getNodeType() == NodeType.STRING_CONST) {
						final ElementAccess access = new ElementAccess.Default(node);
						access.fFlags = ElementAccess.A_READ | ElementAccess.A_FUNC;
						access.fNameNode = argValue;
						fGenericDefaultEnvir.addLateResolve(argValue.getText(), access);
						
						fArgValueToIgnore.add(argValue);
						continue ITER_ARGS;
					}
					if ((fArgsDef.get(i).type & ArgsDefinition.CLASS_NAME) != 0
							&& argValue.getNodeType() == NodeType.STRING_CONST) {
						registerSimpleClassAccessInEnvir(node, argValue);
						
						fArgValueToIgnore.add(argValue);
						continue ITER_ARGS;
					}
					if ((fArgsDef.get(i).type & ArgsDefinition.UNSPECIFIC_NAME) != 0
							&& argValue.getNodeType() == NodeType.STRING_CONST) {
						final ElementAccess access = new ElementAccess.Default(node);
						access.fFlags = ElementAccess.A_READ;
						access.fNameNode = argValue;
						fTopScope.addLateResolve(argValue.getText(), access);
						
						fArgValueToIgnore.add(argValue);
						continue ITER_ARGS;
					}
				}
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	
	protected final class BaseAssign implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_x;
		private final int fArgIdx_value;
		
		
		public BaseAssign(final RCoreFunctions rdef) {
			fArgsDef = rdef.BASE_ASSIGN_args;
			fArgIdx_x = fArgsDef.indexOf("x");
			fArgIdx_value = fArgsDef.indexOf("value");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fReturnValue = null;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode xNode = args.getArgValueNode(fArgIdx_x);
			final RAstNode valueNode = args.getArgValueNode(fArgIdx_value);
			
			Object returnValue = null;
			if (valueNode != null) {
				valueNode.acceptInR(SourceAnalyzer.this);
				returnValue = fReturnValue;
				fReturnValue = null;
				fArgValueToIgnore.add(valueNode);
			}
			if (xNode != null && xNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_WRITE;
				access.fNameNode = xNode;
				
				final Envir envir = readScopeArgs(args.getArgValueNode("pos"), fTopScope);
				if (evalBoolean(args.getArgValueNode("inherits"), false)) {
					envir.addLateResolve(xNode.getText(), access);
				}
				else {
					envir.add(xNode.getText(), access);
				}
				
				returnValue = registerSourceElement(returnValue, access);
			}
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = returnValue;
		}
		
	}
	
	protected final class BaseRemove implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		
		public BaseRemove(final RCoreFunctions rdef) {
			fArgsDef = rdef.BASE_REMOVE_args;
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			
			if (args.ellisisArgs.length > 0) {
				for (int i = 0; i < args.ellisisArgs.length; i++) {
					final FCall.Arg argNode = args.ellisisArgs[i];
					if (argNode.hasValue()) {
						final RAstNode valueNode = argNode.getValueChild();
						switch (valueNode.getNodeType()) {
						case SYMBOL:
						case STRING_CONST:
							final ElementAccess access = new ElementAccess.Default(node);
							access.fFlags = ElementAccess.A_DELETE;
							access.fNameNode = valueNode;
							final Envir envir = readScopeArgs(args.getArgValueNode("pos"), fTopScope);
							if (evalBoolean(args.getArgValueNode("inherits"), false)) {
								envir.addLateResolve(valueNode.getText(), access);
							}
							else {
								envir.add(valueNode.getText(), access);
							}
							
							fArgValueToIgnore.add(valueNode);
							break;
						}
					}
				}
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class BaseExists extends CommonVarNamedRead {
		
		public BaseExists(final RCoreFunctions rdef) {
			super(rdef.BASE_EXISTS_args, "x", "where");
		}
		
	}
	
	protected final class BaseGet implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_x;
		private final int fArgIdx_scope;
		
		public BaseGet(final RCoreFunctions rdef) {
			fArgsDef = rdef.BASE_GET_args;
			fArgIdx_x = fArgsDef.indexOf("x");
			fArgIdx_scope = fArgsDef.indexOf("pos");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode xNode = args.getArgValueNode(fArgIdx_x);
			
			if (xNode != null && xNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_READ;
				access.fNameNode = xNode;
				final Envir envir = readScopeArgs(args.getArgValueNode(fArgIdx_scope), fTopScope);
				if (evalBoolean(args.getArgValueNode("inherits"), true)) {
					envir.addLateResolve(xNode.getText(), access);
				}
				else {
					envir.add(xNode.getText(), access);
				}
				
				fArgValueToIgnore.add(xNode);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class BaseSave implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		
		public BaseSave(final RCoreFunctions rdef) {
			fArgsDef = rdef.BASE_SAVE_args;
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			
			if (args.ellisisArgs.length > 0) {
				for (int i = 0; i < args.ellisisArgs.length; i++) {
					final FCall.Arg argNode = args.ellisisArgs[i];
					if (argNode.hasValue()) {
						final RAstNode valueNode = argNode.getValueChild();
						switch (valueNode.getNodeType()) {
						case SYMBOL:
						case STRING_CONST:
							final ElementAccess access = new ElementAccess.Default(node);
							access.fFlags = ElementAccess.A_READ;
							access.fNameNode = valueNode;
							fTopScope.addLateResolve(valueNode.getText(), access);
							
							fArgValueToIgnore.add(valueNode);
							break;
						}
					}
				}
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class BaseCall implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_fName;
		
		public BaseCall(final RCoreFunctions rdef) {
			fArgsDef = rdef.BASE_CALL_args;
			fArgIdx_fName = fArgsDef.indexOf("name");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode nameNode = args.getArgValueNode(fArgIdx_fName);
			
			if (nameNode != null && nameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_READ | ElementAccess.A_FUNC;
				access.fNameNode = nameNode;
				fTopScope.addLateResolve(nameNode.getText(), access);
				
				fArgValueToIgnore.add(nameNode);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	private abstract class BaseCommonPackageLoad implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_packageName;
		private final int fArgIdx_stringOnly;
		
		public BaseCommonPackageLoad(final ArgsDefinition argsDef) {
			fArgsDef = argsDef;
			fArgIdx_packageName = fArgsDef.indexOf("package");
			fArgIdx_stringOnly = fArgsDef.indexOf("character.only");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode nameValue = args.getArgValueNode(fArgIdx_packageName);
			if (nameValue != null 
					&& (nameValue.getNodeType() == NodeType.STRING_CONST
						|| (!evalBoolean(args.getArgNode(fArgIdx_stringOnly), false) && nameValue.getNodeType() == NodeType.SYMBOL))
					&& isValidPackageName(nameValue)) {
				final String packageName = nameValue.getText();
				final ElementAccess access = new ElementAccess.Package(
						node, nameValue);
				fPkgEnvir.add(packageName, access);
				final RPkgImport rImport = new RPkgImport(fCurrentSourceContainerBuilder.element, access);
				fCurrentSourceContainerBuilder.children.add(rImport);
				
				final Envir envir = getPkgEnvir(packageName);
				if (!fGlobalEnvir.fParents.contains(envir)) {
					fGlobalEnvir.fParents.add(0, envir);
				}
				
				fArgValueToIgnore.add(nameValue);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = fGlobalEnvir;
		}
		
	}
	
	protected final class BaseLibrary extends BaseCommonPackageLoad {
		
		public BaseLibrary(final RCoreFunctions rdef) {
			super(rdef.BASE_LIBRARY_args);
		}
		
	}
	
	protected final class BaseRequire extends BaseCommonPackageLoad {
		
		public BaseRequire(final RCoreFunctions rdef) {
			super(rdef.BASE_REQUIRE_args);
		}
		
	}
	
	
	protected final class BaseGlobalenv implements IFCallAnalyzer {
		
		public BaseGlobalenv(final RCoreFunctions rdef) {
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = fGlobalEnvir;
		}
		
	}
	
	protected final class BaseTopenv implements IFCallAnalyzer {
		
		public BaseTopenv(final RCoreFunctions rdef) {
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
//			final RAstNode envir = resolveEnvir(argValues, fArgsDef);
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = fTopLevelEnvir;
		}
		
	}
	
	protected final class BaseC implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		
		public BaseC(final RCoreFunctions rdef) {
			fArgsDef = rdef.BASE_C_args;
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			Object returnValue = null;
			REQUEST: for (int i = 0; i < fRequest.length; i++) {
				if (fRequest[i] == RETURN_STRING_ARRAY) {
					final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
					final RAstNode[] array = new RAstNode[args.ellisisArgs.length];
					for (int j = 0; j < array.length; j++) {
						final FCall.Arg argNode = args.ellisisArgs[j];
						if (argNode.hasValue()) {
							final RAstNode valueNode = argNode.getValueChild();
							if (valueNode.getNodeType() == NodeType.STRING_CONST) {
								array[j] = valueNode;
								fArgValueToIgnore.add(valueNode);
							}
							else {
								break REQUEST;
							}
						}
					}
					returnValue = new NodeArray(RETURN_STRING_ARRAY, array);
					break REQUEST;
				}
			}
			fRequest = NO_REQUESTS;
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = returnValue;
		}
		
	}
	
	private abstract class MethodsCommonSetGeneric implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_fName;
		private final int fArgIdx_def;
		private final int fArgIdx_useAsDefault;
		private final int fArgIdx_genericFunction;
		private final int fArgIdx_signature;
		
		protected MethodsCommonSetGeneric(final ArgsDefinition argsDef) {
			fArgsDef = argsDef;
			fArgIdx_fName = fArgsDef.indexOf("name");
			fArgIdx_def = fArgsDef.indexOf("def");
			fArgIdx_useAsDefault = fArgsDef.indexOf("useAsDefault");
			fArgIdx_genericFunction = fArgsDef.indexOf("genericFunction");
			fArgIdx_signature = fArgsDef.indexOf("signature");
		}
		
		public final void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode fNameNode = args.getArgValueNode(fArgIdx_fName);
			
			if (fNameNode != null && fNameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_WRITE | ElementAccess.A_FUNC;
				access.fNameNode = fNameNode;
				fGenericDefaultEnvir.add(fNameNode.getText(), access);
				
				fArgValueToIgnore.add(fNameNode);
				
				final Envir envir = new Envir.RunScope(IEnvirInSource.T_FUNCTION, Envir.createId(IEnvirInSource.T_FUNCTION, access.getSegmentName(), ++fAnonymCount), fTopScope);
				final RMethod rMethod = new RMethod(fCurrentSourceContainerBuilder.element, 
						IRLangElement.R_GENERIC_FUNCTION, access, envir);
				registerFunctionElement(rMethod);
				
				enterElement(rMethod, envir);
				
				final RMethod defMethod = visitAndCheckValue(args.getArgNode(fArgIdx_def), "def");
				final RMethod defaultMethod = visitAndCheckValue(args.getArgNode(fArgIdx_useAsDefault), "useAsDefault");
				visitAndCheckValue(args.getArgNode(fArgIdx_genericFunction), "genericFunction");
				
				final RAstNode signatureValue = args.getArgValueNode(fArgIdx_signature);
				RAstNode[] signatureArgNodes = null;
				if (signatureValue != null) {
					fRequest = STRING_ARRAY_REQUEST;
					fReturnValue = null;
					signatureValue.acceptInR(SourceAnalyzer.this);
					fArgValueToIgnore.add(signatureValue);
					if (fReturnValue instanceof ReturnValue && ((ReturnValue) fReturnValue).returnType == RETURN_STRING_ARRAY) {
						signatureArgNodes = ((NodeArray) fReturnValue).array;
					}
					fReturnValue = null;
				}
				
				ArgsDefinition baseDef = null;
				ArgsDefinition methodDef;
				if (defMethod != null) {
					baseDef = defMethod.getArgsDefinition();
				}
				if (defaultMethod != null && (baseDef == null || baseDef.size() == 0)) {
					baseDef = defaultMethod.getArgsDefinition();
				}
				if (baseDef != null && baseDef.size() > 0) {
					final ArgsBuilder argsBuilder = new ArgsBuilder();
					// we copy the names
					final String[] names = new String[baseDef.size()];
					ARGS: for (int i = 0; i < names.length; i++) {
						final String name = baseDef.get(i).name;
						if (name != null && signatureArgNodes != null) {
							for (int j = 0; j < signatureArgNodes.length; j++) {
								argsBuilder.add(name, 0, (!name.equals("...") &&
										name.equals(signatureArgNodes[j].getText())) ? "<?>" : "\u2014");
								continue ARGS;
							}
						}
						argsBuilder.add(name);
						continue ARGS;
					}
					methodDef = argsBuilder.toDef();
				}
				else {
					methodDef = new ArgsDefinition();
				}
				rMethod.complete(methodDef);
				
				node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
				
				leaveElement();
			}
			else {
				node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			}
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsSetGeneric extends MethodsCommonSetGeneric {
		
		public MethodsSetGeneric(final RCoreFunctions rdef) {
			super(rdef.METHODS_SETGENERIC_args);
		}
		
	}
	
	protected final class MethodsSetGroupGeneric extends MethodsCommonSetGeneric {
		
		public MethodsSetGroupGeneric(final RCoreFunctions rdef) {
			super(rdef.METHODS_SETGROUPGENERIC_args);
		}
		
	}
	
	protected final class MethodsRemoveGeneric implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_fName;
		
		public MethodsRemoveGeneric(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SETGROUPGENERIC_args;
			fArgIdx_fName = fArgsDef.indexOf("f");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode fNameNode = args.getArgValueNode(fArgIdx_fName);
			
			if (fNameNode != null && fNameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_DELETE | ElementAccess.A_FUNC;
				access.fNameNode = fNameNode;
				fGenericDefaultEnvir.add(fNameNode.getText(), access);
				
				fArgValueToIgnore.add(fNameNode);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsSignature implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		
		public MethodsSignature(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SIGNATURE_args;
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			Object returnValue = null;
			
			if (args.ellisisArgs.length > 0) {
				final String[] argNames = new String[args.ellisisArgs.length];
				final String[] classNames = new String[args.ellisisArgs.length];
				for (int i = 0; i < args.ellisisArgs.length; i++) {
					final FCall.Arg arg = args.ellisisArgs[i];
					if (arg.hasName()) {
						argNames[i] = arg.getNameChild().getText();
					}
					if (arg.hasValue()) {
						final RAstNode value = arg.getValueChild();
						if (value.getNodeType() == NodeType.STRING_CONST) {
							classNames[i] = value.getText();
							registerSimpleClassAccessInEnvir(node, value);
							fArgValueToIgnore.add(value);
						}
					}
				}
				returnValue = new Signature(argNames, classNames);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = returnValue;
		}
		
	}
	
	protected final class MethodsSetClass implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_className;
		private final int fArgIdx_superClasses;
		private final int fArgIdx_representation;
		private final int fArgIdx_prototype;
		
		public MethodsSetClass(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SETCLASS_args;
			fArgIdx_className = fArgsDef.indexOf("Class");
			fArgIdx_superClasses = fArgsDef.indexOf("contains");
			fArgIdx_representation = fArgsDef.indexOf("representation");
			fArgIdx_prototype = fArgsDef.indexOf("prototype");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			
			final ElementAccess access = new ElementAccess.Class(node);
			access.fFlags = ElementAccess.A_WRITE;
			String name;
			final RAstNode classNameValue = args.getArgValueNode(fArgIdx_className);
			if (classNameValue != null && classNameValue.getNodeType() == NodeType.STRING_CONST) {
				name = classNameValue.getText();
				access.fNameNode = classNameValue;
				fArgValueToIgnore.add(classNameValue);
			}
			else {
				name = null;
			}
			fGenericDefaultEnvir.addClass(name, access);
			
			final Envir envir = new Envir.RunScope(IEnvirInSource.T_CLASS, Envir.createId(IEnvirInSource.T_CLASS, access.getSegmentName(), ++fAnonymCount), 
					fTopScope);
			fEnvironments.put(envir.getId(), envir);
			node.addAttachment(envir);
			
			final RClass rClass = new RSourceElementByElementAccess.RClass(fCurrentSourceContainerBuilder.element, access, envir);
			registerClassElement(rClass);
			enterElement(rClass, envir);
			
			final RAstNode representationValue = args.getArgValueNode(fArgIdx_representation);
			if (representationValue != null) {
				fRequest = REPRESENTATION_REQUEST;
				representationValue.acceptInR(SourceAnalyzer.this);
				
				fArgValueToIgnore.add(representationValue);
				fRequest = NO_REQUESTS;
			}
			
			final RAstNode superClasses = args.getArgValueNode(fArgIdx_superClasses);
			if (superClasses != null) {
				fRequest = STRING_ARRAY_REQUEST;
				fReturnValue = null;
				superClasses.acceptInR(SourceAnalyzer.this);
				fArgValueToIgnore.add(superClasses);
				if (fReturnValue instanceof ReturnValue && ((ReturnValue) fReturnValue).returnType == RETURN_STRING_ARRAY) {
					final RAstNode refNode = args.allocatedArgs[fArgIdx_superClasses];
					final RAstNode[] superClassNameNodes = ((NodeArray) fReturnValue).array;
					final String[] names = new String[superClassNameNodes.length];
					for (int i = 0; i < superClassNameNodes.length; i++) {
						final ElementAccess superClassAccess = registerSimpleClassAccessInEnvir(refNode, superClassNameNodes[i]);
						names[i] = superClassAccess.getSegmentName();
					}
					rClass.addSuperClasses(names);
				}
				fRequest = NO_REQUESTS;
				fReturnValue = null;
			}
			
			final RAstNode prototypeValue = args.getArgValueNode(fArgIdx_prototype);
			if (prototypeValue != null) {
				fRequest = PROTOTYPE_REQUEST;
				prototypeValue.acceptInR(SourceAnalyzer.this);
				
				fArgValueToIgnore.add(prototypeValue);
				fRequest = NO_REQUESTS;
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			
			leaveElement();
			
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsSetClassUnion implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_className;
		private final int fArgIdx_superClassNames;
		
		public MethodsSetClassUnion(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SETCLASSUNION_args;
			fArgIdx_className = fArgsDef.indexOf("name");
			fArgIdx_superClassNames = fArgsDef.indexOf("members");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode classNameValue = args.getArgValueNode(fArgIdx_className);
			final RAstNode superClassNamesValue = args.getArgValueNode(fArgIdx_superClassNames);
			
			final ElementAccess access = new ElementAccess.Class(node);
			final String name;
			access.fFlags = ElementAccess.A_WRITE;
			if (classNameValue != null && classNameValue.getNodeType() == NodeType.STRING_CONST) {
				name = classNameValue.getText();
				access.fNameNode = classNameValue;
				fArgValueToIgnore.add(classNameValue);
			}
			else {
				name = null;
			}
			fGenericDefaultEnvir.addClass(name, access);
			
			final Envir envir = new Envir.RunScope(IEnvirInSource.T_CLASS, Envir.createId(IEnvirInSource.T_CLASS, access.getSegmentName(), ++fAnonymCount), 
					fTopScope);
			fEnvironments.put(envir.getId(), envir);
			node.addAttachment(envir);
			
			final RClass rClass = new RSourceElementByElementAccess.RClass(fCurrentSourceContainerBuilder.element, access, envir);
			registerClassElement(rClass);
			enterElement(rClass, envir);
			
			if (superClassNamesValue != null) {
				fRequest = STRING_ARRAY_REQUEST;
				fReturnValue = null;
				superClassNamesValue.acceptInR(SourceAnalyzer.this);
				fArgValueToIgnore.add(superClassNamesValue);
				if (fReturnValue instanceof ReturnValue && ((ReturnValue) fReturnValue).returnType == RETURN_STRING_ARRAY) {
					final RAstNode refNode = args.allocatedArgs[fArgIdx_superClassNames];
					final RAstNode[] superClassNameNodes = ((NodeArray) fReturnValue).array;
					final String[] names = new String[superClassNameNodes.length];
					for (int i = 0; i < superClassNameNodes.length; i++) {
						final ElementAccess superClassAccess = registerSimpleClassAccessInEnvir(refNode, superClassNameNodes[i]);
						names[i] = superClassAccess.getSegmentName();
					}
					rClass.addSuperClasses(names);
				}
				fRequest = NO_REQUESTS;
				fReturnValue = null;
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			
			leaveElement();
			
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsRepresentation implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		
		public MethodsRepresentation(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_REPRESENTATION_args;
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			final boolean requested = (fRequest == REPRESENTATION_REQUEST // || isRequested(REG_CLASS_REPRESENTATION)
					&& fCurrentSourceContainerBuilder.element.getElementType() == IRLangElement.R_S4CLASS);
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			
			if (args.ellisisArgs.length > 0) {
				final RSourceElementByElementAccess.RClass rClass = requested ?
								(RSourceElementByElementAccess.RClass) fCurrentSourceContainerBuilder.element : null;
				final String[] superClassNames = new String[args.ellisisArgs.length];
				
				for (int i = 0; i < args.ellisisArgs.length; i++) {
					final FCall.Arg arg = args.ellisisArgs[i];
					if (arg.hasName()) { // slot
						final RAstNode nameNode = arg.getNameChild();
						RSlot slot = null;
						if (rClass != null) {
							final ElementAccess.Slot access = new ElementAccess.Slot(arg);
							access.fFlags = ElementAccess.A_WRITE;
							access.fNameNode = nameNode;
							fCurrentSourceContainerBuilder.envir.addRunResolve(nameNode.getText(), access);
							slot = new RSourceElementByElementAccess.RSlot(rClass, access);
							fCurrentSourceContainerBuilder.children.add(slot);
						}
						if (arg.hasValue()) {
							final RAstNode valueNode = arg.getValueChild();
							if (valueNode.getNodeType() == NodeType.STRING_CONST) {
								registerSimpleClassAccessInEnvir(arg, valueNode);
								if (slot != null) {
									slot.completeType(valueNode.getText());
								}
								fArgValueToIgnore.add(valueNode);
							}
						}
					}
					else { // superclasses (like setClass arg contains)
						if (arg.hasValue()) {
							final RAstNode value = arg.getValueChild();
							if (value.getNodeType() == NodeType.STRING_CONST) {
								registerSimpleClassAccessInEnvir(arg, value);
								if (rClass != null) {
									superClassNames[i] = value.getText();
								}
								fArgValueToIgnore.add(value);
							}
						}
					}
				}
				if (rClass != null) {
					rClass.addSuperClasses(superClassNames);
				}
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsPrototype implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		
		public MethodsPrototype(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_PROTOTYPE_args;
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			final boolean requested = (fRequest == PROTOTYPE_REQUEST // || isRequested(REG_CLASS_REPRESENTATION)
					&& fCurrentSourceContainerBuilder.element.getElementType() == IRLangElement.R_S4CLASS);
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			
			if (args.ellisisArgs.length > 0) {
				final RSourceElementByElementAccess.RClass classDef = requested ?
						(RSourceElementByElementAccess.RClass) fCurrentSourceContainerBuilder.element : null;
				for (int i = 0; i < args.ellisisArgs.length; i++) {
					final FCall.Arg arg = args.ellisisArgs[i];
					if (arg.hasName()) { // slot
						final RAstNode slotNameNode = arg.getNameChild();
						final String slotName = slotNameNode.getText();
						RSlot slot = null;
						if (classDef != null && slotName != null) {
							final ElementAccess.Slot access = new ElementAccess.Slot(arg);
							access.fFlags = ElementAccess.A_WRITE;
							access.fNameNode = slotNameNode;
							fCurrentSourceContainerBuilder.envir.addRunResolve(slotName, access);
							for (final RSourceElementByElementAccess child : fCurrentSourceContainerBuilder.children) {
								if (child.getElementType() == IRLangElement.R_S4SLOT
										&& slotName.equals(child.getElementName().getSegmentName()) ) {
									slot = (RSlot) child;
									break;
								}
							}
						}
						if (arg.hasValue()) {
//							final RAstNode valueNode = arg.getValueChild();
//							if (slot != null) {
//								slot.fPrototypeCode = value.toString();
//							}
						}
					}
//					else { // data
//					}
				}
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsSetIs implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_className;
		private final int fArgIdx_classToExtendName;
		private final int fArgIdx_testF;
		private final int fArgIdx_coerceF;
		private final int fArgIdx_replaceF;
		
		public MethodsSetIs(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SETIS_args;
			fArgIdx_className = fArgsDef.indexOf("class1");
			fArgIdx_classToExtendName = fArgsDef.indexOf("class2");
			fArgIdx_testF = fArgsDef.indexOf("test");
			fArgIdx_coerceF = fArgsDef.indexOf("coerce");
			fArgIdx_replaceF = fArgsDef.indexOf("replace");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode classNameNode = args.getArgValueNode(fArgIdx_className);
			final RAstNode cToExtendNameNode = args.getArgValueNode(fArgIdx_classToExtendName);
			RClassExt rClassExt = null;
			Envir envir = null;
			
			if (classNameNode != null && classNameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Class(node);
				access.fFlags = ElementAccess.A_WRITE;
				access.fNameNode = classNameNode;
				fGenericDefaultEnvir.addClass(classNameNode.getText(), access);
				
				fArgValueToIgnore.add(classNameNode);
				
				envir = new Envir.RunScope(IEnvirInSource.T_FUNCTION, Envir.createId(IEnvirInSource.T_FUNCTION, access.getSegmentName(), ++fAnonymCount), fTopScope);
				rClassExt = new RClassExt(fCurrentSourceContainerBuilder.element, access, envir, "setIs");
				registerClassExtElement(rClassExt);
			}
			if (cToExtendNameNode != null && cToExtendNameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Class(node);
				access.fFlags = ElementAccess.A_READ;
				access.fNameNode = cToExtendNameNode;
				fGenericDefaultEnvir.addClass(cToExtendNameNode.getText(), access);
				
				fArgValueToIgnore.add(classNameNode);
				
				if (rClassExt != null) {
					rClassExt.complete(classNameNode.getText());
				}
			}
			
			if (rClassExt != null) {
				enterElement(rClassExt, envir);
				
				visitAndCheckValue(args.allocatedArgs[fArgIdx_testF], "test");
				visitAndCheckValue(args.allocatedArgs[fArgIdx_coerceF], "coerce");
				visitAndCheckValue(args.allocatedArgs[fArgIdx_replaceF], "replace");
				
				node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
				
				leaveElement();
			}
			else {
				node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			}
			fReturnValue = null;
			return;
		}
		
	}
	
	
	protected final class MethodsRemoveClass implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_className;
		
		public MethodsRemoveClass(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_REMOVECLASS_args;
			fArgIdx_className = fArgsDef.indexOf("Class");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode classNameNode = args.getArgValueNode(fArgIdx_className);
			
			if (classNameNode != null && classNameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Class(node);
				access.fFlags = ElementAccess.A_DELETE;
				access.fNameNode = classNameNode;
				fGenericDefaultEnvir.addClass(classNameNode.getText(), access);
				
				fArgValueToIgnore.add(classNameNode);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
			return;
		}
		
	}
	
	protected final class MethodsSetAs implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_className;
		private final int fArgIdx_toClass;
		
		public MethodsSetAs(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SETAS_args;
			fArgIdx_className = fArgsDef.indexOf("from");
			fArgIdx_toClass = fArgsDef.indexOf("to");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode classNameNode = args.getArgValueNode(fArgIdx_className);
			final RAstNode toClassNode = args.getArgValueNode(fArgIdx_toClass);
			
			if (classNameNode != null && classNameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Class(node);
				access.fFlags = ElementAccess.A_WRITE;
				access.fNameNode = classNameNode;
				fGenericDefaultEnvir.addClass(classNameNode.getText(), access);
				
				fArgValueToIgnore.add(classNameNode);
			}
			if (toClassNode != null && toClassNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Class(node);
				access.fFlags = ElementAccess.A_READ;
				access.fNameNode = toClassNode;
				fGenericDefaultEnvir.addClass(toClassNode.getText(), access);
				
				fArgValueToIgnore.add(toClassNode);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
			return;
		}
		
	}
	
	protected final class MethodsSetValidity implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_className;
		
		public MethodsSetValidity(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SETVALIDITY_args;
			fArgIdx_className = fArgsDef.indexOf("Class");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode classNameNode = args.getArgValueNode(fArgIdx_className);
			
			if (classNameNode != null && classNameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Class(node);
				access.fFlags = ElementAccess.A_WRITE;
				access.fNameNode = classNameNode;
				fGenericDefaultEnvir.addClass(classNameNode.getText(), access);
				
				fArgValueToIgnore.add(classNameNode);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
			return;
		}
		
	}
	
	private abstract class MethodsCommonClassRead implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_className;
		
		protected MethodsCommonClassRead(final ArgsDefinition argsDef, final String classNameNodeName) {
			fArgsDef = argsDef;
			fArgIdx_className = fArgsDef.indexOf(classNameNodeName);
		}
		
		public final void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode classNameNode = args.getArgValueNode(fArgIdx_className);
			
			if (classNameNode != null && classNameNode.getNodeType() == NodeType.STRING_CONST) {
				registerSimpleClassAccessInEnvir(node, classNameNode);
				fArgValueToIgnore.add(classNameNode);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsGetClass extends MethodsCommonClassRead {
		
		public MethodsGetClass(final RCoreFunctions rdef) {
			super(rdef.METHODS_GETCLASS_args, "Class");
		}
		
	}
	
	protected final class MethodsGetClassDef extends MethodsCommonClassRead {
		
		public MethodsGetClassDef(final RCoreFunctions rdef) {
			super(rdef.METHODS_GETCLASSDEF_args, "Class");
		}
		
	}
	
	protected final class MethodsFindClass extends MethodsCommonClassRead {
		
		public MethodsFindClass(final RCoreFunctions rdef) {
			super(rdef.METHODS_FINDCLASS_args, "Class");
		}
		
	}
	
	protected final class MethodsNew extends MethodsCommonClassRead {
		
		public MethodsNew(final RCoreFunctions rdef) {
			super(rdef.METHODS_NEW_args, "Class");
		}
		
	}
	
	protected final class MethodsAs extends MethodsCommonClassRead {
		
		public MethodsAs(final RCoreFunctions rdef) {
			super(rdef.METHODS_AS_args, "Class");
		}
		
	}
	
	protected final class MethodsSetMethod implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_fName;
		private final int fArgIdx_signature;
		private final int fArgIdx_fDef;
		
		public MethodsSetMethod(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SETMETHOD_args;
			fArgIdx_fName = fArgsDef.indexOf("f");
			fArgIdx_signature = fArgsDef.indexOf("signature");
			fArgIdx_fDef = fArgsDef.indexOf("definition");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode fNameArg = args.getArgValueNode(fArgIdx_fName);
			final RAstNode fDefArg = args.getArgValueNode(fArgIdx_fDef);
			
			if (fNameArg != null && fNameArg.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_WRITE | ElementAccess.A_FUNC;
				access.fNameNode = fNameArg;
				fGenericDefaultEnvir.add(fNameArg.getText(), access);
				
				fArgValueToIgnore.add(fNameArg);
				
				final Signature sig = readSignature(node, args.getArgValueNode(fArgIdx_signature));
				fReturnValue = null;
				if (fDefArg != null) {
					fDefArg.acceptInR(SourceAnalyzer.this);
					fArgValueToIgnore.add(fDefArg);
				}
				RMethod rMethod;
				if (fReturnValue instanceof RMethod) {
					rMethod = (RMethod) fReturnValue;
					registerFunctionElement(rMethod, IRLangElement.R_S4METHOD, access, sig);
				}
				else {
					rMethod = new RMethod(fCurrentSourceContainerBuilder.element, IRLangElement.R_S4METHOD, access, null);
					rMethod.complete(null);
					registerFunctionElement(rMethod);
				}
				fReturnValue = null;
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsRemoveMethod implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_fName;
		private final int fArgIdx_signature;
		
		public MethodsRemoveMethod(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_REMOVEMETHOD_args;
			fArgIdx_fName = fArgsDef.indexOf("f");
			fArgIdx_signature = fArgsDef.indexOf("signature");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode fNameArg = args.getArgValueNode(fArgIdx_fName);
			
			if (fNameArg != null && fNameArg.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_DELETE | ElementAccess.A_FUNC;
				access.fNameNode = fNameArg;
				fGenericDefaultEnvir.addLateResolve(fNameArg.getText(), access);
				
				fArgValueToIgnore.add(fNameArg);
				
//				final Signature sig = readSignature(node, args.getArgValueNode(fArgIdx_signature));
//				fReturnValue = null;
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsRemoveMethods implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_fName;
		
		public MethodsRemoveMethods(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_REMOVEMETHODS_args;
			fArgIdx_fName = fArgsDef.indexOf("f");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode fNameArg = args.getArgValueNode(fArgIdx_fName);
			
			if (fNameArg != null && fNameArg.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_DELETE | ElementAccess.A_FUNC;
				access.fNameNode = fNameArg;
				fGenericDefaultEnvir.addLateResolve(fNameArg.getText(), access);
				
				fArgValueToIgnore.add(fNameArg);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	private class MethodsCommonMethodRead implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_fName;
		
		protected MethodsCommonMethodRead(final ArgsDefinition argsDef, final String fNameNodeName) {
			fArgsDef = argsDef;
			fArgIdx_fName = fArgsDef.indexOf(fNameNodeName);
		}
		
		public final void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode fNameNode = args.getArgValueNode(fArgIdx_fName);
			
			if (fNameNode != null && fNameNode.getNodeType() == NodeType.STRING_CONST) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = ElementAccess.A_READ | ElementAccess.A_FUNC;
				access.fNameNode = fNameNode;
				fGenericDefaultEnvir.add(fNameNode.getText(), access);
				fArgValueToIgnore.add(fNameNode);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	protected final class MethodsGetMethod extends MethodsCommonMethodRead {
		
		public MethodsGetMethod(final RCoreFunctions rdef) {
			super(rdef.METHODS_GETMETHOD_args, "f");
		}
		
	}
	
	protected final class MethodsSelectMethod extends MethodsCommonMethodRead {
		
		public MethodsSelectMethod(final RCoreFunctions rdef) {
			super(rdef.METHODS_SELECTMETHOD_args, "f");
		}
		
	}
	
	protected final class MethodsGetMethods extends MethodsCommonMethodRead {
		
		public MethodsGetMethods(final RCoreFunctions rdef) {
			super(rdef.METHODS_GETMETHODS_args, "f");
		}
		
	}
	
	protected final class MethodsFindMethod extends MethodsCommonMethodRead {
		
		public MethodsFindMethod(final RCoreFunctions rdef) {
			super(rdef.METHODS_FINDMETHOD_args, "f");
		}
		
	}
	
	protected final class MethodsSlot implements IFCallAnalyzer {
		
		private final ArgsDefinition fArgsDef;
		private final int fArgIdx_object;
		private final int fArgIdx_slotName;
		
		
		public MethodsSlot(final RCoreFunctions rdef) {
			fArgsDef = rdef.METHODS_SLOT_args;
			fArgIdx_object = fArgsDef.indexOf("object");
			fArgIdx_slotName = fArgsDef.indexOf("name");
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			final ReadedFCallArgs args = RAst.readArgs(node.getArgsChild(), fArgsDef);
			final RAstNode objectArg = args.getArgValueNode(fArgIdx_object);
			final RAstNode slotArg = args.getArgValueNode(fArgIdx_slotName);
			
			if (objectArg != null && objectArg.getNodeType() == NodeType.SYMBOL) {
				final ElementAccess access = new ElementAccess.Default(node);
				access.fFlags = (assignment) ?
						(ElementAccess.A_WRITE | ElementAccess.A_SUB) :
						(ElementAccess.A_READ | ElementAccess.A_SUB);
				access.fNameNode = objectArg;
				fArgValueToIgnore.add(objectArg);
				
				if (slotArg != null && slotArg.getNodeType() == NodeType.STRING_CONST) {
					access.fNextSegment = new SubNamedSlotInFunElementAccess(access, slotArg);
					fArgValueToIgnore.add(slotArg);
				}
				
				fTopScope.addLateResolve(objectArg.getText(), access);
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
	
	protected final class NoDefFallback implements IFCallAnalyzer {
		
		public NoDefFallback() {
		}
		
		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
			fRequest = NO_REQUESTS;
			
			final FCall.Args args = node.getArgsChild();
			if (args.getChildCount() > 0 && assignment) {
				final FCall.Arg firstArg = args.getChild(0);
				final RAstNode argName = firstArg.getNameChild();
				final RAstNode argValue = firstArg.getValueChild();
				if (firstArg.hasValue()
						&& (!firstArg.hasName() || argName.getText().equals("x"))) { 
					final ElementAccess access = new ElementAccess.Default(node);
					access.fFlags = ElementAccess.A_WRITE;
					final String mainName = resolveElementName(argValue, access, false);
					fArgValueToIgnore.add(argValue);
					if (mainName != null) {
						registerInEnvir(S_SEARCH, mainName, access);
					}
				}
			}
			
			node.getArgsChild().acceptInRChildren(SourceAnalyzer.this);
			fReturnValue = null;
		}
		
	}
	
//	protected final class Template1 implements IFCallAnalyzer {
//		
//		public Template1(final RCoreFunctions rdef) {
//		}
//		
//		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
//		}
//		
//	}
	
//	protected final class Template2 implements IFCallAnalyzer {
//		
//		private final ArgsDefinition fArgsDef;
//		
//		public Template2(final RCoreFunctions rdef) {
//			fArgsDef = rdef.;
//		}
//		
//		public void visit(final FCall node, final boolean assignment) throws InvocationTargetException {
//		}
//		
//	}
	
	private Signature readSignature(final RAstNode refNode, final RAstNode sigNode) throws InvocationTargetException {
		if (sigNode != null) {
			fRequest = SIGNATURE_REQUESTS;
			fReturnValue = null;
			sigNode.acceptInR(SourceAnalyzer.this);
			fArgValueToIgnore.add(sigNode);
			if (fReturnValue instanceof ReturnValue) {
				final ReturnValue value = (ReturnValue) fReturnValue;
				fReturnValue = null;
				if (value.returnType == RETURN_METHOD_SIGNATURE) {
					return (Signature) value;
				}
				if (value.returnType == RETURN_STRING_ARRAY) {
					final RAstNode[] nodes = ((NodeArray) value).array;
					final String[] classNames = new String[nodes.length];
					for (int i = 0; i < nodes.length; i++) {
						registerSimpleClassAccessInEnvir(refNode, nodes[i]);
						classNames[i] = nodes[i].getText();
					}
					return new Signature(null, classNames);
				}
			}
			else {
				fReturnValue = null;
				return null;
			}
		}
		return null;
	}
	
	private ArgsDefinition createMethodArgDef(final FDef fdefNode, final Signature sig) {
		final FDef.Args argList = (fdefNode != null) ? fdefNode.getArgsChild() : null;
		final ArgsBuilder b = new ArgsBuilder();
		if (argList != null) {
			final int n = argList.getChildCount();
			if (sig != null && sig.classNames != null) {
				ITER_ARGS: for (int i = 0; i < n; i++) {
					final String argName = argList.getChild(i).getNameChild().getText();
					if (argName != null && sig.argNames != null) {
						for (int j = 0; j < sig.argNames.length; j++) {
							if (argName.equals(sig.argNames[j])) {
								b.add(argName, 0, sig.classNames[j]);
								continue ITER_ARGS;
							}
						}
						continue ITER_ARGS;
					}
					else if (i < sig.classNames.length) {
						b.add(argName, 0, sig.classNames[i]);
						continue ITER_ARGS;
					}
					b.add(argName, 0, null);
					continue ITER_ARGS;
				}
			}
			else { // (sig == null || sigClasses == null)
				ITER_ARGS: for (int i = 0; i < n; i++) {
					final String argName = argList.getChild(i).getNameChild().getText();
					b.add(argName, 0, null);
					continue ITER_ARGS;
				}
			}
		}
		else { // (argList == null)
			if (sig != null && sig.argNames != null && sig.classNames != null) {
				ITER_ARGS: for (int i = 0; i < sig.argNames.length; i++) {
					if (sig.argNames[i] != null) {
						b.add(sig.argNames[i], 0, sig.classNames[i]);
						continue ITER_ARGS;
					}
					else {
						break ITER_ARGS;
					}
				}
			}
		}
		return b.toDef();
	}
	
	private boolean evalBoolean(final RAstNode valueNode, final boolean defaultValue) {
		if (valueNode != null && valueNode.getNodeType() == NodeType.NUM_CONST) {
			if (valueNode.getOperator(0) == RTerminal.TRUE) {
				return true;
			}
			if (valueNode.getOperator(0) == RTerminal.FALSE) {
				return false;
			}
		}
		return defaultValue;
	}
	
	private Envir readScopeArgs(final RAstNode pos, final Envir defaultScope) throws InvocationTargetException {
		fReturnValue = null;
		Envir envir = null;
		if (pos != null) {
			switch (pos.getNodeType()) {
			case NUM_CONST:
				if (pos.getText().equals("1")) { // search pos
					envir = fGlobalEnvir;
					break;
				}
				break;
			case STRING_CONST: // search name
				if (pos.getText().equals(".GlobalEnv")) {
					envir = fGlobalEnvir;
					break;
				}
				if (pos.getText().startsWith("package:")) {
					envir = getPkgEnvir(pos.getText().substring(8));
					break;
				}
				break;
			default:
				// check for environment
				pos.acceptInR(SourceAnalyzer.this);
				if (fReturnValue instanceof Envir) {
					envir = (Envir) fReturnValue;
					break;
				}
				break;
			}
			fArgValueToIgnore.add(pos);
		}
		if (envir != null) {
			return envir;
		}
		return defaultScope;
	}
	
}