/*******************************************************************************
 * Copyright (c) 2006 StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.nico.ui.views;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.ViewPart;

import de.walware.eclipsecommons.ui.dialogs.Layouter;
import de.walware.eclipsecommons.ui.util.UIAccess;

import de.walware.statet.nico.core.runtime.IToolRunnable;
import de.walware.statet.nico.core.runtime.Queue;
import de.walware.statet.nico.core.runtime.ToolProcess;
import de.walware.statet.nico.ui.IToolRegistry;
import de.walware.statet.nico.ui.IToolRegistryListener;
import de.walware.statet.nico.ui.IToolRunnableAdapter;
import de.walware.statet.nico.ui.NicoUITools;
import de.walware.statet.nico.ui.ToolSessionUIData;
import de.walware.statet.nico.ui.actions.PauseAction;
import de.walware.statet.ui.StatetImages;


/**
 * A view for the queue of a tool process.
 *
 * Usage: This class is not intended to be subclassed.
 */
public class QueueView extends ViewPart {

	
	private class ViewContentProvider implements IStructuredContentProvider, IDebugEventSetListener {

		private volatile boolean fExpectInfoEvent = false;
		private IToolRunnable[] fRefreshData;
		
		
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			
			if (oldInput != null && newInput == null) {
				unregister();
				fPauseAction.setTool(null);
			}
			if (newInput != null) {
				ToolProcess newProcess = (ToolProcess) newInput;

				fPauseAction.setTool(newProcess);
				
				DebugPlugin manager = DebugPlugin.getDefault();
				if (manager != null) {
					manager.addDebugEventListener(this);
				}
			}
		}

		public Object[] getElements(Object inputElement) {

			IToolRunnable[] elements;
			if (fRefreshData != null) {
				elements = fRefreshData;
				fRefreshData = null;
			}
			else {
				elements = new IToolRunnable[0];
				Queue queue = getQueue();
				if (queue != null) {
					fExpectInfoEvent = true;
					queue.sendElements();
				}
			}
			return elements;
		}
		
		private void unregister() {
			
			DebugPlugin manager = DebugPlugin.getDefault();
			if (manager != null) {
				manager.removeDebugEventListener(this);
			}
		}

		public void dispose() {
			
			unregister();
		}
		
		private void setElements(final IToolRunnable[] elements) {
			
			UIAccess.getDisplay().syncExec(new Runnable() {
				public void run() {
					if (!Layouter.isOkToUse(fTableViewer)) {
						return;
					}
					fRefreshData = elements;
					fTableViewer.refresh();
				}
			});
		}
		
