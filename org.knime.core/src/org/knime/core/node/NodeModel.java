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
 *   17.01.2006(sieb, ohl): reviewed
 */
package org.knime.core.node;

import java.io.File;
import java.io.IOException;

import org.knime.core.data.DataTableSpec;


/**
 * Class implements the general model of a node which gives access to the
 * <code>DataTable</code>,<code>HiLiteHandler</code>, and
 * <code>DataTableSpec</code> of all outputs.
 * <p>
 * The <code>NodeModel</code> should contain the node's "model", i.e., what
 * ever is stored, contained, done in this node - it's the "meat" of this node.
 *
 * @author Thomas Gabriel, University of Konstanz
 */
public abstract class NodeModel extends GenericNodeModel {

    private final int m_nrDataInPorts;
    private final int m_nrDataOutPorts;
    private final int m_nrModelInPorts;
    private final int m_nrModelOutPorts;

    /**
     * Creates a new model with the given number of in- and outputs.
     *
     * @param nrDataIns Number of data inputs.
     * @param nrDataOuts Number of data outputs.
     *
     * @see NodeModel#NodeModel(int, int, int, int)
     */
    protected NodeModel(final int nrDataIns, final int nrDataOuts) {
        this(nrDataIns, nrDataOuts, 0, 0);
    }

    private static PortType[] createArrayOfDataAndModelTypes(
            final int nrDataPorts, final int nrModelPorts) {
        PortType[] pTypes = new PortType[nrDataPorts + nrModelPorts];
        for (int i = 0; i < nrDataPorts; i++) {
            pTypes[i] = BufferedDataTable.TYPE;
        }
        for (int i = nrDataPorts; i < nrDataPorts + nrModelPorts; i++) {
            pTypes[i] = OLDSTYLEMODELPORTTYPE;
        }
        return pTypes;
    }

    /**
     * <code>PortType</code> only used for old {@link NodeModel}s with
     * model ports.
     */
    public static final PortType OLDSTYLEMODELPORTTYPE = new PortType(
            ModelContentWrapper.class, ModelContentWrapper.class);

    /**
     *
     * Old-style constructor creating a NodeModel that also has model ports.
     *
     * @param nrDataIns the number of <code>DataTable</code> elements expected
     *            as inputs
     * @param nrDataOuts the number of <code>DataTable</code> objects expected
     *            at the output
     * @param nrModelIns the number of <code>ModelContent</code>
     *            elements available as inputs
     * @param nrModelOuts the number of <code>ModelContent</code>
     *            objects available at the output
     *
     * @deprecated Please use the {@link GenericNodeModel} instead of this
     * constructor if you want to have model ports
     */
    @Deprecated
    protected NodeModel(final int nrDataIns, final int nrDataOuts,
            final int nrModelIns, final int nrModelOuts) {
        super(createArrayOfDataAndModelTypes(nrDataIns, nrModelIns),
              createArrayOfDataAndModelTypes(nrDataOuts, nrModelOuts));
        m_nrDataInPorts = nrDataIns;
        m_nrDataOutPorts = nrDataOuts;
        m_nrModelInPorts = nrModelIns;
        m_nrModelOutPorts = nrModelOuts;
        // init wrapper for model content for all out-ports
        m_localOutModels = new ModelContentWrapper[nrModelOuts];
        for (int i = 0; i < m_localOutModels.length; i++) {
            m_localOutModels[i] = new ModelContentWrapper(null);
        }
    }

    /**
     * @see #configure(PortObjectSpec[])
     */
    protected abstract DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException;

