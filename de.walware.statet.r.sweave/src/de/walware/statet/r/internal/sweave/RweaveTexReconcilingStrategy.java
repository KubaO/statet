/*******************************************************************************
 * Copyright (c) 2007-2009 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.sweave;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.ui.IEditorInput;

import de.walware.ecommons.ltk.ISourceUnit;
import de.walware.ecommons.ui.text.IEditorInputAcceptor;

import de.walware.statet.r.internal.sweave.editors.RweaveTexDocumentProvider;
import de.walware.statet.r.internal.sweave.model.RweaveTexEditorWorkingCopy;


public class RweaveTexReconcilingStrategy implements IReconcilingStrategy, IReconcilingStrategyExtension, IEditorInputAcceptor {
	
	
	private RweaveTexDocumentProvider fDocumentProvider;
	private IEditorInput fEditorInput;
	private IProgressMonitor fMonitor;
	
	
	public RweaveTexReconcilingStrategy() {
		fDocumentProvider = SweavePlugin.getDefault().getRTexDocumentProvider();
	}
	
	public void initialReconcile() {
		reconcile();
	}
	
	public void setDocument(final IDocument document) {
	}
	
	public void setEditorInput(final IEditorInput input) {
		fEditorInput = input;
	}
	
	public void reconcile(final IRegion partition) {
		reconcile();
	}
	
	public void reconcile(final DirtyRegion dirtyRegion, final IRegion subRegion) {
		reconcile();
	}
	
	public void setProgressMonitor(final IProgressMonitor monitor) {
		fMonitor = monitor;
	}
	
	
	protected void reconcile() {
		final ISourceUnit u = fDocumentProvider.getWorkingCopy(fEditorInput);
		if (u == null || fMonitor.isCanceled()) {
			return;
		}
		if (u instanceof RweaveTexEditorWorkingCopy) {
			((RweaveTexEditorWorkingCopy) u).reconcileR(0, fMonitor);
		}
	}
	
}
