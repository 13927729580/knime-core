/* 
 * -------------------------------------------------------------------
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
 * -------------------------------------------------------------------
 */
package org.knime.core.data.container;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnDomainCreator;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.NominalValue;
import org.knime.core.data.RowIterator;
import org.knime.core.data.RowKey;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsWO;


/**
 * Buffer that collects <code>DataRow</code> objects and creates a 
 * <code>DataTable</code> on request. This data structure is useful if the 
 * number of rows is not known in advance. 
 * 
 * <p>Usage: Create a container with a given spec (matching the rows being added
 * later on, add the data using the 
 * <code>addRowToTable(DataRow)</code> method and finally close it with
 * <code>close()</code>. You can access the table by <code>getTable()</code>.
 * 
 * <p>Note regarding the column domain: This implementation updates the column
 * domain while new rows are added to the table. It will keep the lower and 
 * upper bound for all columns that are numeric, i.e. whose column type is
 * a sub type of <code>DoubleCell.TYPE</code>. For categorical columns,
 * it will keep the list of possible values if the number of different values
 * does not exceed 60. (If there are more, the values are forgotten and 
 * therefore not available in the final table.) A categorical column is 
 * a column whose type is a sub type of <code>StringCell.TYPE</code>, 
 * i.e. <code>StringCell.TYPE.isSuperTypeOf(yourtype)</code> where 
 * yourtype is the given column type.
 * 
 * @author Bernd Wiswedel, University of Konstanz
 */
public class DataContainer implements RowAppender {
    
    /** 
     * Number of cells that are cached without being written to the 
     * temp file (see Buffer implementation).
     */
    public static final int MAX_CELLS_IN_MEMORY = 100000;
    
    /**
     * The number of possible values being kept at most. If the number of
     * possible values in a column exceeds this values, no values will
     * be memorized.
     */
    private static final int MAX_POSSIBLE_VALUES = 60;
    
    /** The object that instantiates the buffer, may be set right after 
     * constructor call before any rows are added. */
    private BufferCreator m_bufferCreator;
    
    /** The object that saves the rows. */
    private Buffer m_buffer;
    
    private int m_maxRowsInMemory; 
    
    /** Holds the keys of the added rows to check for duplicates. */
    private HashSet<RowKey> m_keySet;
    
    /** The tablespec of the return table. */
    private DataTableSpec m_spec;
    
    /** Table to return. Not null when close() is called. */
    private ContainerTable m_table;
    
    /** For each column, memorize the possible values. For detailed information
     * regarding the possible values and range determination, refer to the
     * class description.
     */
    private LinkedHashSet<DataCell>[] m_possibleValues;
    
    /**
     * For each column, memorize how many possible values need to be stored. 
     * Important when the domain is initialized on the argument spec. For those
     * columns, this value will be very high!
     */
    private int[] m_possibleValuesSizes;
    
    /** The min values in each column, we keep these values only for numerical
     * column (i.e. double, int).  For non-numerical columns the respective
     * entries will be null.
     */
    private DataCell[] m_minCells;

    /** The max values in each column, similar to m_minCells.
     */
    private DataCell[] m_maxCells;
    
    /** Global repository map, created lazily. */
    private Map<Integer, ContainerTable> m_globalMap;
    
    /** Local repository map, created lazily. */
    private Map<Integer, ContainerTable> m_localMap;
    
    /**
     * Opens the container so that rows can be added by
     * <code>addRowToTable(DataRow)</code>. The table spec of the resulting
     * table (the one being returned by <code>getTable()</code>) will have a
     * valid column domain. That means, while rows are added to the container,
     * the domain of each column is adjusted.
     * <p>
     * If you prefer to stick with the domain as passed in the argument, use the
     * constructor <code>DataContainer(DataTableSpec, true, 
     * DataContainer.MAX_CELLS_IN_MEMORY)</code> instead.
     * 
     * @param spec Table spec of the final table. Rows that are added to the
     *            container must comply with this spec.
     * @throws NullPointerException If <code>spec</code> is <code>null</code>.
     */
    public DataContainer(final DataTableSpec spec) {
        this(spec, false);
    }
    
