/* 
 * -------------------------------------------------------------------
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
 * -------------------------------------------------------------------
 * 
 * History
 *   29.10.2005 (mb): created
 *   2006-05-26 (tm): reviewed
 */
package org.knime.core.node.defaultnodesettings;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.util.ConvenientComboBoxRenderer;
import org.knime.core.node.util.StringHistory;
import org.knime.core.util.SimpleFileFilter;

/**
 * A standard component allowing to choose a location(directory) and/or file
 * name.
 * 
 * @author M. Berthold, University of Konstanz
 */
public class DialogComponentFileChooser extends DialogComponent {

    private final JComboBox m_fileComboBox;

    private StringHistory m_fileHistory;

    private final JButton m_browseButton;

    private final TitledBorder m_border;

    /**
     * Constructor that creates a file chooser with an
     * {@link JFileChooser#OPEN_DIALOG} that filters files according to the
     * given extensions. Also non-existing paths are accepted.
     * 
     * @param stringModel the model holding the value
     * @param historyID to identify the file history
     * @param validExtensions only show files with those extensions
     */
    public DialogComponentFileChooser(final SettingsModelString stringModel,
            final String historyID, final String... validExtensions) {
        this(stringModel, historyID, JFileChooser.OPEN_DIALOG, validExtensions);
    }

    /**
     * Constructor that creates a file/directory chooser of the given type
     * without a file filter. Also non-existing paths are accepted.
     * 
     * @param stringModel the model holding the value
     * @param dialogType {@link JFileChooser#OPEN_DIALOG},
     *            {@link JFileChooser#SAVE_DIALOG} or
     *            {@link JFileChooser#CUSTOM_DIALOG}
     * @param historyID to identify the file history
     * @param directoryOnly <code>true</code> if only directories should be
     *            selectable, otherwise only files can be selected
     */
    public DialogComponentFileChooser(final SettingsModelString stringModel,
            final String historyID, final int dialogType,
            final boolean directoryOnly) {
        this(stringModel, historyID, dialogType, directoryOnly, new String[0]);
    }

    /**
     * Constructor that creates a file chooser of the given type that filters
     * the files according to the given extensions. Also non-existing paths are
     * accepted.
     * 
     * @param stringModel the model holding the value
     * @param historyID id for the file history
     * @param dialogType {@link JFileChooser#OPEN_DIALOG},
     *            {@link JFileChooser#SAVE_DIALOG} or
     *            {@link JFileChooser#CUSTOM_DIALOG}
     * @param validExtensions only show files with those extensions
     */
    public DialogComponentFileChooser(final SettingsModelString stringModel,
            final String historyID, final int dialogType,
            final String... validExtensions) {
        this(stringModel, historyID, dialogType, false, validExtensions);
    }

