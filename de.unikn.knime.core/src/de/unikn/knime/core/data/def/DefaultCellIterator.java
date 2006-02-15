/* @(#)$RCSfile$ 
 * $Revision$ $Date$ $Author$
 * 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2006
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 * 
 * History
 *   Nov 23, 2005 (wiswedel): created
 */
package de.unikn.knime.core.data.def;

import java.util.Iterator;
import java.util.NoSuchElementException;

import de.unikn.knime.core.data.DataCell;
import de.unikn.knime.core.data.DataRow;

/**
 * Default implementation of an iterator over <code>DataCell</code>s of a
 * DataRow. It uses the <code>getNumCells()</code> and
 * <code>getCell(int)</code> methods of the underlying row to return the
 * cells. Hence, it starts at the first cell in the row and then sequentially
 * returns the cell up to the last cell in the row.
 * 
 * <p>
 * The iterator doesn't support removal of datacells, an invocation of the 
 * method <code>remove()</code> will end up with an exception.
 * 
 * @author wiswedel, University of Konstanz
 */
public class DefaultCellIterator implements Iterator<DataCell> {

    /** The row to iterate over. */
    private final DataRow m_row;

    /** The index of the cell that will be returned next. */
    private int m_index;

    /**
     * Creates a new iterator over a given <code>DataRow</code>.
     * 
     * @param row The row to traverse.
     * @throws NullPointerException If argument is null.
     */
    public DefaultCellIterator(final DataRow row) {
        if (row == null) {
            throw new NullPointerException("Row must not be null.");
        }
        m_row = row;
        m_index = 0;
    }

    /**
     * @see java.util.Iterator#hasNext()
     */
    public boolean hasNext() {
        return m_index < m_row.getNumCells();
    }

    /**
     * @see java.util.Iterator#next()
     */
    public DataCell next() {
        if (!hasNext()) {
            throw new NoSuchElementException("Iterator is at end.");
        }
        DataCell result = m_row.getCell(m_index);
        m_index++;
        return result;
    }

    /**
     * Throws UnsupportedOperationException as this operation is not supported.
     * 
     * @see java.util.Iterator#remove()
     */
    public void remove() {
        throw new UnsupportedOperationException(
                "Removing cells is not allowed.");
    }

}
