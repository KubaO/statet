/*******************************************************************************
 * Copyright (c) 2010-2012 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.ui.dataeditor;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.ibm.icu.util.TimeZone;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.sort.ISortModel;
import org.eclipse.nebula.widgets.nattable.sort.SortDirectionEnum;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.statushandlers.StatusManager;

import de.walware.ecommons.FastList;
import de.walware.ecommons.collections.ConstList;
import de.walware.ecommons.ts.ISystemRunnable;
import de.walware.ecommons.ts.ITool;
import de.walware.ecommons.ts.IToolRunnable;
import de.walware.ecommons.ts.IToolService;
import de.walware.ecommons.ui.util.UIAccess;

import de.walware.rj.data.RCharacterStore;
import de.walware.rj.data.RDataUtil;
import de.walware.rj.data.RFactorStore;
import de.walware.rj.data.RIntegerStore;
import de.walware.rj.data.RObject;
import de.walware.rj.data.RObjectFactory;
import de.walware.rj.data.RStore;
import de.walware.rj.data.RVector;
import de.walware.rj.data.UnexpectedRDataException;
import de.walware.rj.data.defaultImpl.RFactorDataStruct;
import de.walware.rj.data.defaultImpl.RObjectFactoryImpl;
import de.walware.rj.eclient.IRToolService;
import de.walware.rj.services.FunctionCall;

import de.walware.statet.r.core.model.RElementName;
import de.walware.statet.r.internal.ui.dataeditor.Store.Item;
import de.walware.statet.r.internal.ui.dataeditor.Store.LoadDataException;
import de.walware.statet.r.internal.ui.dataeditor.Store.Lock;
import de.walware.statet.r.internal.ui.intable.InfoString;
import de.walware.statet.r.nico.ICombinedRDataAdapter;
import de.walware.statet.r.ui.RUI;
import de.walware.statet.r.ui.dataeditor.IRDataTableInput;
import de.walware.statet.r.ui.dataeditor.IRDataTableVariable;
import de.walware.statet.r.ui.dataeditor.RDataTableColumn;
import de.walware.statet.r.ui.dataeditor.RToolDataTableInput;


public abstract class AbstractRDataProvider<T extends RObject> implements IDataProvider {
	
	
	public static final Object LOADING = new InfoString("loading...");
	public static final Object ERROR = new InfoString("ERROR");
	
	protected static final RElementName BASE_NAME = RElementName.create(RElementName.MAIN_DEFAULT, "x"); //$NON-NLS-1$
	
	
	static void checkCancel(final Exception e) throws CoreException {
		if (e instanceof CoreException
				&& ((CoreException) e).getStatus().getSeverity() == IStatus.CANCEL) {
			throw (CoreException) e;
		}
	}
	
	static void cleanTmp(final String name, final IRToolService r, final IProgressMonitor monitor) throws CoreException {
		final FunctionCall call = r.createFunctionCall(RJTmp.REMOVE); 
		call.addChar(RJTmp.NAME_PAR, name);
		call.evalVoid(monitor);
	}
	
	
	public static final class SortColumn {
		
		
		public final int columnIdx;
		public final boolean decreasing;
		
		
		public SortColumn(final int columnIdx, final boolean decreasing) {
			this.columnIdx = columnIdx;
			this.decreasing = decreasing;
		}
		
		
		@Override
		public int hashCode() {
			return (decreasing) ? (-columnIdx) : columnIdx;
		}
		
		@Override
		public boolean equals(final Object obj) {
			if (!(obj instanceof SortColumn)) {
				return false;
			}
			final SortColumn other = (SortColumn) obj;
			return (columnIdx == other.columnIdx && decreasing == other.decreasing);
		}
		
	}
	
	public static interface IDataProviderListener {
		
		
		static final int ERROR_STRUCT_CHANGED = 1;
		
		
		void onInputInitialized(boolean structChanged);
		
		void onInputFailed(int error);
		
		void onRowCountChanged();
		
		void onRowsChanged(int begin, int end);
		
	}
	
	
	private class MainLock extends Lock {
		
		boolean scheduled;
		Object waiting;
		
		@Override
		void schedule(final Object obj) {
			if (!scheduled) {
				scheduled = true;
				AbstractRDataProvider.this.schedule(fUpdateRunnable);
				if (obj != null) {
					waiting = obj;
					try {
						fFragmentsLock.wait(25);
					}
					catch (final InterruptedException e) {
					}
					finally {
						if (waiting == obj) {
							waiting = null;
						}
					}
				}
			}
		}
		
		void notify(final Object obj) {
			if (obj == waiting) {
				notifyAll();
			}
		}
		
	}
	
	protected class ColumnDataProvider implements IDataProvider {
		
		
		public ColumnDataProvider() {
		}
		
		
		@Override
		public int getColumnCount() {
			return AbstractRDataProvider.this.getColumnCount();
		}
		
		@Override
		public int getRowCount() {
			return 1;
		}
		
		@Override
		public Object getDataValue(final int columnIndex, final int rowIndex) {
			try {
				final Store.Fragment<T> fragment = fDataStore.getFor(0, columnIndex);
				if (fragment != null) {
					return getColumnName(fragment, columnIndex);
				}
				else {
					return LOADING;
				}
			}
			catch (final LoadDataException e) {
				return handleLoadDataException(e);
			}
		}
		
		@Override
		public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue) {
			throw new UnsupportedOperationException();
		}
		
	}
	
	protected class RowDataProvider implements IDataProvider {
		
		
		public RowDataProvider() {
		}
		
		
		@Override
		public int getColumnCount() {
			return 1;
		}
		
		@Override
		public int getRowCount() {
			return AbstractRDataProvider.this.getRowCount();
		}
		
		@Override
		public Object getDataValue(final int columnIndex, final int rowIndex) {
			try {
				final Store.Fragment<RVector<?>> fragment = fRowNamesStore.getFor(rowIndex, 0);
				if (fragment != null) {
					return fragment.rObject.getData().get(rowIndex - fragment.beginRowIdx);
				}
				else {
					return LOADING;
				}
			}
			catch (final LoadDataException e) {
				return handleLoadDataException(e);
			}
		}
		
		@Override
		public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue) {
			throw new UnsupportedOperationException();
		}
		
	}
	
	protected class SortModel implements ISortModel {
		
		
		@Override
		public List<Integer> getSortedColumnIndexes() {
			final SortColumn sortColumn = getSortColumn();
			if (sortColumn != null) {
				return Collections.singletonList(sortColumn.columnIdx);
			}
			return Collections.<Integer>emptyList();
		}
		
		@Override
		public void sort(final int columnIndex, final SortDirectionEnum sortDirection, final boolean accumulate) {
			SortColumn sortColumn;
			switch (sortDirection) {
			case ASC:
				sortColumn = new SortColumn(columnIndex, false);
				break;
			case DESC:
				sortColumn = new SortColumn(columnIndex, true);
				break;
			default:
				sortColumn = null;
				break;
			}
			setSortColumn(sortColumn);
		}
		
		@Override
		public int getSortOrder(final int columnIndex) {
			final SortColumn sortColumn = getSortColumn();
			if (sortColumn != null && sortColumn.columnIdx == columnIndex) {
				return 0;
			}
			return -1;
		}
		
		@Override
		public boolean isColumnIndexSorted(final int columnIndex) {
			final SortColumn sortColumn = getSortColumn();
			if (sortColumn != null && sortColumn.columnIdx == columnIndex) {
				return true;
			}
			return false;
		}
		
		@Override
		public SortDirectionEnum getSortDirection(final int columnIndex) {
			final SortColumn sortColumn = getSortColumn();
			if (sortColumn != null && sortColumn.columnIdx == columnIndex) {
				return (!sortColumn.decreasing) ? SortDirectionEnum.ASC : SortDirectionEnum.DESC;
			}
			return SortDirectionEnum.NONE;
		}
		
		@Override
		public void clear() {
			setSortColumn(null);
		}
		
		@Override
		public List<Comparator> getComparatorsForColumnIndex(final int columnIndex) {
			throw new UnsupportedOperationException();
		}
		
	}
	
	
	private final IToolRunnable fInitRunnable = new ISystemRunnable() {
		
		@Override
		public String getTypeId() {
			return "r/dataeditor/init";
		}
		
		@Override
		public String getLabel() {
			return "Prepare Data Viewer (" + fInput.getLastName() + ")";
		}
		
		@Override
		public boolean isRunnableIn(final ITool tool) {
			return true; // TODO
		}
		
		@Override
		public boolean changed(final int event, final ITool tool) {
			if (event == MOVING_FROM) {
				return false;
			}
			return true;
		}
		
		@Override
		public void run(final IToolService service,
				final IProgressMonitor monitor) throws CoreException {
			runInit((IRToolService) service, monitor);
		}
		
	};
	
	private final IToolRunnable fUpdateRunnable = new ISystemRunnable() {
		
		@Override
		public String getTypeId() {
			return "r/dataeditor/load"; //$NON-NLS-1$
		}
		
		@Override
		public String getLabel() {
			return "Load Data (" + fInput.getLastName() + ")";
		}
		
		@Override
		public boolean isRunnableIn(final ITool tool) {
			return true; // TODO
		}
		
		@Override
		public boolean changed(final int event, final ITool tool) {
			switch (event) {
			case MOVING_FROM:
				return false;
			case REMOVING_FROM:
			case BEING_ABANDONED:
				synchronized (fFragmentsLock) {
					fFragmentsLock.scheduled = false;
					fFragmentsLock.notifyAll();
				}
				break;
			default:
				break;
			}
			return true;
		}
		
		@Override
		public void run(final IToolService service,
				final IProgressMonitor monitor) throws CoreException {
			runUpdate((IRToolService) service, monitor);
		}
		
	};
	
	private final IToolRunnable fCleanRunnable = new ISystemRunnable() {
		
		@Override
		public String getTypeId() {
			return "r/dataeditor/clean"; //$NON-NLS-1$
		}
		
		@Override
		public String getLabel() {
			return "Clean Cache (" + fInput.getLastName() + ")";
		}
		
		@Override
		public boolean isRunnableIn(final ITool tool) {
			return true; // TODO
		}
		
		@Override
		public boolean changed(final int event, final ITool tool) {
			switch (event) {
			case MOVING_FROM:
			case REMOVING_FROM:
				return false;
			default:
				break;
			}
			return true;
		}
		
		@Override
		public void run(final IToolService service,
				final IProgressMonitor monitor) throws CoreException {
			runClean((IRToolService) service, monitor);
		}
		
	};
	
	
	private final Display fRealm;
	
	protected final IRDataTableInput fInput;
	
	private final int fColumnCount;
	private int fFullRowCount;
	private int fRowCount;
	
	private final FastList<IDataProviderListener> fDataListeners =
			new FastList<IDataProviderListener>(IDataProviderListener.class);
	
	private boolean fInitScheduled;
	
	protected RDataTableContentDescription fDescription;
	
	private final IDataProvider fRowDataProvider;
	private final IDataProvider fColumnDataProvider;
	
	private final MainLock fFragmentsLock = new MainLock();
	
	private final Store<T> fDataStore;
	private final Store<RVector<?>> fRowNamesStore;
	
	private boolean fUpdateSorting;
	private boolean fUpdateFiltering;
	
	private final StringBuilder fRStringBuilder = new StringBuilder(128);
	private String fRCacheId; // only in R jobs
	private T fRObjectStruct;
	
	private final ISortModel fSortModel;
	private SortColumn fSortColumn = null;
	private String fRCacheSort; // only in R jobs
	
	private String fFilter;
	private String fRCacheFilter;
	
	private boolean fUpdateIdx; // only in R jobs
	private String fRCacheIdx; // only in R jobs
	private String fRCacheIdxR; // only in R jobs
	
	private final FindManager fFindManager;
	
	
	protected AbstractRDataProvider(final IRDataTableInput input, final T initialRObject) {
		fRealm = UIAccess.getDisplay();
		fInput = input;
		
		fFullRowCount = fRowCount = getRowCount(initialRObject);
		fColumnCount = getColumnCount(initialRObject);
		
		final int dataMax;
		if (fColumnCount <= 25) {
			dataMax = 10;
		}
		else if (fColumnCount <= 50) {
			dataMax = 20;
		}
		else {
			dataMax = 25;
		}
		fDataStore = new Store<T>(fFragmentsLock, fColumnCount, fRowCount, dataMax);
		fRowNamesStore = new Store<RVector<?>>(fFragmentsLock, 1, fRowCount, 10);
		fFindManager = new FindManager(this);
		
		fColumnDataProvider = createColumnDataProvider();
		fRowDataProvider = createRowDataProvider();
		fSortModel = createSortModel();
	}
	
	
	public abstract RObject getRObject();
	
	final int getLockState() {
		return fFragmentsLock.state;
	}
	
	final void schedule(final IToolRunnable runnable) {
		try {
			final ITool tool = ((RToolDataTableInput) fInput).getTool();
			final IStatus status = tool.getQueue().add(runnable);
			if (status.getSeverity() == IStatus.ERROR && !tool.isTerminated()) {
				throw new CoreException(status);
			}
		}
		catch (final CoreException e) {
			clear(Lock.ERROR_STATE);
			StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, -1,
					"An error occurred when scheduling job for data viewer.", e));
		}
	}
	
	private void runInit(final IRToolService r, final IProgressMonitor monitor) throws CoreException {
		try {
			if (fRCacheId == null) {
				r.evalVoid("require(\"rj\", quietly = TRUE)", monitor);
				final FunctionCall call = r.createFunctionCall(RJTmp.CREATE_ID);
				call.addChar("viewer"); //$NON-NLS-1$
				fRCacheId = RDataUtil.checkSingleCharValue(call.evalData(monitor));
			}
		}
		catch (final Exception e) {
			synchronized (fInitRunnable) {
				fInitScheduled = false;
			}
			checkCancel(e);
			clear(Lock.ERROR_STATE);
			StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, -1,
					"An error occurred when preparing tmp variables for data viewer.", e));
			
			fRealm.syncExec(new Runnable() {
				@Override
				public void run() {
					for (final IDataProviderListener listener : fDataListeners.toArray()) {
						listener.onInputFailed(0);
					}
				}
			});
			return;
		}
		
		try {
			final RObject rObject = (r instanceof ICombinedRDataAdapter) ?
					((ICombinedRDataAdapter) r).evalCombinedStruct(fInput.getElementName(), 0, 1, monitor) :
						r.evalData(fInput.getFullName(), null, RObjectFactory.F_ONLY_STRUCT, 1, monitor) ;
			fRObjectStruct = validateObject(rObject);
		}
		catch (final Exception e) {
			synchronized (fInitRunnable) {
				fInitScheduled = false;
			}
			checkCancel(e);
			clear(Lock.RELOAD_STATE);
			StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, -1,
					"An error occurred when initializing structure data for data viewer.", e));
			
			fRealm.syncExec(new Runnable() {
				@Override
				public void run() {
					for (final IDataProviderListener listener : fDataListeners.toArray()) {
						listener.onInputFailed(IDataProviderListener.ERROR_STRUCT_CHANGED);
					}
				}
			});
			return;
		}
		final RDataTableContentDescription description;
		try {
			description = loadDescription(fInput.getElementName(), fRObjectStruct, r, monitor);
		}
		catch (final Exception e) {
			synchronized (fInitRunnable) {
				fInitScheduled = false;
			}
			checkCancel(e);
			clear(Lock.RELOAD_STATE);
			StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, -1,
					"An error occurred when initializing default formats for data viewer.", e));
			return;
		}
		
		fRealm.syncExec(new Runnable() {
			@Override
			public void run() {
				fDescription = description;
				final int rowCount = getRowCount(fRObjectStruct);
				final boolean rowsChanged = (rowCount != getRowCount());
				clear(0, rowCount, rowCount, true, true, true);
				
				synchronized (fInitRunnable) {
					fInitScheduled = false;
				}
//				if (rowsChanged) {
//					for (final IDataProviderListener listener : dataListeners) {
//						listener.onRowCountChanged();
//					}
//				}
				for (final IDataProviderListener listener : fDataListeners.toArray()) {
					listener.onInputInitialized(rowsChanged);
				}
			}
		});
	}
	
	final String createTmp(final String key) {
		return fRCacheId + key;
	}
	
	private void runUpdate(final IRToolService r, final IProgressMonitor monitor) throws CoreException {
		boolean work = true;
		while (work) {
			try {
				final Item<T>[] dataToUpdate;
				final Item<RVector<?>>[] rowNamesToUpdate;
				
				boolean updateSorting = false;
				boolean updateFiltering = false;
				work = false;
				
				synchronized (fFragmentsLock) {
					dataToUpdate = fDataStore.internalForUpdate();
					rowNamesToUpdate = fRowNamesStore.internalForUpdate();
					
					if (fUpdateSorting) {
						updateSorting = true;
					}
					if (fUpdateFiltering) {
						updateFiltering = true;
					}
					fFragmentsLock.scheduled = false;
				}
				
				if (updateSorting) {
					updateSorting(r, monitor);
					work = true;
				}
				if (updateFiltering) {
					updateFiltering(r, monitor);
					work = true;
				}
				if (fUpdateIdx) {
					updateIdx(r, monitor);
					work = true;
				}
				
				for (int i = 0; i < rowNamesToUpdate.length; i++) {
					final Item<RVector<?>> item = rowNamesToUpdate[i];
					if (item == null) {
						break;
					}
					work = true;
					synchronized (fFragmentsLock) {
						if (!item.scheduled) {
							continue;
						}
					}
					final RVector<?> fragment = loadRowNamesFragment(item, r, monitor);
					synchronized (fFragmentsLock) {
						if (!item.scheduled) {
							continue;
						}
						item.fragment = new Store.Fragment<RVector<?>>(fragment,
								item.beginRowIdx, item.endRowIdx,
								item.beginColumnIdx, item.endColumnIdx );
						item.scheduled = false;
						
						fFragmentsLock.notify(item);
					}
					notifyListener(item);
				}
				for (int i = 0; i < dataToUpdate.length; i++) {
					final Item<T> item = dataToUpdate[i];
					if (item == null) {
						break;
					}
					work = true;
					synchronized (fFragmentsLock) {
						if (!item.scheduled) {
							continue;
						}
					}
					final T fragment = loadDataFragment(item, r, monitor);
					synchronized (fFragmentsLock) {
						if (!item.scheduled) {
							continue;
						}
						item.fragment = new Store.Fragment<T>(fragment,
								item.beginRowIdx, item.endRowIdx,
								item.beginColumnIdx, item.endColumnIdx );
						item.scheduled = false;
						
						fFragmentsLock.notify(item);
					}
					notifyListener(item);
				}
			}
			catch (final Exception e) {
				checkCancel(e);
				clear(Lock.RELOAD_STATE);
				StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, -1,
						NLS.bind("An error occurred when loading data of ''{0}'' for data viewer.", fInput.getFullName()), e));
				return;
			}
		}
	}
	
	private void updateSorting(
			final IRToolService r, final IProgressMonitor monitor) throws UnexpectedRDataException, CoreException {
		cleanSorting(r, monitor);
		
		final SortColumn sortColumn;
		synchronized (fFragmentsLock) {
			sortColumn = fSortColumn;
			fUpdateSorting = false;
		}
		if (sortColumn != null) {
			if (fRCacheSort == null) {
				fRCacheSort = fRCacheId + ".order"; //$NON-NLS-1$
			}
			final FunctionCall call = r.createFunctionCall(RJTmp.SET); 
			call.addChar(RJTmp.NAME_PAR, fRCacheSort);
			final StringBuilder cmd = getRCmdStringBuilder();
			appendOrderCmd(cmd, sortColumn);
			call.add(RJTmp.VALUE_PAR, cmd.toString()); 
			call.evalVoid(monitor);
		}
	}
	
	private void updateFiltering(
			final IRToolService r, final IProgressMonitor monitor) throws UnexpectedRDataException, CoreException {
		cleanFiltering(r, monitor);
		
		String filter;
		synchronized (fFragmentsLock) {
			filter = fFilter;
			fUpdateFiltering = false;
		}
		final int filteredRowCount;
		if (filter == null) {
			filteredRowCount = getFullRowCount();
		}
		else {
			if (fRCacheFilter == null) {
				fRCacheFilter = fRCacheId + ".include"; //$NON-NLS-1$
			}
			{	final FunctionCall call = r.createFunctionCall(RJTmp.SET); 
				call.addChar(RJTmp.NAME_PAR, fRCacheFilter);
				call.add(RJTmp.VALUE_PAR, filter); 
				call.evalVoid(monitor);
			}
			{	final FunctionCall call = r.createFunctionCall(RJTmp.GET_FILTERED_COUNT);
				call.addChar(RJTmp.FILTER_PAR, fRCacheFilter);
				filteredRowCount = RDataUtil.checkSingleIntValue(call.evalData(monitor));
			}
		}
		fRealm.syncExec(new Runnable() {
			@Override
			public void run() {
				clear(0, filteredRowCount, getFullRowCount(), false, false, false);
				for (final IDataProviderListener listener : fDataListeners.toArray()) {
					listener.onRowCountChanged();
				}
			}
		});
	}
	
	private void updateIdx(
			final IRToolService r, final IProgressMonitor monitor) throws UnexpectedRDataException, CoreException {
		cleanIdx(r, monitor);
		if (fRCacheSort != null || fRCacheFilter != null) {
			if (fRCacheIdx == null) {
				fRCacheIdx = fRCacheId + ".idx";
			}
			if (fRCacheFilter == null) { // fRCacheSort != null
				final FunctionCall call = r.createFunctionCall(RJTmp.SET);
				call.addChar(RJTmp.NAME_PAR, fRCacheIdx);
				call.add(RJTmp.VALUE_PAR, RJTmp.ENV+'$'+ fRCacheSort);
				call.evalVoid(monitor);
			}
			else if (fRCacheSort == null) { // fRCacheFilter != null
				final FunctionCall call = r.createFunctionCall(RJTmp.SET_WHICH_INDEX);
				call.addChar(RJTmp.NAME_PAR, fRCacheIdx);
				call.addChar(RJTmp.FILTER_PAR, fRCacheFilter);
				call.evalVoid(monitor);
			}
			else { // fRCacheSort != null && fRCacheFilter != null
				final FunctionCall call = r.createFunctionCall(RJTmp.SET_FILTERED_INDEX);
				call.addChar(RJTmp.NAME_PAR, fRCacheIdx);
				call.addChar(RJTmp.FILTER_PAR, fRCacheFilter);
				call.addChar(RJTmp.INDEX_PAR, fRCacheSort);
				call.evalVoid(monitor);
			}
		}
		fUpdateIdx = false;
	}
	
	String checkFilter() {
		return fRCacheFilter;
	}
	
	String checkRevIndex(
			final IRToolService r, final IProgressMonitor monitor) throws CoreException {
		if (fRCacheIdx != null && fRCacheIdxR == null) {
			final String name = fRCacheIdx + ".r";
			try {
				final FunctionCall call = r.createFunctionCall(RJTmp.SET_REVERSE_INDEX);
				call.addChar(RJTmp.NAME_PAR, name);
				call.addChar(RJTmp.INDEX_PAR, fRCacheIdx);
				call.addInt(RJTmp.LEN_PAR, getFullRowCount());
				call.evalVoid(monitor);
				fRCacheIdxR = name;
				return name;
			}
			finally {
				if (fRCacheIdxR == null) {
					cleanTmp(name, r, monitor);
				}
			}
		}
		else {
			return null;
		}
	}
	
	protected abstract int getColumnCount(T struct);
	protected abstract int getRowCount(T struct);
	
	protected abstract T loadDataFragment(Store.Fragment<T> f,
			IRToolService r, IProgressMonitor monitor) throws CoreException, UnexpectedRDataException;
	
	protected abstract RVector<?> loadRowNamesFragment(final Store.Fragment<RVector<?>> f,
			IRToolService r, IProgressMonitor monitor) throws CoreException, UnexpectedRDataException;
	
	protected abstract T validateObject(RObject struct) throws UnexpectedRDataException;
	
	protected abstract RDataTableContentDescription loadDescription(RElementName name,
			T struct, IRToolService r,
			IProgressMonitor monitor) throws CoreException, UnexpectedRDataException;
	
	
	protected RDataTableColumn createColumn(final RStore store, final String expression,
			final RElementName elementName, final int columnIndex, final String columnName,
			final IRToolService r, final IProgressMonitor monitor) throws CoreException, UnexpectedRDataException {
		
		final ConstList<String> classNames;
		
		RObject rObject;
		{	final FunctionCall call = r.createFunctionCall("class"); //$NON-NLS-1$
			call.add(expression);
			rObject = call.evalData(monitor);
			final RVector<RCharacterStore> names = RDataUtil.checkRCharVector(rObject);
			classNames = new ConstList<String>(names.getData().toArray());
		}
		RDataTableColumn column;
		final RDataFormatter format = new RDataFormatter();
		switch (store.getStoreType()) {
		case RStore.LOGICAL:
			format.setAutoWidth(5);
			column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
					IRDataTableVariable.LOGI, store, classNames, format);
			break;
		case RStore.NUMERIC:
			if (checkDateFormat(expression, classNames, format, r, monitor)) {
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.DATE, store, classNames, format);
				break;
			}
			if (checkDateTimeFormat(expression, classNames, format, r, monitor)) {
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.DATETIME, store, classNames, format);
				break;
			}
			{	final FunctionCall call = r.createFunctionCall("format.info"); //$NON-NLS-1$
				call.add(expression);
				rObject = call.evalData(monitor);
			}
			{	final RIntegerStore formatInfo = RDataUtil.checkRIntVector(rObject).getData();
				RDataUtil.checkLengthGreaterOrEqual(formatInfo, 3);
				format.setAutoWidth(Math.max(formatInfo.getInt(0), 3));
				format.initNumFormat(formatInfo.getInt(1), formatInfo.getInt(2) > 0 ?
						formatInfo.getInt(2) + 1 : 0);
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.NUM, store, classNames, format);
				break;
			}
		case RStore.INTEGER:
			if (checkDateFormat(expression, classNames, format, r, monitor)) {
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.DATE, store, classNames, format);
				break;
			}
			if (checkDateTimeFormat(expression, classNames, format, r, monitor)) {
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.DATETIME, store, classNames, format);
				break;
			}
			{	final FunctionCall call = r.createFunctionCall("format.info"); //$NON-NLS-1$
				call.add(expression);
				rObject = call.evalData(monitor);
			}
			{	final RIntegerStore formatInfo = RDataUtil.checkRIntVector(rObject).getData();
				RDataUtil.checkLengthGreaterOrEqual(formatInfo, 1);
				format.setAutoWidth(Math.max(formatInfo.getInt(0), 3));
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.INT, store, classNames, format);
				break;
			}
		case RStore.CHARACTER:
			{	final FunctionCall call = r.createFunctionCall("format.info"); //$NON-NLS-1$
				call.add(expression);
				rObject = call.evalData(monitor);
			}
			{	final RIntegerStore formatInfo = RDataUtil.checkRIntVector(rObject).getData();
				RDataUtil.checkLengthGreaterOrEqual(formatInfo, 1);
				format.setAutoWidth(Math.max(formatInfo.getInt(0), 3));
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.CHAR, store, classNames, format);
				break;
			}
		case RStore.COMPLEX:
			{	final FunctionCall call = r.createFunctionCall("format.info"); //$NON-NLS-1$
				call.add(expression);
				rObject = call.evalData(monitor);
			}
			{	final RIntegerStore formatInfo = RDataUtil.checkRIntVector(rObject).getData();
				RDataUtil.checkLengthGreaterOrEqual(formatInfo, 3);
				format.setAutoWidth(Math.max(formatInfo.getInt(0), 3));
				format.initNumFormat(formatInfo.getInt(1), formatInfo.getInt(2) > 0 ?
						formatInfo.getInt(2) + 1 : 0);
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.CPLX, store, classNames, format);
				break;
			}
		case RStore.RAW:
			format.setAutoWidth(2);
			column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
					IRDataTableVariable.RAW, store, classNames, format);
			break;
		case RStore.FACTOR:
			{	final FunctionCall call = r.createFunctionCall("levels"); //$NON-NLS-1$
				call.add(expression);
				rObject = call.evalData(monitor);
			}
			{	format.setAutoWidth(3);
				final RCharacterStore levels = RDataUtil.checkRCharVector(rObject).getData();
				for (int i = 0; i < levels.getLength(); i++) {
					if (!levels.isNA(i)) {
						final int length = levels.getChar(i).length();
						if (length > format.getAutoWidth()) {
							format.setAutoWidth(length);
						}
					}
				}
				format.initFactorLevels(levels);
				column = new RDataTableColumn(columnIndex, columnName, expression, elementName,
						IRDataTableVariable.FACTOR, RFactorDataStruct.addLevels((RFactorStore) store, levels),
						classNames, format);
				break;
			}
		default:
			throw new UnexpectedRDataException("store type: " + store.getStoreType());
		}
		return column;
	}
	
	protected boolean checkDateFormat(final String expression, final List<String> classNames,
			final RDataFormatter formatter,
			final IRToolService r, final IProgressMonitor monitor) throws CoreException, UnexpectedRDataException {
		if (classNames.contains("Data")) {
			formatter.initDateFormat(RDataFormatter.MILLIS_PER_DAY);
			formatter.setAutoWidth(10);
			return true;
		}
		return false;
	}
	
	protected boolean checkDateTimeFormat(final String expression, final List<String> classNames,
			final RDataFormatter formatter,
			final IRToolService r, final IProgressMonitor monitor) throws CoreException, UnexpectedRDataException {
		RObject rObject;
		if (classNames.contains("POSIXct")) {
			formatter.initDateTimeFormat(RDataFormatter.MILLIS_PER_SECOND);
			formatter.setAutoWidth(27);
			
			{	final FunctionCall call = r.createFunctionCall("base::attr"); //$NON-NLS-1$
				call.add(expression);
				call.addChar("tzone");
				rObject = call.evalData(monitor);
			}
			if (rObject.getRObjectType() != RObject.TYPE_NULL) {
				formatter.setDateTimeZone(TimeZone.getTimeZone(RDataUtil.checkSingleCharValue(rObject)));
			}
			return true;
		}
		return false;
	}
	
	protected RDataTableColumn createNamesColumn(final String expression, final int count,
			final IRToolService r, final IProgressMonitor monitor) throws CoreException, UnexpectedRDataException {
		final RObject names = r.evalData(expression, null, RObjectFactory.F_ONLY_STRUCT, 1, monitor);
		if (names != null && names.getRObjectType() == RObject.TYPE_VECTOR
				&& names.getLength() == count
				&& (names.getData().getStoreType() == RStore.CHARACTER
						|| names.getData().getStoreType() == RStore.INTEGER)) {
			return createColumn(names.getData(), expression, null, -1, null, r, monitor);
		}
		return createAutoNamesColumn(count);
	}
	
	private RDataTableColumn createAutoNamesColumn(final int count) {
		final RDataFormatter format = new RDataFormatter();
		format.setAutoWidth(Math.max(Integer.toString(count).length(), 3));
		return new RDataTableColumn(-1, null, null, null,
				IRDataTableVariable.INT, RObjectFactoryImpl.INT_STRUCT_DUMMY,
				new ConstList<String>(RObject.CLASSNAME_INTEGER),
				format);
	}
	
	protected abstract void appendOrderCmd(StringBuilder cmd, SortColumn sortColumn);
	
	
	private void runClean(final IRToolService r, final IProgressMonitor monitor) throws CoreException {
		clear(Lock.ERROR_STATE);
		cleanSorting(r, monitor);
		cleanFiltering(r, monitor);
		fFindManager.clean(r, monitor);
		cleanTmp(fRCacheId, r, monitor);
	}
	
	private void cleanSorting(final IRToolService r, final IProgressMonitor monitor) throws CoreException {
		cleanIdx(r, monitor);
		if (fRCacheSort != null) {
			cleanTmp(fRCacheSort, r, monitor);
			fRCacheSort = null;
		}
	}
	
	private void cleanFiltering(final IRToolService r, final IProgressMonitor monitor) throws CoreException {
		cleanIdx(r, monitor);
		if (fRCacheFilter != null) {
			cleanTmp(fRCacheFilter, r, monitor);
			fRCacheFilter = null;
		}
	}
	
	private void cleanIdx(final IRToolService r, final IProgressMonitor monitor) throws CoreException {
		fUpdateIdx = true;
		if (fRCacheIdx != null) {
			cleanTmp(fRCacheIdx, r, monitor);
			fRCacheIdx = null;
		}
		if (fRCacheIdxR != null) {
			cleanTmp(fRCacheIdxR, r, monitor);
			fRCacheIdxR = null;
		}
	}
	
	
	protected final StringBuilder getRCmdStringBuilder() {
		fRStringBuilder.setLength(0);
		return fRStringBuilder;
	}
	
	protected void appendRowIdxs(final StringBuilder cmd, final int beginRowIdx, final int endRowIdx) {
		if (fRCacheIdx != null) {
			cmd.append(RJTmp.ENV+'$');
			cmd.append(fRCacheIdx);
			cmd.append('[');
			cmd.append((beginRowIdx + 1));
			cmd.append('L');
			cmd.append(':');
			cmd.append(endRowIdx);
			cmd.append('L');
			cmd.append(']');
		}
		else {
			cmd.append((beginRowIdx + 1));
			cmd.append('L');
			cmd.append(':');
			cmd.append(endRowIdx);
			cmd.append('L');
		}
	}
	
	protected void appendColumnIdxs(final StringBuilder cmd, final int beginColumnIdx, final int endColumnIdx) {
		cmd.append((beginColumnIdx + 1));
		cmd.append('L');
		cmd.append(':');
		cmd.append(endColumnIdx);
		cmd.append('L');
	}
	
	
	public boolean getAllColumnsEqual() {
		return false;
	}
	
	public RDataTableContentDescription getDescription() {
		return fDescription;
	}
	
	@Override
	public int getColumnCount() {
		return fColumnCount;
	}
	
	public int getFullRowCount() {
		return fFullRowCount;
	}
	
	@Override
	public int getRowCount() {
		return fRowCount;
	}
	
	@Override
	public Object getDataValue(final int columnIndex, final int rowIndex) {
		try {
			final Store.Fragment<T> fragment = fDataStore.getFor(rowIndex, columnIndex);
			if (fragment != null) {
				return getDataValue(fragment, rowIndex, columnIndex);
			}
			else {
				return LOADING;
			}
		}
		catch (final LoadDataException e) {
			return handleLoadDataException(e);
		}
	}
	
	protected abstract Object getDataValue(Store.Fragment<T> fragment, int rowIdx, int columnIdx);
	
	@Override
	public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue) {
		throw new UnsupportedOperationException();
	}
	
	protected abstract Object getColumnName(Store.Fragment<T> fragment, int columnIdx);
	
	public IDataProvider getColumnDataProvider() {
		return fColumnDataProvider;
	}
	
	public IDataProvider getRowDataProvider() {
		return fRowDataProvider;
	}
	
	protected IDataProvider createColumnDataProvider() {
		return new ColumnDataProvider();
	}
	
	protected IDataProvider createRowDataProvider() {
		return new RowDataProvider();
	}
	
	protected ISortModel createSortModel() {
		return new SortModel();
	}
	
	private Object handleLoadDataException(final LoadDataException e) {
		if (e.isUnrecoverable()) {
			return ERROR;
		}
		reset();
		return LOADING;
	}
	
	
	public ISortModel getSortModel() {
		return fSortModel;
	}
	
	public SortColumn getSortColumn() {
		return fSortColumn;
	}
	
	private void setSortColumn(final SortColumn column) {
		synchronized (fFragmentsLock) {
			if ((fSortColumn != null) ? fSortColumn.equals(column) : null == column) {
				return;
			}
			fSortColumn = column;
			
			clear(-1, -1, -1, true, false, false);
			
			fFindManager.reset(false);
		}
	}
	
	public void setFilter(final String filter) {
		synchronized (fFragmentsLock) {
			if ((fFilter != null) ? fFilter.equals(filter) : null == filter) {
				return;
			}
			fFilter = filter;
			
			clear(-1, -1, -1, false, true, false);
			
			fFindManager.reset(true);
			
			fFragmentsLock.schedule(null);
		}
	}
	
	
	
	public void addFindListener(final IFindListener listener) {
		fFindManager.addFindListener(listener);
	}
	
	public void removeFindListener(final IFindListener listener) {
		fFindManager.removeFindListener(listener);
	}
	
	public void find(final FindTask task) {
		fFindManager.find(task);
	}
	
	
	public void addDataChangedListener(final IDataProviderListener listener) {
		fDataListeners.add(listener);
	}
	
	public void removeDataChangedListener(final IDataProviderListener listener) {
		fDataListeners.remove(listener);
	}
	
	protected void notifyListener(final Store.Fragment<?> item) {
		try {
			for (final IDataProviderListener listener : fDataListeners.toArray()) {
				listener.onRowsChanged(item.beginRowIdx, item.endRowIdx);
			}
		}
		catch (final Exception e) {
			StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, -1,
					"An error occurred when notifying about row updates.", e));
		}
	}
	
	
	public void reset() {
		clear(Lock.PAUSE_STATE);
		synchronized (fInitRunnable) {
			if (fInitScheduled) {
				return;
			}
			fInitScheduled = true;
		}
		try {
			final IStatus status = ((RToolDataTableInput) fInput).getTool().getQueue().add(fInitRunnable);
			if (status.getSeverity() >= IStatus.ERROR) {
				throw new CoreException(status);
			}
		}
		catch (final CoreException e) {
		}
	}
	
	private void clear(final int newState) {
		clear(newState, -1, -1, true, true, true);
	}
	
	private void clear(final int newState, final int filteredRowCount, final int fullRowCount,
			final boolean updateSorting, final boolean updateFiltering,
			final boolean clearFind) {
		synchronized (fFragmentsLock) {
			fDataStore.internalClear(filteredRowCount);
			fRowNamesStore.internalClear(filteredRowCount);
			fUpdateSorting |= updateSorting;
			fUpdateFiltering |= updateFiltering;
			
			if (newState >= 0 && fFragmentsLock.state < Lock.ERROR_STATE) {
				fFragmentsLock.state = newState;
			}
			if (filteredRowCount >= 0) {
				fRowCount = filteredRowCount;
				fFullRowCount = fullRowCount;
			}
			
			if (clearFind) {
				fFindManager.clear(newState);
			}
		}
	}
	
	public void dispose() {
		schedule(fCleanRunnable);
	}
	
}