    /**
     * Opens the container so that rows can be added by 
     * <code>addRowToTable(DataRow)</code>. 
     * @param spec Table spec of the final table. Rows that are added to the
     *        container must comply with this spec.
     * @param initDomain if set to true, the column domains in the 
     *        container are initialized with the domains from spec. 
     * @throws NullPointerException If <code>spec</code> is <code>null</code>.
     */
    @SuppressWarnings ("unchecked")
    public DataContainer(final DataTableSpec spec, 
            final boolean initDomain) {
        this(spec, initDomain, MAX_CELLS_IN_MEMORY);
    }
    
    /**
     * Opens the container so that rows can be added by 
     * <code>addRowToTable(DataRow)</code>. 
     * @param spec Table spec of the final table. Rows that are added to the
     *        container must comply with this spec.
     * @param initDomain if set to true, the column domains in the 
     *        container are initialized with the domains from spec. 
     * @param maxCellsInMemory Maximum count of cells in memory before swapping.
     * @throws IllegalArgumentException If <code>maxCellsInMemory</code> &lt; 0.
     * @throws NullPointerException If <code>spec</code> is <code>null</code>.
     */
    @SuppressWarnings ("unchecked")
    public DataContainer(final DataTableSpec spec, final boolean initDomain,
            final int maxCellsInMemory) {
        this(spec, initDomain, maxCellsInMemory, true);
    }
    
    /**
     * Opens the container so that rows can be added by 
     * <code>addRowToTable(DataRow)</code>. 
     * @param spec Table spec of the final table. Rows that are added to the
     *        container must comply with this spec.
     * @param initDomain if set to true, the column domains in the 
     *        container are initialized with the domains from spec. 
     * @param maxCellsInMemory Maximum count of cells in memory before swapping.
     * @param enableKeyHashing If <code>true</code> the row keys are hashed
     * to check for duplicates. (Highly advisable for sanity checks, but 
     * requires additional memory).
     * @throws IllegalArgumentException If <code>maxCellsInMemory</code> &lt; 0.
     * @throws NullPointerException If <code>spec</code> is <code>null</code>.
     * @deprecated enableKeyHashing option won't be available in the future. 
     * We added this feature to get around with bug #1090 (memory problem)
     */
    @SuppressWarnings ("unchecked")
    @Deprecated
    public DataContainer(final DataTableSpec spec, final boolean initDomain,
            final int maxCellsInMemory, final boolean enableKeyHashing) {
        if (maxCellsInMemory < 0) {
            throw new IllegalArgumentException(
                    "Cell count must be positive: " + maxCellsInMemory); 
        }
        if (spec == null) {
            throw new NullPointerException("Spec must not be null!");
        }
        DataTableSpec oldSpec = m_spec;
        m_spec = spec;
        m_keySet = enableKeyHashing ? new HashSet<RowKey>() : null;
        if (m_buffer != null) {
            m_buffer.close(oldSpec);
        }
        
        // figure out for which columns it's worth to keep the list of possible
        // values and min/max ranges
        m_possibleValues = new LinkedHashSet[m_spec.getNumColumns()];
        m_possibleValuesSizes = new int[m_spec.getNumColumns()];
        m_minCells = new DataCell[m_spec.getNumColumns()];
        m_maxCells = new DataCell[m_spec.getNumColumns()];
        for (int i = 0; i < m_spec.getNumColumns(); i++) {
            DataColumnSpec colSpec = m_spec.getColumnSpec(i);
            DataType colType = colSpec.getType();
            // bug fix #591: We must init the domain no matter what data
            // type is in the column (we had the problem where one passed
            // a FuzzyIntervalCell which is not compatible to doublevalue).
            
            // do first for possible values
            if (initDomain) {
                Set<DataCell> values = colSpec.getDomain().getValues();
                if (values != null) {
                    m_possibleValues[i] = new LinkedHashSet<DataCell>(values);
                    // negative value means: store all!
                    m_possibleValuesSizes[i] = -1;
                } else if (colType.isCompatible(NominalValue.class)) {
                    // mb: used to test for StringValue - let's be more specific
                    m_possibleValues[i] = new LinkedHashSet<DataCell>();
                    m_possibleValuesSizes[i] = MAX_POSSIBLE_VALUES;
                } else {
                    m_possibleValues[i] = null;
                    m_possibleValuesSizes[i] = -1;
                }
            } else if (colType.isCompatible(NominalValue.class)) {
                // mb: used to test for StringValue - let's be more specific
                m_possibleValues[i] = new LinkedHashSet<DataCell>();
                m_possibleValuesSizes[i] = MAX_POSSIBLE_VALUES;
            } else {
                m_possibleValues[i] = null;
                m_possibleValuesSizes[i] = -1;
            }
            
            // do now for min/max
            if (initDomain) {
                DataCell min = colSpec.getDomain().getLowerBound();
                DataCell max = colSpec.getDomain().getUpperBound();
                if (min != null || max != null) {
                    m_minCells[i] = min != null ? min 
                            : DataType.getMissingCell();
                    m_maxCells[i] = max != null ? max 
                            : DataType.getMissingCell();
                } else if (colType.isCompatible(DoubleValue.class)) {
                    // if no min/max available, init only if column is
                    // double compatible
                    m_minCells[i] = DataType.getMissingCell();
                    m_maxCells[i] = DataType.getMissingCell();
                } else {
                    // invalid column type, no domain initialized
                    m_minCells[i] = null;
                    m_maxCells[i] = null;
                }
            } else {
                if (colType.isCompatible(DoubleValue.class)) {
                    m_minCells[i] = DataType.getMissingCell();
                    m_maxCells[i] = DataType.getMissingCell();
                } else {
                    m_minCells[i] = null;
                    m_maxCells[i] = null;
                }
            }
        }
        // how many rows will occupy MAX_CELLS_IN_MEMORY
        final int colCount = spec.getNumColumns();
        m_maxRowsInMemory = maxCellsInMemory / ((colCount > 0) ? colCount : 1);
        m_bufferCreator = new BufferCreator();
    }
    