    /**
     * {@inheritDoc}
     */
    @Override
    protected final PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
    throws InvalidSettingsException {
        // convert all PortObjectSpecs corresponding to data ports to
        // DataTableSpecs
        DataTableSpec[] inTableSpecs = new DataTableSpec[m_nrDataInPorts];
        for (int i = 0; i < m_nrDataInPorts; i++) {
            inTableSpecs[i] = (DataTableSpec) inSpecs[i];
        }
        for (int i = m_nrDataInPorts;
                i < m_nrDataInPorts + m_nrModelInPorts; i++) {
            assert (inSpecs[i] instanceof ModelContentWrapper);
            ModelContentWrapper mlw = (ModelContentWrapper)inSpecs[i];
            ModelContentRO mdl = mlw.m_hiddenModel;
            loadModelContent(i - m_nrDataInPorts, mdl);
        }
        // call old-style configure
        DataTableSpec[] outTableSpecs = configure(inTableSpecs);
        // copy output specs and put dummy model-out specs in result array
        PortObjectSpec[] returnObjectSpecs =
            new PortObjectSpec[m_nrDataOutPorts + m_nrModelOutPorts];
        for (int i = 0; outTableSpecs != null && i < m_nrDataOutPorts; i++) {
            returnObjectSpecs[i] = outTableSpecs[i];
        }
        for (int i = m_nrDataOutPorts;
                i < m_nrDataOutPorts + m_nrModelOutPorts; i++) {
            m_localOutModels[i - m_nrDataOutPorts].m_hiddenModel = null;
            returnObjectSpecs[i] = m_localOutModels[i - m_nrDataOutPorts];
        }
        return returnObjectSpecs;
    }
    
    /////////////////////////////////////
    // The following is a hack to allow usage of ModelContent object already
    // during configure! (old v1.x model ports!)
    //
    // hide model content in a modern style PortObjectSpec
    public final static class ModelContentWrapper
            implements ModelPortObjectSpec, ModelPortObject {
        private ModelContent m_hiddenModel;
        ModelContentWrapper(final ModelContent mdl) {
            m_hiddenModel = mdl;
        }
        public final ModelContentRO getModelContent() {
            return m_hiddenModel;
        }
        public ModelContentWrapper getSpec() {
            return this;
        }
        
        /**
         * 
         * @return
         */
        static final PortObjectSerializer<ModelContentWrapper> 
            getPortObjectSerializer() {
            return new PortObjectSerializer<ModelContentWrapper>() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                protected ModelContentWrapper loadPortObject(
                        final File directory, final ExecutionMonitor c)
                        throws IOException, CanceledExecutionException {
                    ModelContent cnt = new ModelContent();
                    cnt.load(directory, c);
                    return new ModelContentWrapper(cnt);
                }
                /**
                 * {@inheritDoc}
                 */
                @Override
                protected void savePortObject(final ModelContentWrapper o,
                        final File directory, final ExecutionMonitor c)
                        throws IOException, CanceledExecutionException {
                    o.m_hiddenModel.save(directory, c);
                }
            };
        }
        
