/*
 * --------------------------------------------------------------------- *
 *   This source code, its documentation and all appendant files         *
 *   are protected by copyright law. All rights reserved.                *
 *                                                                       *
 *   Copyright, 2003 - 2006                                              *
 *   Universitaet Konstanz, Germany.                                     *
 *   Lehrstuhl fuer Angewandte Informatik                                *
 *   Prof. Dr. Michael R. Berthold                                       *
 *                                                                       *
 *   You may not modify, publish, transmit, transfer or sell, reproduce, *
 *   create derivative works from, distribute, perform, display, or in   *
 *   any way exploit any of the content, in whole or in part, except as  *
 *   otherwise expressly permitted in writing by the copyright owner.    *
 * --------------------------------------------------------------------- *
 */
package de.unikn.knime.core.node;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import de.unikn.knime.core.util.FileReaderFileFilter;

/**
 * Node view base class which implements the basic and common window properties.
 * The part specific to the special purpose node view must be implemented in the
 * derived class and must take place in a <code>Panel</code>. This panel is
 * registered in this base class (method <code>#setComponent(Component)</code>)
 * and will be displayed in the JFrame provided and handled by this class.
 * 
 * @see JFrame
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
public abstract class NodeView {

    /**
     * The node logger for this class; do not make static to make sure the right
     * class name is printed in messages.
     */
    private final NodeLogger m_logger;

    /**
     * Holds the underlying <code>NodeModel</code>.
     */
    private final NodeModel m_nodeModel;

    /**
     * Default background color.
     */
    public static final Color COLOR_BACKGROUND = Color.LIGHT_GRAY;

    /**
     * Initial width of the dummy component during construction.
     */
    private static final int INIT_COMP_WIDTH = 300;

    /**
     * Initial height of the dummy component during construction.
     */
    private static final int INIT_COMP_HEIGTH = 200;

    /**
     * Underlying frame, not visible from the outside.
     */
    private final JFrame m_frame;

    /**
     * Component in the center of this frame, set by
     * <code>#setComponent(Component)</code>.
     */
    private Component m_comp;

    /**
     * Component that is shown when no data is available (not connected, e.g.)
     * and the view is open.
     */
    private Component m_noDataComp;

    /**
     * References either to <code>m_comp</code> or <code>m_noDataComp</code>
     * depending on which is currently shown.
     */
    private Component m_activeComp;

    /**
     * If the view is opened the first time, it will be centered and packed. All
     * the following openings do not have an impact on these properties.
     */
    private boolean m_wasOpened = false;

    /**
     * Remembers the first time the actual component was set. The reason is the
     * resizing, the first time the proper component (not the "no data"
     * component) is set. This resizing should only occure the firs time.
     */
    private boolean m_componentSet = false;

    /**
     * Determines if this view is always on top. Usefull if special views should
     * stay on top all the time
     */
    private boolean m_alwaysOnTop = false;

    /**
     * The directory to export the view as image.
     */
    private static String exportDir;

    /**
     * This class sends property events when the status changes. So far, the
     * very only possible listenere is the EmbeddedNodeView that is informed
     * when the view finally closes (e.g. because the node was deleted). Once
     * the member m_frame is deleted from this class, the frame will also be a
     * potential listener.
     */
    public static final String PROP_CHANGE_CLOSE = "nodeview_close";

    /**
     * Creates a new view with the given title (<code>#getViewName()</code>),
     * a menu bar, and the panel (<code>#getComponent()</code>) in the
     * center. The default title is <i>View - </i>, and the default close
     * operation <code>JFrame.DISPOSE_ON_CLOSE</code>.
     * 
     * @param nodeModel The underlying node model.
     * @throws NullPointerException If the <code>nodeModel</code> is null.
     * @see #setComponent(Component)
     * @see #onClose()
     */
    protected NodeView(final NodeModel nodeModel) {
        if (nodeModel == null) {
            throw new NullPointerException();
        }

        // create logger
        m_logger = NodeLogger.getLogger(this.getClass());

        // store reference to the node model
        m_nodeModel = nodeModel;

        // init frame
        m_frame = new JFrame();
        if (KNIMEConstants.KNIME16X16 != null) {
            m_frame.setIconImage(KNIMEConstants.KNIME16X16.getImage());
        }
        m_frame.setBackground(COLOR_BACKGROUND);
        m_frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        m_frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent e) {
                onClose();
                m_nodeModel.unregisterView(NodeView.this);
            }
        });

        // creates menu item to close this view
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("File");
        menu.setMnemonic('F');

        // create always on top entry
        JMenuItem item = new JCheckBoxMenuItem("Always on top", m_alwaysOnTop);
        item.setMnemonic('T');
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent event) {

                boolean selected = ((JCheckBoxMenuItem)event.getSource())
                        .isSelected();
                m_alwaysOnTop = selected;
                m_frame.setAlwaysOnTop(m_alwaysOnTop);
            }
        });
        menu.add(item);

        // create close entry
        item = new JMenuItem("Export as image");
        item.setMnemonic('E');
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent event) {
                exportAsImage();
            }
        });
        menu.add(item);

        // create close entry
        item = new JMenuItem("Close");
        item.setMnemonic('C');
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent event) {
                closeView();
            }
        });
        menu.add(item);
        menuBar.add(menu);
        m_frame.setJMenuBar(menuBar);

        // set properties of the content pane
        Container cont = m_frame.getContentPane();
        cont.setLayout(new BorderLayout());
        cont.setBackground(COLOR_BACKGROUND);

        // set a dummy component to get the default size
        setShowNODATALabel(true);
        setComponent(m_noDataComp);

        // after view has been created: register the view with the model
        m_nodeModel.registerView(this);

    } // NodeView(NodeModel,String)

    /**
     * Exports the current view to an image file.
     */
    private void exportAsImage() {

        // get a possible previous save location
        // get the location to export the view
        File exportDirFile = null;
        try {

            exportDirFile = new File(new URL(exportDir).getFile());

        } catch (Exception e) {

            // do nothing here
            // in case of an wrong / invalid path, the
            // file chooser starts at the default location
        }

        String path;
        JFileChooser chooser = new JFileChooser(exportDirFile);
        chooser.setFileFilter(new FileReaderFileFilter("png",
                "PNG - Portable Network Graphics"));

        int returnVal = chooser.showSaveDialog(m_frame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {

            try {
                String fileName = chooser.getSelectedFile().getAbsolutePath();

                // append "zip" extension if not there.
                String extension = fileName.substring(fileName.length() - 4,
                        fileName.length());

                if (!extension.equals(".png")) {
                    fileName = fileName + ".png";
                }
                path = new File(fileName).toURL().toString();
            } catch (Exception e) {
                path = "<Error: Couldn't create URL for file>";
            }
            exportDir = path;
        } else {
            // do not save anything
            return;
        }
        
        // create an image from the view component
        BufferedImage image = new BufferedImage(m_comp.getWidth(), m_comp
                .getHeight(), BufferedImage.TYPE_INT_RGB);

        // create graphics object to paint in
        Graphics2D graphics = image.createGraphics();

        m_comp.paint(graphics);

        // write image to file
        try {
            File exportFile = new File(new URL(exportDir).getFile());
            exportFile.createNewFile();
            ImageIO.write(image, "png", exportFile);
        } catch (Exception e) {

            JOptionPane.showConfirmDialog(m_frame,
                    "View could not be exported.", "Warning",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE);

            m_logger.warn("View could not be exported due to io problems: ", e);
        }

        JOptionPane.showConfirmDialog(m_frame, "View successfully exported.",
                "Info", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Get reference to underlying <code>NodeModel</code>. Access this if
     * access to your model is needes and cast it if necessary. Alternatively,
     * you can also override this method in your derived node view and do the
     * cast implicitly, for instance:
     * 
     * <pre>
     * protected FooNodeModel getNodeModel() {
     *     return (FooNodeModel)super.getNodeModel();
     * }
     * </pre>
     * 
     * @return NodeModel reference.
     */
    protected NodeModel getNodeModel() {
        assert m_nodeModel != null;
        return m_nodeModel;
    }

    /**
     * Sets the property if the "no data" label is shown when the underlying
     * node is not executed but the view is shown (replaces whatever has been
     * set by <code>#setComponent(Component)</code>. Once the node is
     * executed the user tab is shown again.
     * 
     * @param showIt <code>true</code> for replace the current view,
     *            <code>false</code> always show the real view.
     */
    protected final void setShowNODATALabel(final boolean showIt) {
        m_noDataComp = (showIt ? createNoDataComp() : null);
    }

    /**
     * Called from the model that something has changed. It internally sets the
     * proper component depending on the node's execution state and if the no
     * data label is set. This method will invoke the abstract
     * <code>#modelChanged()</code> method.
     */
    final void callModelChanged() {
        setComponent(m_comp);
        try {
            modelChanged();
        } catch (NullPointerException npe) {
            throw new IllegalStateException(
                    "Implementation error of NodeView.modelChanged(). "
                            + "NullPointerException during notification of a "
                            + "changed model: " + npe.getMessage(), npe);
        } catch (Exception e) {
            throw new IllegalStateException("Error during notification "
                    + "of a changed model (in NodeView.modelChanged()). "
                    + "Reason: " + e.getMessage(), e);
        }
    }

    /**
     * Method is invoked when the underlying <code>NodeModel</code> has
     * changed. Also the HiLightHandler may be changed, as well as the
     * <code>NodeModel</code> content may be not available.
     */
    protected abstract void modelChanged();

    /**
     * This method is supposed to be overridden by views that want to receive
     * events from their assigned models via the
     * <code>NodeModel#notifyViews(Object)</code> method. Can be used to
     * iteratively update the view during execute.
     * 
     * @param arg The argument can be everything.
     */
    protected void updateModel(final Object arg) {
        // dummy statement to get rid of 'parameter not used' warning.
        assert arg == arg;
    }

    /**
     * Invoked when the window has been closed. Unregister
     * <code>HiLiteListeners</code>. Dispose internal members.
     */
    protected abstract void onClose();

    /**
     * Invoked when the window has been opened. Register property listeners.
     */
    protected abstract void onOpen();

    /**
     * Returns menu bar of this frame.
     * 
     * @return menu bar.
     */
    protected final JMenuBar getJMenuBar() {
        return m_frame.getJMenuBar();
    }

    /**
     * Initializes the view before opening.
     */
    private void preOpenView() {
        m_nodeModel.registerView(this);
        callModelChanged();
        if (!m_wasOpened) { // if the view was already visible
            m_wasOpened = true;
            if (m_comp != null) {
                m_comp.invalidate();
                m_comp.repaint();
            }
            m_frame.pack();
            setLocation();
        }
    }

    /**
     * Initializes all view components and returns the view's content pane. If
     * you derive this class, <strong>do not</strong> call this method. It's
     * being used by the framework (if views are shown within a JFrame) or by
     * eclipse (if available, i.e. when views are embedded in eclipse)
     * 
     * @return The view's content pane.
     */
    public final Component openViewComponent() {
        // init
        preOpenView();
        // since the frame is not opened, we must call this by hand
        onOpen();
        // return content pane
        return m_frame.getContentPane();
    }

    /**
     * Opens the view.
     * 
     * @see #onOpen
     */
    final void openView() {
        // init
        preOpenView();
        // inform derived class
        onOpen();
        // show frame
        m_frame.setVisible(true); // triggers WindowEvent 'Opened' which
        // brings the frame to front
        m_frame.toFront();
    }

    /**
     * Called by the node when it is deleted or by the "close" button. Disposes
     * the frame.
     * <p>
     * Calls the onClose method and unregisters this view from the model. If you
     * derive this class, <strong>do not</strong> call this method. It's being
     * used by the framework (if views are shown within a JFrame) or by eclipse
     * (if available, i.e. when views are embedded in eclipse).
     */
    public final void closeView() {
        onClose();
        m_nodeModel.unregisterView(this);
        if (m_frame != null) {
            m_frame.getContentPane()
                    .firePropertyChange(PROP_CHANGE_CLOSE, 0, 1);
            // this will trigger a windowClosed event
            // (listener see above) and call closeViewComponent()
            m_frame.setVisible(false);
            m_frame.dispose();
        }
    }

    /**
     * Sets this frame in the center of the screen observing the current screen
     * size.
     */
    private void setLocation() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension size = m_frame.getSize();
        m_frame.setBounds(Math.max(0, (screenSize.width - size.width) / 2),
                Math.max(0, (screenSize.height - size.height) / 2), Math.min(
                        screenSize.width, size.width), Math.min(
                        screenSize.height, size.height));
    }

    /**
     * @return Checks whether the view is open or not.
     */
    protected final boolean isOpen() {
        return m_frame.isVisible();
    }
    
    /**
     * Set a new name for this view. The title is updated to the given title. 
     * If <code>newName</code> is <code>null</code> the new title is 
     * <i>base name - &lt; no title &gt; </i>.
     * 
     * @param newName The new title to be set.
     */
    protected final void setViewTitle(final String newName) {
        if (newName == null) {
            m_frame.setTitle(getViewName() + " - <no title>");
        } else {
            m_frame.setTitle(newName);
        }
    }
    
    /**
     * @return The current view's title.
     */
    public final String getViewTitle() {
        return m_frame.getTitle();
    }
    
    /**
     * Sets the given name as frame name and title. 
     * @param name The frame's name and title.
     */
    final void setViewName(final String name) {
        m_frame.setName(name);
        m_frame.setTitle(name);
    }   
    
    /**
     * Returns the view name as set by <code>#setViewName(String)</code> or
     * <code>null</code> if that hasn't happen yet.
     * 
     * @return The view's name.
     * @see JFrame#setName(String)
     */
    protected final String getViewName() {
        return m_frame.getName();
    }

    /**
     * Returns the underlying content pane's panel placed at the center of the
     * view.
     * 
     * @return panel of the view's center area.
     */
    protected final Component getComponent() {
        return m_comp;
    }

    /**
     * Sets the panel of the view's content pane center area. Register your
     * <code>Component</code> that implements the functionality of the derived
     * class with this function. The foreground and background colors of your
     * panel are set to the default colors defined in this class.
     * 
     * @param comp Component to set in the center of the view.
     */
    protected void setComponent(final Component comp) {
        if (!m_nodeModel.isExecuted() && m_noDataComp != null) {
            setComponentIntern(m_noDataComp, false);
        } else {
            if (!m_componentSet) {
                setComponentIntern(comp, true);
                m_componentSet = true;
            } else {
                setComponentIntern(comp, false);
            }
        }
        m_comp = comp;
    }

    /**
     * Helper method that internally sets the current component; it does not
     * update m_comp (which setComponent does).
     * 
     * @param cmp The new component to show (might be m_noDataComp)
     * @param doPack if true, the frame is packed which results in resizing the
     */
    private void setComponentIntern(final Component cmp, final boolean doPack) {
        if (m_activeComp == cmp || cmp == null) {
            return;
        }

        Container cont = m_frame.getContentPane();
        if (m_activeComp != null) {
            cont.remove(m_activeComp);
        }

        m_activeComp = cmp;
//        cmp.setBackground(COLOR_BACKGROUND);
        cont.add(m_activeComp, BorderLayout.CENTER);

        if (doPack) {
            m_frame.pack();
        } else {
            m_frame.invalidate();
            m_frame.validate();
            m_frame.repaint();
        }
    }

    /**
     * Creates the label that is shown when no node is not connected or not
     * executed.
     * 
     * @return Default "no label" component.
     */
    private Component createNoDataComp() {
        JLabel noData = new JLabel("<html><center>No data in<br>"
                + getViewName() + "</center></html>", SwingConstants.CENTER);
        noData
                .setPreferredSize(new Dimension(INIT_COMP_WIDTH,
                        INIT_COMP_HEIGTH));
        return noData;
    }
}
