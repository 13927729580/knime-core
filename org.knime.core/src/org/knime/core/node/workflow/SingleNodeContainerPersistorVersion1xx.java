/* 
 * ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2008
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
 * History
 *   Sep 18, 2007 (wiswedel): created
 */
package org.knime.core.node.workflow;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.knime.core.data.container.ContainerTable;
import org.knime.core.eclipseUtil.GlobalClassCreator;
import org.knime.core.internal.ReferencedFile;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.GenericNodeFactory;
import org.knime.core.node.GenericNodeModel;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.Node;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodePersistorVersion1xx;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodePersistor.LoadNodeModelSettingsFailPolicy;
import org.knime.core.node.workflow.NodeContainer.State;
import org.knime.core.node.workflow.WorkflowPersistor.LoadResult;

/**
 * 
 * @author wiswedel, University of Konstanz
 */
class SingleNodeContainerPersistorVersion1xx implements SingleNodeContainerPersistor {

    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(SingleNodeContainerPersistorVersion1xx.class);

    private Node m_node;
    
    private NodeContainerMetaPersistorVersion1xx m_metaPersistor;
    
    private final HashMap<Integer, ContainerTable> m_globalTableRepository;
    
    private NodeSettingsRO m_nodeSettings;
    private ReferencedFile m_nodeDir;
    private boolean m_needsResetAfterLoad;
    private List<ScopeObject> m_scopeObjects;
    
    /** {@inheritDoc} */
    public boolean needsResetAfterLoad() {
        return m_needsResetAfterLoad;
    }
    
    /** Indicate that node should be reset after load (due to load problems). */
    public void setNeedsResetAfterLoad() {
        m_needsResetAfterLoad = true;
    }

    
    SingleNodeContainerPersistorVersion1xx(
            final HashMap<Integer, ContainerTable> tableRep) {
        m_globalTableRepository = tableRep;
    }

    public NodeContainerMetaPersistor getMetaPersistor() {
        return m_metaPersistor;
    }

    /** {@inheritDoc} */
    public Node getNode() {
        return m_node;
    }
    
    /** {@inheritDoc} */
    public List<ScopeObject> getScopeObjects() {
        return m_scopeObjects;
    }
    
    /** {@inheritDoc} */
    public SingleNodeContainer getNodeContainer(
            final WorkflowManager wm, final NodeID id) {
        return new SingleNodeContainer(wm, id, this);
    }
    
    protected NodePersistorVersion1xx createNodePersistor(
            final LoadNodeModelSettingsFailPolicy failPolicy) {
        return new NodePersistorVersion1xx(failPolicy);
    }

    /** {@inheritDoc} */
    public LoadResult preLoadNodeContainer(final ReferencedFile settingsFileRef,
            final NodeSettingsRO parentSettings) 
    throws InvalidSettingsException, CanceledExecutionException, IOException {
        LoadResult result = new LoadResult();
        File settingsFile = settingsFileRef.getFile();
        String error;
        if (!settingsFile.isFile()) {
            throw new IOException("Can't read node file \"" 
                    + settingsFile.getAbsolutePath() + "\"");
        }
        NodeSettingsRO settings = NodeSettings.loadFromXML(
                new BufferedInputStream(new FileInputStream(settingsFile)));
        String nodeFactoryClassName;
        try {
            nodeFactoryClassName = loadNodeFactoryClassName(
                    parentSettings, settings);
        } catch (InvalidSettingsException e) {
            if (settingsFile.getName().equals(
                    WorkflowPersistor.WORKFLOW_FILE)) {
                error = "Can't load meta flows in this version";
            } else {
                error = "Can't load node factory class name";
            }
            throw new InvalidSettingsException(error, e);
        }
        GenericNodeFactory<GenericNodeModel> nodeFactory;
        
        try {
            nodeFactory = loadNodeFactory(nodeFactoryClassName);
        } catch (Exception e) {
            error =  "Unable to load factory class \"" 
                + nodeFactoryClassName + "\"";
            throw new InvalidSettingsException(error, e);
        }
        m_node = new Node(nodeFactory);
        m_metaPersistor = createNodeContainerMetaPersistor(
                settingsFileRef.getParent());
        LoadResult metaResult = m_metaPersistor.load(settings);
        result.addError(metaResult);
        m_nodeSettings = settings;
        m_nodeDir = settingsFileRef.getParent();
        return result;
    }
    
