/*******************************************************************************
 * Copyright (c) 2005-2006 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.ui.editors.text.templates.ContributionContextTypeRegistry;
import org.eclipse.ui.editors.text.templates.ContributionTemplateStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.services.IDisposable;
import org.osgi.framework.BundleContext;

import de.walware.eclipsecommons.preferences.IPreferenceAccess;
import de.walware.eclipsecommons.preferences.PreferencesUtil;
import de.walware.eclipsecommons.ui.util.ImageRegistryUtil;

import de.walware.statet.base.core.preferences.PreferencesManageListener;
import de.walware.statet.base.ui.StatetUIServices;
import de.walware.statet.ext.ui.editors.StatextSourceViewerConfiguration;
import de.walware.statet.nico.core.ConsoleInstanceScope;
import de.walware.statet.nico.core.NicoCore;
import de.walware.statet.r.codegeneration.RCodeTemplatesContextType;
import de.walware.statet.r.codegeneration.RdCodeTemplatesContextType;
import de.walware.statet.r.ui.RUI;
import de.walware.statet.r.ui.editors.RDocumentProvider;
import de.walware.statet.r.ui.editors.REditorOptions;
import de.walware.statet.r.ui.editors.RdDocumentProvider;
import de.walware.statet.r.ui.editors.templates.REditorTemplatesContextType;


/**
 * The main plugin class to be used in the desktop.
 */
public class RUIPlugin extends AbstractUIPlugin {
	
	
	public static final int INTERNAL_ERROR = 100;
	public static final int IO_ERROR = 101;
	
	
	public static final String IMG_WIZBAN_NEWRDFILE = RUI.PLUGIN_ID + "/image/wizban/new.rd-file"; //$NON-NLS-1$
	public static final String IMG_WIZBAN_NEWRFILE = RUI.PLUGIN_ID + "/image/wizban/new.r-file"; //$NON-NLS-1$
	public static final String IMG_WIZBAN_NEWRPROJECT = RUI.PLUGIN_ID + "/image/wizban/new.r-project"; //$NON-NLS-1$
	
	
	private static final String R_CODE_TEMPLATES_KEY  = "de.walware.statet.r.ui.text.r_code_templates"; //$NON-NLS-1$
	private static final String RD_CODE_TEMPLATES_KEY = "de.walware.statet.r.ui.text.rd_code_templates"; //$NON-NLS-1$
	private static final String R_EDITOR_TEMPLATES_KEY  = "de.walware.statet.r.ui.text.r_editor_templates"; //$NON-NLS-1$
	
	
	//The shared instance.
	private static RUIPlugin gPlugin;
	
	/**
	 * Returns the shared instance.
	 */
	public static RUIPlugin getDefault() {
		return gPlugin;
	}
	
	public static void logError(final int code, final String message, final Throwable e) {
		getDefault().getLog().log(new Status(IStatus.ERROR, RUI.PLUGIN_ID, code, message, e));
	}
	
	
	private RDocumentProvider fRDocumentProvider;
	private RdDocumentProvider fRdDocumentProvider;
	
	private IPreferenceStore fEditorPreferenceStore;
	
	private RIdentifierGroups fIdentifierGroups;
	private REditorOptions fEditorSettings;
	private REditorOptions fConsoleSettings;
	private List<PreferencesManageListener> fPrefUpdaters;
	
	private TemplateStore fRCodeTemplatesStore;
	private ContextTypeRegistry fRCodeTemplatesContextTypeRegistry;
	private TemplateStore fRdCodeTemplatesStore;
	private ContextTypeRegistry fRdCodeTemplatesContextTypeRegistry;
	
	private TemplateStore fREditorTemplatesStore;
	private ContextTypeRegistry fREditorContextTypeRegistry;
	
	private List<IDisposable> fDisposables;
	
	
	/**
	 * The constructor.
	 */
	public RUIPlugin() {
		gPlugin = this;
	}
	
	/**
	 * This method is called upon plug-in activation
	 */
	@Override
	public void start(final BundleContext context) throws Exception {
		fPrefUpdaters = new ArrayList<PreferencesManageListener>();
		fDisposables = new ArrayList<IDisposable>();
		super.start(context);
	}
	