    /** Set a buffer creator to be used to initialize the buffer. This
     * method must be called before any rows are added. 
     * @param bufferCreator To be used.
     * @throws NullPointerException If the argument is <code>null</code>.
     * @throws IllegalStateException If the buffer has already been created. 
     */
    protected void setBufferCreator(final BufferCreator bufferCreator) {
        if (m_buffer != null) {
            throw new IllegalStateException("Buffer has already been created.");
        }
        if (bufferCreator == null) {
            throw new NullPointerException("BufferCreator must not be null.");
        }
        m_bufferCreator = bufferCreator;
    }
    
    /** Define a new threshold for number of possible values to memorize.
     * It makes sense to call this method before any rows are added.
     * @param maxPossibleValues The new number.
     * @throws IllegalArgumentException If the value < 0
     */
    public void setMaxPossibleValues(final int maxPossibleValues) {
        if (maxPossibleValues < 0) {
            throw new IllegalArgumentException(
                    "number < 0: " + maxPossibleValues);
        }
        for (int i = 0; i < m_possibleValuesSizes.length; i++) {
            if (m_possibleValuesSizes[i] >= 0) {
                m_possibleValuesSizes[i] = maxPossibleValues;
                if (m_possibleValues[i].size() > maxPossibleValues) {
                    m_possibleValuesSizes[i] = -1; // invalid
                    m_possibleValues[i] = null;
                }
            }
        }
    }
    
    /**
     * Returns <code>true</code> if the container has been initialized with 
     * <code>DataTableSpec</code> and is ready to accept rows.
     *
     * <p>This implementation returns <code>!isClosed()</code>;
     * @return <code>true</code> if container is accepting rows.
     */
    public boolean isOpen() {
        return !isClosed();
    }

    /**
     * Returns <code>true</code> if table has been closed and 
     * <code>getTable()</code> will return a <code>DataTable</code> object.
     * @return <code>true</code> if table is available, <code>false</code>
     *         otherwise.
     */
    public boolean isClosed() {
        return m_table != null;
    }

