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

package de.walware.statet.r.internal.ui;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.editors.text.EditorsUI;

import de.walware.eclipsecommons.preferences.Preference;
import de.walware.eclipsecommons.preferences.PreferencesUtil;
import de.walware.eclipsecommons.preferences.Preference.BooleanPref;

import de.walware.statet.nico.core.NicoPreferenceNodes;
import de.walware.statet.r.internal.debug.ui.RDebugPreferenceConstants;
import de.walware.statet.r.internal.ui.editors.DefaultRFoldingProvider;
import de.walware.statet.r.ui.RUI;
import de.walware.statet.r.ui.RUIPreferenceConstants;
import de.walware.statet.r.ui.editors.REditorOptions;


public class RUIPreferenceInitializer extends AbstractPreferenceInitializer {


	public static final String REDITOR_NODE = RUI.PLUGIN_ID + "/editor.r/options"; //$NON-NLS-1$
	public static final String RCONSOLE_NODE = RUI.PLUGIN_ID + '/'+NicoPreferenceNodes.SCOPE_QUALIFIER+ "/editor.r/options"; // NicoPreferenceNodes.createScopeQualifier(REDITOR_NODE); //$NON-NLS-1$
	
	public static final BooleanPref CONSOLE_SMARTINSERT_CLOSECURLY_ENABLED = new BooleanPref(
			RCONSOLE_NODE, "smartinsert.close_curlybrackets.enabled"); //$NON-NLS-1$
	public static final BooleanPref CONSOLE_SMARTINSERT_CLOSEROUND_ENABLED = new BooleanPref(
			RCONSOLE_NODE, "smartinsert.close_roundbrackets.enabled"); //$NON-NLS-1$
	public static final BooleanPref CONSOLE_SMARTINSERT_CLOSESQUARE_ENABLED = new BooleanPref(
			RCONSOLE_NODE, "smartinsert.close_squarebrackets.enabled"); //$NON-NLS-1$
	public static final BooleanPref CONSOLE_SMARTINSERT_CLOSESPECIAL_ENABLED = new BooleanPref(
			RCONSOLE_NODE, "smartinsert.close_specialpercent.enabled"); //$NON-NLS-1$
	public static final BooleanPref CONSOLE_SMARTINSERT_CLOSESTRINGS_ENABLED = new BooleanPref(
			RCONSOLE_NODE, "smartinsert.close_strings.enabled"); //$NON-NLS-1$
	
	public static final BooleanPref PREF_FOLDING_ASDEFAULT_ENABLED = new BooleanPref(
			REDITOR_NODE, "folding.enable_as_default.enabled"); //$NON-NLS-1$
	
	
	@Override
	public void initializeDefaultPreferences() {
		final IPreferenceStore store = RUIPlugin.getDefault().getPreferenceStore();
		EditorsUI.useAnnotationsPreferencePage(store);
		EditorsUI.useQuickDiffPreferencePage(store);
		RUIPreferenceConstants.initializeDefaultValues(store);
		
		final DefaultScope defaultScope = new DefaultScope();
		final Map<Preference, Object> defaults = new HashMap<Preference, Object>();
		new REditorOptions(0).deliverToPreferencesMap(defaults);
		PreferencesUtil.setPrefValues(defaultScope, defaults);
		
		PreferencesUtil.setPrefValue(defaultScope, CONSOLE_SMARTINSERT_CLOSECURLY_ENABLED, false);
		PreferencesUtil.setPrefValue(defaultScope, CONSOLE_SMARTINSERT_CLOSEROUND_ENABLED, true);
		PreferencesUtil.setPrefValue(defaultScope, CONSOLE_SMARTINSERT_CLOSESQUARE_ENABLED, true);
		PreferencesUtil.setPrefValue(defaultScope, CONSOLE_SMARTINSERT_CLOSESPECIAL_ENABLED, true);
		PreferencesUtil.setPrefValue(defaultScope, CONSOLE_SMARTINSERT_CLOSESTRINGS_ENABLED, false);
		
		PreferencesUtil.setPrefValue(defaultScope, PREF_FOLDING_ASDEFAULT_ENABLED, true);
		PreferencesUtil.setPrefValue(defaultScope, DefaultRFoldingProvider.PREF_MINLINES_NUM, 4);
		PreferencesUtil.setPrefValue(defaultScope, DefaultRFoldingProvider.PREF_OTHERBLOCKS_ENABLED, false);
		PreferencesUtil.setPrefValue(defaultScope, REditorOptions.PREF_SPELLCHECKING_ENABLED, false);
		
		RDebugPreferenceConstants.initializeDefaultValues(defaultScope);
	}
	
}
