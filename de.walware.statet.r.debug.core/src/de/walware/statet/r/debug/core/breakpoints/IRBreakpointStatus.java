/*=============================================================================#
 # Copyright (c) 2011-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.debug.core.breakpoints;

public interface IRBreakpointStatus {
	
	
	int HIT =                                               0x00000001;
	
	
	int getKind();
	String getLabel();
	
	IRBreakpoint getBreakpoint();
	
}