    /**
     * Closes container and creates table that can be accessed by 
     * <code>getTable()</code>. Successive calls of <code>addRowToTable</code>
     * will fail with an exception.
     * @throws IllegalStateException If container is not open.
     */
    public void close() {
        if (isClosed()) {
            return;
        }
        if (!isOpen()) {
            throw new IllegalStateException("Cannot close table: container has"
                    + " not been initialized (opened).");
        }
        if (m_buffer == null) {
            m_buffer = m_bufferCreator.createBuffer(m_maxRowsInMemory, 
                    createInternalBufferID(), getGlobalTableRepository(),
                    getLocalTableRepository());
        }
        DataTableSpec finalSpec = createTableSpecWithRange();
        m_buffer.close(finalSpec);
        m_table = new ContainerTable(m_buffer);
        getLocalTableRepository().put(m_table.getBufferID(), m_table);
        m_buffer = null;
        m_spec = null;
        m_keySet = null;
        m_possibleValues = null;
        m_minCells = null;
        m_maxCells = null;
    }
    
    /** Get the number of rows that have been added so far.
     * (How often has <code>addRowToTable</code> been called since the last
     * <code>openTable</code> request)
     * @return The number of rows in the container (independent of closed or
     *         not closed).
     * @throws IllegalStateException If container is not open.
     */
    public int size() {
        if (isClosed()) {
            return m_table.getBuffer().size();
        }
        if (!isOpen()) {
            throw new IllegalStateException("Container is not open.");
        }
        return m_buffer != null ? m_buffer.size() : 0;
    }

    /**
     * Get reference to table. This method throws an exception unless the 
     * container is closed and has therefore a table available.
     * @return Reference to the table that has been built up.
     * @throws IllegalStateException If <code>isClosed()</code> returns
     *         <code>false</code>
     */
    public DataTable getTable() {
        return getBufferedTable();
    }
    
    /** Returns the table holding the data. This method is identical to
     * the getTable() method but is more specific with respec to the return
     * type. It's used in derived classes.
     * @return The table underlying this container.
     * @throws IllegalStateException If <code>isClosed()</code> returns
     *         <code>false</code>
     */
    protected final ContainerTable getBufferedTable() {
        if (!isClosed()) {
            throw new IllegalStateException(
            "Cannot get table: container is not closed.");
        }
        return m_table;
    }
    
    /** 
     * Get the currently set DataTableSpec.
     * @return The current spec.
     */
    public DataTableSpec getTableSpec() {
        if (isClosed()) {
            return m_table.getDataTableSpec();
        } else if (isOpen()) {
            return m_spec;
        }
        throw new IllegalStateException("Cannot get spec: container not open.");
    }
    
    /** 
     * @see RowAppender#addRowToTable(DataRow)
     */
    public void addRowToTable(final DataRow row) {
        if (!isOpen()) {
            throw new IllegalStateException("Cannot add row: container has"
                    + " not been initialized (opened).");
        }
        if (m_buffer == null) {
            int bufID = createInternalBufferID();
            Map<Integer, ContainerTable> globalTableRep = 
                getGlobalTableRepository();
            Map<Integer, ContainerTable> localTableRep = 
                getLocalTableRepository();
            m_buffer = m_bufferCreator.createBuffer(
                    m_maxRowsInMemory, bufID, globalTableRep, localTableRep);
            if (m_buffer == null) {
                throw new NullPointerException(
                        "Implementation error, must not return a null buffer.");
            }
        }
        // let's do every possible sanity check
        int numCells = row.getNumCells();
        RowKey key = row.getKey();
        if (numCells != m_spec.getNumColumns()) {
            throw new IllegalArgumentException("Cell count in row \"" + key
                    + "\" is not equal to length of column names " + "array: "
                    + numCells + " vs. " + m_spec.getNumColumns());
        }
        for (int c = 0; c < numCells; c++) {
            DataType columnClass = m_spec.getColumnSpec(c).getType();
            DataCell value;
            DataType runtimeClass;
            if (row instanceof BlobSupportDataRow) {
                BlobSupportDataRow bsvalue = (BlobSupportDataRow)row;
                DataCell ce  = bsvalue.getRawCell(c);
                runtimeClass = ce instanceof BlobWrapperDataCell
                    ? DataType.getType(((BlobWrapperDataCell)ce).getBlobClass())
                            : ce.getType();
                value = ce;
            } else {
                value = row.getCell(c);
                runtimeClass = value.getType();
            }
                
            if (!columnClass.isASuperTypeOf(runtimeClass)) {
                throw new IllegalArgumentException("Runtime class of object \""
                        + value.toString() + "\" (index " + c
                        + ") in " + "row \"" + key + "\" is "
                        + runtimeClass.toString()
                        + " and does not comply with its supposed superclass "
                        + columnClass.toString());
            }
            // keep the list of possible values and the range updated
            updatePossibleValues(c, value);
            updateMinMax(c, value);
            
        } // for all cells
        if (m_keySet != null && !m_keySet.add(key)) {
            throw new IllegalArgumentException("Container contains already a"
                    + " row with key \"" + key + "\".");
        }

        m_buffer.addRow(row);
    } // addRowToTable(DataRow)
    