    /** {@inheritDoc} */
    public LoadResult loadNodeContainer(
            final Map<Integer, BufferedDataTable> tblRep, 
            final ExecutionMonitor exec) throws InvalidSettingsException, 
            CanceledExecutionException, IOException {
        LoadResult result = new LoadResult();
        String nodeFileName;
        try {
            nodeFileName = loadNodeFile(m_nodeSettings);
        } catch (InvalidSettingsException e) {
            String error = 
                "Unable to load node settings file for node with ID suffix "
                    + m_metaPersistor.getNodeIDSuffix() + " (node \""
                    + m_node.getName() + "\"): " + e.getMessage();
            result.addError(error);
            LOGGER.debug(error, e);
            return result;
        }
        ReferencedFile nodeFile = new ReferencedFile(m_nodeDir, nodeFileName);
        NodePersistorVersion1xx nodePersistor = createNodePersistor(
                translateToFailPolicy(m_metaPersistor.getState()));
        try {
            LoadResult nodeLoadResult = nodePersistor.load(
                    m_node, nodeFile, exec, tblRep, m_globalTableRepository);
            result.addError(nodeLoadResult);
        } catch (final Exception e) {
            String error = "Error loading node content: " + e.getMessage();
            LOGGER.debug(error, e);
            needsResetAfterLoad();
            result.addError(error);
        }
        try {
            m_scopeObjects = loadScopeObjects(m_nodeSettings);
        } catch (InvalidSettingsException e) {
            m_scopeObjects = Collections.emptyList();
            String error = "Error loading scope objects (flow variables): "
                + e.getMessage();
            LOGGER.debug(error, e);
            result.addError(error);
            needsResetAfterLoad();
        }
        if (nodePersistor.needsResetAfterLoad()) {
            setNeedsResetAfterLoad();
        }
        loadNodeStateIntoMetaPersistor(nodePersistor);
        return result;
    }
    
    protected NodeContainerMetaPersistorVersion1xx 
        createNodeContainerMetaPersistor(final ReferencedFile baseDir) {
        return new NodeContainerMetaPersistorVersion1xx(baseDir);
    }
    
    protected void loadNodeStateIntoMetaPersistor(
            final NodePersistorVersion1xx nodePersistor) {
        State nodeState;
        if (nodePersistor.isExecuted()) {
            nodeState = State.EXECUTED;
        } else if (nodePersistor.isConfigured()) {
            nodeState = State.CONFIGURED;
        } else {
            nodeState = State.IDLE;
        }
        m_metaPersistor.setState(nodeState);
    }
    
    protected String loadNodeFactoryClassName(
            final NodeSettingsRO parentSettings, final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return parentSettings.getString(KEY_FACTORY_NAME);
    }

    @SuppressWarnings("unchecked")
    protected GenericNodeFactory<GenericNodeModel> loadNodeFactory(
            final String factoryClassName) throws InvalidSettingsException,
            InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        // use global Class Creator utility for Eclipse "compatibility"
        try {
            GenericNodeFactory<GenericNodeModel> f = (GenericNodeFactory<GenericNodeModel>)((GlobalClassCreator
                    .createClass(factoryClassName)).newInstance());
            return f;
        } catch (ClassNotFoundException ex) {
            String[] x = factoryClassName.split("\\.");
            String simpleClassName = x[x.length - 1];

            for (String s : GenericNodeFactory.getLoadedNodeFactories()) {
                if (s.endsWith("." + simpleClassName)) {
                    GenericNodeFactory<GenericNodeModel> f = (GenericNodeFactory<GenericNodeModel>)((GlobalClassCreator
                            .createClass(s)).newInstance());
                    LOGGER.warn("Substituted '" + f.getClass().getName()
                            + "' for unknown factory '" + factoryClassName
                            + "'");
                    return f;
                }
            }
            throw ex;
        }
    }
    
    protected String loadNodeFile(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        return SETTINGS_FILE_NAME;
    }
    
    protected List<ScopeObject> loadScopeObjects(
            final NodeSettingsRO settings) throws InvalidSettingsException {
        return Collections.emptyList();
    }
    
    static final LoadNodeModelSettingsFailPolicy translateToFailPolicy(
            final State nodeState) { 
        switch (nodeState) {
        case IDLE:
            return LoadNodeModelSettingsFailPolicy.IGNORE;
        case EXECUTED:
            return LoadNodeModelSettingsFailPolicy.WARN;
        default:
            return LoadNodeModelSettingsFailPolicy.FAIL;
        }
    }
}
