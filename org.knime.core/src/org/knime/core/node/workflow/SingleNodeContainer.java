/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2008
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
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * --------------------------------------------------------------------- *
 *
 * History
 *   14.03.2007 (mb): created
 */
package org.knime.core.node.workflow;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.knime.core.data.container.ContainerTable;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.DefaultNodeProgressMonitor;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.GenericNodeDialogPane;
import org.knime.core.node.GenericNodeModel;
import org.knime.core.node.GenericNodeView;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.Node;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeProgressMonitor;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.PortObject;
import org.knime.core.node.PortObjectSpec;
import org.knime.core.node.GenericNodeFactory.NodeType;
import org.knime.core.node.property.hilite.HiLiteHandler;
import org.knime.core.node.workflow.WorkflowPersistor.LoadResult;
import org.w3c.dom.Element;

/**
 * Holds a node in addition to some status information.
 *
 * @author M. Berthold/B. Wiswedel, University of Konstanz
 */
public final class SingleNodeContainer extends NodeContainer
    implements NodeMessageListener, NodeProgressListener {

    /** my logger. */
    private static final NodeLogger LOGGER =
        NodeLogger.getLogger(SingleNodeContainer.class);
    
    /** underlying node. */
    private final Node m_node;

    /** remember ID of the job when this node is submitted to a JobExecutor. */
    private JobID m_executionID;

    /** progress monitor. */
    private final NodeProgressMonitor m_progressMonitor =
            new DefaultNodeProgressMonitor(this);

    /**
     * Create new SingleNodeContainer based on existing Node.
     *
     * @param parent the workflow manager holding this node
     * @param n the underlying node
     * @param id the unique identifier
     */
    SingleNodeContainer(final WorkflowManager parent, final Node n,
            final NodeID id) {
        super(parent, id);
        m_node = n;
        m_node.addMessageListener(this);
    }

    /**
     * Create new SingleNodeContainer from persistor.
     * 
     * @param parent the workflow manager holding this node
     * @param id the identifier
     * @param persistor to read from
     */
    SingleNodeContainer(final WorkflowManager parent, final NodeID id,
            final SingleNodeContainerPersistor persistor) {
        super(parent, id, persistor.getMetaPersistor());
        m_node = persistor.getNode();
        assert m_node != null : persistor.getClass().getSimpleName()
            + " did not provide Node instance for " + getClass().getSimpleName()
            + " with id \"" + id + "\"";
        m_node.addMessageListener(this);

    }

    /**
     * @return the underlying Node
     */
    Node getNode() {
        return m_node;
    }

    /* ------------------ Port Handling ------------- */

    /** {@inheritDoc} */
    @Override
    public int getNrOutPorts() {
        return m_node.getNrOutPorts();
    }

    /** {@inheritDoc} */
    @Override
    public int getNrInPorts() {
        return m_node.getNrInPorts();
    }
    
    private NodeContainerOutPort[] m_outputPorts = null;
    /**
     * Returns the output port for the given <code>portID</code>. This port
     * is essentially a container for the underlying Node and the index and
     * will retrieve all interesting data from the Node.
     * 
     * @param index The output port's ID.
     * @return Output port with the specified ID.
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    @Override
    public NodeOutPort getOutPort(final int index) {
        if (m_outputPorts == null) {
            m_outputPorts = new NodeContainerOutPort[getNrOutPorts()];
        }
        if (m_outputPorts[index] == null) {
            m_outputPorts[index] = new NodeContainerOutPort(this, index);
        }
        return m_outputPorts[index];
    }

    private NodeInPort[] m_inputPorts = null;
    /**
     * Return a port, which for the inputs really only holds the type
     * and some other static information.
     * 
     * @param index the index of the input port
     * @return port
     */
    @Override
    public NodeInPort getInPort(final int index) {
        if (m_inputPorts == null) {
            m_inputPorts = new NodeInPort[getNrInPorts()];
        }
        if (m_inputPorts[index] == null) {
            m_inputPorts[index] 
                            = new NodeInPort(index, m_node.getInputType(index));
        }
        return m_inputPorts[index];
    }

    /* ------------------ Views ---------------- */

    /**
     * Set a new HiLiteHandler for an incoming connection.
     * 
     */
    void setInHiLiteHandler(final int index, final HiLiteHandler hdl) {
        m_node.setInHiLiteHandler(index, hdl);
    }
    
    /** {@inheritDoc} */
    @Override
    public GenericNodeView<GenericNodeModel> getView(final int i) {
        String title = getNameWithID() + " (" + getViewName(i) + ")";
        if (getCustomName() != null) {
            title += " - " + getCustomName();
        }
        return (GenericNodeView<GenericNodeModel>)m_node.getView(i, title);
    }

    /** {@inheritDoc} */
    @Override
    public String getViewName(final int i) {
        return m_node.getViewName(i);
    }

    /** {@inheritDoc} */
    @Override
    public int getNrViews() {
        return m_node.getNrViews();
    }
    
    /** {@inheritDoc} */
    @Override
    void cleanup() {
        super.cleanup();
        m_node.cleanup();
    }

    /**
     * Set a new JobExecutor for this node but before check for valid state.
     *
     * @param je the new JobExecutor.
     */
    @Override
    public void setJobExecutor(final JobExecutor je) {
        if (getState().equals(State.EXECUTING)
                || getState().equals(State.QUEUED)) {
            throw new IllegalStateException("Illegal state " + getState()
                    + " in setJobExecutor - can not change a running node.");

        }
        super.setJobExecutor(je);
    }

    private ExecutionContext createExecutionContext() {
        return new ExecutionContext(m_progressMonitor, getNode(),
                getParent().getGlobalTableRepository());
    }

    // ////////////////////////////////
    // Handle State Transitions
    // ////////////////////////////////

    /**
     * Configure underlying node and update state accordingly.
     *
     * @param inObjectSpecs input table specifications
     * @return true if output specs have changed.
     * @throws IllegalStateException in case of illegal entry state.
     */
    @Override
    boolean configureAsNodeContainer(final PortObjectSpec[] inObjectSpecs) {
        synchronized (m_nodeMutex) {
            // remember old specs
            PortObjectSpec[] prevSpecs =
                    new PortObjectSpec[getNrOutPorts()];
            for (int i = 0; i < prevSpecs.length; i++) {
                prevSpecs[i] = getOutPort(i).getPortObjectSpec();
            }
            // perform action
            switch (getState()) {
            case IDLE:
                if (m_node.configure(inObjectSpecs)) {
                    setState(State.CONFIGURED);
                } else {
                    setState(State.IDLE);
                }
                break;
            case UNCONFIGURED_MARKEDFOREXEC:
                if (m_node.configure(inObjectSpecs)) {
                    setState(State.MARKEDFOREXEC);
                } else {
                    setState(State.UNCONFIGURED_MARKEDFOREXEC);
                }
                break;
            case CONFIGURED:
                // m_node.reset();
                boolean success = m_node.configure(inObjectSpecs);
                if (success) {
                    setState(State.CONFIGURED);
                } else {
                    // m_node.reset();
                    setState(State.IDLE);
                }
                break;
            case MARKEDFOREXEC:
                // these are dangerous - otherwise re-queued loop-ends are
                // reset!
                // m_node.reset();
                success = m_node.configure(inObjectSpecs);
                if (success) {
                    setState(State.MARKEDFOREXEC);
                } else {
                    // m_node.reset();
                    setState(State.UNCONFIGURED_MARKEDFOREXEC);
                }
                break;
            default:
                throw new IllegalStateException("Illegal state " + getState()
                        + " encountered in configureNode(), node " + getID());
            }
            // compare old and new specs
            for (int i = 0; i < prevSpecs.length; i++) {
                PortObjectSpec newSpec =
                        getOutPort(i).getPortObjectSpec();
                if (newSpec != null) {
                    if (!newSpec.equals(prevSpecs[i])) {
                        return true;
                    }
                } else if (prevSpecs[i] != null) {
                    return true; // newSpec is null!
                }
            }
            return false; // all specs stayed the same!
        }
    }

    /** check if node can be safely reset.
     * @return if node can be reset.
     */
    @Override
    boolean isResetableAsNodeContainer() {
        return (getState().equals(State.EXECUTED)
                || getState().equals(State.MARKEDFOREXEC)
                || getState().equals(State.UNCONFIGURED_MARKEDFOREXEC));
    }

    /** {@inheritDoc} */
    @Override
    void resetAsNodeContainer() {
        synchronized (m_nodeMutex) {
            switch (getState()) {
            case EXECUTED:
                m_node.reset(true);
                // After reset we need explicit configure!
                setState(State.IDLE);
                return;
            case MARKEDFOREXEC:
                setState(State.CONFIGURED);
                return;
            case UNCONFIGURED_MARKEDFOREXEC:
                setState(State.IDLE);
                return;
            default:
                throw new IllegalStateException("Illegal state " + getState()
                        + " encountered in resetNode().");
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    void markForExecutionAsNodeContainer(final boolean flag) {
        synchronized (m_nodeMutex) {
            if (flag) {  // we want to mark the node for execution!
                switch (getState()) {
                case CONFIGURED:
                    setState(State.MARKEDFOREXEC);
                    return;
                case IDLE:
                    setState(State.UNCONFIGURED_MARKEDFOREXEC);
                    return;
                default:
                    throw new IllegalStateException("Illegal state "
                            + getState()
                            + " encountered in markForExecution(true).");
                }
            } else {  // we want to remove the mark for execution
                switch (getState()) {
                case MARKEDFOREXEC:
                    setState(State.CONFIGURED);
                    return;
                case UNCONFIGURED_MARKEDFOREXEC:
                    setState(State.IDLE);
                    return;
                default:
                    throw new IllegalStateException("Illegal state "
                            + getState()
                            + " encountered in markForExecution(false).");
                }
            }                    
        }
    }

    /**
     * Queue underlying node for re-execution (= update state accordingly).
     *
     * @throws IllegalStateException in case of illegal entry state.
     */
    void enableReQueuing() {
        synchronized (m_nodeMutex) {
            switch (getState()) {
            case EXECUTED:
                m_node.cleanOutPorts();
                setState(State.MARKEDFOREXEC);
                return;
            default:
                throw new IllegalStateException("Illegal state " + getState()
                        + " encountered in enableReQueuing().");
            }
        }
    }

    /**
     * Change state of marked (for execution) node to queued once it has been
     * assigned to a JobExecutor.
     *
     * @param inData the incoming data for the execution
     * @throws IllegalStateException in case of illegal entry state.
     */
    void queueAsNodeContainer(final PortObject[] inData) {
        synchronized (m_nodeMutex) {
            switch (getState()) {
            case MARKEDFOREXEC:
                setState(State.QUEUED);
                ExecutionContext execCon = createExecutionContext();
                m_executionID
                       = findJobExecutor().submitJob(new JobRunnable(execCon) {
                    @Override
                    public void run(final ExecutionContext ec) {
                        executeNode(inData, ec);
                    }
                });
                return;
            default:
                throw new IllegalStateException("Illegal state " + getState()
                        + " encountered in queueNode(). Node "
                        + getNameWithID());
            }
        }
    }
    

    /** {@inheritDoc} */
    @Override
    void cancelExecutionAsNodeContainer() {
        synchronized (m_nodeMutex) {
            switch (getState()) {
            case UNCONFIGURED_MARKEDFOREXEC:
                setState(State.IDLE);
                break;
            case MARKEDFOREXEC:
                setState(State.CONFIGURED);
                break;
            case QUEUED:
            case EXECUTING:
                findJobExecutor().cancelJob(m_executionID);
                break;
            case EXECUTED:
                // Too late - do nothing.
                break;
            default:
                LOGGER.warn("Strange state " + getState()
                        + " encountered in cancelExecution().");
            }
        }
    }
    

    //////////////////////////////////////
    //  internal state change actions
    //////////////////////////////////////

    /** This should be used to change the nodes states correctly (and likely
     * needs to be synchronized with other changes visible to successors of
     * this node as well!) BEFORE the actual execution.
     * The main reason is that the actual execution should be performed
     * unsychronized!
     */
    void preExecuteNode() {
        synchronized (m_nodeMutex) {
            switch (getState()) {
            case QUEUED:
                // clear loop status
                m_node.clearLoopStatus();
                // change state to avoid more than one executor
                setState(State.EXECUTING);
                break;
            default:
                throw new IllegalStateException("Illegal state " + getState()
                        + " encountered in executeNode(), node: " + getID());
            }
        }
    }

    /** This should be used to change the nodes states correctly (and likely
     * needs to be synchronized with other changes visible to successors of
     * this node as well!) AFTER the actual execution.
     * The main reason is that the actual execution should be performed
     * unsychronized!
     * 
     * @param success indicates if execution was successful
     */
    void postExecuteNode(final boolean success) {
        synchronized (m_nodeMutex) {
            if (success) {
                if (m_node.getLoopStatus() == null) {
                    setState(State.EXECUTED);

                } else {
                    // loop not yet done - "stay" configured until done.
                    setState(State.CONFIGURED);
                }
            } else {
                m_node.reset(false);  // we need to clean up remaining nonsense...
                m_node.clearLoopStatus();  // ...and the loop status
                // but node will not be reconfigured!
                // (configure does not prepare execute but only tells us what
                //  output execute() may create hence we do not need it here)
                setState(State.CONFIGURED);
            }
        }
    }

    /**
     * Execute underlying Node asynchronisly. Make sure to give Workflow-
     * Manager a chance to call pre- and postExecuteNode() appropriately
     * and synchronize those parts (since they changes states!).
     *
     * @param inTables input parameters
     * @throws IllegalStateException in case of illegal entry state.
     */
    private void executeNode(final PortObject[] inObjects,
            final ExecutionContext ec) {
        // this will allow the parent to call state changes etc properly
        // synchronized. The main execution is done asynchronsly.
        getParent().doBeforeExecution(SingleNodeContainer.this);
        // TODO: the progress monitor should not be accessible from the
        // public world.
        ec.getProgressMonitor().reset();
        // execute node outside any synchronization!
        boolean success = m_node.execute(inObjects, ec);
        if (success) {
            // output tables are made publicly available (for blobs)
            putOutputTablesIntoGlobalRepository(ec);
        }
        // clean up stuff and especially change states synchronized again
        getParent().doAfterExecution(SingleNodeContainer.this, success);
    }
    

    /**
     * Enumerates the output tables and puts them into the workflow global
     * repository of tables. All other (temporary) tables that were created in
     * the given execution context, will be put in a set of temporary tables in
     * the node.
     * 
     * @param c The execution context containing the (so far) local tables.
     */
    private void putOutputTablesIntoGlobalRepository(final ExecutionContext c) {
        HashMap<Integer, ContainerTable> globalRep = 
            getParent().getGlobalTableRepository();
        m_node.putOutputTablesIntoGlobalRepository(globalRep);
        HashMap<Integer, ContainerTable> localRep =
                Node.getLocalTableRepositoryFromContext(c);
        Set<ContainerTable> localTables = new HashSet<ContainerTable>();
        for (Map.Entry<Integer, ContainerTable> t : localRep.entrySet()) {
            ContainerTable fromGlob = globalRep.get(t.getKey());
            if (fromGlob == null) {
                // not used globally
                localTables.add(t.getValue());
            } else {
                assert fromGlob == t.getValue();
            }
        }
        m_node.addToTemporaryTables(localTables);
    }

    // //////////////////////////////////////
    // Save & Load Settings and Content
    // //////////////////////////////////////

    /** {@inheritDoc} */
    @Override
    void loadSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        synchronized (m_nodeMutex) {
            m_node.loadSettingsFrom(settings);
            setDirty();
        }
    }
    
    /** {@inheritDoc} */
    @Override
    LoadResult loadContent(final NodeContainerPersistor nodePersistor,
            final Map<Integer, BufferedDataTable> tblRep, 
            final ScopeObjectStack inStack, 
            final ExecutionMonitor exec) throws CanceledExecutionException {
        synchronized (m_nodeMutex) {
            if (!(nodePersistor instanceof SingleNodeContainerPersistor)) {
                throw new IllegalStateException("Expected " 
                        + SingleNodeContainerPersistor.class.getSimpleName() 
                        + " persistor object, got " 
                        + nodePersistor.getClass().getSimpleName());
            }
            SingleNodeContainerPersistor persistor = 
                (SingleNodeContainerPersistor)nodePersistor;
            State state = persistor.getMetaPersistor().getState();
            setState(state, false);
            if (state.equals(State.EXECUTED)) {
                m_node.putOutputTablesIntoGlobalRepository(
                        getParent().getGlobalTableRepository());
            }
            for (ScopeObject s : persistor.getScopeObjects()) {
                inStack.push(s);
            }
            setScopeObjectStack(inStack);
            return new LoadResult();
        }
    }

    /** {@inheritDoc} */
    @Override
    void saveSettings(final NodeSettingsWO settings)
    throws InvalidSettingsException {
        m_node.saveSettingsTo(settings);
    }

    /** {@inheritDoc} */
    @Override
    boolean areSettingsValid(final NodeSettingsRO settings) {
        return m_node.areSettingsValid(settings);
    }

    ////////////////////////////////////
    // ScopeObjectStack handling
    ////////////////////////////////////

    /**
     * Set ScopeObjectStack.
     * @param st new stack
     */
    void setScopeObjectStack(final ScopeObjectStack st) {
        synchronized (m_nodeMutex) {
            m_node.setScopeContextStackContainer(st);
        }
    }

    /**
     * @return current ScopeObjectStack
     */
    ScopeObjectStack getScopeObjectStack() {
        synchronized (m_nodeMutex) {
            return m_node.getScopeContextStackContainer();
        }
    }
    
    Node.LoopRole getLoopRole() {
        return getNode().getLoopRole();
    }

    ////////////////////////
    // Progress forwarding
    ////////////////////////

    /**
     * {@inheritDoc}
     */
    public void progressChanged(final NodeProgressEvent pe) {
        // set our ID as source ID
        NodeProgressEvent event =
                new NodeProgressEvent(getID(), pe.getNodeProgress());
        // forward the event
        notifyProgressListeners(event);
    }

    ///////////////////////////////////
    // NodeContainer->Node forwarding
    ///////////////////////////////////
    
    /** {@inheritDoc} */
    @Override
    public String getName() {
        return m_node.getName();
    }

    /**
     * @return Node name with status information.
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return m_node.getName() + "(" + getID() + ")" + ";status:" + getState();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasDialog() {
        return m_node.hasDialog();
    }

    /** {@inheritDoc} */
    @Override
    GenericNodeDialogPane getDialogPaneWithSettings(
            final PortObjectSpec[] inSpecs) throws NotConfigurableException {
        ScopeObjectStack stack = getScopeObjectStack();
        return m_node.getDialogPaneWithSettings(inSpecs, stack);
    }

    /** {@inheritDoc} */
    @Override
    GenericNodeDialogPane getDialogPane() {
        return m_node.getDialogPane();
    }

    /** {@inheritDoc} */
    @Override
    public boolean areDialogAndNodeSettingsEqual() {
        return m_node.areDialogAndNodeSettingsEqual();
    }

    /** {@inheritDoc} */
    @Override
    void loadSettingsFromDialog() throws InvalidSettingsException {
        synchronized (m_nodeMutex) {
            m_node.loadSettingsFromDialog();
        }
    }

    /** {@inheritDoc} */
    @Override
    public NodeMessage getNodeMessage() {
        return m_node.getNodeMessage();
    }

    /** {@inheritDoc} */
    public void messageChanged(final NodeMessageEvent messageEvent) {
        notifyMessageListeners(messageEvent);
    }

    /** {@inheritDoc} */
    @Override
    public NodeType getType() {
        return m_node.getType();
    }

    /** {@inheritDoc} */
    @Override
    public URL getIcon() {
        return m_node.getFactory().getIcon();
    }

    /**
     * @return the XML description of the node for the NodeDescription view
     */
    public Element getXMLDescription() {
        return m_node.getXMLDescription();
    }
    
    /** Ensures that any port object in the associated node is read from 
     * its saved location. Especially BufferedDataTable objects are read as
     * late as possible (in order to reduce start-up time), this method makes
     * sure that they are read (and either copied into TMP or into memory), so
     * the underlying node directory can be savely deleted. 
     * <p>This method is used when the loaded version is older than the version
     * used for saving. */
    void ensureOutputDataIsRead() {
        m_node.ensureOutputDataIsRead();
    }
    
    /** {@inheritDoc} */
    @Override
    protected NodeContainerPersistor getCopyPersistor(
            final HashMap<Integer, ContainerTable> tableRep) {
        return new CopySingleNodeContainerPersistor(this);
    }

}