    /**
     * Get an internal id for the buffer being used. This ID is used in 
     * conjunction with blob serialization to locate buffers. Blobs that belong
     * to a Buffer (i.e. they have been created in a particular Buffer) will 
     * write this ID when serialized to a file. Subsequent Buffers that also 
     * need to serialize Blob cells (which, however, have already been written)
     * can then reference to the respective Buffer object using this ID.
     * 
     * <p>An ID of -1 denotes the fact, that the buffer is not intended to be
     * used for sophisticated blob serialization. All blob cells that are added
     * to it will be newly serialized as if they were created for the first 
     * time. 
     * 
     * <p>This implementation returns -1.
     * @return -1 or a unique buffer ID.
     */
    protected int createInternalBufferID() {
        return -1;
    }
    
    /**
     * Get the map of buffers that potentially have written blob objects. 
     * If m_buffer needs to serialize a blob, it will check if any other buffer
     * has written the blob already and then reference to this buffer rather 
     * than writing out the blob again.
     * <p>If used along with the {@link org.knime.core.node.ExecutionContext}, 
     * this method returns the global table repository (global = in the context
     * of the current workflow).
     * <p>This implementation does not support sophisticated blob serialization.
     * It will return a <code>new HashMap&lt;Integer, Buffer&gt;()</code>.
     * @return The map bufferID to Buffer.
     * @see #getLocalTableRepository()
     */
    protected Map<Integer, ContainerTable> getGlobalTableRepository() {
        if (m_globalMap == null) {
            m_globalMap = new HashMap<Integer, ContainerTable>();
        }
        return m_globalMap;
    }
    
    /**
     * Get the local repository. Overridden in 
     * {@link 
     * org.knime.core.node.BufferedDataContainer#getLocalTableRepository()}
     * @return A local repository to which tables are added that have been
     * created during the node's execution.
     */
    protected Map<Integer, ContainerTable> getLocalTableRepository() {
        if (m_localMap == null) {
            m_localMap = new HashMap<Integer, ContainerTable>();
        }
        return m_localMap;
    }
    
    /** Adds another value to the list of possible values in a certain column.
     * This method does nothing if the values don't need to be stored for the
     * respective column.
     * @param col The column of interest.
     * @param value The (maybe) new value.
     */
    private void updatePossibleValues(final int col, final DataCell cell) {
        if (m_possibleValues[col] == null || cell.isMissing()) {
            return;
        } 
        DataCell value = cell instanceof BlobWrapperDataCell 
            ? ((BlobWrapperDataCell)cell).getCell() : cell;
        m_possibleValues[col].add(value);
        if (m_possibleValuesSizes[col] >= 0 
                && m_possibleValues[col].size() > m_possibleValuesSizes[col]) {
            // forget possible values
            m_possibleValues[col] = null;
            m_possibleValuesSizes[col] = -1;
        }
    }
    
