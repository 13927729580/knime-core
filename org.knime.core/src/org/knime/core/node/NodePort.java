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
 *   02.05.2006(sieb, ohl): reviewed 
 */
package org.knime.core.node;


/**
 * Abstract node port implementation which keeps a unique id and a port name.
 * The inner classes can be used to distinguish between <code>DataPort</code>
 * and <code>ModelContentPort</code> objects.
 * 
 * @see NodeInPort
 * @see NodeOutPort
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
public abstract class NodePort {

    /** This ports ID assigned from the underlying node. */
    private final int m_portID;
    
    /** The type of this port. */
    private final PortType m_portType;

    /** The port name which can be used for displaying purposes. */
    private String m_portName;

    /**
     * Creates a new node port with an ID assigned from the underlying node. The
     * default port name is "Port [portID]" and can be changed via
     * <code>#setPortName(String)</code>.
     * 
     * @param portID the port's id, greater or equal zero
     * 
     * @see #setPortName(String)
     */
    NodePort(final int portID, final PortType pType) {
        assert (portID >= 0);
        assert (pType != null);
        m_portID = portID;
        m_portType = pType;
        setPortName(null);
    }

    /**
     * @return The port id.
     */
    public final int getPortID() {
        return m_portID;
    }
    
    /**
     * @return The port type. 
     */
    public final PortType getPortType() {
        return m_portType;
    }

    /**
     * @return The port name.
     */
    public final String getPortName() {
        return m_portName;
    }

    /**
     * Sets a new name for this port. If null or an empty string is passed, the
     * default name will be generated: "Port [" + portID + "]".
     * 
     * @param portName The new name for this port. If null is passed, the
     *            default name will be generated.
     */
    public final void setPortName(final String portName) {
        if (portName == null || portName.trim().length() == 0) {
            if (this instanceof NodeInPort) {
                m_portName = "Inport " + m_portID;
            } else {
                m_portName = "Outport " + m_portID;
            }
        } else {
            m_portName = portName.trim();
        }
    }

}
