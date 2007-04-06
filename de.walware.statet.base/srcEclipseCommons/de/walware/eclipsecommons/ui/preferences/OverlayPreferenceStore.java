/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package de.walware.eclipsecommons.ui.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import de.walware.eclipsecommons.preferences.Preference.Type;


/**
 * An overlaying preference store.
 */
public class OverlayPreferenceStore implements IPreferenceStore {
	
	private class PropertyListener implements IPropertyChangeListener {
				
		/*
		 * @see IPropertyChangeListener#propertyChange(PropertyChangeEvent)
		 */
		public void propertyChange(PropertyChangeEvent event) {
			OverlayStorePreference key= findPreferenceKey(event.getProperty());
			if (key != null)
				propagateProperty(fParent, key, fStore); 
		}
	}
	
	
	private IPreferenceStore fParent;
	private IPreferenceStore fStore;
	private OverlayStorePreference[] fPreferenceKeys;
	
	private PropertyListener fPropertyListener;
	private boolean fLoaded;
	
	
	public OverlayPreferenceStore(IPreferenceStore parent, OverlayStorePreference[] PreferenceKeys) {
		fParent= parent;
		fPreferenceKeys= PreferenceKeys;
		fStore= new PreferenceStore();
	}
	
	private OverlayStorePreference findPreferenceKey(String key) {
		for (int i= 0; i < fPreferenceKeys.length; i++) {
			if (fPreferenceKeys[i].fKey.equals(key))
				return fPreferenceKeys[i];
		}
		return null;
	}
	
	private boolean covers(String key) {
		return (findPreferenceKey(key) != null);
	}
	
	private void propagateProperty(IPreferenceStore orgin, OverlayStorePreference key, IPreferenceStore target) {
		
		if (orgin.isDefault(key.fKey)) {
			if (!target.isDefault(key.fKey))
				target.setToDefault(key.fKey);
			return;
		}
		
		Type type = key.fType;
		switch (type) {

		case BOOLEAN: {
			boolean originValue= orgin.getBoolean(key.fKey);
			boolean targetValue= target.getBoolean(key.fKey);
			if (targetValue != originValue)
				target.setValue(key.fKey, originValue);
			break; }
				
		case DOUBLE: {
			double originValue= orgin.getDouble(key.fKey);
			double targetValue= target.getDouble(key.fKey);
			if (targetValue != originValue)
				target.setValue(key.fKey, originValue);
			break; }
		
		case FLOAT: {
			float originValue= orgin.getFloat(key.fKey);
			float targetValue= target.getFloat(key.fKey);
			if (targetValue != originValue)
				target.setValue(key.fKey, originValue);
			break; }
		
		case INT: {
			int originValue= orgin.getInt(key.fKey);
			int targetValue= target.getInt(key.fKey);
			if (targetValue != originValue)
				target.setValue(key.fKey, originValue);
			break; }

		case LONG: {
			long originValue= orgin.getLong(key.fKey);
			long targetValue= target.getLong(key.fKey);
			if (targetValue != originValue)
				target.setValue(key.fKey, originValue);
			break; }

		case STRING: {
			String originValue= orgin.getString(key.fKey);
			String targetValue= target.getString(key.fKey);
			if (targetValue != null && originValue != null && !targetValue.equals(originValue))
				target.setValue(key.fKey, originValue);
			break; }
		}
	}
	
	public void propagate() {
		for (int i= 0; i < fPreferenceKeys.length; i++)
			propagateProperty(fStore, fPreferenceKeys[i], fParent);
	}
	
