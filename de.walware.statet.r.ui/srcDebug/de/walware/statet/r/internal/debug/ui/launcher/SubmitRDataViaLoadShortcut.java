/*=============================================================================#
 # Copyright (c) 2005-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.debug.ui.launcher;

import de.walware.statet.r.launching.SubmitFileViaCommandLaunchShortcut;


/**
 * Launch shortcut, which loads the data file
 * using the <code>load</code> command to R.
 * Does not change the focus by default.
 */
public class SubmitRDataViaLoadShortcut extends SubmitFileViaCommandLaunchShortcut {
	
	
	public SubmitRDataViaLoadShortcut() {
		this(false);
	}
	
	protected SubmitRDataViaLoadShortcut(final boolean gotoConsole) {
		super("de.walware.statet.r.rFileCommand.LoadRData", gotoConsole); //$NON-NLS-1$
	}
	
}
