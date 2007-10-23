/*******************************************************************************
 * Copyright (c) 2005 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.ext.ui.text;

import java.util.Arrays;

import org.apache.commons.collections.primitives.ArrayIntList;
import org.apache.commons.collections.primitives.IntList;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.TypedRegion;


/**
 * Utility methods for heuristic based R manipulations in an incomplete source file.
 *
 * <p>An instance holds some internal position in the document and is therefore not threadsafe.</p>
 *
 * @since 0.2
 */
public final class BasicHeuristicTokenScanner implements ITokenScanner {

	/* character constants */
//	private static final char LBRACE = '{';
//	private static final char RBRACE = '}';
//	private static final char LPAREN = '(';
//	private static final char RPAREN = ')';
//	private static final char SEMICOLON = ';';
//	private static final char COLON = ':';
//	private static final char COMMA = ',';
//	private static final char LBRACKET = '[';
//	private static final char RBRACKET = ']';
//	private static final char QUESTIONMARK = '?';
//	private static final char EQUAL = '=';
//	private static final char LANGLE = '<';
//	private static final char RANGLE = '>';

	/**
	 * Specifies the stop condition, upon which the <code>scan...</code> methods will decide whether
	 * to keep scanning or not. This interface may implemented by clients.
	 */
	private static abstract class StopCondition {
		/**
		 * Instructs the scanner to return the current position.
		 *
		 * @param ch the char at the current position
		 * @param position the current position
		 * @param forward the iteration direction
		 * @return <code>true</code> if the stop condition is met.
		 */
		public abstract boolean stop(char ch, int position, boolean forward);

		/**
		 * Asks the condition to return the next position to query. The default
		 * is to return the next/previous position.
		 *
		 * @return the next position to scan
		 */
		public int nextPosition(int position, boolean forward) {
			return forward ? position + 1 : position - 1;
		}
	}

	/**
	 * Stops upon a non-whitespace (as defined by {@link Character#isWhitespace(char)}) character.
	 */
//	private static class NonWhitespace extends StopCondition {
//		/*
//		 * @see org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner.StopCondition#stop(char)
//		 */
//		public boolean stop(char ch, int position, boolean forward) {
//			return !Character.isWhitespace(ch);
//		}
//	}

	/**
	 * Stops upon a non-whitespace character in the default partition.
	 *
	 * @see NonWhitespace
	 */
//	private final class NonWhitespaceDefaultPartition extends NonWhitespace {
//		/*
//		 * @see org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner.StopCondition#stop(char)
//		 */
//		public boolean stop(char ch, int position, boolean forward) {
//			return super.stop(ch, position, true) && isDefaultPartition(position);
//		}
//
//		/*
//		 * @see org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner.StopCondition#nextPosition(int, boolean)
//		 */
//		public int nextPosition(int position, boolean forward) {
//			ITypedRegion partition = getPartition(position);
//			if (fPartition.equals(partition.getType()))
//				return super.nextPosition(position, forward);
//
//			if (forward) {
//				int end= partition.getOffset() + partition.getLength();
//				if (position < end)
//					return end;
//			} else {
//				int offset= partition.getOffset();
//				if (position > offset)
//					return offset - 1;
//			}
//			return super.nextPosition(position, forward);
//		}
//	}

	/**
	 * Stops upon a non-java identifier (as defined by {@link Character#isJavaIdentifierPart(char)}) character.
	 */
//	private static class NonJavaIdentifierPart extends StopCondition {
//		/*
//		 * @see org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner.StopCondition#stop(char)
//		 */
//		public boolean stop(char ch, int position, boolean forward) {
//			return !Character.isJavaIdentifierPart(ch);
//		}
//	}

	/**
	 * Stops upon a non-java identifier character in the default partition.
	 *
	 * @see NonJavaIdentifierPart
	 */
//	private final class NonJavaIdentifierPartDefaultPartition extends NonJavaIdentifierPart {
//		/*
//		 * @see org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner.StopCondition#stop(char)
//		 */
//		public boolean stop(char ch, int position, boolean forward) {
//			return super.stop(ch, position, true) || !isDefaultPartition(position);
//		}
//
//		/*
//		 * @see org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner.StopCondition#nextPosition(int, boolean)
//		 */
//		public int nextPosition(int position, boolean forward) {
//			ITypedRegion partition= getPartition(position);
//			if (fPartition.equals(partition.getType()))
//				return super.nextPosition(position, forward);
//
//			if (forward) {
//				int end= partition.getOffset() + partition.getLength();
//				if (position < end)
//					return end;
//			} else {
//				int offset= partition.getOffset();
//				if (position > offset)
//					return offset - 1;
//			}
//			return super.nextPosition(position, forward);
//		}
//	}

