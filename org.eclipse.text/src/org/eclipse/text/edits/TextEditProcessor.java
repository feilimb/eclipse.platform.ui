/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.text.edits;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

/**
 * A <code>TextEditProcessor</code> manages a set of edits and applies
 * them as a whole to an <code>IDocument</code>.
 * <p>
 * This class isn't intended to be subclassed.
 * 
 * @see org.eclipse.text.edits.TextEdit#apply(IDocument)
 * 
 * @since 3.0
 */
public class TextEditProcessor {
	
	private IDocument fDocument;
	private TextEdit fRoot;
	private int fStyle;
	
	private boolean fChecked;
	private MalformedTreeException fException;
	
	private List fSourceEdits;
	
	/**
	 * Constructs a new edit processor for the given
	 * document.
	 * 
	 * @param document the document to manipulate
	 * @param root the root of the text edit tree describing
	 *  the modifications. By passing a text edit a a text edit
	 *  processor the ownership of the edit is transfered to the
	 *  text edit processors. Clients must not modify the edit
	 *  (e.g adding new children) any longer.
	 * @param style {@link TextEdit#NONE}, {@link TextEdit#CREATE_UNDO} or {@link TextEdit#UPDATE_REGIONS}) 
	 */
	public TextEditProcessor(IDocument document, TextEdit root, int style) {
		Assert.isNotNull(document);
		Assert.isNotNull(root);
		fDocument= document;
		fRoot= root;
		if (fRoot instanceof MultiTextEdit)
			((MultiTextEdit)fRoot).defineRegion(0);
		fStyle= style;
	}
	
	/**
	 * Returns the document to be manipulated.
	 * 
	 * @return the document
	 */
	public IDocument getDocument() {
		return fDocument;
	}
	
	/**
	 * Returns the edit processor's root edit.
	 * 
	 * @return the processor's root edit
	 */
	public TextEdit getRoot() {
		return fRoot;
	}
	
	/**
	 * Returns the style bits of the text edit processor
	 * 
	 * @return the style bits
	 * @see TextEdit#CREATE_UNDO
	 * @see TextEdit#UPDATE_REGIONS
	 */
	public int getStyle() {
		return fStyle;
	}
	
	/**
	 * Checks if the processor can execute all its edits.
	 * 
	 * @return <code>true</code> if the edits can be executed. Return  <code>false
	 * 	</code>otherwise. One major reason why edits cannot be executed are wrong 
	 *  offset or length values of edits. Calling perform in this case will very
	 *  likely end in a <code>BadLocationException</code>.
	 */
	public boolean canPerformEdits() {
		try {
			fRoot.dispatchCheckIntegrity(this);
			fChecked= true;
		} catch (MalformedTreeException e) {
			fException= e;
			return false;
		}
		return true;
	}
	
	/**
	 * Executes the text edits.
	 * 
	 * @return an object representing the undo of the executed edits
	 * @exception MalformedTreeException is thrown if the edit tree isn't
	 *  in a valid state. This exception is thrown before any edit is executed. 
	 *  So the document is still in its original state.
	 * @exception BadLocationException is thrown if one of the edits in the 
	 *  tree can't be executed. The state of the document is undefined if this 
	 *  exception is thrown.
	 */
	public UndoEdit performEdits() throws MalformedTreeException, BadLocationException {
		if (!fChecked) {
			fRoot.dispatchCheckIntegrity(this);
		} else {
			if (fException != null)
				throw fException;
		}
		return fRoot.dispatchPerformEdits(this);
	}

	/* non Java-doc
	 * Class isn't intended to be sublcassed
	 */	
	protected boolean considerEdit(TextEdit edit) {
		return true;
	}
		
	//---- checking --------------------------------------------------------------------
	
	/* package */ void checkIntegrityDo() throws MalformedTreeException {
		fSourceEdits= new ArrayList();
		fRoot.traverseConsistencyCheck(this, fDocument, fSourceEdits);
		if (fRoot.getExclusiveEnd() > fDocument.getLength())
			throw new MalformedTreeException(null, fRoot, TextEditMessages.getString("TextEditProcessor.invalid_length")); //$NON-NLS-1$
	}
	
	/* package */ void checkIntegrityUndo() {
		if (fRoot.getExclusiveEnd() > fDocument.getLength())
			throw new MalformedTreeException(null, fRoot, TextEditMessages.getString("TextEditProcessor.invalid_length")); //$NON-NLS-1$
	}
	
	//---- execution --------------------------------------------------------------------
	
	/* package */ UndoEdit executeDo() throws BadLocationException {
		UndoCollector collector= new UndoCollector(fRoot);
		try {
			if (createUndo())
				collector.connect(fDocument);
			computeSources();
			fRoot.traverseDocumentUpdating(this, fDocument);
			if (updateRegions()) {
				fRoot.traverseRegionUpdating(this, fDocument, 0, false);
			}
		} finally {
			collector.disconnect(fDocument);
		}
		return collector.undo;
	}
	
	private void computeSources() {
		for (Iterator iter= fSourceEdits.iterator(); iter.hasNext();) {
			List list= (List)iter.next();
			if (list != null) {
				for (Iterator edits= list.iterator(); edits.hasNext();) {
					TextEdit edit= (TextEdit)edits.next();
					edit.traverseSourceComputation(this, fDocument);
				}
			}
		}
	}
	
	/* package */ UndoEdit executeUndo() throws BadLocationException {
		UndoCollector collector= new UndoCollector(fRoot);
		try {
			if (createUndo())
				collector.connect(fDocument);
			TextEdit[] edits= fRoot.getChildren();
			for (int i= edits.length - 1; i >= 0; i--) {
				edits[i].performDocumentUpdating(fDocument);
			}
		} finally {
			collector.disconnect(fDocument);
		}
		return collector.undo;
	}
	
	private boolean createUndo() {
		return (fStyle & TextEdit.CREATE_UNDO) != 0;
	}
	
	private boolean updateRegions() {
		return (fStyle & TextEdit.UPDATE_REGIONS) != 0;
	}
}