	private void loadProperty(IPreferenceStore orgin, OverlayStorePreference key, IPreferenceStore target, boolean forceInitialization) {
		
		Type type = key.fType;
		switch (type) {
		
		case BOOLEAN: {
			if (forceInitialization)
				target.setValue(key.fKey, true);
			target.setValue(key.fKey, orgin.getBoolean(key.fKey));
			target.setDefault(key.fKey, orgin.getDefaultBoolean(key.fKey));
			break; }
		
		case DOUBLE: {
			if (forceInitialization)
				target.setValue(key.fKey, 1.0D);
			target.setValue(key.fKey, orgin.getDouble(key.fKey));
			target.setDefault(key.fKey, orgin.getDefaultDouble(key.fKey));
			break; }
			
		case FLOAT: {
			if (forceInitialization)
				target.setValue(key.fKey, 1.0F);
			target.setValue(key.fKey, orgin.getFloat(key.fKey));
			target.setDefault(key.fKey, orgin.getDefaultFloat(key.fKey));
			break; }
		
		case INT: {
			if (forceInitialization)
				target.setValue(key.fKey, 1);
			target.setValue(key.fKey, orgin.getInt(key.fKey));
			target.setDefault(key.fKey, orgin.getDefaultInt(key.fKey));
			break; }
		
		case LONG: {
			if (forceInitialization)
				target.setValue(key.fKey, 1L);
			target.setValue(key.fKey, orgin.getLong(key.fKey));
			target.setDefault(key.fKey, orgin.getDefaultLong(key.fKey));
			break; }
			
		case STRING: {
			if (forceInitialization)
				target.setValue(key.fKey, "1"); //$NON-NLS-1$
			target.setValue(key.fKey, orgin.getString(key.fKey));
			target.setDefault(key.fKey, orgin.getDefaultString(key.fKey));
			break; }
		}
	}
	
	public void load() {
		for (int i= 0; i < fPreferenceKeys.length; i++)
			loadProperty(fParent, fPreferenceKeys[i], fStore, true);
		
		fLoaded= true;
		
	}
	
	public void loadDefaults() {
		for (int i= 0; i < fPreferenceKeys.length; i++)
			setToDefault(fPreferenceKeys[i].fKey);
	}
	
	public void start() {
		if (fPropertyListener == null) {
			fPropertyListener= new PropertyListener();
			fParent.addPropertyChangeListener(fPropertyListener);
		}
	}
	
	public void stop() {
		if (fPropertyListener != null)  {
			fParent.removePropertyChangeListener(fPropertyListener);
			fPropertyListener= null;
		}
	}
	