    /** Updates the min and max value for an respective column. This method 
     * does nothing if the min and max values don't need to be stored, e.g.
     * the column at hand contains string values.
     * @param col The column of interest.
     * @param value The new value to check.
     */
    private void updateMinMax(final int col, final DataCell cell) {
        if (m_minCells[col] == null || cell.isMissing()) {
            return;
        }
        DataCell value = cell instanceof BlobWrapperDataCell 
        ? ((BlobWrapperDataCell)cell).getCell() : cell;
        DataType colType = m_spec.getColumnSpec(col).getType();
        Comparator<DataCell> comparator = colType.getComparator();
        if (m_minCells[col].isMissing()
                || comparator.compare(value, m_minCells[col]) < 0) {
            m_minCells[col] = value;
        }
        if (m_maxCells[col].isMissing() 
                || comparator.compare(value, m_maxCells[col]) > 0) {
            m_maxCells[col] = value;
        }
    }
    
    /** Creates the final data table spec. It also includes the column domain
     * information (if any)
     * @return The final data table spec to be used.
     */
    private DataTableSpec createTableSpecWithRange() {
        DataColumnSpec[] colSpec = new DataColumnSpec[m_spec.getNumColumns()];
        for (int i = 0; i < colSpec.length; i++) {
            DataColumnSpec original = m_spec.getColumnSpec(i);
            DataCell[] possVal = m_possibleValues[i] != null 
                ? m_possibleValues[i].toArray(new DataCell[0]) : null;
            DataCell min = m_minCells[i] != null && !m_minCells[i].isMissing()
                ? m_minCells[i] : null;
            DataCell max = m_maxCells[i] != null && !m_maxCells[i].isMissing()
                ? m_maxCells[i] : null;
            DataColumnDomainCreator domainCreator = 
                new DataColumnDomainCreator(possVal, min, max);
            DataColumnSpecCreator specCreator = 
                new DataColumnSpecCreator(original);
            specCreator.setDomain(domainCreator.createDomain());
            colSpec[i] = specCreator.createSpec();
        }
        return new DataTableSpec(m_spec.getName(), colSpec);
    }
    
    /** Convenience method that will buffer the entire argument table. This is
     * useful if you have a wrapper table at hand and want to make sure that 
     * all calculations are done here 
     * @param table The table to cache.
     * @param exec The execution monitor to report progress to and to check
     * for the cancel status.
     * @param maxCellsInMemory The number of cells to be kept in memory before
     * swapping to disk.
     * @return A cache table containing the data from the argument.
     * @throws NullPointerException If the argument is <code>null</code>.
     * @throws CanceledExecutionException If the process has been canceled.
     */
    public static DataTable cache(final DataTable table, 
            final ExecutionMonitor exec, final int maxCellsInMemory) 
        throws CanceledExecutionException {
        DataContainer buf = new DataContainer(
                table.getDataTableSpec(), true, maxCellsInMemory);
        int row = 0;
        try {
            for (RowIterator it = table.iterator(); it.hasNext(); row++) {
                DataRow next = it.next();
                exec.setMessage("Caching row #" + (row + 1) + " (\"" 
                        + next.getKey() + "\")");
                exec.checkCanceled();
                buf.addRowToTable(next);
            }
        } finally {
            buf.close();
        }
        return buf.getTable();
    }
    
    /** Convenience method that will buffer the entire argument table. This is
     * useful if you have a wrapper table at hand and want to make sure that 
     * all calculations are done here 
     * @param table The table to cache.
     * @param exec The execution monitor to report progress to and to check
     * for the cancel status.
     * @return A cache table containing the data from the argument.
     * @throws NullPointerException If the argument is <code>null</code>.
     * @throws CanceledExecutionException If the process has been canceled.
     */
    public static DataTable cache(final DataTable table, 
            final ExecutionMonitor exec) throws CanceledExecutionException {
        return cache(table, exec, MAX_CELLS_IN_MEMORY);
    }
    
    /** Used in write/readFromZip: Name of the zip entry containing the spec. */
    static final String ZIP_ENTRY_SPEC = "spec.xml";
    /** Used in write/readFromZip: Config entry: The spec of the table. */
    static final String CFG_TABLESPEC = "table.spec";
    