	/**
	 * Stops upon a character in the default partition that matches the given character list.
	 */
	private class CharacterMatch extends StopCondition {
		protected final char[] fChars;

		/**
		 * Creates a new instance.
		 * @param ch the single character to match
		 */
		public CharacterMatch(char ch) {
			this(new char[] {ch});
		}

		/**
		 * Creates a new instance.
		 * @param chars the chars to match.
		 */
		public CharacterMatch(char[] chars) {
			Assert.isNotNull(chars);
			Assert.isTrue(chars.length > 0);
			fChars = chars;
			Arrays.sort(chars);
		}

		/* 
		 * @see de.walware.statet.ext.ui.text.BasicHeuristicTokenScanner.StopCondition#stop(char, int, boolean)
		 */
		public boolean stop(char ch, int position, boolean forward) {
			return Arrays.binarySearch(fChars, ch) >= 0 && (getContentType(position) == fPartition);
		}

		/* 
		 * @see de.walware.statet.ext.ui.text.BasicHeuristicTokenScanner.StopCondition#nextPosition(int, boolean)
		 */
		public int nextPosition(int position, boolean forward) {
			ITypedRegion partition = getPartition(position);
			if (fPartition.equals(partition.getType()))
				return super.nextPosition(position, forward);

			if (forward) {
				int end = partition.getOffset() + partition.getLength();
				if (position < end)
					return end;
			} else {
				int offset = partition.getOffset();
				if (position > offset)
					return offset - 1;
			}
			return super.nextPosition(position, forward);
		}
	}

	private class ExtCharacterMatch extends CharacterMatch {
		
		private final char fEscapeChar;
		private int lastEscapeOffset = -100;
		
		ExtCharacterMatch(char[] chars, char escapeChar) {
			super(chars);
			fEscapeChar = escapeChar;
		}

		public boolean stop(char ch, int position, boolean forward) {
			if (position == lastEscapeOffset+1)
				return false;
			if (ch == fEscapeChar) {
				lastEscapeOffset = position; 
				return false;
			}
			return Arrays.binarySearch(fChars, ch) >= 0 && (getContentType(position) == fPartition);
		}
		
	}
	
	
	/** The document being scanned. */
	private IDocument fDocument;
	/** The partitioning being used for scanning. */
	private String fPartitioning;
	/** The partition to scan in. */
	private String fPartition;

	/* internal scan state */

	/** the most recently read character. */
	private char fChar;
	/** the most recently read position. */
	private int fPos;
	/** the most recently read line of position (only if used). */
	private int fLine;

	/* preset stop conditions */
//	private final StopCondition fNonWSDefaultPart= new NonWhitespaceDefaultPartition();
//	private final static StopCondition fNonWS = new NonWhitespace();
//	private final StopCondition fNonIdent= new NonJavaIdentifierPartDefaultPartition();

	/**
	 * Creates a new instance.
	 * Before using scan..., you have to call configure...
	 * 
	 * @param partitioning the partitioning to use for scanning
	 */
	public BasicHeuristicTokenScanner(String partitioning) {
		Assert.isNotNull(partitioning);
		fPartitioning = partitioning;
	}
	
	/**
	 * 
	 * @param document the document to scan
	 * @param partition the partition to scan in
	 */
	public void configure(IDocument document, String partition) {
		Assert.isNotNull(document);
		Assert.isNotNull(partition);
		fDocument = document;
		fPartition = partition;
	}
	
//	public void configure(IDocument document, int offset) throws BadLocationException {
//		configure(document, TextUtilities.getContentType(
//				document, fPartitioning, offset, false));
//	}
	
	
	/**
	 * Returns the most recent internal scan position.
	 *
	 * @return the most recent internal scan position.
	 */
	public int getPosition() {
		return fPos;
	}

