/*=============================================================================#
 # Copyright (c) 2010-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.core.rhelp;

import java.util.List;

import org.eclipse.core.runtime.CoreException;

import de.walware.jcommons.collections.ImCollections;
import de.walware.jcommons.collections.ImList;

import de.walware.statet.r.core.renv.IREnv;
import de.walware.statet.r.internal.core.rhelp.index.IREnvIndex;
import de.walware.statet.r.internal.core.rhelp.index.SearchQuery;


public class RHelpSearchQuery {
	
	
	public static final int TOPIC_SEARCH= 1;
	public static final int FIELD_SEARCH= 2;
	public static final int DOC_SEARCH= 3;
	
	public static final String TOPICS_FIELD= IREnvIndex.ALIAS_TXT_FIELD_NAME;
	public static final String TITLE_FIELD= IREnvIndex.TITLE_TXT_FIELD_NAME;
	public static final String CONCEPTS_FIELD= IREnvIndex.CONCEPT_TXT_FIELD_NAME;
	
	
	public static class Compiled extends RHelpSearchQuery {
		
		private final Object fCompiled;
		
		public Compiled(final RHelpSearchQuery org, final Object compiled) {
			super(org.getSearchType(),
					org.getSearchString(),
					org.getEnabledFields(),
					org.getKeywords(),
					org.getPackages(),
					org.getREnv().resolve() );
			this.fCompiled= compiled;
		}
		
		@Override
		public Compiled compile() {
			return this;
		}
		
		public Object compiled() {
			return this.fCompiled;
		}
		
	}
	
	
	private final int searchType;
	private final String searchText;
	private final ImList<String> fields;
	private final ImList<String> keywords;
	private final ImList<String> packages;
	
	private final IREnv rEnv;
	
	
	/**
	 * @param type
	 * @param text
	 * @param fields
	 * @param keywords
	 * @param packages
	 */
	public RHelpSearchQuery(final int type, final String text, final List<String> fields,
			final List<String> keywords, final List<String> packages, final IREnv rEnv) {
		this.searchType= type;
		this.searchText= text;
		this.fields= ImCollections.toList(fields);
		this.keywords= ImCollections.toList(keywords);
		this.packages= ImCollections.toList(packages);
		this.rEnv= rEnv;
	}
	
	
	public IREnv getREnv() {
		return this.rEnv;
	}
	
	public int getSearchType() {
		return this.searchType;
	}
	
	public String getSearchString() {
		return this.searchText;
	}
	
	public ImList<String> getEnabledFields() {
		return this.fields;
	}
	
	public ImList<String> getKeywords() {
		return this.keywords;
	}
	
	public ImList<String> getPackages() {
		return this.packages;
	}
	
	public RHelpSearchQuery.Compiled compile() throws CoreException {
		final Object compiled= SearchQuery.compile(this);
		return new RHelpSearchQuery.Compiled(this, compiled);
	}
	
	
	@Override
	public String toString() {
		return this.searchText;
	}
	
}