	/*
	 * @see IPreferenceStore#addPropertyChangeListener(IPropertyChangeListener)
	 */
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		fStore.addPropertyChangeListener(listener);
	}
	
	/*
	 * @see IPreferenceStore#removePropertyChangeListener(IPropertyChangeListener)
	 */
	public void removePropertyChangeListener(IPropertyChangeListener listener) {
		fStore.removePropertyChangeListener(listener);
	}
	
	/*
	 * @see IPreferenceStore#firePropertyChangeEvent(String, Object, Object)
	 */
	public void firePropertyChangeEvent(String name, Object oldValue, Object newValue) {
		fStore.firePropertyChangeEvent(name, oldValue, newValue);
	}

	/*
	 * @see IPreferenceStore#contains(String)
	 */
	public boolean contains(String name) {
		return fStore.contains(name);
	}
	
	/*
	 * @see IPreferenceStore#getBoolean(String)
	 */
	public boolean getBoolean(String name) {
		return fStore.getBoolean(name);
	}

	/*
	 * @see IPreferenceStore#getDefaultBoolean(String)
	 */
	public boolean getDefaultBoolean(String name) {
		return fStore.getDefaultBoolean(name);
	}

	/*
	 * @see IPreferenceStore#getDefaultDouble(String)
	 */
	public double getDefaultDouble(String name) {
		return fStore.getDefaultDouble(name);
	}

	/*
	 * @see IPreferenceStore#getDefaultFloat(String)
	 */
	public float getDefaultFloat(String name) {
		return fStore.getDefaultFloat(name);
	}

	/*
	 * @see IPreferenceStore#getDefaultInt(String)
	 */
	public int getDefaultInt(String name) {
		return fStore.getDefaultInt(name);
	}

	/*
	 * @see IPreferenceStore#getDefaultLong(String)
	 */
	public long getDefaultLong(String name) {
		return fStore.getDefaultLong(name);
	}

	/*
	 * @see IPreferenceStore#getDefaultString(String)
	 */
	public String getDefaultString(String name) {
		return fStore.getDefaultString(name);
	}

	/*
	 * @see IPreferenceStore#getDouble(String)
	 */
	public double getDouble(String name) {
		return fStore.getDouble(name);
	}

	/*
	 * @see IPreferenceStore#getFloat(String)
	 */
	public float getFloat(String name) {
		return fStore.getFloat(name);
	}

	/*
	 * @see IPreferenceStore#getInt(String)
	 */
	public int getInt(String name) {
		return fStore.getInt(name);
	}

	/*
	 * @see IPreferenceStore#getLong(String)
	 */
	public long getLong(String name) {
		return fStore.getLong(name);
	}

	/*
	 * @see IPreferenceStore#getString(String)
	 */
	public String getString(String name) {
		return fStore.getString(name);
	}

	/*
	 * @see IPreferenceStore#isDefault(String)
	 */
	public boolean isDefault(String name) {
		return fStore.isDefault(name);
	}

	/*
	 * @see IPreferenceStore#needsSaving()
	 */
	public boolean needsSaving() {
		return fStore.needsSaving();
	}

	/*
	 * @see IPreferenceStore#putValue(String, String)
	 */
	public void putValue(String name, String value) {
		if (covers(name))
			fStore.putValue(name, value);
	}

	/*
	 * @see IPreferenceStore#setDefault(String, double)
	 */
	public void setDefault(String name, double value) {
		if (covers(name))
			fStore.setDefault(name, value);
	}

	/*
	 * @see IPreferenceStore#setDefault(String, float)
	 */
	public void setDefault(String name, float value) {
		if (covers(name))
			fStore.setDefault(name, value);
	}

	/*
	 * @see IPreferenceStore#setDefault(String, int)
	 */
	public void setDefault(String name, int value) {
		if (covers(name))
			fStore.setDefault(name, value);
	}

	/*
	 * @see IPreferenceStore#setDefault(String, long)
	 */
	public void setDefault(String name, long value) {
		if (covers(name))
			fStore.setDefault(name, value);
	}

	/*
	 * @see IPreferenceStore#setDefault(String, String)
	 */
	public void setDefault(String name, String value) {
		if (covers(name))
			fStore.setDefault(name, value);
	}

	/*
	 * @see IPreferenceStore#setDefault(String, boolean)
	 */
	public void setDefault(String name, boolean value) {
		if (covers(name))
			fStore.setDefault(name, value);
	}

	/*
	 * @see IPreferenceStore#setToDefault(String)
	 */
	public void setToDefault(String name) {
		fStore.setToDefault(name);
	}

	/*
	 * @see IPreferenceStore#setValue(String, double)
	 */
	public void setValue(String name, double value) {
		if (covers(name))
			fStore.setValue(name, value);
	}

	/*
	 * @see IPreferenceStore#setValue(String, float)
	 */
	public void setValue(String name, float value) {
		if (covers(name))
			fStore.setValue(name, value);
	}

	/*
	 * @see IPreferenceStore#setValue(String, int)
	 */
	public void setValue(String name, int value) {
		if (covers(name))
			fStore.setValue(name, value);
	}

	/*
	 * @see IPreferenceStore#setValue(String, long)
	 */
	public void setValue(String name, long value) {
		if (covers(name))
			fStore.setValue(name, value);
	}

	/*
	 * @see IPreferenceStore#setValue(String, String)
	 */
	public void setValue(String name, String value) {
		if (covers(name))
			fStore.setValue(name, value);
	}

	/*
	 * @see IPreferenceStore#setValue(String, boolean)
	 */
	public void setValue(String name, boolean value) {
		if (covers(name))
			fStore.setValue(name, value);
	}

	/**
	 * The keys to add to the list of overlay keys.
	 * <p>
	 * Note: This method must be called before {@link #load()} is called. 
	 * </p>
	 * 
	 * @param keys
	 * @since 3.0
	 */
	public void addKeys(OverlayStorePreference[] keys) {
		assert (!fLoaded);
		assert (keys != null);
		
		int PreferenceKeysLength= fPreferenceKeys.length;
		OverlayStorePreference[] result= new OverlayStorePreference[keys.length + PreferenceKeysLength];

		for (int i= 0, length= PreferenceKeysLength; i < length; i++)
			result[i]= fPreferenceKeys[i];
		
		for (int i= 0, length= keys.length; i < length; i++)
			result[PreferenceKeysLength + i]= keys[i];
		
		fPreferenceKeys= result;
		
		if (fLoaded)
			load();
	}
}