        /**
         * 
         * @return
         */
        static PortObjectSpecSerializer<ModelContentWrapper> 
            getPortObjectSpecSerializer() {
            return new PortObjectSpecSerializer<ModelContentWrapper>() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                protected ModelContentWrapper loadPortObjectSpec(
                        final File directory) throws IOException {
                    File nullFile = new File(directory, "null.xml");
                    if (nullFile.exists()) {
                        return new ModelContentWrapper(null);
                    }
                    ModelContent cnt = new ModelContent();
                    try {
                        cnt.load(directory, new ExecutionMonitor());
                    } catch (CanceledExecutionException cee) {
                        new ModelContentWrapper(null);
                    }
                    return new ModelContentWrapper(cnt);
                }
                /**
                 * {@inheritDoc}
                 */
                @Override
                protected void savePortObjectSpec(final ModelContentWrapper o,
                        final File directory) throws IOException {
                    if (o.m_hiddenModel != null) {
                        try {
                            o.m_hiddenModel.save(directory, 
                                    new ExecutionMonitor());
                        } catch (CanceledExecutionException cee) {
                            // can't happen, no access to ExecutionMonitor
                            assert false;
                        }
                    } else {
                        File nullFile = new File(directory, "null.xml");
                        nullFile.createNewFile();
                    }
                }
            };
        }
    }
    //
    // allow to replace model content generated during execute in the modern
    // style PortObjectSpec (Schweinerei! changes spec under the model's ass)
    private final ModelContentWrapper[] m_localOutModels;
    //
    // end of evil hack.
    ///////////////////////////////////////////

    /**
     * @see #execute(PortObject[], ExecutionContext)
     */
    protected abstract BufferedDataTable[] execute(
            final BufferedDataTable[] inData, final ExecutionContext exec)
            throws Exception;

    @Override
    protected final PortObject[] execute(
            final PortObject[] inData, final ExecutionContext exec)
            throws Exception {
        // convert all PortObjects to DataTables
        BufferedDataTable[] inTables =
            new BufferedDataTable[m_nrDataInPorts];
        for (int i = 0; i < m_nrDataInPorts; i++) {
            inTables[i] = (BufferedDataTable)(inData[i]);
        }
        // load remaining Model Objects into old style NodeModel
        for (int i = m_nrDataInPorts;
                 i < m_nrDataInPorts + m_nrModelInPorts; i++) {
            assert (inData[i] instanceof ModelContentWrapper);
            ModelContentRO mdl = 
                ((ModelContentWrapper) inData[i]).m_hiddenModel;
            loadModelContent(i - m_nrDataInPorts, mdl);
        }
        // finally call old style execute
        BufferedDataTable[] outTables = execute(inTables, exec);
        // retrieve models from old style NodeModel
        PortObject[] returnObjects =
            new PortObject[m_nrDataOutPorts + m_nrModelOutPorts];
        for (int i = 0; i < m_nrDataOutPorts; i++) {
            returnObjects[i] = outTables[i];
        }
        for (int i = m_nrDataOutPorts;
                 i < m_nrDataOutPorts + m_nrModelOutPorts; i++) {
            int mdlIndex = i - m_nrDataOutPorts;
            ModelContent thisMdl = new ModelContent("ModelContent");
            saveModelContent(mdlIndex, thisMdl);
            m_localOutModels[mdlIndex].m_hiddenModel = thisMdl;
            returnObjects[i] = m_localOutModels[mdlIndex];
        }
        // and return the assembled data+models
        return returnObjects;
    }

    ///////////////////// DEPRECATED STARTS HERE /////////////////////

    /**
     * Override this method if <code>ModelContent</code> input(s) have
     * been set. This method is then called for each ModelContent input to
     * load the <code>ModelContent</code> after the previous node has been
     * executed successfully or is reset.
     *
     * <p>This implementation throws a InvalidSettingsException as it should
     * not have been called: If a derived NodeModel defines a model input, it
     * must override this method.
     *
     * @deprecated Node with model in- or outputs should extend 
     * {@link GenericNodeModel} and set the port types accordingly. As of 
     * KNIME 2.0, models are ordinary port objects.  
     * @param index The input index, starting from 0.
     * @param predParams The ModelContent to load, which can be null to
     *            indicate that no ModelContent model is available.
     * @throws InvalidSettingsException If the predictive parameters could not
     *             be loaded.
     */
    @Deprecated 
    protected void loadModelContent(final int index,
            final ModelContentRO predParams) throws InvalidSettingsException {
        assert predParams == predParams;
        throw new InvalidSettingsException(
                "loadModelContent() not overridden: " + index);
    }

    /**
     * Override this method if <code>ModelContent</code> output(s) have
     * been set. This method is then called for each
     * <code>ModelContent</code> output to save the
     * <code>ModelContent</code> after this node has been successfully
     * executed.
     *
     * <p>This implementation throws a InvalidSettingsException as it should
     * not have been called: If a derived NodeModel defines a model output, it
     * must override this method.
     * @deprecated Node with model in- or outputs should extend 
     * {@link GenericNodeModel} and set the port types accordingly. As of 
     * KNIME 2.0, models are ordinary port objects.  
     * 
     * @param index The output index, starting from 0.
     * @param predParams The ModelContent to save to.
     * @throws InvalidSettingsException If the model could not be saved.
     */
    @Deprecated
    protected void saveModelContent(final int index,
            final ModelContentWO predParams) throws InvalidSettingsException {
        assert predParams == predParams;
        throw new InvalidSettingsException(
                "saveModelContent() not overridden: " + index);
    }

}