    /** Writes a given DataTable permanently to a zip file. This includes
     * also all table spec information, such as color, size, and shape 
     * properties. 
     * @param table The table to write.
     * @param zipFile The file to write to. Will be created or overwritten.
     * @param exec For progress info.
     * @throws IOException If writing fails.
     * @throws CanceledExecutionException If canceled.
     * @see #readFromZip(File)
     */
    public static void writeToZip(final DataTable table, final File zipFile,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        Buffer buf;
        ExecutionMonitor e = exec;
        boolean canUseBuffer = table instanceof ContainerTable;
        if (canUseBuffer) {
            Buffer b = ((ContainerTable)table).getBuffer();
            if (b.containsBlobCells() && b.getBufferID() != -1) {
                canUseBuffer = false;
            }
        }
        if (canUseBuffer) {
            buf = ((ContainerTable)table).getBuffer();
        } else {
            exec.setMessage("Archiving table");
            e = exec.createSubProgress(0.8);
            buf = new Buffer(0, -1, 
                    new HashMap<Integer, ContainerTable>(),
                    new HashMap<Integer, ContainerTable>());
            int rowCount = 0;
            for (DataRow row : table) {
                rowCount++;
                e.setMessage("Writing row #" + rowCount + " (\"" 
                        + row.getKey() + "\")");
                e.checkCanceled();
                buf.addRow(row);
            }
            buf.close(table.getDataTableSpec());
            exec.setMessage("Closing zip file");
            e = exec.createSubProgress(0.2);
        }
        ZipOutputStream zipOut = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipFile)));
        buf.addToZipFile(zipOut, e);
        zipOut.putNextEntry(new ZipEntry(ZIP_ENTRY_SPEC));
        NodeSettings settings = new NodeSettings("Table Spec");
        NodeSettingsWO specSettings = settings.addNodeSettings(CFG_TABLESPEC);
        buf.getTableSpec().save(specSettings);
        settings.saveToXML(new NonClosableZipOutputStream(zipOut));
        zipOut.close();
    }
    
    /** 
     * Reads a table from a zip file that has been written using the 
     * {@link #writeToZip(DataTable, File, ExecutionMonitor)} method.
     * @param zipFile To read from.
     * @return The table contained in the zip file.
     * @throws IOException If that fails.
     * @see #writeToZip(DataTable, File, ExecutionMonitor)
     */
    public static ContainerTable readFromZip(final File zipFile) 
        throws IOException {
        return readFromZip(zipFile, new BufferCreator());
    }
    
    /**
     * Factory method used to restore table from zip file.  
     * @param zipFile To read from.
     * @param creator Factory object to create a buffer instance.
     * @return The table contained in the zip file.
     * @throws IOException If that fails.
     * @see #readFromZip(File)
     */
    // This method is used from #readFromZip(File) or from a 
    // RearrangeColumnsTable when it reads a table that has been written
    // with KNIME 1.1.x or before.
    static ContainerTable readFromZip(final File zipFile, 
            final BufferCreator creator) throws IOException {
        /*
         * Ideally, the entire functionality of reading the zip file should take
         * place in the Buffer class (as that is also the place where save is
         * implemented). However, that is not that obvious to implement since
         * the buffer needs to be created using the DataTableSpec
         * (DataContainer.writeToZip stores the DTS in the zip file), which we
         * need to extract beforehand. Using a ZipFile here and extracting only
         * the DTS is not possible, as that may crash (OutOfMemoryError), see
         * bug http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4705373
         */
        // bufferID = -1: all blobs are contained in buffer, no fancy
        // reference handling to other buffer objects
        CopyOnAccessTask coa = new CopyOnAccessTask(zipFile, null, -1, 
                new HashMap<Integer, ContainerTable>(), creator);
        // executing the createBuffer() method will start the copying process
        Buffer buffer = coa.createBuffer();
        return new ContainerTable(buffer);
    }
    
    /**
     * Used in {@link org.knime.core.node.BufferedDataContainer} to read
     * the tables from the workspace location. 
     * @param zipFile To read from (is going to be copied to temp on access)
     * @param spec The DTS for the table.
     * @param bufferID The buffer's id used for blob (de)serialization
     * @param bufferRep Repository of buffers for blob (de)serialization.
     * @return Table contained in <code>zipFile</code>.
     */
    protected static ContainerTable readFromZipDelayed(final File zipFile, 
            final DataTableSpec spec, final int bufferID, 
            final Map<Integer, ContainerTable> bufferRep) {
        CopyOnAccessTask t = new CopyOnAccessTask(
                zipFile, spec, bufferID, bufferRep, new BufferCreator());
        return readFromZipDelayed(t, spec);
    }
    
    /**
     * Used in {@link org.knime.core.node.BufferedDataContainer} to read the 
     * tables from the workspace location.
     * @param c The factory that create the Buffer instance that the 
     * returned table reads from. 
     * @param spec The DTS for the table.
     * @return Table contained in <code>zipFile</code>.
     */
    static ContainerTable readFromZipDelayed(final CopyOnAccessTask c,
            final DataTableSpec spec) {
        return new ContainerTable(c, spec);
    }
    
    /** the temp file will have a time stamp in its name. */
    private static final SimpleDateFormat DATE_FORMAT = 
        new SimpleDateFormat("yyyyMMdd");
    
    /** Creates a temp file called "knime_container_<i>date</i>_xxxx.zip" and
     * marks it for deletion upon exit. This method is used to init the file 
     * when the data container flushes to disk. It is also used when the nodes
     * are read back in to copy the data to the tmp-directory.
     * @return A temp file to use. The file is empty.
     * @throws IOException If that fails for any reason.
     */
    public static final File createTempFile() throws IOException {
        String date = DATE_FORMAT.format(new Date());
        String fileName = "knime_container_" + date + "_";
        String suffix = ".bin.gz";
        // the preference page contains an entry to set the temp dir
        // we evaluate it here explicitly as the environment variable may
        // not have been set by the preference page when File.createTempFile
        // is called the very first time (may happen in some early plugin load)
        String tmpDir = System.getProperty("java.io.tmpdir");
        File tmpDirFile = null;
        if (tmpDir != null) {
            tmpDirFile = new File(tmpDir);
            if (!tmpDirFile.isDirectory()) {
                NodeLogger.getLogger(DataContainer.class).warn(
                        "System property \"java.io.tmpdir\" (\""
                        + tmpDir + "\") does not point to existing directory," 
                        + " using default");
                tmpDir = null;
            }
        }
        File f = File.createTempFile(fileName, suffix, tmpDirFile);
        f.deleteOnExit();
        return f;
    }
    
    /**
     * Get the next running index of the table id counter. This id is supposed
     * to be unique.
     */
    
    /** Returns <code>true</code> if the given argument table has been created
     * by the DataContainer, <code>false</code> otherwise.
     * @param table The table to check.
     * @return If the given table was created by a DataContainer.
     * @throws NullPointerException If the argument is <code>null</code>.
     */
    public static final boolean isContainerTable(final DataTable table) {
        return table instanceof ContainerTable;
    }
    
    /**
     * Helper class to create a Buffer instance given a binary file and the
     * data table spec.
     */
    static class BufferCreator {
        
        /** Creates buffer for reading.
         * @param binFile the binary temp file.
         * @param blobDir temp directory containing blobs (may be null).
         * @param spec The spec.
         * @param metaIn Input stream containing meta information.
         * @param bufID The buffer's id used for blob (de)serialization
         * @param tblRep Table repository for blob (de)serialization.
         * @return A buffer instance.
         * @throws IOException If parsing fails.
         */
        Buffer createBuffer(final File binFile, final File blobDir, 
                final DataTableSpec spec, final InputStream metaIn, 
                final int bufID, final Map<Integer, ContainerTable> tblRep) 
            throws IOException {
            return new Buffer(binFile, blobDir, spec, metaIn, bufID, tblRep);
        }
        
        /** Creates buffer for writing (adding of rows). 
         * @param rowsInMemory The number of rows being kept in memory.
         * @param bufferID The buffer's id used for blob (de)serialization.
         * @param globalTableRep Table repository for blob (de)serialization.
         * @param localTableRep Table repository for blob (de)serialization.
         * @return A newly created buffer. 
         */
        Buffer createBuffer(final int rowsInMemory, final int bufferID,
                final Map<Integer, ContainerTable> globalTableRep,
                final Map<Integer, ContainerTable> localTableRep) {
            return new Buffer(
                    rowsInMemory, bufferID, globalTableRep, localTableRep);
        }
        
    }
    
}