	/**
	 * Returns the next token in forward direction, starting at <code>start</code>, and not extending
	 * further than <code>bound</code>. The return value is one of the constants defined in {@link Symbols}.
	 * After a call, {@link #getPosition()} will return the position just after the scanned token
	 * (i.e. the next position that will be scanned).
	 *
	 * @param start the first character position in the document to consider
	 * @param bound the first position not to consider any more
	 * @return a constant from {@link Symbols} describing the next token
	 */
//	public int nextToken(int start, int bound) {
//		int pos = scanForward(start, bound, fNonWSDefaultPart);
//		if (pos == NOT_FOUND)
//			return TokenEOF;
//
//		fPos++;
//
//		switch (fChar) {
//			case LBRACE:
//				return TokenLBRACE;
//			case RBRACE:
//				return TokenRBRACE;
//			case LBRACKET:
//				return TokenLBRACKET;
//			case RBRACKET:
//				return TokenRBRACKET;
//			case LPAREN:
//				return TokenLPAREN;
//			case RPAREN:
//				return TokenRPAREN;
//			case SEMICOLON:
//				return TokenSEMICOLON;
//			case COMMA:
//				return TokenCOMMA;
//			case QUESTIONMARK:
//				return TokenQUESTIONMARK;
//			case EQUAL:
//				return TokenEQUAL;
//			case LANGLE:
//				return TokenLESSTHAN;
//			case RANGLE:
//				return TokenGREATERTHAN;
//		}
//
//		// else
//		if (Character.isJavaIdentifierPart(fChar)) {
//			// assume an ident or keyword
//			int from= pos, to;
//			pos= scanForward(pos + 1, bound, fNonIdent);
//			if (pos == NOT_FOUND)
//				to= bound == UNBOUND ? fDocument.getLength() : bound;
//			else
//				to= pos;
//
//			String identOrKeyword;
//			try {
//				identOrKeyword= fDocument.get(from, to - from);
//			} catch (BadLocationException e) {
//				return TokenEOF;
//			}
//
//			return getToken(identOrKeyword);
//
//
//		} else {
//			// operators, number literals etc
//			return TokenOTHER;
//		}
//	}

//	/**
//	 * Returns the next token in backward direction, starting at <code>start</code>, and not extending
//	 * further than <code>bound</code>. The return value is one of the constants defined in {@link Symbols}.
//	 * After a call, {@link #getPosition()} will return the position just before the scanned token
//	 * starts (i.e. the next position that will be scanned).
//	 *
//	 * @param start the first character position in the document to consider
//	 * @param bound the first position not to consider any more
//	 * @return a constant from {@link Symbols} describing the previous token
//	 */
//	public int previousToken(int start, int bound) {
//		int pos= scanBackward(start, bound, fNonWSDefaultPart);
//		if (pos == NOT_FOUND)
//			return TokenEOF;
//
//		fPos--;
//
//		switch (fChar) {
//			case LBRACE:
//				return TokenLBRACE;
//			case RBRACE:
//				return TokenRBRACE;
//			case LBRACKET:
//				return TokenLBRACKET;
//			case RBRACKET:
//				return TokenRBRACKET;
//			case LPAREN:
//				return TokenLPAREN;
//			case RPAREN:
//				return TokenRPAREN;
//			case SEMICOLON:
//				return TokenSEMICOLON;
//			case COLON:
//				return TokenCOLON;
//			case COMMA:
//				return TokenCOMMA;
//			case QUESTIONMARK:
//				return TokenQUESTIONMARK;
//			case EQUAL:
//				return TokenEQUAL;
//			case LANGLE:
//				return TokenLESSTHAN;
//			case RANGLE:
//				return TokenGREATERTHAN;
//		}
//
//		// else
//		if (Character.isJavaIdentifierPart(fChar)) {
//			// assume an ident or keyword
//			int from, to= pos + 1;
//			pos= scanBackward(pos - 1, bound, fNonIdent);
//			if (pos == NOT_FOUND)
//				from= bound == UNBOUND ? 0 : bound + 1;
//			else
//				from= pos + 1;
//
//			String identOrKeyword;
//			try {
//				identOrKeyword= fDocument.get(from, to - from);
//			} catch (BadLocationException e) {
//				return TokenEOF;
//			}
//
//			return getToken(identOrKeyword);
//
//
//		} else {
//			// operators, number literals etc
//			return TokenOTHER;
//		}
//
//	}