	/**
	 * This method is called when the plug-in is stopped
	 */
	@Override
	public void stop(final BundleContext context) throws Exception {
		try {
			for (final IDisposable d : fDisposables) {
				try {
					d.dispose();
				}
				catch (final Throwable e) {
					logError(-1, "Error occured when dispose module", e); //$NON-NLS-1$
				}
			}
			fRDocumentProvider = null;
			fRdDocumentProvider = null;
			fEditorPreferenceStore = null;
			fRCodeTemplatesStore = null;
			fRCodeTemplatesContextTypeRegistry = null;
			fRdCodeTemplatesStore = null;
			fRdCodeTemplatesContextTypeRegistry = null;
			fREditorTemplatesStore = null;
			fREditorContextTypeRegistry = null;
			final Iterator<PreferencesManageListener> iter = fPrefUpdaters.iterator();
			while (iter.hasNext()) {
				iter.next().dispose();
			}
			fPrefUpdaters.clear();
		} finally {
			gPlugin = null;
			super.stop(context);
		}
	}
	
	@Override
	protected ImageRegistry createImageRegistry() {
		return StatetUIServices.getSharedImageRegistry();
	}
	
	@Override
	protected void initializeImageRegistry(final ImageRegistry reg) {
		final ImageRegistryUtil util = new ImageRegistryUtil(this);
		util.register(IMG_WIZBAN_NEWRPROJECT, ImageRegistryUtil.T_WIZBAN, "new_r-project.png"); //$NON-NLS-1$
		util.register(IMG_WIZBAN_NEWRFILE, ImageRegistryUtil.T_WIZBAN, "new_r-file.png"); //$NON-NLS-1$
		util.register(IMG_WIZBAN_NEWRDFILE, ImageRegistryUtil.T_WIZBAN, "new_rd-file.png"); //$NON-NLS-1$
		
		util.register(RUI.IMG_OBJ_R_ENVIRONMENT, ImageRegistryUtil.T_OBJ, "r-env.png"); //$NON-NLS-1$
	}
	
	
	public synchronized RDocumentProvider getRDocumentProvider() {
		if (fRDocumentProvider == null) {
			fRDocumentProvider = new RDocumentProvider();
		}
		return fRDocumentProvider;
	}
	
	public synchronized RdDocumentProvider getRdDocumentProvider() {
		if (fRdDocumentProvider == null) {
			fRdDocumentProvider = new RdDocumentProvider();
		}
		return fRdDocumentProvider;
	}
	
	
	public IPreferenceStore getEditorPreferenceStore() {
		if (fEditorPreferenceStore == null) {
			fEditorPreferenceStore = StatextSourceViewerConfiguration.createCombinedPreferenceStore(
				getPreferenceStore());
		}
		return fEditorPreferenceStore;
	}
	
	public synchronized RIdentifierGroups getRIdentifierGroups() {
		if (fIdentifierGroups == null) {
			fIdentifierGroups = new RIdentifierGroups();
			fPrefUpdaters.add(new PreferencesManageListener(
					fIdentifierGroups, PreferencesUtil.getInstancePrefs(), RIdentifierGroups.CONTEXT_ID));
		}
		return fIdentifierGroups;
	}
	
	public synchronized REditorOptions getREditorSettings(final IPreferenceAccess prefs) {
		final IScopeContext[] contexts = prefs.getPreferenceContexts();
		for (int i = 0; i < contexts.length; i++) {
			if (contexts[i].getName().equals(ConsoleInstanceScope.SCOPE)) {
				if (fConsoleSettings== null) {
					fConsoleSettings = new REditorOptions(1);
					fPrefUpdaters.add(new PreferencesManageListener(
							fConsoleSettings, NicoCore.getDefaultConsolePreferences(), REditorOptions.CONTEXT_ID));
				}
				return fConsoleSettings;
			}
			if (contexts[i].getName().equals(InstanceScope.SCOPE)) {
				if (fEditorSettings == null) {
					fEditorSettings = new REditorOptions(1);
					fPrefUpdaters.add(new PreferencesManageListener(
							fEditorSettings, PreferencesUtil.getInstancePrefs(), REditorOptions.CONTEXT_ID));
				}
				return fEditorSettings;
			}
		}
		return null;
	}
	
