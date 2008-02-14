/* 
 * -------------------------------------------------------------------
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
 * -------------------------------------------------------------------
 * 
 * History
 *   12.07.2006 (gabriel): created
 */
package org.knime.core.node;

import org.knime.core.node.config.ConfigRO;

/**
 * Read-only interface for <code>ModelContent</code> objects.
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
public interface ModelContentRO 
        extends ConfigRO, ModelPortObjectSpec, ModelPortObject {

    /**
     * Returns a read-only <code>ModelContentRO</code> object from this config.
     * @param key The identifier.
     * @return A new <code>ModelContentRO</code> object.
     * @throws InvalidSettingsException If the object can't be accessed.
     */
    ModelContentRO getModelContent(String key) 
        throws InvalidSettingsException;
    
}