    /**
     * Constructor that creates a file or directory chooser of the given type
     * that filters the files according to the given extensions. Also
     * non-existing paths are accepted.
     * 
     * @param stringModel the model holding the value
     * @param historyID to identify the file histroy
     * @param dialogType {@link JFileChooser#OPEN_DIALOG},
     *            {@link JFileChooser#SAVE_DIALOG} or
     *            {@link JFileChooser#CUSTOM_DIALOG}
     * @param directoryOnly <code>true</code> if only directories should be
     *            selectable, otherwise only files can be selected
     * @param validExtensions only show files with those extensions
     */
    public DialogComponentFileChooser(final SettingsModelString stringModel,
            final String historyID, final int dialogType,
            final boolean directoryOnly, final String... validExtensions) {
        super(stringModel);

        getComponentPanel().setLayout(new FlowLayout());

        JPanel p = new JPanel();
        m_fileHistory = StringHistory.getInstance(historyID);
        m_fileComboBox = new JComboBox();
        m_fileComboBox.setPreferredSize(new Dimension(300, m_fileComboBox
                .getPreferredSize().height));
        m_fileComboBox.setRenderer(new ConvenientComboBoxRenderer());
        m_fileComboBox.setEditable(true);
        
        for (String fileName : m_fileHistory.getHistory()) {
            m_fileComboBox.addItem(fileName);
        }

        m_browseButton = new JButton("Browse...");

        String title = directoryOnly ? "Selected Directory:" : "Selected File:";
        m_border = BorderFactory.createTitledBorder(title);
        p.setBorder(m_border);
        p.add(m_fileComboBox);
        p.add(m_browseButton);
        getComponentPanel().add(p);

        m_browseButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent ae) {
                // sets the path in the file text field.
                String selectedFile = 
                    m_fileComboBox.getEditor().getItem().toString();
                if (selectedFile.length() == 0) { 
                    selectedFile = (String)m_fileComboBox.getItemAt(0);
                }
                JFileChooser chooser = new JFileChooser(selectedFile);
                chooser.setDialogType(dialogType);
                if (directoryOnly) {
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                } else {
                    // if extensions are defined
                    if (validExtensions != null && validExtensions.length > 0) {
                        // disable "All Files" selection
                        chooser.setAcceptAllFileFilterUsed(false);
                        // set file filter for given extensions
                        for (String extension : validExtensions) {
                            chooser.setFileFilter(new SimpleFileFilter(
                                    extension));
                        }
                    }
                }
                int returnVal =
                        chooser.showDialog(getComponentPanel().getParent(),
                                null);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    String newFile;
                    try {
                        newFile =
                                chooser.getSelectedFile().getAbsoluteFile()
                                        .toString();
                        // if file selection and only on extension available
                        if (!directoryOnly && validExtensions != null 
                                && validExtensions.length == 1) {
                            // and the file names has no this extension
                            if (!newFile.endsWith(validExtensions[0])) {
                                // then append it
                                newFile += validExtensions[0];
                            }
                        }
                    } catch (SecurityException se) {
                        newFile = "<Error: " + se.getMessage() + ">";
                    }
                    // avoid adding the same string twice...
                    m_fileComboBox.removeItem(newFile);
                    m_fileComboBox.addItem(newFile);
                    m_fileComboBox.setSelectedItem(newFile);
                    getComponentPanel().revalidate();
                }
            }
        });

        m_fileComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                filenameChanged();            }
        });
        m_fileComboBox.addItemListener(new ItemListener() {
            public void itemStateChanged(final ItemEvent e) {
                filenameChanged();
            }
        });

        /* install action listeners */
        // set stuff to update preview when file location changes
        m_fileComboBox.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent e) {
                filenameChanged();
            }
            /**
             * {@inheritDoc}
             */
            @Override
            public void focusGained(final FocusEvent e) {
                filenameChanged();
            }
        });
        Component editor = m_fileComboBox.getEditor().getEditorComponent();
        if (editor instanceof JTextComponent) {
            Document d = ((JTextComponent)editor).getDocument();
            d.addDocumentListener(new DocumentListener() {
                public void changedUpdate(final DocumentEvent e) {
                    filenameChanged();
                }

                public void insertUpdate(final DocumentEvent e) {
                    filenameChanged();
                }

                public void removeUpdate(final DocumentEvent e) {
                    filenameChanged();
                }
            });
        }


        getModel().prependChangeListener(new ChangeListener() {
            public void stateChanged(final ChangeEvent e) {
                updateComponent();
            }
        });
    }

    // called by all action/change listeners to transfer the new filename into
    // the settings model. (And ignore any invalid situations.)
    private void filenameChanged() {
        // transfer the new filename into the settings model
        try {
            clearError(m_fileComboBox);
            updateModel(true); // don't color the combobox red.
        } catch (InvalidSettingsException ise) {
            // ignore it here.
        } 
    }
    
    /**
     * Transfers the value from the component into the settings model.
     * 
     * @param noColoring if set true, the component will not be marked red, even
     *            if the entered value was erroneous.
     * @throws InvalidSettingsException if the entered filename is null or
     *             empty.
     */
    private void updateModel(final boolean noColoring)
            throws InvalidSettingsException {

        String file = m_fileComboBox.getEditor().getItem().toString();
        if ((file != null) && (file.trim().length() > 0)) {

            try {
                ((SettingsModelString)getModel()).setStringValue(file);
            } catch (RuntimeException e) {
                // if value was not accepted by setter method
                if (!noColoring) {
                    showError(m_fileComboBox);
                }
                throw new InvalidSettingsException(e);
            }

        } else {
            if (!noColoring) {
                showError(m_fileComboBox);
            }
            throw new InvalidSettingsException("Please specify a filename.");
        }
    }

    /**
     * Seems the super.showError doesn't work with comboboxes. This is to
     * replace it with a working version.
     * 
     * @param box the box to color red.
     */
    private void showError(final JComboBox box) {

        if (!getModel().isEnabled()) {
            // don't flag an error in disabled components.
            return;
        }
        String selection = box.getEditor().getItem().toString();

        if ((selection == null) || (selection.length() == 0)) {
            box.setBackground(Color.RED);
        } else {
            box.setForeground(Color.RED);
        }
        box.requestFocusInWindow();

        // change the color back as soon as he changes something
        box.addItemListener(new ItemListener() {
            public void itemStateChanged(final ItemEvent e) {
                box.setForeground(DEFAULT_FG);
                box.setBackground(DEFAULT_BG);
                box.removeItemListener(this);
            }
        });
    }

    /**
     * Sets the coloring of the specified component back to normal.
     * 
     * @param box the component to clear the error status for.
     */
    protected void clearError(final JComboBox box) {
        box.setForeground(DEFAULT_FG);
        box.setBackground(DEFAULT_BG);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateComponent() {

        clearError(m_fileComboBox);

        // update the component only if model and component are out of sync
        SettingsModelString model = (SettingsModelString)getModel();
        String newValue = model.getStringValue();
        boolean update;
        if (newValue == null) {
            update = (m_fileComboBox.getSelectedItem() != null);
        } else {
            String file = m_fileComboBox.getEditor().getItem().toString();
            update = !newValue.equals(file);
        }
        if (update) {
            // to avoid multiply added items...
            m_fileComboBox.removeItem(newValue);
            m_fileComboBox.addItem(newValue);
            m_fileComboBox.setSelectedItem(newValue);
        }

        // also update the enable status
        setEnabledComponents(model.isEnabled());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateStettingsBeforeSave()
            throws InvalidSettingsException {
        // just in case we didn't get notified about the last change...
        updateModel(false); // mark the erroneous component red.
        // store the saved filename in the history
        m_fileHistory.add(((SettingsModelString)getModel()).getStringValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkConfigurabilityBeforeLoad(final DataTableSpec[] specs)
            throws NotConfigurableException {
        // we're always good - independent of the incoming spec
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setEnabledComponents(final boolean enabled) {
        m_browseButton.setEnabled(enabled);
        m_fileComboBox.setEnabled(enabled);
    }

    /**
     * Replaces the title displayed in the border that surrounds the editfield
     * and browse button with the specified new title. The default title of the
     * component is "Selected File:" or "Selected Directory:".
     * 
     * @param newTitle the new title to display in the border.
     * 
     * @throws NullPointerException if the new title is null.
     */
    public void setBorderTitle(final String newTitle) {
        if (newTitle == null) {
            throw new NullPointerException("New title to display can't"
                    + " be null.");
        }
        m_border.setTitle(newTitle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setToolTipText(final String text) {
        m_browseButton.setToolTipText(text);
        m_fileComboBox.setToolTipText(text);
    }

}
