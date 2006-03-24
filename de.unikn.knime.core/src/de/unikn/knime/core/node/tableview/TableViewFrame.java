/*
 * --------------------------------------------------------------------- *
 *   This source code, its documentation and all appendant files         *
 *   are protected by copyright law. All rights reserved.                *
 *                                                                       *
 *   Copyright, 2003 - 2006                                              *
 *   Universitaet Konstanz, Germany.                                     *
 *   Lehrstuhl fuer Angewandte Informatik                                *
 *   Prof. Dr. Michael R. Berthold                                       *
 *                                                                       *
 *   You may not modify, publish, transmit, transfer or sell, reproduce, *
 *   create derivative works from, distribute, perform, display, or in   *
 *   any way exploit any of the content, in whole or in part, except as  *
 *   otherwise expressly permitted in writing by the copyright owner.    *
 * --------------------------------------------------------------------- *
 */
package de.unikn.knime.core.node.tableview;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.WindowConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import de.unikn.knime.core.data.DataTable;
import de.unikn.knime.core.data.def.DefaultTable;
import de.unikn.knime.core.node.property.hilite.HiLiteHandler;

/** 
 * Frame for a <code>TableContentView</code>. It simply puts a table view into
 * a frame, adds a menu bar (by now with one item "Close") and displays it.
 * 
 * <p><b>Note:</b>This class is obsolete as a table view pops up in an specific
 * frame in the flow. But this view is currently used inside the port to show
 * its data table. Might be extended.
 * 
 * @author Bernd Wiswedel, University of Konstanz
 * @see de.unikn.knime.core.node.tableview.TableContentView
 * @see de.unikn.knime.core.node.tableview.TableContentModel
 */
public class TableViewFrame extends JFrame {
    
    /** Scroll pane to be displayed. Holds the table in its view port */
    private final TableView m_scroller;
    
    /**
     * Opens new frame and displays the <code>TableContentView</code> in a 
     * scroll pane.
     * @param view The table content view to display.
     * @throws NullPointerException if <code>view</code> is <code>null</code>.
     */
    public TableViewFrame(final TableContentView view) {
        if (view == null) {
            throw new NullPointerException("View must not be null.");
        }
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Close");
        // emulate same handling as done by JFrame
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                int defaultCloseOperation = getDefaultCloseOperation();
                switch (defaultCloseOperation) {
                    case WindowConstants.DO_NOTHING_ON_CLOSE: 
                        return;
                    case WindowConstants.HIDE_ON_CLOSE: 
                        TableViewFrame.this.setVisible(false);
                        return;
                    case WindowConstants.DISPOSE_ON_CLOSE:
                        TableViewFrame.this.setVisible(false);
                        TableViewFrame.this.dispose();
                        return;
                    case WindowConstants.EXIT_ON_CLOSE:
                        TableViewFrame.this.dispose();
                        System.exit(0);
                    default:
                        // do nothing
                }
            }
        });
        menu.add(item);
        menuBar.add(menu);
        setJMenuBar(menuBar);
        m_scroller = new TableView(view);
        
        // TableModelListener that prints the row and column count in the 
        // frame's title bar. It is updated as new rows are inserted (user
        // scrolls down)
        view.getContentModel().addTableModelListener(new TableModelListener() {
            public void tableChanged(final TableModelEvent e) {
                // fired when new rows have been seen (refer to description
                // of caching strategy of the model)
                if (e.getType() == TableModelEvent.INSERT) {
                    updateTitle();
                }
            }
        });
        
        getJMenuBar().add(m_scroller.createHighlightMenu());
        getJMenuBar().add(m_scroller.createNavigationMenu());
        getJMenuBar().add(m_scroller.createViewMenu());
        
        getContentPane().add(m_scroller);
        updateTitle();
        pack();
        setVisible(true);
        
    } // TableViewFrame(TableContentView)
    
    /** 
     * Creates a new frame by initializing a new <code>TableContentView</code>
     * and displays it.
     * @param table The table to display.
     * @see TableContentView#TableContentView(DataTable)
     * @throws NullPointerException if <code>table</code> is <code>null</code>. 
     */ 
    public TableViewFrame(final DataTable table) {
        this(new TableContentView(table));
    }
    
    /** 
     * Get reference to underlying <code>TableView</code>.
     * @return The scroll pane displayed.
     */
    public TableView getTableView() {
        return m_scroller;
    }
    
    /** Delegating method to internal table view.
     * @return If data is available.
     */
    public boolean hasData() {
        return m_scroller.hasData();
    }
    
    /** Delegating method to internal table view.
     * @return If highlight handler has been set.
     */
    public boolean hasHiLiteHandler() {
        return m_scroller.hasHiLiteHandler();
    }
    
    /** Delegating method to internal table view.
     * @param data New data to be displayed
     */
    public void setDataTable(final DataTable data) {
        m_scroller.setDataTable(data);
    }

    /** Delegating method to internal table view.
     * @param hiLiteHdl A new highlight handler.
     */
    public void setHiLiteHandler(final HiLiteHandler hiLiteHdl) {
        m_scroller.setHiLiteHandler(hiLiteHdl);
    }

    /** 
     * Updates the title of the frame. It prints: 
     * "Table View (#rows[+] x #cols)". It is invoked each time new rows are
     * inserted (user scrolls down).
     */
    protected void updateTitle() {
        final TableContentView view = m_scroller.getContentTable();
        int rowCount = view.getRowCount();
        boolean isFinal = view.isRowCountFinal();
        StringBuffer title = new StringBuffer("Table View (");
        title.append(isFinal ? rowCount : rowCount - 1);
        title.append(isFinal ? " x " : "+ x ");
        title.append(view.getColumnCount() + ")");
        setTitle(title.toString());
    }
    

    /** Main method for testing purposes.
     * @param args Ignored.
     */
    public static void main(final String[] args) {
        Object[][] os = new Object[][] {
                new Object[] {new Integer(1), new Double(1.1), "eins"},
                new Object[] {new Integer(2), new Double(2.1), "zwei"},
                new Object[] {new Integer(3), new Double(3.1), "drei"}
        };
        DefaultTable table = new DefaultTable(os);
        new TableViewFrame(table);
    }
    
}   // TableViewFrame
