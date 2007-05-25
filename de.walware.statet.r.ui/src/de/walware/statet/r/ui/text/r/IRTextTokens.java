/*******************************************************************************
 * Copyright (c) 2005-2007 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.ui.text.r;


/**
 * Keys of TextTokens recognized by text-parser (syntax-highlighting)
 *
 * @author Stephan Wahlbrink
 */
public interface IRTextTokens {
	
	public static final String ROOT = "text_R_"; //$NON-NLS-1$
	
	public static final String DEFAULT = ROOT+"rDefault"; //$NON-NLS-1$
	public static final String IDENTIFIER_SUB_ASSIGNMENT = DEFAULT + ".Assignment"; //$NON-NLS-1$
	public static final String IDENTIFIER_SUB_LOGICAL = DEFAULT + ".Logical"; //$NON-NLS-1$
	public static final String IDENTIFIER_SUB_FLOWCONTROL = DEFAULT + ".Flowcontrol"; //$NON-NLS-1$
	public static final String IDENTIFIER_SUB_CUSTOM1 = DEFAULT + ".Custom2"; //$NON-NLS-1$
	public static final String IDENTIFIER_SUB_CUSTOM2 = DEFAULT + ".Custom1"; //$NON-NLS-1$
	
	public static final String UNDEFINED = ROOT+"rUndefined"; //$NON-NLS-1$
	public static final String COMMENT = ROOT+"rComment"; //$NON-NLS-1$
	public static final String STRING = ROOT+"rString"; //$NON-NLS-1$
	
	public static final String NUMBERS = ROOT+"rNumbers"; //$NON-NLS-1$

	/** @see de.walware.statet.r.core.rlang.RTokens */
	public static final String SPECIAL_CONSTANTS = ROOT+"rSpecialConstants"; //$NON-NLS-1$

	/** @see de.walware.statet.r.core.rlang.RTokens */
	public static final String LOGICAL_CONSTANTS = ROOT+"rLogicalConstants"; //$NON-NLS-1$

	/** @see de.walware.statet.r.core.rlang.RTokens */
	public static final String FLOWCONTROL = ROOT+"rFlowcontrol"; //$NON-NLS-1$
	
	/** @see de.walware.statet.r.core.rlang.RTokens */
	public static final String SEPARATORS = ROOT+"rSeparators"; //$NON-NLS-1$

	/** @see de.walware.statet.r.core.rlang.RTokens */
	public static final String ASSIGNMENT = ROOT+"rAssignment"; //$NON-NLS-1$
	public static final String ASSIGNMENT_SUB_EQUALSIGN = ASSIGNMENT + ".Equalsign"; //$NON-NLS-1$
	
	/** @see de.walware.statet.r.core.rlang.RTokens */
	public static final String OPERATORS = ROOT+"rOtherOperators"; //$NON-NLS-1$
	public static final String OPERATORS_SUB_LOGICAL = OPERATORS + ".Logical"; //$NON-NLS-1$
	public static final String OPERATORS_SUB_RELATIONAL = OPERATORS + ".Relational"; //$NON-NLS-1$
	public static final String OPERATORS_SUB_USERDEFINED = OPERATORS + ".Userdefined"; //$NON-NLS-1$
	
	/** @see de.walware.statet.r.core.rlang.RTokens */
	public static final String GROUPING = ROOT+"rGrouping"; //$NON-NLS-1$
	
	/** @see de.walware.statet.r.core.rlang.RTokens */
	public static final String INDEXING = ROOT+"rIndexing";	 //$NON-NLS-1$
	
	public static final String TASK_TAG = ROOT+"taskTag"; //$NON-NLS-1$
	
}