	/**
	 * Returns the template context type registry for the code generation
	 * templates.
	 * 
	 * @return the template context type registry
	 */
	public synchronized ContextTypeRegistry getRCodeGenerationTemplateContextRegistry() {
		if (fRCodeTemplatesContextTypeRegistry == null) {
			fRCodeTemplatesContextTypeRegistry = new ContributionContextTypeRegistry();
			
			RCodeTemplatesContextType.registerContextTypes(fRCodeTemplatesContextTypeRegistry);
		}
		return fRCodeTemplatesContextTypeRegistry;
	}
	
	/**
	 * Returns the template store for the code generation templates.
	 * 
	 * @return the template store
	 */
	public synchronized TemplateStore getRCodeGenerationTemplateStore() {
		if (fRCodeTemplatesStore == null) {
			fRCodeTemplatesStore = new ContributionTemplateStore(
					getRCodeGenerationTemplateContextRegistry(), getPreferenceStore(), R_CODE_TEMPLATES_KEY);
			try {
				fRCodeTemplatesStore.load();
			} catch (final IOException e) {
				RUIPlugin.logError(IO_ERROR, "Error occured when loading 'R code generation' template store.", e); //$NON-NLS-1$
			}
		}
		return fRCodeTemplatesStore;
	}
	
	/**
	 * Returns the template context type registry for the code generation
	 * templates.
	 * 
	 * @return the template context type registry
	 */
	public synchronized ContextTypeRegistry getRdCodeGenerationTemplateContextRegistry() {
		if (fRdCodeTemplatesContextTypeRegistry == null) {
			fRdCodeTemplatesContextTypeRegistry = new ContributionContextTypeRegistry();
			
			RdCodeTemplatesContextType.registerContextTypes(fRdCodeTemplatesContextTypeRegistry);
		}
		return fRdCodeTemplatesContextTypeRegistry;
	}
	
	/**
	 * Returns the template store for the code generation templates.
	 * 
	 * @return the template store
	 */
	public synchronized TemplateStore getRdCodeGenerationTemplateStore() {
		if (fRdCodeTemplatesStore == null) {
			fRdCodeTemplatesStore = new ContributionTemplateStore(
					getRdCodeGenerationTemplateContextRegistry(), getPreferenceStore(), RD_CODE_TEMPLATES_KEY);
			try {
				fRdCodeTemplatesStore.load();
			} catch (final IOException e) {
				RUIPlugin.logError(IO_ERROR, "Error occured when loading 'Rd code generation' template store.", e); //$NON-NLS-1$
			}
		}
		return fRdCodeTemplatesStore;
	}
	
	
	/**
	 * Returns the template context type registry for the code generation
	 * templates.
	 * 
	 * @return the template context type registry
	 */
	public synchronized ContextTypeRegistry getREditorTemplateContextRegistry() {
		if (fREditorContextTypeRegistry == null) {
			fREditorContextTypeRegistry = new ContributionContextTypeRegistry();
			
			REditorTemplatesContextType.registerContextTypes(fREditorContextTypeRegistry);
		}
		return fREditorContextTypeRegistry;
	}
	
	/**
	 * Returns the template store for the code generation templates.
	 * 
	 * @return the template store
	 */
	public synchronized TemplateStore getREditorTemplateStore() {
		if (fREditorTemplatesStore == null) {
			fREditorTemplatesStore = new ContributionTemplateStore(
					getREditorTemplateContextRegistry(), getPreferenceStore(), R_EDITOR_TEMPLATES_KEY);
			try {
				fREditorTemplatesStore.load();
			} catch (final IOException e) {
				RUIPlugin.logError(IO_ERROR, "Error occured when loading 'R Editor' template store.", e); //$NON-NLS-1$
			}
		}
		return fREditorTemplatesStore;
	}
	
	public void registerPluginDisposable(final IDisposable d) {
		fDisposables.add(d);
	}
	
}
