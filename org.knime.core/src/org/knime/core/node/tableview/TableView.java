/* 
 * --------------------------------------------------------------------- *
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2007
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * --------------------------------------------------------------------- *
 * 
 * 2006-06-08 (tm): reviewed 
 */
package org.knime.core.node.tableview;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputAdapter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.knime.core.data.DataTable;
import org.knime.core.node.property.hilite.HiLiteHandler;


/** 
 * Panel containing a table view on a generic {@link DataTable}. The
 * table is located in a scroll pane and row and column headers are visible and
 * fixed.
 * <br />
 * For the caching strategy used in the table refer to
 * {@link TableContentModel}.
 *  
 * @author Bernd Wiswedel, University of Konstanz
 */
public class TableView extends JScrollPane {
    private static final long serialVersionUID = -5066803221414314340L;

    // TODO adjust header width automatically on start-up 
    // and add functionality to mouse-drag the header width 
    /** Header column's width in pixel. */
    private static final int ROWHEADER_WIDTH = 100;

    /** Cursor that is shown when the column header is resized (north-south). */
    private static final Cursor RESIZE_CURSOR = 
        Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);

    
    /** The popup menu which allows to trigger hilite events. */
    private JPopupMenu m_popup;
    
    /** Enables the resizing of the column header. Instantiated lazily. */
    private ColumnHeaderResizeMouseHandler m_columnHeaderResizeHandler;
    
    /** Whether or not column header resizing is allowed. Defaults to false. */
    private boolean m_isColumnHeaderResizingAllowed;
    
    /** Next row to start search from (defaults to 0). */
    private int m_searchRow;
    
    /** Last search string, needed for continued search. */
    private String m_searchString;
    
    /** Whether continued search only search row id column or entire data. */
    private boolean m_searchIDOnly;
    
    /** 
     * Creates new empty <code>TableView</code>. Content and handlers are set
     * using the appropriate methods, that is, 
     * {@link #setDataTable(DataTable)} and 
     * {@link #setHiLiteHandler(HiLiteHandler)}. The model for this 
     * view, however, is not <code>null</code>. That is, it's completely legal 
     * to do {@link #getContentModel()} right after calling this 
     * constructor.
     */
    public TableView() {
        this(new TableContentModel());
    }
    
    /** 
     * Creates new instance of a <code>TableView</code> given a content view.
     * A row header is created and displayed. There is no property handler
     * connected to this view at this time.
     * 
     * @param contentView view to display.
     * @throws NullPointerException if contentView is <code>null</code>
     */
    public TableView(final TableContentView contentView) {
        // disallow null arguments
        super(checkNull(contentView));
        m_isColumnHeaderResizingAllowed = false;
        // if not "off", the horizontal scroll bar is never shown (reduces
        // size of columns to minimum)
        contentView.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableRowHeaderView rowHeadView = 
            TableRowHeaderView.createHeaderView(contentView);
        setRowHeaderView(rowHeadView);
        // set width of the row header
        rowHeadView.setPreferredScrollableViewportSize(
            new Dimension(ROWHEADER_WIDTH, 0));
        setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, 
            rowHeadView.getTableHeader());
        
        // workaround for bug 4202002: The scrolling is out of sync when 
        // scrolled in RowHeader.
        // add a listener to force re-synchronization
        getRowHeader().addChangeListener(new ChangeListener() {
            public void stateChanged(final ChangeEvent e) {
                JViewport jvViewport = (JViewport) e.getSource();
                int iExtent = jvViewport.getExtentSize().height;
                int iMax = jvViewport.getViewSize().height;
                int iValue = Math.max(0, Math.min(
                    jvViewport.getViewPosition().y, iMax - iExtent));
                getVerticalScrollBar().setValues(iValue, iExtent, 0, iMax);
            } // stateChanged(ChangeEvent)
        });
        // listener that opens the popup on the table's row header
        getHeaderTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    Point ePoint = e.getPoint();
                    Point thisPoint = SwingUtilities.convertPoint(
                            (Component)e.getSource(), ePoint, TableView.this);
                    showPopup(thisPoint);
                }
            }
        });
        getContentModel().addPropertyChangeListener(
                TableContentModel.PROPERTY_DATA, new PropertyChangeListener() {
           public void propertyChange(final PropertyChangeEvent evt) {
               
           }         
        });
    } // TableView(TableContentView)
    
    /** 
     * Constructs new View by calling 
     * <code>this(new TableContentView(model))</code>.
     * 
     * @param model model to be displayed.
     * @see TableView#TableView(TableContentView)
     * @throws NullPointerException if <code>model</code> is <code>null</code>.
     */ 
    public TableView(final TableContentModel model) {
        this(new TableContentView(model));
    } // TableView(TableContentModel)

    /**
     * Creates new instance of a <code>TableView</code> based on a 
     * {@link DataTable}. A row header is created and displayed.
     * 
     * @param table table to be displayed
     * @throws NullPointerException if <code>table</code> is <code>null</code>.
     */
    public TableView(final DataTable table) {
        this(new TableContentModel(table));
    } // TableView(DataTable)
    
    /**
     * Creates new instance of a <code>TableView</code> based on a 
     * {@link DataTable}. A row header is created and displayed.
     * 
     * @param table table to be displayed
     * @param propHdl used to connect other views, may be <code>null</code>
     * @throws NullPointerException if <code>table</code> is <code>null</code>
     */
    public TableView(final DataTable table, final HiLiteHandler propHdl) {
        this(new TableContentModel(table, propHdl));
    } // TableView(DataTable, HiLiteHandler)

    /**
     * Simply checks if argument is <code>null</code> and throws an exception if
     * it is. Otherwise returns argument. This method is called in the 
     * constructor.
     * 
     * @param content argument to check.
     * @return <code>content</code>
     * @throws NullPointerException if <code>content</code> is <code>null</code>
     */    
    private static TableContentView checkNull(final TableContentView content) {
        if (content == null) {
            throw new NullPointerException("Content View must not be null!");
        }
        return content;
    }

    /** 
     * Get reference to table view that is in the scroll pane's view port.
     * 
     * @return reference to content table
     */
    public TableContentView getContentTable() {
        return (TableContentView) getViewport().getView();
    }
    
    /** 
     * Get reference to underlying <code>TableContentModel</code>. This call 
     * is identical to calling 
     * <code>(TableContentModel)(getContentTable().getModel())</code>.
     * 
     * @return the model displayed.
     */
    public TableContentModel getContentModel() {
        return (TableContentModel)(getContentTable().getModel());
    }
    
    /** 
     * Get reference to row header table, that is the column displaying the
     * row keys from the underlying table.
     * 
     * @return reference to row header.
     */
    public TableRowHeaderView getHeaderTable() {
        return (TableRowHeaderView)getRowHeader().getView();
    }
    
    /** Delegates to super implementation but sets an appropriate preferred
     * size before returning. If the table has no columns (but more than 0 
     * rows), the corner was not shown (because of 0,0 preferred dimension).
     * {@inheritDoc}
     */
    @Override
    public JViewport getColumnHeader() {
        JViewport viewPort = super.getColumnHeader();
        if (viewPort != null && viewPort.getView() != null) { 
            Component view = viewPort.getView();
            int viewHeight = view.getPreferredSize().height;
            boolean hasData = hasData();
            // bug fix #934: header of row header column was not shown when
            // table contains no columns
            if (hasData && viewHeight == 0) {
                view.setPreferredSize(
                        getCorner(UPPER_LEFT_CORNER).getPreferredSize());
            } else if (!hasData && viewHeight > 0) {
                // null is perfectly ok, it seems
                view.setPreferredSize(null);
            }
        }
        return viewPort;
    }
    
    /** Overwritten to add (north-south) resize listener to upper left corner.  
     * {@inheritDoc} */
    @Override
    public void setCorner(final String key, final Component corner) {
        if (UPPER_LEFT_CORNER.equals(key)) {
            Component old = getCorner(UPPER_LEFT_CORNER); 
            if (old != null && m_columnHeaderResizeHandler != null) {
                old.removeMouseListener(m_columnHeaderResizeHandler);
                old.removeMouseMotionListener(m_columnHeaderResizeHandler);
            } 
            if (corner != null && isColumnHeaderResizingAllowed()) {
                if (m_columnHeaderResizeHandler == null) {
                    m_columnHeaderResizeHandler = 
                        new ColumnHeaderResizeMouseHandler();
                }
                corner.addMouseListener(m_columnHeaderResizeHandler);
                corner.addMouseMotionListener(m_columnHeaderResizeHandler);
            }
        }
        super.setCorner(key, corner);
    }

    /** 
     * Checks if a property handler is registered.
     * 
     * @return <code>true</code> if global hiliting is possible (property
     *         handler is available).
     * @see TableContentModel#hasHiLiteHandler()
     */
    public final boolean hasHiLiteHandler() {
        return getContentTable().hasHiLiteHandler();
    }

    /**
     * This table "has data" when there is valid input, i.e. the 
     * {@link DataTable} to display is not <code>null</code>. The 
     * status may changed during runtime by calling the model's 
     * <code>setDataTable(DataTable)</code> method.
     * 
     * @return <code>true</code> when there is data to display,
     *      <code>false</code> otherwise
     * @see TableContentModel#hasData()
     */
    public boolean hasData() {
        return getContentTable().hasData();
    }
    
    /**
     * Sends a request to the content table to hilite all currently selected 
     * rows.
     * 
     * @see TableContentView#hiliteSelected()
     */
    public void hiliteSelected() {
        getContentTable().hiliteSelected();
    }
    
    /**
     * Sends a request to the content table to unhilite all currently selected 
     * rows.
     * 
     * @see TableContentView#unHiliteSelected()
     */
    public void unHiliteSelected() {
        getContentTable().unHiliteSelected();
    }

    /**
     * Sends a request to the content table to reset (unhilite) all rows.
     * 
     * @see TableContentView#resetHilite() 
     */
    public void resetHilite() {
        getContentTable().resetHilite();
    }
    
    /**
     * Sets a new <code>DataTable</code> as content. 
     * 
     * @param data new data to be shown; may be <code>null</code> to have an
     *        empty table.
     * @see TableContentModel#setDataTable(DataTable)
     */
    public void setDataTable(final DataTable data) {
        getContentTable().setDataTable(data);
    }
    
    /**
     * Sets a new <code>HiLiteHandler</code> this view talks to. 
     * The argument may be <code>null</code> to disconnect from the
     * current <code>HiLiteHandler</code>.
     * 
     * @param hiLiteHdl the new <code>HiLiteHandler</code>.
     */
    public void setHiLiteHandler(final HiLiteHandler hiLiteHdl) {
        getContentTable().setHiLiteHandler(hiLiteHdl);
    }
    
    /**
     * Control behaviour to show only hilited rows.
     * 
     * @param showOnlyHilite <code>true</code> Filter and display only
     *        rows whose hilite status is set.
     * @see TableContentModel#showHiLitedOnly(boolean)
     */
    public final void showHiLitedOnly(final boolean showOnlyHilite) {
        getContentTable().showHiLitedOnly(showOnlyHilite);
    }

    /**
     * Get status of filtering for hilited rows.
     * 
     * @return <code>true</code>: only hilited rows are shown, 
     *         <code>false</code>: all rows are shown.
     * @see TableContentModel#showsHiLitedOnly() 
     */
    public boolean showsHiLitedOnly() {
        return getContentTable().showsHiLitedOnly();
    }
    
    /**
     * Delegate method to cancel row counting.
     * 
     * @see TableContentModel#cancelRowCountingInBackground()
     */
    public void cancelRowCountingInBackground() {
        getContentModel().cancelRowCountingInBackground();
    }

    /**
     * Delegate method to start row counting.
     * 
     * @see TableContentModel#countRowsInBackground()
     */
    public void countRowsInBackground() {
        getContentModel().countRowsInBackground();
    }

    
    /**
     * Get row height from table.
     * 
     * @return current row height
     * @see javax.swing.JTable#getRowHeight() 
     */
    public int getRowHeight() {
        return getHeaderTable().getRowHeight();
    }
   
    /**
     * Returns the width of the first column or -1 if there are no columns.
     * 
     * @return the width of the first column.
     */
    public int getColumnWidth() {
        TableColumnModel colModel = getContentTable().getColumnModel();
        if (colModel.getColumnCount() > 0) {
            return colModel.getColumn(0).getWidth();
        } else {
            return -1;
        }
    }
    
    /**
     * Sets an equal width in all columns.
     * 
     * @param width the new width.
     * @see javax.swing.table.TableColumn#setPreferredWidth(int)
     */
    public void setColumnWidth(final int width) {
        TableColumnModel colModel = getContentTable().getColumnModel();
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            TableColumn c = colModel.getColumn(i);
            if (width < c.getMinWidth()) {
                c.setMinWidth(width);
            }
            c.setPreferredWidth(width);
        }
    }
    
    /** Get the height of the column header view or -1 if none has been set
     * (no data available).
     * @return The height of the column header view.
     */
    public int getColumnHeaderViewHeight() {
        JViewport header = getColumnHeader();
        Component v;
        if (header != null) {
            v = header.getView();
        } else {
            // the header == null if the table has not been completely 
            // initialized (i.e. configureEnclosingScrollPane has not been
            // called the JTable). The header will be the JTableHeader of the
            // table
            v = getContentTable().getTableHeader();
        }
        if (v != null) {
            return v.getHeight();
        }
        return -1;
    }
    
    /** Set the height of the column header view. If none has been set
     * (i.e. no data is available), this method does nothing.
     * @param newHeight The new height.
     */
    public void setColumnHeaderViewHeight(final int newHeight) {
        JViewport header = getColumnHeader();
        Component v;
        if (header != null) {
            v = header.getView();
        } else {
            // the header == null if the table has not been completely 
            // initialized (i.e. configureEnclosingScrollPane has not been
            // called the JTable). The header will be the JTableHeader of the
            // table
            v = getContentTable().getTableHeader();
        }
        if (v != null) {
            Dimension d = v.getSize();
            d.height = newHeight;
            v.setSize(d);
            v.setPreferredSize(d);
        }
    }
    
    /**
     * Set a new row height in the table.
     * 
     * @param newHeight the new height
     * @see javax.swing.JTable#setRowHeight(int)
     */
    public void setRowHeight(final int newHeight) {
        // perform action on header - it makes sure that the event gets
        // propagated to the content view
        getHeaderTable().setRowHeight(newHeight);
    }
    
    /**
     * Shall row header encode the color information in an icon?
     * 
     * @param showIt <code>true</code> for show icon (and thus the color),
     *        <code>false</code> ignore colors
     * @see TableRowHeaderView#setShowColorInfo(boolean)
     */
    public void setShowColorInfo(final boolean showIt) {
        getHeaderTable().setShowColorInfo(showIt);
    }
    
    /**
     * Is color icon shown?
     * 
     * @return <code>true</code> if it is, <code>false</code> otherwise
     * @see TableRowHeaderView#isShowColorInfo()
     */
    public boolean isShowColorInfo() {
        return getHeaderTable().isShowColorInfo();
    }
    
    /** 
     * Set whether or not the icon in the column header is to be displayed.
     * Delegate method to {@link TableView#setShowColorInfo(boolean)}.
     * @param show Whether or not this icon should be shown.
     */
    public void setShowIconInColumnHeader(final boolean show) {
        getContentTable().setShowIconInColumnHeader(show);
    }
    
    /**
     * Get the status if the icon in the column header is shown.
     * Delegate method to {@link TableView#isShowColorInfo()}.
     * @return true when the icon is shown, false otherwise.
     */
    public boolean isShowIconInColumnHeader() {
        return getContentTable().isShowIconInColumnHeader();
    }
    
    /**
     * Whether or not the resizing of the column header height is allowed.
     * The default is <code>false</code>.
     * @return the isColumnHeaderResizingAllowed.
     * @see #setColumnHeaderResizingAllowed(boolean)
     */
    public boolean isColumnHeaderResizingAllowed() {
        return m_isColumnHeaderResizingAllowed;
    }

    /**
     * Enable or disable the resizing of the column header height.
     * @param isColumnHeaderResizingAllowed If <code>true</code> resizing is
     * allowed.
     */
    public void setColumnHeaderResizingAllowed(
            final boolean isColumnHeaderResizingAllowed) {
        if (m_isColumnHeaderResizingAllowed == isColumnHeaderResizingAllowed) {
            return;
        }
        m_isColumnHeaderResizingAllowed = isColumnHeaderResizingAllowed;
        Component corner = getCorner(UPPER_LEFT_CORNER);
        if (m_isColumnHeaderResizingAllowed) {
            if (corner != null) {
                if (m_columnHeaderResizeHandler == null) {
                    m_columnHeaderResizeHandler = 
                        new ColumnHeaderResizeMouseHandler();
                }
                corner.addMouseListener(m_columnHeaderResizeHandler);
                corner.addMouseMotionListener(m_columnHeaderResizeHandler);
            }
        } else {
            if (corner != null && m_columnHeaderResizeHandler != null) {
                corner.removeMouseListener(m_columnHeaderResizeHandler);
                corner.removeMouseMotionListener(m_columnHeaderResizeHandler);
            }
        }
    }
    
    /**
     * Find cells (or row IDs) that match the search string. If a matching 
     * element is found, the view is scrolled to that position. Successive calls
     * of this method with the same arguments will continue the search.
     * 
     * @param search The search string.
     * @param idOnly If only the ID column should be searched.
     * @throws NullPointerException If <code>search</code> argument is null.
     */
    protected void find(final String search, final boolean idOnly) {
        /* assert idOnly = true;
         * Searching the content is currently disabled for different reasons:
         * - For continuing at last hit position, we need to store the last
         *   hit column (not only the row number)
         * - Need to define a "match" (the implementation below checks whether
         *   the string, which is rendered, contains the search string
         */
        // new search string, reset search position and start on top
        if (!search.equals(m_searchString) || idOnly != m_searchIDOnly) {
            m_searchRow = 0;
        }
        m_searchString = search;
        m_searchIDOnly = idOnly;
        TableContentView cView = getContentTable();
        if (cView == null) {
            return;
        }
        int rowCount = cView.getRowCount();
        int lastSearchPosition = m_searchRow;
        for (int i = 0; i < rowCount; i++) {
            int pos = (m_searchRow + i) % rowCount;
            int col = 0;
            if (pos < lastSearchPosition) {
                JOptionPane.showMessageDialog(this, 
                        "Reached end of table, starting at top");
                lastSearchPosition = -1;
            }
            boolean matches = cView.getContentModel().getRowKey(pos).
                toString().contains(search);
            if (!m_searchIDOnly) {
                for (int c = 0; c < cView.getColumnCount(); c++) {
                    TableCellRenderer rend = cView.getCellRenderer(pos, c);
                    Component comp = rend.getTableCellRendererComponent(cView, 
                            cView.getValueAt(pos, c), false, false, pos, c);
                    if (comp instanceof JLabel 
                            && ((JLabel)comp).getText().contains(search)) {
                        col = c;
                        matches = true;
                        break;
                    }
                }
            }
            if (matches) {
                m_searchRow = pos + 1;
                gotoCell(pos, col);
                return;
            }
        }
        JOptionPane.showMessageDialog(this, "Search string not found");
        m_searchRow = 0;
    }

    /**
     * Scrolls to the given coordinate cell This method is invoked
     * from the navigation menu. If there is no such coordinate it will 
     * display an error message.
     * 
     * @param row the row to scroll to 
     * @param col the col to scroll to
     */
    public void gotoCell(final int row, final int col) {
        TableContentView cView = getContentTable();
        try {
            cView.getValueAt(row, col);
        } catch (IndexOutOfBoundsException ioe) {
            if (cView.getColumnCount() != 0) {
                JOptionPane.showMessageDialog(this, "No such row/col: (" 
                        + (row + 1) + ", " + (col + 1) + ")", 
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        Rectangle rec = cView.getCellRect(row, Math.max(col, 0), false);
        cView.getSelectionModel().setSelectionInterval(row, row);
        if (col >= 0) {
            ListSelectionModel colSelModel = 
                cView.getColumnModel().getSelectionModel();
            if (colSelModel != null) {
                colSelModel.setSelectionInterval(col, col);
            }
        }
        cView.scrollRectToVisible(rec);
    }

    /**
     * Opens the popup menu on the row header. It allows to trigger hilite
     * events.
     * 
     * @param p location where to open the popup
     */
    protected void showPopup(final Point p) {
        if (!hasHiLiteHandler()) {
            return;
        }
        if (m_popup == null) {
            m_popup = new JPopupMenu();
            for (JMenuItem item : createHiLiteMenuItems()) {
                m_popup.add(item);
            }
        }
        m_popup.show(this, p.x, p.y);
    }
    
    /**
     * Create the navigation menu for this table view.
     *  
     * @return a new <code>JMenu</code> with navigation controllers.
     */
    public JMenu createNavigationMenu() {
        final JMenu result = new JMenu("Navigation");
        result.setMnemonic('N');
        JMenuItem item = new JMenuItem("Go to Row...");
        item.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                String rowString = JOptionPane.showInputDialog(
                        TableView.this, "Enter row number:", "Go to Row", 
                        JOptionPane.QUESTION_MESSAGE);
                if (rowString == null) { // canceled
                     return;
                }
                try { 
                    int row = Integer.parseInt(rowString);
                    gotoCell(row - 1, 0);
                } catch (NumberFormatException nfe) {
                    JOptionPane.showMessageDialog(TableView.this, 
                            "Can't parse " + rowString, "Error", 
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        item.addPropertyChangeListener(new EnableListener(this, true, false));
        item.setEnabled(hasData());
        result.add(item);
        item = new JMenuItem("Find Row ID...");
        item.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
//                JCheckBox rowKeyBox = 
//                    new JCheckBox("ID only", m_searchIDOnly);
//                JPanel panel = new JPanel(new BorderLayout());
//                panel.add(new JLabel("Find String: "), BorderLayout.WEST);
//                panel.add(rowKeyBox, BorderLayout.EAST);
                String panel = "Find Row ID:";
                String in = JOptionPane.showInputDialog(
                        TableView.this, panel, "Search",
                        JOptionPane.QUESTION_MESSAGE);
                if (in == null) { // canceled
                     return;
                }
                find(in, true/*rowKeyBox.isSelected()*/);
            }
        });
        item.addPropertyChangeListener(new EnableListener(this, true, false));
        item.setEnabled(hasData());
        result.add(item);
        item = new JMenuItem("Find Next");
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                find(m_searchString, m_searchIDOnly);
            }
        });
        item.addPropertyChangeListener(new EnableListener(this, true, false));
        item.setEnabled(hasData());
        result.add(item);
        return result;
    } // createNavigationMenu()
    
    /**
     * Get a new menu to control hiliting for this view.
     * 
     * @return a new JMenu with hiliting buttons
     */
    public JMenu createHiLiteMenu() {
        final JMenu result = new JMenu("Hilite");
        result.setMnemonic('H');
        for (JMenuItem item : createHiLiteMenuItems()) {
            result.add(item);
        }
        return result;
    } // createHiLiteMenu()
    
    /**
     * Helper function to create new JMenuItems that are in the hilite menu.
     * 
     * @return all those items in an array
     */
    Collection<JMenuItem> createHiLiteMenuItems() {
        ArrayList<JMenuItem> result = new ArrayList<JMenuItem>();
        JMenuItem hsitem = new JMenuItem("Hilite Selected");
        hsitem.setMnemonic('S');
        hsitem.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                hiliteSelected();
            }
        });
        hsitem.addPropertyChangeListener(new EnableListener(this, true, true));
        hsitem.setEnabled(hasData() && hasHiLiteHandler());
        result.add(hsitem);
        
        JMenuItem usitem = new JMenuItem("Unhilite Selected");
        usitem.setMnemonic('U');
        usitem.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                unHiliteSelected();
            }
        });
        usitem.addPropertyChangeListener(new EnableListener(this, true, true));
        usitem.setEnabled(hasData() && hasHiLiteHandler());
        result.add(usitem);
        
        JMenuItem chitem = new JMenuItem("Clear Hilite");
        chitem.setMnemonic('C');
        chitem.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                resetHilite();
            }
        });
        chitem.addPropertyChangeListener(new EnableListener(this, true, true));
        chitem.setEnabled(hasData() && hasHiLiteHandler());
        result.add(chitem);
        
        JMenuItem shoitem = new JCheckBoxMenuItem("Show Hilited Only");
        shoitem.setMnemonic('O');
        shoitem.addPropertyChangeListener(
                "ancestor", new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                JCheckBoxMenuItem source = (JCheckBoxMenuItem)evt.getSource();
                source.setSelected(showsHiLitedOnly());
            }
        });
        shoitem.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                boolean i = ((JCheckBoxMenuItem)e.getSource()).isSelected();
                showHiLitedOnly(i);
            }
        });
        shoitem.addPropertyChangeListener(new EnableListener(this, true, true));
        shoitem.setEnabled(hasData() && hasHiLiteHandler());
        result.add(shoitem);
        
        return result;
    }
    
    /**
     * Get a new menu with view controllers (row height, etc.) for this view.
     * 
     * @return a new JMenu with control buttons.
     */
    public JMenu createViewMenu() {
        final JMenu result = new JMenu("View");
        result.setMnemonic('V');
        JMenuItem item = new JMenuItem("Row Height...");
        item.setMnemonic('H');
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                while (true) {
                    int curRowHeight = getRowHeight();
                    String in = JOptionPane.showInputDialog(
                            TableView.this, "Enter new row height:", 
                            "" + curRowHeight);
                    if (in == null) { // canceled
                         return;
                    }
                    try {
                        int newHeight = Integer.parseInt(in);
                        if (newHeight <= 0) { // disallow negative values.
                            JOptionPane.showMessageDialog(
                                    TableView.this, "No negative values allowed"
                                    , "Error", JOptionPane.ERROR_MESSAGE);
                        } else {
                            setRowHeight(newHeight);
                            return;
                        }
                    } catch (NumberFormatException nfe) {
                        JOptionPane.showMessageDialog(
                                TableView.this, "Can't parse "
                                + in, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        item.addPropertyChangeListener(new EnableListener(this, true, false));
        item.setEnabled(hasData());
        result.add(item);
        
        item = new JMenuItem("Column Width...");
        item.setMnemonic('W');
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                while (true) {
                    int curWidth = getColumnWidth();
                    String in = JOptionPane.showInputDialog(
                            TableView.this, "Enter new column width:", 
                            "" + curWidth);
                    if (in == null) { // canceled
                        return;
                    }
                    try {
                        int newWidth = Integer.parseInt(in);
                        if (newWidth <= 0) { // disallow negative values.
                            JOptionPane.showMessageDialog(
                                    TableView.this, 
                                    "No negative values allowed",
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        } else {
                            setColumnWidth(newWidth);
                            return;
                        }
                    } catch (NumberFormatException nfe) {
                        JOptionPane.showMessageDialog(
                                TableView.this, "Can't parse "
                                + in, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        item.addPropertyChangeListener(new EnableListener(this, true, false));
        item.setEnabled(hasData());
        result.add(item);
        
        item = new JCheckBoxMenuItem("Show Color Information");
        item.setMnemonic('C');
        item.addPropertyChangeListener("ancestor",
        new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                JCheckBoxMenuItem source = (JCheckBoxMenuItem)evt.getSource();
                source.setSelected(isShowColorInfo());
            }
        });
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                boolean v = ((JCheckBoxMenuItem)e.getSource()).isSelected();
                setShowColorInfo(v);
            }
        });
        item.addPropertyChangeListener(new EnableListener(this, true, false));
        item.setEnabled(hasData());
        result.add(item);
        return result;
    } // createViewMenu()
    
    /** PropertyChangeListener that will disable/enable the menu items. */
    private static class EnableListener implements PropertyChangeListener {
        private final boolean m_watchData;
        private final boolean m_watchHilite;
        private final TableView m_view;
        
        /**
         * Constructor. Will respect the hasData(), hasHiliteHandler() flag
         * according to the arguments.
         * @param view The view to get status from
         * @param watchData Shall this listener respect data change events.
         * @param watchHilite Shall this listener respect hilite change events.
         * 
         */
        public EnableListener(final TableView view,
                final boolean watchData, final boolean watchHilite) {
            m_view = view;
            m_watchData = watchData;
            m_watchHilite = watchHilite;
        }
        
        /**
         * {@inheritDoc}
         */
        public void propertyChange(final PropertyChangeEvent evt) {
            JComponent source = (JComponent)evt.getSource();
            boolean data = !m_watchData || m_view.hasData();
            boolean hilite = !m_watchHilite || m_view.hasHiLiteHandler();
            source.setEnabled(data && hilite);
        }
    }
    
    /** Mouse handler that takes care of the column header resizing. */
    private class ColumnHeaderResizeMouseHandler extends MouseInputAdapter {
        
        private int m_oldMouseY;
        
        private Cursor m_swapCursor = RESIZE_CURSOR; 
        
        /** {@inheritDoc} */
        @Override
        public void mouseMoved(final MouseEvent e) {
            Component c = (Component)e.getSource();
            if (canResize(c, e) != c.getCursor().equals(RESIZE_CURSOR)) {
                swapCursor(c);
            }
        }
        
        /** {@inheritDoc} */
        @Override
        public void mousePressed(final MouseEvent e) {
            Component c = (Component)e.getSource();
            if (canResize(c, e)) {
                m_oldMouseY = e.getPoint().y;
            }
        }
        
        /** {@inheritDoc} */
        @Override
        public void mouseDragged(final MouseEvent e) {
            if (m_oldMouseY > 0) {
                int diff = e.getPoint().y - m_oldMouseY;
                int oldHeight = getColumnHeaderViewHeight();
                setColumnHeaderViewHeight(oldHeight + diff);
                m_oldMouseY = getColumnHeaderViewHeight();
            }
        }
        
        /** {@inheritDoc} */
        @Override
        public void mouseReleased(final MouseEvent e) {
            m_oldMouseY = -1;
        }
        
        private void swapCursor(final Component c) {
            Cursor oldCursor = c.getCursor();
            c.setCursor(m_swapCursor);
            m_swapCursor = oldCursor;
        }
        
        private boolean canResize(final Component c, final MouseEvent e) {
            return (c.getHeight() - e.getPoint().y <= 3);
        }
    }
    
}   // TableView
