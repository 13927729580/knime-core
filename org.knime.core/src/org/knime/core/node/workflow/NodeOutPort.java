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
 *   04.05.2006(sieb, ohl): reviewed
 *   21.03.2008(mb): changed to an interface to accomodate wfm 2.0
 */
package org.knime.core.node.workflow;

import org.knime.core.node.PortObject;
import org.knime.core.node.PortObjectSpec;
import org.knime.core.node.property.hilite.HiLiteHandler;


/**
 * Interface for a node's output port. A variable number of input ports can
 * be connected to it (which are part of the next nodes in the workflow).
 *
 * @author Thomas Gabriel, University of Konstanz
 */
public interface NodeOutPort extends NodePort {

    /**
     * Returns the <code>DataTableSpec</code> or null if not available.
     *
     * @return The <code>DataTableSpec</code> for this port.
     */
    public PortObjectSpec getPortObjectSpec();

    /**
     * Returns the DataTable for this port, as set by the node this port is
     * output for.
     *
     * @return PortObject the object for this port. Can be null.
     */
    public PortObject getPortObject();
    
    /**
     * Returns the hilite handler for this port as set by the node this port is
     * output for.
     *
     * @return The HiLiteHandler for this port or null.
     */
    public HiLiteHandler getHiLiteHandler();

    /**
     * Returns the scope object stack of the underlying node.
     *
     * @return the scope obj stack container
     */
    public ScopeObjectStack getScopeContextStackContainer();

    /**
     * Opens the port view for this port with the given name.
     *
     * @param name The name of the port view.
     */
    // TODO: return component with convenience method for Frame construction.
    public void openPortView(final String name);

}
