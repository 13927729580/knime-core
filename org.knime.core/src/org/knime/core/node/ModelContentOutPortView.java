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
 * -------------------------------------------------------------------
 *
 * History
 *   26.10.2005 (gabriel): created
 *   15.05.1006 (sieb&ohl): reviewed
 */
package org.knime.core.node;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.knime.core.node.NodeModel.ModelContentWrapper;

/**
 * A port view showing the port's <code>ModelContent</code> as
 * <code>JTree</code>.
 *
 * @author Thomas Gabriel, University of Konstanz
 */
final class ModelContentOutPortView extends NodeOutPortView {

    /** Shows the ModelContent as JTree. */
    private final JTree m_tree;

    /** If no ModelContent available. */
    private static final TreeNode NO_TEXT
        = new DefaultMutableTreeNode("<No Model>", false);

    private final JPopupMenu m_treePopup = new JPopupMenu();

    /**
     * A view showing the data model stored in the specified ModelContent
     * output port.
     *
     * @param nodeName Name of the node the inspected port belongs to. Will
     *            be part of the frame's title.
     * @param portName Name of the port to view the ModelContent from. Will
     *            be part of the frame's title.
     *
     */
    ModelContentOutPortView(final String nodeName, final String portName) {
        super(nodeName + ", " + portName);
        m_tree = new JTree();
        m_tree.setEditable(false);
        m_tree.setFont(new Font("Courier", Font.PLAIN, 12));
        m_tree.setLargeModel(true);
        m_tree.setRowHeight(20);
        m_treePopup.setLightWeightPopupEnabled(false);
        final JMenuItem expand = new JMenuItem("Expand");
        expand.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent ae) {
                TreePath[] tps = m_tree.getSelectionPaths();
                if (tps != null) {
                    for (TreePath tp : tps) {
                        expandAll(tp);
                    }
                }
            }
        });
        m_treePopup.add(expand);
        final JMenuItem collapse = new JMenuItem("Collapse");
        collapse.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent ae) {
                TreePath[] tps = m_tree.getSelectionPaths();
                if (tps != null) {
                    for (TreePath tp : tps) {
                        collapseAll(tp);
                    }
                }
            }
        });
        m_treePopup.add(collapse);
        m_tree.add(m_treePopup);
        m_tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(final MouseEvent e) {
                if (e.isPopupTrigger() && m_tree.getSelectionPaths() != null) {
                    m_treePopup.show(m_tree, e.getX(), e.getY());
                }
            }
           @Override
           public void mousePressed(final MouseEvent e) {
               if (e.isPopupTrigger() && m_tree.getSelectionPaths() != null) {
                   m_treePopup.show(m_tree, e.getX(), e.getY());
               }
           }
        });
        Container cont = getContentPane();
        cont.setLayout(new BorderLayout());
        cont.setBackground(NodeView.COLOR_BACKGROUND);
        cont.add(new JScrollPane(m_tree), BorderLayout.CENTER);
    }

    private void expandAll(final TreePath parent) {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration e = node.children(); e.hasMoreElements();) {
                TreeNode n = (TreeNode) e.nextElement();
                TreePath path = parent.pathByAddingChild(n);
                expandAll(path);
            }
        }
        m_tree.expandPath(parent);
    }

    private void collapseAll(final TreePath parent) {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration e = node.children(); e.hasMoreElements();) {
                TreeNode n = (TreeNode) e.nextElement();
                TreePath path = parent.pathByAddingChild(n);
                collapseAll(path);
            }
        }
        m_tree.expandPath(parent);
    }

    /**
     * Updates the view's content with new ModelContent object.
     *
     * @param predParams the new content can be null
     * @param spec the new spec, not used
     */
    @Override
    void update(final PortObject predParams, final PortObjectSpec spec) {
        m_tree.removeAll();
        if (predParams == null) {
            m_tree.setModel(new DefaultTreeModel(NO_TEXT));
        } else {
            ModelContentRO cont = 
                ((ModelContentWrapper) predParams).getModelContent();
            if (cont == null) {
                m_tree.setModel(new DefaultTreeModel(NO_TEXT));
            } else {
                m_tree.setModel(new DefaultTreeModel(cont));
            }
        }
        super.updatePortView();
    }

}