	/**
	 * Returns one of the keyword constants or <code>TokenIDENT</code> for a scanned identifier.
	 *
	 * @param s a scanned identifier
	 * @return one of the constants defined in {@link Symbols}
	 */
//	private int getToken(String s) {
//		Assert.isNotNull(s);
//
//		switch (s.length()) {
//			case 2:
//				if ("if".equals(s)) //$NON-NLS-1$
//					return TokenIF;
//				if ("do".equals(s)) //$NON-NLS-1$
//					return TokenDO;
//				break;
//			case 3:
//				if ("for".equals(s)) //$NON-NLS-1$
//					return TokenFOR;
//				if ("try".equals(s)) //$NON-NLS-1$
//					return TokenTRY;
//				if ("new".equals(s)) //$NON-NLS-1$
//					return TokenNEW;
//				break;
//			case 4:
//				if ("case".equals(s)) //$NON-NLS-1$
//					return TokenCASE;
//				if ("else".equals(s)) //$NON-NLS-1$
//					return TokenELSE;
//				if ("enum".equals(s)) //$NON-NLS-1$
//					return TokenENUM;
//				if ("goto".equals(s)) //$NON-NLS-1$
//					return TokenGOTO;
//				break;
//			case 5:
//				if ("break".equals(s)) //$NON-NLS-1$
//					return TokenBREAK;
//				if ("catch".equals(s)) //$NON-NLS-1$
//					return TokenCATCH;
//				if ("class".equals(s)) //$NON-NLS-1$
//					return TokenCLASS;
//				if ("while".equals(s)) //$NON-NLS-1$
//					return TokenWHILE;
//				break;
//			case 6:
//				if ("return".equals(s)) //$NON-NLS-1$
//					return TokenRETURN;
//				if ("static".equals(s)) //$NON-NLS-1$
//					return TokenSTATIC;
//				if ("switch".equals(s)) //$NON-NLS-1$
//					return TokenSWITCH;
//				break;
//			case 7:
//				if ("default".equals(s)) //$NON-NLS-1$
//					return TokenDEFAULT;
//				if ("finally".equals(s)) //$NON-NLS-1$
//					return TokenFINALLY;
//				break;
//			case 9:
//				if ("interface".equals(s)) //$NON-NLS-1$
//					return TokenINTERFACE;
//				break;
//			case 12:
//				if ("synchronized".equals(s)) //$NON-NLS-1$
//					return TokenSYNCHRONIZED;
//				break;
//		}
//		return TokenIDENT;
//	}

	/* 
	 * @see de.walware.statet.ext.ui.text.ITokenScanner#findClosingPeer(int, char[])
	 */
	public int findClosingPeer(int start, char[] pair) {
		return findClosingPeer(start, pair, (char)0);
	}

	/* 
	 * @see de.walware.statet.ext.ui.text.ITokenScanner#findClosingPeer(int, char[], char)
	 */
	public int findClosingPeer(int start, char[] pair, char escapeChar) {
		
		Assert.isNotNull(fDocument);
		Assert.isTrue(start >= 0);
		
		StopCondition cond = (escapeChar == (char)0) ? 
				new CharacterMatch(pair) : new ExtCharacterMatch(pair, escapeChar);

		try {
			int depth = 1;
			start -= 1;
			while (true) {
				start = scanForward(start + 1, UNBOUND, cond);
				if (start == NOT_FOUND)
					return NOT_FOUND;

				if (fDocument.getChar(start) == pair[OPENING_PEER])
					depth++;
				else
					depth--;

				if (depth == 0)
					return start;
			}

		} catch (BadLocationException e) {
			return NOT_FOUND;
		}
	}

	/*
	 * @see de.walware.statet.ext.ui.text.ITokenScanner#findOpeningPeer(int, char[])
	 */
	public int findOpeningPeer(int start, char[] pair) {
		Assert.isTrue(start < fDocument.getLength());

		StopCondition cond = new CharacterMatch(pair);

		try {
			int depth= 1;
			start += 1;
			while (true) {
				start = scanBackward(start - 1, UNBOUND, cond);
				if (start == NOT_FOUND)
					return NOT_FOUND;

				if (fDocument.getChar(start) == pair[CLOSING_PEER])
					depth++;
				else
					depth--;

				if (depth == 0)
					return start;
			}

		} catch (BadLocationException e) {
			return NOT_FOUND;
		}
	}

