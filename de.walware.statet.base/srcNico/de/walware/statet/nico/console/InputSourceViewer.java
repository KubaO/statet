/*******************************************************************************
 * Copyright (c) 2005 StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.nico.console;

import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import de.walware.statet.ui.util.DNDUtil;


public class InputSourceViewer extends SourceViewer {

	
	InputSourceViewer(Composite parent) {
		
		super(parent, null, null, false, SWT.SINGLE);
		
		initDND();
		initTabControl();
	}
	

	private void initDND() {
		
		// TODO: Drop Action, related to Eclipse-Bug #106372
		DropTargetListener textDropListener = new DropTargetAdapter() {
			public void dragEnter(DropTargetEvent e) {
				if (e.detail == DND.DROP_DEFAULT) {
					e.detail = DND.DROP_COPY;
				}
			}
			public void dragOperationChanged(DropTargetEvent e) {
				if (e.detail == DND.DROP_DEFAULT) {
					e.detail = DND.DROP_COPY;
				}
			}
			public void drop(DropTargetEvent e) {
				getTextWidget().insert((String)e.data);
			}
		};
		DNDUtil.addDropSupport(this, textDropListener,
				new Transfer[] { TextTransfer.getInstance() });
	}
	
	private void initTabControl() {
		// disable traverse on TAB key event, to enable TAB char insertion.
		
		getTextWidget().addListener(SWT.Traverse, new Listener() {
			public void handleEvent(Event event) {
				
				if (event.stateMask == SWT.NONE && event.character == SWT.TAB) {
					event.doit = false;
				}
			}
		});
	}
	
    /**
     * Sets the tab width used by this viewer.
     * 
     * @param tabWidth
     *            the tab width used by this viewer
     */
    public void setTabWidth(int tabWidth) {
    	
        StyledText styledText = getTextWidget();
        int oldWidth = styledText.getTabs();
        if (tabWidth != oldWidth) {
            styledText.setTabs(tabWidth);
        }
    }
    
    
}