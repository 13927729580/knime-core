/*
 * --------------------------------------------------------------------- *
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2006
 * University of Konstanz, Germany.
 * Chair for Bioinformatics and Information Mining
 * Prof. Dr. Michael R. Berthold
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any quesions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * --------------------------------------------------------------------- *
 * 
 * 2006-06-08 (tm): reviewed
 */
package de.unikn.knime.core.node.property.hilite;

import java.util.Set;

import de.unikn.knime.core.data.DataCell;

/**
 * Interface for all hilite handlers supporting set/reset of hilite status
 * and un/registering of listeners.
 * 
 * All methods are public, allowing for objects being manager and listener at
 * the same time (i.e. they can change the status and query the current status).
 * 
 * {@link de.unikn.knime.core.node.property.hilite.HiLiteListener} can register
 * and will receive {@link de.unikn.knime.core.node.property.hilite.KeyEvent}
 * objects informing them about a change of status of certain row IDs.
 * 
 * <br />
 * Due to performance issues (as all views for example will query the status
 * from this object) all hilite handlers should be able to answer calls to get
 * status methods quickly.
 * 
 * @see DefaultHiLiteHandler
 * @see HiLiteListener
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
public interface HiLiteHandler {
    
    /**
     * Constant for the Menu entry 'HiLite'.
     */
    public static final String HILITE = "HiLite";
    
    /**
     * Constant for the menu entry 'HiLite Selected'.
     */
    public static final String HILITE_SELECTED = "HiLite Selected";
    
    /**
     * Constant for the menu entry 'UnHiLite Selected'.
     */
    public static final String UNHILITE_SELECTED = "UnHiLite Selected";
    
    /**
     * Constant for the menu entry 'Clear HiLite'.
     */
    public static final String CLEAR_HILITE = "Clear HiLite";
    
    /**
     * Adds a new <code>HiLiteListener</code> to the list of registered
     * listener objects that will then in turn recieve (un)hilite events.
     * 
     * @param listener the hilite listener to add
     */
    void addHiLiteListener(final HiLiteListener listener);

    /**
     * Removes the <code>HiLiteListener</code> from the list.
     * 
     * @param listener the hilite listener to remove from the list
     */
    void removeHiLiteListener(final HiLiteListener listener);

    /**
     * Removes all hilite listeners from this handler.
     */
    void removeAllHiLiteListeners();

    /**
     * Checks if the given row IDs have been hilit.
     * 
     * @param ids the row IDs to check the hilite status of
     * @return <code>true</code> if all IDs are hilit, <code>false</code>
     * otherwise
     */
    boolean isHiLit(final DataCell... ids);

    /**
     * Returns an unmodifiable set of hilit keys.
     * 
     * @return a set of hilit keys.
     */
    Set<DataCell> getHiLitKeys();

    /**
     * Hilites the given items and fires an event to all registered listeners.
     * 
     * @param ids an array of row IDs to hilite
     */
    void hiLite(final DataCell... ids);

    /**
     * Hilites the given keys and fires an event to all registered listeners.
     * 
     * @param ids the set of row keys to hilite
     */
    void hiLite(final Set<DataCell> ids);

    /**
     * Unhilites the given items and fires the event to all registered
     * listeners.
     * 
     * @param ids an array of row IDs to reset hilite status
     */
    void unHiLite(final DataCell... ids);

    /**
     * Unhilites the given keys and fires an event to all registered listeners.
     * 
     * @param ids the set of row IDs to unhilite
     */
    void unHiLite(final Set<DataCell> ids);

    /**
     * Unhilites all hilit items and fires an event.
     */
    void unHiLiteAll();
}