	public int findOpeningPeer(int start, char[] pair, char escapeChar) {
		Assert.isTrue(start < fDocument.getLength());
		if (escapeChar == (char)0)
			return findOpeningPeer(start, pair);

		StopCondition cond = new ExtCharacterMatch(pair, escapeChar);

		try {
			int depth= 1;
			start += 1;
			fLine = fDocument.getLineOfOffset(start);			
			while (true) {
				int[] list = preScanBackward(start - 1, UNBOUND, cond);
				if (list == null)
					return NOT_FOUND;
				
				for (int i = list.length-1; i >= 0; i--) {
					start = list[i];
					if (fDocument.getChar(start) == pair[CLOSING_PEER])
						depth++;
					else
						depth--;
					
					if (depth == 0)
						return start;
				}
				start = fDocument.getLineOffset(fLine);
			}

		} catch (BadLocationException e) {
			return NOT_FOUND;
		}
	}
	
	private int[] preScanBackward(int start, int bound, StopCondition condition) throws BadLocationException {

		IntList list = new ArrayIntList();
		int scanEnd = start;

		NEXT_LINE: while (list.isEmpty() && fLine >= 0) {
			int lineOffset = fDocument.getLineOffset(fLine);
			int next = lineOffset - 1;
			while ((next = scanForward(next + 1, scanEnd, condition)) != NOT_FOUND) {
				if (bound < next)
					list.add(next);
			}
			
			if (lineOffset <= bound)
				break NEXT_LINE;
			
			fLine--;
			scanEnd = lineOffset - 1;
		}
		
		if (!list.isEmpty())
			return list.toArray();
		else
			return null;
	}


	/**
	 * Computes the surrounding block around <code>offset</code>. The search is started at the
	 * beginning of <code>offset</code>, i.e. an opening brace at <code>offset</code> will not be
	 * part of the surrounding block, but a closing brace will.
	 *
	 * @param offset the offset for which the surrounding block is computed
	 * @return a region describing the surrounding block, or <code>null</code> if none can be found
	 */
//	public IRegion findSurroundingBlock(int offset) {
//		if (offset < 1 || offset >= fDocument.getLength())
//			return null;
//
//		int begin = findOpeningPeer(offset - 1, LBRACE, RBRACE);
//		int end = findClosingPeer(offset, LBRACE, RBRACE);
//		if (begin == NOT_FOUND || end == NOT_FOUND)
//			return null;
//		return new Region(begin, end + 1 - begin);
//	}

	/**
	 * Finds the smallest position in <code>fDocument</code> such that the position is &gt;= <code>position</code>
	 * and &lt; <code>bound</code> and <code>Character.isWhitespace(fDocument.getChar(pos))</code> evaluates to <code>false</code>
	 * and the position is in the default partition.
	 *
	 * @param position the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &gt; <code>position</code>, or <code>UNBOUND</code>
	 * @return the smallest position of a non-whitespace character in [<code>position</code>, <code>bound</code>) that resides in a Java partition, or <code>NOT_FOUND</code> if none can be found
	 */
//	public int findNonWhitespaceForward(int position, int bound) {
//		return scanForward(position, bound, fNonWSDefaultPart);
//	}

	/**
	 * Finds the smallest position in <code>fDocument</code> such that the position is &gt;= <code>position</code>
	 * and &lt; <code>bound</code> and <code>Character.isWhitespace(fDocument.getChar(pos))</code> evaluates to <code>false</code>.
	 *
	 * @param position the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &gt; <code>position</code>, or <code>UNBOUND</code>
	 * @return the smallest position of a non-whitespace character in [<code>position</code>, <code>bound</code>), or <code>NOT_FOUND</code> if none can be found
	 */
//	public int findNonWhitespaceForwardInAnyPartition(int position, int bound) {
//		return scanForward(position, bound, fNonWS);
//	}

