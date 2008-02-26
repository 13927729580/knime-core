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

/**
 * Holds all information related to one connection between specific ports
 * of two nodes. It also holds additional information, which can be adjusted
 * from the outside (bend points on a layout, for example).
 * 
 * @author M. Berthold/B. Wiswedel, University of Konstanz
 */
public class ConnectionContainer {
    private final NodeID m_source;
    private final int m_sourcePort;
    private final NodeID m_dest;
    private final int m_destPort;
    private UIInformation m_uiInfo;
    
    enum ConnectionType { STD, WFMIN, WFMOUT, WFMTHROUGH;
        /**
         * @return Whether this type is leaving a workflow (through or out)
         */
        public boolean isLeavingWorkflow() {
            switch (this) {
                case WFMOUT:
                case WFMTHROUGH: return true;
                default: return false;
            }
        }
    };
    private final ConnectionType m_type;
    
    /** Creates new connection.
     * 
     * @param src source node
     * @param srcPort port of source node
     * @param dest destination node
     * @param destPort port of destination node
     * @param type of connection
     */
    public ConnectionContainer(final NodeID src, final int srcPort,
            final NodeID dest, final int destPort, final ConnectionType type) {
        if (src == null || dest == null || type == null) {
            throw new NullPointerException("Arguments must not be null.");
        }
        if (srcPort < 0 || destPort < 0) {
            throw new IndexOutOfBoundsException("Port index must not be < 0:"
                    + Math.min(srcPort, destPort));
        }
        m_source = src;
        m_sourcePort = srcPort;
        m_dest = dest;
        m_destPort = destPort;
        m_uiInfo = null;
        m_type = type;
    }

    /////////////////
    // Getter Methods
    /////////////////
    
    /** 
     * @return the uiInfo
     */
    public UIInformation getUIInfo() {
        return m_uiInfo;
    }

    /**
     * @return the dest
     */
    public NodeID getDest() {
        return m_dest;
    }

    /**
     * @return the destPort
     */
    public int getDestPort() {
        return m_destPort;
    }

    /**
     * @return the source
     */
    public NodeID getSource() {
        return m_source;
    }

    /**
     * @return the sourcePort
     */
    public int getSourcePort() {
        return m_sourcePort;
    }

    /**
     * @return type of the connection
     */
    public ConnectionType getType() {
        return m_type;
    }

    /////////////////////////
    
    /**
     * @param uiInfo the uiInfo to set
     */
    public void setUIInfo(final UIInformation uiInfo) {
        m_uiInfo = uiInfo;
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ConnectionContainer)) {
            return false;
        }
        ConnectionContainer cc = (ConnectionContainer)obj;
        return m_dest.equals(cc.m_dest) && (m_destPort == cc.m_destPort)
        && m_source.equals(cc.m_source) && (m_sourcePort == cc.m_sourcePort)
                && m_type.equals(cc.m_type);
    }
    
    @Override
    public int hashCode() {
        return m_dest.hashCode() + m_source.hashCode() + m_destPort
        + m_sourcePort + m_type.hashCode();
    }
    
    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getType() + "[" + getSource() + "(" + getSourcePort() + ") -> "
            + getDest() + "( " + getDestPort() + ")]";
    }
}
