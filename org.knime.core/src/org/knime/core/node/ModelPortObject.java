/* 
 * ------------------------------------------------------------------
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
 * History
 *   14.02.2008 (gabriel): created
 */
package org.knime.core.node;

/**
 * <code>ModelPortObject</code> interface used for model ports.
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
public interface ModelPortObject extends PortObject {

    /**
     * Defines the <code>PortType</code> for <code>ModelPortObject</code> 
     * objects using <code>ModelPortObjectSpec.class</code> and 
     * <code>ModelPortObject.class</code> as underling object spec and object 
     * content.
     */
    public static final PortType TYPE = new PortType(ModelPortObjectSpec.class,
            ModelPortObject.class);
    
}