	/**
	 * Finds the highest position in <code>fDocument</code> such that the position is &lt;= <code>position</code>
	 * and &gt; <code>bound</code> and <code>Character.isWhitespace(fDocument.getChar(pos))</code> evaluates to <code>false</code>
	 * and the position is in the default partition.
	 *
	 * @param position the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &lt; <code>position</code>, or <code>UNBOUND</code>
	 * @return the highest position of a non-whitespace character in (<code>bound</code>, <code>position</code>] that resides in a Java partition, or <code>NOT_FOUND</code> if none can be found
	 */
//	public int findNonWhitespaceBackward(int position, int bound) {
//		return scanBackward(position, bound, fNonWSDefaultPart);
//	}

	/**
	 * Finds the lowest position <code>p</code> in <code>fDocument</code> such that <code>start</code> &lt;= p &lt;
	 * <code>bound</code> and <code>condition.stop(fDocument.getChar(p), p)</code> evaluates to <code>true</code>.
	 *
	 * @param start the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &gt; <code>start</code>, or <code>UNBOUND</code>
	 * @param condition the <code>StopCondition</code> to check
	 * @return the lowest position in [<code>start</code>, <code>bound</code>) for which <code>condition</code> holds, or <code>NOT_FOUND</code> if none can be found
	 */
	protected int scanForward(int start, int bound, StopCondition condition) {
		Assert.isTrue(start >= 0);

		if (bound == UNBOUND)
			bound = fDocument.getLength();

		Assert.isTrue(bound <= fDocument.getLength());

		try {
			fPos = start;
			while (fPos < bound) {

				fChar = fDocument.getChar(fPos);
				if (condition.stop(fChar, fPos, true))
					return fPos;

				fPos = condition.nextPosition(fPos, true);
			}
		} catch (BadLocationException e) {
		}
		return NOT_FOUND;
	}


	/**
	 * Finds the lowest position in <code>fDocument</code> such that the position is &gt;= <code>position</code>
	 * and &lt; <code>bound</code> and <code>fDocument.getChar(position) == ch</code> evaluates to <code>true</code>
	 * and the position is in the default partition.
	 *
	 * @param position the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &gt; <code>position</code>, or <code>UNBOUND</code>
	 * @param ch the <code>char</code> to search for
	 * @return the lowest position of <code>ch</code> in (<code>bound</code>, <code>position</code>] that resides in a Java partition, or <code>NOT_FOUND</code> if none can be found
	 */
	public int scanForward(int position, int bound, char ch) {
		return scanForward(position, bound, new CharacterMatch(ch));
	}

	/**
	 * Finds the lowest position in <code>fDocument</code> such that the position is &gt;= <code>position</code>
	 * and &lt; <code>bound</code> and <code>fDocument.getChar(position) == ch</code> evaluates to <code>true</code> for at least one
	 * ch in <code>chars</code> and the position is in the default partition.
	 *
	 * @param position the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &gt; <code>position</code>, or <code>UNBOUND</code>
	 * @param chars an array of <code>char</code> to search for
	 * @return the lowest position of a non-whitespace character in [<code>position</code>, <code>bound</code>) that resides in a Java partition, or <code>NOT_FOUND</code> if none can be found
	 */
	public int scanForward(int position, int bound, char[] chars) {
		return scanForward(position, bound, new CharacterMatch(chars));
	}

	/**
	 * Finds the highest position <code>p</code> in <code>fDocument</code> such that <code>bound</code> &lt; <code>p</code> &lt;= <code>start</code>
	 * and <code>condition.stop(fDocument.getChar(p), p)</code> evaluates to <code>true</code>.
	 *
	 * @param start the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &lt; <code>start</code>, or <code>UNBOUND</code>
	 * @param condition the <code>StopCondition</code> to check
	 * @return the highest position in (<code>bound</code>, <code>start</code> for which <code>condition</code> holds, or <code>NOT_FOUND</code> if none can be found
	 */
	protected int scanBackward(int start, int bound, StopCondition condition) {
		if (bound == UNBOUND)
			bound = -1;

		Assert.isTrue(bound >= -1);
		Assert.isTrue(start < fDocument.getLength() );

		try {
			fPos= start;
			while (fPos > bound) {

				fChar = fDocument.getChar(fPos);
				if (condition.stop(fChar, fPos, false))
					return fPos;

				fPos = condition.nextPosition(fPos, false);
			}
		} catch (BadLocationException e) {
		}
		return NOT_FOUND;
	}
	
