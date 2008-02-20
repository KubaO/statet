/*******************************************************************************
 * Copyright (c) 2005-2007 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.nico.core.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchesListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamsProxy;

import de.walware.statet.nico.core.ITool;
import de.walware.statet.nico.core.NicoCore;
import de.walware.statet.nico.core.runtime.ToolController.IToolStatusListener;


/**
 * Provides <code>IProcess</code> for a <code>ToolController</code>.
 */
public class ToolProcess<WorkspaceType extends ToolWorkspace>
		extends PlatformObject implements IProcess, ITool, IToolStatusListener {
	
	
	public static final int MASK_STATUS = 0x001000;
	public static final int MASK_REQUEST = 0x002000;
	
	private static final int PROCESS = 0x010;
	private static final int IDLE = 0x020;
	private static final int PAUSE = 0x040;
	private static final int OTHER = 0x080;
	private static final int TERMINATE = 0x0f0;
	
	
	/**
	 * Constant for detail of a DebugEvent, signalising that
	 * the process/controller started to work/calculate.
	 * 
	 * Applicable for DebugEvents of kind <code>MODEL_SPECIFIC</code>.
	 * The status can be ended by another status event or by a
	 * DebugEvent of kind <code>TERMINATE</code>.
	 */
	public static final int STATUS_PROCESS = MASK_STATUS | PROCESS;
	
	/**
	 * Constant for detail of a DebugEvent, signalising that
	 * the process/controller switched into idle mode.
	 * 
	 * Applicable for DebugEvents of kind <code>MODEL_SPECIFIC</code>.
	 * The status can be ended by another status event or by a
	 * DebugEvent of kind <code>TERMINATE</code>.
	 */
	public static final int STATUS_IDLE = MASK_STATUS | IDLE;
	
	/**
	 * Constant for detail of a DebugEvent, signalising that
	 * the process/controller was paused.
	 * 
	 * Applicable for DebugEvents of kind <code>MODEL_SPECIFIC</code>.
	 * The status can be ended by another status event or by a
	 * DebugEvent of kind <code>TERMINATE</code>.
	 */
	public static final int STATUS_PAUSE = MASK_STATUS | PAUSE;
	
	
	public static final int REQUEST_PAUSE = MASK_REQUEST | PAUSE | 0x1;
	public static final int REQUEST_PAUSE_CANCELED = MASK_REQUEST | PAUSE | 0x2;
	
	public static final int REQUEST_TERMINATE = MASK_REQUEST | TERMINATE | 0x1;
	public static final int REQUEST_TERMINATE_CANCELED = MASK_REQUEST | TERMINATE | 0x2;
	
	public static ToolStatus getChangedToolStatus(final DebugEvent event) {
		switch (event.getKind()) {
		case DebugEvent.CREATE:
			return ToolStatus.STARTING;
		case DebugEvent.MODEL_SPECIFIC:
			switch (event.getDetail()) {
			case STATUS_PROCESS:
				return ToolStatus.STARTED_PROCESSING;
			case STATUS_IDLE:
				return ToolStatus.STARTED_IDLING;
			case STATUS_PAUSE:
				return ToolStatus.STARTED_PAUSED;
			default:
				return null;
			}
		case DebugEvent.TERMINATE:
			return ToolStatus.TERMINATED;
		default:
			return null;
		}
	}
	
	
	private final ILaunch fLaunch;
	private Set<String> fFeatureSets = new HashSet<String>();
	private final String fName;
	private String fToolLabelShort;
	
	private ToolController<WorkspaceType> fController;
	private Queue fQueue;
	private History fHistory;
	private WorkspaceType fWorkspaceData;
	
	private final Map<String, String> fAttributes;
	private final boolean fCaptureOutput;
	
	private volatile ToolStatus fStatus = ToolStatus.STARTING;
	protected volatile int fExitValue = 0;
	
	
	public ToolProcess(final ILaunch launch, final String type, final String prefix, final String name) {
		fLaunch = launch;
		fName = name;
		fAttributes = new HashMap<String, String>(5);
		fToolLabelShort = prefix;
		doSetAttribute(IProcess.ATTR_PROCESS_LABEL,
				computerConsoleLabel(ToolStatus.STARTING.getMarkedLabel()));
		doSetAttribute(IProcess.ATTR_PROCESS_TYPE, type);
		
		final String captureOutput = launch.getAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT);
		fCaptureOutput = !("false".equals(captureOutput)); //$NON-NLS-1$
		
		DebugPlugin.getDefault().getLaunchManager().addLaunchListener(new ILaunchesListener() {
			public void launchesAdded(final ILaunch[] launches) {
			}
			public void launchesChanged(final ILaunch[] launches) {
			}
			public void launchesRemoved(final ILaunch[] launches) {
				for (final ILaunch launch : launches) {
					if (fLaunch == launch) {
						final DebugPlugin plugin = DebugPlugin.getDefault();
						if (plugin != null) {
							plugin.getLaunchManager().removeLaunchListener(this);
							dispose();
						}
					}
				}
				
			}
		});
	}
	
	public void init(final ToolController<WorkspaceType> controller) {
		fController = controller;
		fHistory = new History(this);
		fQueue = fController.getQueue();
		fWorkspaceData = fController.fWorkspaceData;
		
		fLaunch.addProcess(this);
		fireEvent(new DebugEvent(ToolProcess.this, DebugEvent.CREATE));
	}
	
	
	public void registerFeatureSet(final String featureSetID) {
		fFeatureSets.add(featureSetID);
	}
	
	public boolean isProvidingFeatureSet(final String featureSetID) {
		return fFeatureSets.contains(featureSetID);
	}
	
	private String computerConsoleLabel(final String statusLabel) {
		return fToolLabelShort + " " + fName + " " + statusLabel; //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	public String getLabel() {
		return fName;
	}
	
	public String getToolLabel(final boolean longLabel) {
		if (longLabel) {
			return fToolLabelShort + ' ' + fName;
		}
		return fToolLabelShort;
	}
	
	public ILaunch getLaunch() {
		return fLaunch;
	}
	
	public ToolController getController() {
		return fController;
	}
	
	public History getHistory() {
		return fHistory;
	}
	
	public Queue getQueue() {
		return fQueue;
	}
	
	public IStreamsProxy getStreamsProxy() {
		return (fCaptureOutput && fController != null) ? fController.getStreams() : null;
	}
	
	
	public WorkspaceType getWorkspaceData() {
		return fWorkspaceData;
	}
	
	public void setAttribute(final String key, final String value) {
		final DebugEvent event = doSetAttribute(key, value);
		if (event != null) {
			fireEvent(event);
		}
	}
	
	private DebugEvent doSetAttribute(final String key, final String value) {
		synchronized (fAttributes) {
			final String oldValue = fAttributes.put(key, value);
			if (oldValue == value
					|| (oldValue != null && oldValue.equals(value)) ) {
				return null;
			}
			final DebugEvent event = new DebugEvent(ToolProcess.this, DebugEvent.CHANGE);
			event.setData(new String[] { key, oldValue, value });
			return event;
		}
	}
	
	public String getAttribute(final String key) {
		synchronized (fAttributes) {
			return fAttributes.get(key);
		}
	}
	
	public ToolStatus getToolStatus() {
		return fStatus;
	}
	
	@Override
	public Object getAdapter(final Class adapter) {
		if (adapter.equals(IProcess.class)) {
			return this;
		}
		if (adapter.equals(IDebugTarget.class)) {
			final ILaunch launch = getLaunch();
			final IDebugTarget[] targets = launch.getDebugTargets();
			for (int i = 0; i < targets.length; i++) {
				if (this.equals(targets[i].getProcess())) {
					return targets[i];
				}
			}
			return null;
		}
		if (adapter.equals(ILaunch.class)) {
			return getLaunch();
		}
		return super.getAdapter(adapter);
	}
	
	
	public boolean canTerminate() {
		return (!isTerminated());
	}
	
	public void terminate() throws DebugException {
		final ToolController controller = fController;
		if (controller != null) {
			controller.scheduleQuit();
		}
	}
	
	public boolean isTerminated() {
		return fStatus == ToolStatus.TERMINATED;
	}
	
	public int getExitValue() throws DebugException {
		if (!isTerminated()) {
			throw new DebugException(new Status(
					IStatus.ERROR, NicoCore.PLUGIN_ID, 0,
					"Exit value is not available until process terminates.", //$NON-NLS-1$
					null));
		}
		return fExitValue;
	}
	
	public void controllerStatusRequested(final ToolStatus currentStatus, final ToolStatus requestedStatus, final List<DebugEvent> eventCollection) {
		switch(requestedStatus) {
		case STARTED_PAUSED:
			eventCollection.add(new DebugEvent(ToolProcess.this,
					DebugEvent.MODEL_SPECIFIC, REQUEST_PAUSE) );
			break;
		case TERMINATED:
			eventCollection.add(new DebugEvent(ToolProcess.this,
					DebugEvent.MODEL_SPECIFIC, REQUEST_TERMINATE) );
			break;
		}
	}
	
	public void controllerStatusRequestCanceled(final ToolStatus currentStatus, final ToolStatus requestedStatus, final List<DebugEvent> eventCollection) {
		switch(requestedStatus) {
		case STARTED_PAUSED:
			eventCollection.add(new DebugEvent(ToolProcess.this,
					DebugEvent.MODEL_SPECIFIC, REQUEST_PAUSE_CANCELED) );
			break;
		case TERMINATED:
			eventCollection.add(new DebugEvent(ToolProcess.this,
					DebugEvent.MODEL_SPECIFIC, REQUEST_TERMINATE_CANCELED) );
			break;
		}
	}
	
	/** Called by Controller */
	public void controllerStatusChanged(final ToolStatus oldStatus, final ToolStatus newStatus, final List<DebugEvent> eventCollection) {
		fStatus = newStatus;
		switch(newStatus) {
		
		case STARTED_PROCESSING:
			eventCollection.add(new DebugEvent(ToolProcess.this,
					DebugEvent.MODEL_SPECIFIC, STATUS_PROCESS) );
			break;
		case STARTED_IDLING:
			eventCollection.add(new DebugEvent(ToolProcess.this,
					DebugEvent.MODEL_SPECIFIC, STATUS_IDLE) );
			break;
		case STARTED_PAUSED:
			eventCollection.add(new DebugEvent(ToolProcess.this,
					DebugEvent.MODEL_SPECIFIC, STATUS_PAUSE) );
			break;
			
		case TERMINATED:
			fController = null;
			eventCollection.add(new DebugEvent(ToolProcess.this,
					DebugEvent.TERMINATE) );
			break;
		}
		
		synchronized (fAttributes) {
			final DebugEvent nameEvent = doSetAttribute(IProcess.ATTR_PROCESS_LABEL,
					computerConsoleLabel(newStatus.getMarkedLabel()));
			if (nameEvent != null) {
				eventCollection.add(nameEvent);
			}
		}
	}
	
	protected void dispose() {
		if (fQueue != null) {
			fQueue.dispose();
		}
	}
	
	/**
	 * Fires the given debug events.
	 * 
	 * @param event array with debug events to fire
	 */
	protected void fireEvent(final DebugEvent event) {
		final DebugPlugin manager = DebugPlugin.getDefault();
		if (manager != null) {
			manager.fireDebugEventSet(new DebugEvent[] { event });
		}
	}
	
}