		public void handleDebugEvents(DebugEvent[] events) {
			
			ToolProcess process = fProcess;
			if (process == null) {
				return;
			}
			Queue queue = fProcess.getQueue();
			EVENT: for (int i = 0; i < events.length; i++) {
				DebugEvent event = events[i];
				Object source = event.getSource();
				if (source == process) {
					if (event.getKind() == DebugEvent.TERMINATE) {
						fPauseAction.setTool(null);
					}
					continue EVENT;
				}
				if (source == queue) {
					switch (event.getKind()) {
					
					case DebugEvent.CHANGE:
						if (event.getDetail() != DebugEvent.CONTENT) {
							continue EVENT;
						}
						final Queue.Delta delta = (Queue.Delta) event.getData();
						switch (delta.type) {
						case Queue.ENTRIES_ADD:
							if (!fExpectInfoEvent) {
								if (events.length > i+1 && delta.data.length == 1) {
									// Added and removed in same set
									DebugEvent next = events[i+1];
									if (next.getSource() == queue
											&& next.getKind() == DebugEvent.CHANGE
											&& next.getDetail() == DebugEvent.CONTENT) {
										Queue.Delta nextDelta = (Queue.Delta) next.getData();
										if (nextDelta.type == Queue.ENTRY_START_PROCESSING
												&& delta.data[0] == nextDelta.data[0]) {
											i++;
											continue EVENT;
										}
									}
								}
								UIAccess.getDisplay().syncExec(new Runnable() {
									public void run() {
										if (!Layouter.isOkToUse(fTableViewer)) {
											return;
										}
										fTableViewer.add(delta.data);
									}
								});
							}
							continue EVENT;
						
						case Queue.ENTRY_START_PROCESSING:
						case Queue.ENTRIES_DELETE:
							if (!fExpectInfoEvent) {
								UIAccess.getDisplay().syncExec(new Runnable() {
									public void run() {
										if (!Layouter.isOkToUse(fTableViewer)) {
											return;
										}
										fTableViewer.remove(delta.data);
									}
								});
							}
							continue EVENT;
						
//						case Queue.QUEUE_CHANGE:
//							if (!fExpectInfoEvent) {
//								setElements((IToolRunnable[]) event.getData());
//							}
//							continue EVENT;
						}
						continue EVENT;
						
					case DebugEvent.MODEL_SPECIFIC:
						if (event.getDetail() == Queue.QUEUE_INFO && fExpectInfoEvent) {
							fExpectInfoEvent = false;
							setElements((IToolRunnable[]) event.getData());
						}
						continue EVENT;
						
					case DebugEvent.TERMINATE:
						disconnect(process);
						continue EVENT;
						
					default:
						continue EVENT;
					}
				}
			}
		}
	}
	
	private class TableLabelProvider extends LabelProvider implements ITableLabelProvider {

		public Image getColumnImage(Object element, int columnIndex) {
			
			if (columnIndex == 0) {
				return getImage(element);
			}
			return null;
		}
		
		@Override
		public Image getImage(Object element) {
			
			IToolRunnable runnable = (IToolRunnable) element;
			IToolRunnableAdapter adapter = getAdapter(runnable);
			if (adapter != null) {
				ImageDescriptor descriptor = adapter.getImageDescriptor();
				if (descriptor != null) {
					return StatetImages.getCachedImage(descriptor);
				}
			}
			return null;
		}

		public String getColumnText(Object element, int columnIndex) {

			if (columnIndex == 0) {
				return getText(element);
			}
			return ""; //$NON-NLS-1$
		}
		
		public String getText(Object element) {
			
			IToolRunnable runnable = (IToolRunnable) element;
			return runnable.getLabel();
		}
		
	    protected IToolRunnableAdapter getAdapter(IToolRunnable runnable) {
	    	
	        if (!(runnable instanceof IAdaptable)) {
	            return null;
	        }
	        return (IToolRunnableAdapter) ((IAdaptable) runnable)
	                .getAdapter(IToolRunnableAdapter.class);
	    }
	}
	
	
	private TableViewer fTableViewer;
	
	private ToolProcess fProcess; // f�r submit
	private IToolRegistryListener fToolRegistryListener;
	
	private PauseAction fPauseAction;
	
	private Action fSelectAllAction;
	private Action fDeleteAction;
	
	
	@Override
	public void createPartControl(Composite parent) {
		
		fTableViewer = new TableViewer(parent, SWT.MULTI | SWT.V_SCROLL);
		fTableViewer.getTable().setLinesVisible(false);
		fTableViewer.getTable().setHeaderVisible(false);
		new TableColumn(fTableViewer.getTable(), SWT.DEFAULT);
		fTableViewer.getTable().addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				// adapt the column width to the width of the table 
				Table table = fTableViewer.getTable();
				Rectangle area = table.getClientArea();
				TableColumn column = table.getColumn(0);
				column.setWidth(area.width-3); // it looks better with a small gap
			}
		});

		fTableViewer.setContentProvider(new ViewContentProvider());
		fTableViewer.setLabelProvider(new TableLabelProvider());
		
		createActions();
		contributeToActionBars();
		
		// listen on console changes
		IToolRegistry toolRegistry = NicoUITools.getRegistry();
		connect(toolRegistry.getActiveToolSession(getViewSite().getPage()).getProcess());
		fToolRegistryListener = new IToolRegistryListener() {
			public void toolSessionActivated(ToolSessionUIData info) {
				final ToolProcess process = info.getProcess();
				UIAccess.getDisplay().asyncExec(new Runnable() {
					public void run() {
						connect(process);
					}
				});
			}
			public void toolSessionClosed(ToolSessionUIData info) { 
				disconnect(info.getProcess());
			}
		};
		toolRegistry.addListener(fToolRegistryListener, getViewSite().getPage());
	}
	
	private void createActions() {
		
		fPauseAction = new PauseAction();
		
		fSelectAllAction = new Action() {
			public void run() {
				fTableViewer.getTable().selectAll();
			}
		};
		fDeleteAction = new Action() {
			public void run() {
				Queue queue = getQueue();
				if (queue != null) {
					IStructuredSelection selection = (IStructuredSelection) fTableViewer.getSelection();
					Object[] elements = selection.toArray();
					queue.removeElements(elements);
				}
			}
		};
	}

	private void contributeToActionBars() {
		
		IActionBars bars = getViewSite().getActionBars();
		
		bars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), fSelectAllAction);
		bars.setGlobalActionHandler(ActionFactory.DELETE.getId(), fDeleteAction);
		
//		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		
		manager.add(fPauseAction);
	}
	
	private void disconnect(final ToolProcess process) {
		
		UIAccess.getDisplay().syncExec(new Runnable() {
			public void run() {
				if (fProcess != null && fProcess == process) {
					connect(null);
				}
			}
		});
	}

	/** Should only be called inside UI Thread */
	public void connect(final ToolProcess process) {
		
		Runnable runnable = new Runnable() {
			public void run() {
				if (!Layouter.isOkToUse(fTableViewer)) {
					return;
				}
				fProcess = process;
				fPauseAction.setTool(process);
				fTableViewer.setInput(process);
			}
		};
		BusyIndicator.showWhile(UIAccess.getDisplay(), runnable);
	}
	
	/**
	 * Returns the tool process, which this view is connected to.
	 * 
	 * @return a tool process or <code>null</code>, if no process is connected.
	 */
	public ToolProcess getProcess() {
		
		return fProcess;
	}
	
	public Queue getQueue() {
		
		if (fProcess != null) {
			return fProcess.getQueue();
		}
		return null;
	}
	
	
	@Override
	public void setFocus() {
		// Passing the focus request to the viewer's control.

		fTableViewer.getControl().setFocus();
	}
	
	@Override
	public void dispose() {
		
		fPauseAction.dispose();
		fPauseAction = null;
		
		super.dispose();
	}
	
}