	/**
	 * Finds the highest position in <code>fDocument</code> such that the position is &lt;= <code>position</code>
	 * and &gt; <code>bound</code> and <code>fDocument.getChar(position) == ch</code> evaluates to <code>true</code> for at least one
	 * ch in <code>chars</code> and the position is in the default partition.
	 *
	 * @param position the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &lt; <code>position</code>, or <code>UNBOUND</code>
	 * @param ch the <code>char</code> to search for
	 * @return the highest position of one element in <code>chars</code> in (<code>bound</code>, <code>position</code>] that resides in a Java partition, or <code>NOT_FOUND</code> if none can be found
	 */
	public int scanBackward(int position, int bound, char ch) {
		return scanBackward(position, bound, new CharacterMatch(ch));
	}

	/**
	 * Finds the highest position in <code>fDocument</code> such that the position is &lt;= <code>position</code>
	 * and &gt; <code>bound</code> and <code>fDocument.getChar(position) == ch</code> evaluates to <code>true</code> for at least one
	 * ch in <code>chars</code> and the position is in the default partition.
	 *
	 * @param position the first character position in <code>fDocument</code> to be considered
	 * @param bound the first position in <code>fDocument</code> to not consider any more, with <code>bound</code> &lt; <code>position</code>, or <code>UNBOUND</code>
	 * @param chars an array of <code>char</code> to search for
	 * @return the highest position of one element in <code>chars</code> in (<code>bound</code>, <code>position</code>] that resides in a Java partition, or <code>NOT_FOUND</code> if none can be found
	 */
	public int scanBackward(int position, int bound, char[] chars) {
		return scanBackward(position, bound, new CharacterMatch(chars));
	}

	/**
	 * Checks whether <code>position</code> resides in a default (Java) partition of <code>fDocument</code>.
	 *
	 * @param position the position to be checked
	 * @return <code>true</code> if <code>position</code> is in the default partition of <code>fDocument</code>, <code>false</code> otherwise
	 */
//	public boolean isDefaultPartition(int position) {
//		Assert.isTrue(position >= 0);
//		Assert.isTrue(position <= fDocument.getLength());
//
//		try {
//			return fPartition.equals(TextUtilities.getContentType(fDocument, fPartitioning, position, false));
//		} catch (BadLocationException e) {
//			return false;
//		}
//	}

	/**
	 * Returns the partition at <code>position</code>.
	 *
	 * @param position the position to get the partition for
	 * @return the content type at <code>position</code> or a dummy zero-length
	 *         partition if accessing the document fails
	 */
	private String getContentType(int position) {
		try {
			return TextUtilities.getContentType(fDocument, fPartitioning, position, false);
		} catch (BadLocationException e) {
			return null; // ?
		}
	}

	/**
	 * Returns the partition at <code>position</code>.
	 *
	 * @param position the position to get the partition for
	 * @return the partition at <code>position</code> or a dummy zero-length
	 *         partition if accessing the document fails
	 */
	private ITypedRegion getPartition(int position) {
		try {
			return TextUtilities.getPartition(fDocument, fPartitioning, position, false);
		} catch (BadLocationException e) {
			return new TypedRegion(position, 0, "__no_partition_at_all"); //$NON-NLS-1$
		}
	}

	/**
	 * Checks if the line seems to be an open condition not followed by a block (i.e. an if, while,
	 * or for statement with just one following statement, see example below).
	 *
	 * <pre>
	 * if (condition)
	 *     doStuff();
	 * </pre>
	 *
	 * <p>Algorithm: if the last non-WS, non-Comment code on the line is an if (condition), while (condition),
	 * for( expression), do, else, and there is no statement after that </p>
	 *
	 * @param position the insert position of the new character
	 * @param bound the lowest position to consider
	 * @return <code>true</code> if the code is a conditional statement or loop without a block, <code>false</code> otherwise
	 */
//	public boolean isBracelessBlockStart(int position, int bound) {
//		if (position < 1)
//			return false;
//
//		switch (previousToken(position, bound)) {
//			case TokenDO:
//			case TokenELSE:
//				return true;
//			case TokenRPAREN:
//				position= findOpeningPeer(fPos, LPAREN, RPAREN);
//				if (position > 0) {
//					switch (previousToken(position - 1, bound)) {
//						case TokenIF:
//						case TokenFOR:
//						case TokenWHILE:
//							return true;
//					}
//				}
//		}
//
//		return false;
//	}
	
	
}
