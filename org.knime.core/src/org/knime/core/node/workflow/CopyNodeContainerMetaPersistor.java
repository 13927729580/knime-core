/* ------------------------------------------------------------------
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
 * ---------------------------------------------------------------------
 * 
 * History
 *   Jun 9, 2008 (wiswedel): created
 */
package org.knime.core.node.workflow;

import java.io.IOException;

import org.knime.core.internal.ReferencedFile;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.workflow.NodeContainer.State;
import org.knime.core.node.workflow.WorkflowPersistor.LoadResult;

/**
 * 
 * @author wiswedel, University of Konstanz
 */
final class CopyNodeContainerMetaPersistor implements
        NodeContainerMetaPersistor {
    
    private final NodeContainer m_original;
    
    /**
     * 
     */
    CopyNodeContainerMetaPersistor(final NodeContainer original) {
        m_original = original;
    }

    /** {@inheritDoc} */
    @Override
    public String getCustomDescription() {
        return m_original.getCustomDescription();
    }

    /** {@inheritDoc} */
    @Override
    public String getCustomName() {
        return m_original.getCustomName();
    }

    /** {@inheritDoc} */
    @Override
    public ReferencedFile getNodeContainerDirectory() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public int getNodeIDSuffix() {
        return m_original.getID().getIndex();
    }

    /** {@inheritDoc} */
    @Override
    public State getState() {
        switch (m_original.getState()) {
        case IDLE:
        case UNCONFIGURED_MARKEDFOREXEC:
            return State.IDLE;
        default:
            return State.CONFIGURED;
        }
    }

    /** {@inheritDoc} */
    @Override
    public UIInformation getUIInfo() {
        return m_original.getUIInformation();
    }

    /** {@inheritDoc} */
    @Override
    public LoadResult load(final NodeSettingsRO settings) throws IOException,
            CanceledExecutionException {
        return new LoadResult();
    }

    /** {@inheritDoc} */
    @Override
    public void setNodeIDSuffix(final int nodeIDSuffix) {
    }

    /** {@inheritDoc} */
    @Override
    public void setUIInfo(final UIInformation uiInfo) {
    }

}
