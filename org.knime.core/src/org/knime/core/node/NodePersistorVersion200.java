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
 *   Feb 13, 2008 (wiswedel): created
 */
package org.knime.core.node;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.container.ContainerTable;
import org.knime.core.eclipseUtil.GlobalClassCreator;
import org.knime.core.internal.ReferencedFile;
import org.knime.core.internal.SerializerMethodLoader;
import org.knime.core.node.PortObject.PortObjectSerializer;
import org.knime.core.node.PortObjectSpec.PortObjectSpecSerializer;
import org.knime.core.node.workflow.NodeMessage;
import org.knime.core.util.FileUtil;

/**
 * 
 * @author wiswedel, University of Konstanz
 */
public class NodePersistorVersion200 extends NodePersistorVersion1xx {

    /** Invokes super constructor. 
     * @param modelSettingsFailPolicy Forwared.*/
    public NodePersistorVersion200(
            final LoadNodeModelSettingsFailPolicy modelSettingsFailPolicy) {
        super(modelSettingsFailPolicy);
    }

    private static final NodeLogger LOGGER =
            NodeLogger.getLogger(NodePersistorVersion200.class);
    
    /**
     * Saves the node, node settings, and all internal structures, spec, data,
     * and models, to the given node directory (located at the node file).
     * 
     * @param nodeFile To write node settings to.
     * @param execMon Used to report progress during saving.
     * @throws IOException If the node file can't be found or read.
     * @throws CanceledExecutionException If the saving has been canceled.
     */
    public void save(final Node node, final ReferencedFile nodeFile,
            final ExecutionMonitor execMon, final boolean isSaveData)
            throws IOException, CanceledExecutionException {
        NodeSettings settings = new NodeSettings(SETTINGS_FILE_NAME);
        final ReferencedFile nodeDirRef = nodeFile.getParent();
        if (nodeDirRef == null) {
            throw new IOException("parent file of file \"" + nodeFile 
                    + "\" is not represented as object of class "
                    + ReferencedFile.class.getSimpleName());
        }
        saveCustomName(node, settings);
        node.saveSettingsTo(settings);
        saveHasContent(node, settings);
        saveNodeMessage(node, settings);
        ReferencedFile nodeInternDirRef = getNodeInternDirectory(nodeDirRef);
        File nodeInternDir = nodeInternDirRef.getFile();
        if (nodeInternDir.exists()) {
            FileUtil.deleteRecursively(nodeInternDir);
        }
        if (!node.isAutoExecutable() && isSaveData) {
            saveNodeInternDirectory(node, nodeInternDir, settings, execMon);
        }
        savePorts(node, nodeDirRef, settings, execMon, isSaveData);
        settings.saveToXML(new BufferedOutputStream(new FileOutputStream(
                nodeFile.getFile())));
    }

    protected void savePorts(final Node node, final ReferencedFile nodeDirRef,
            final NodeSettingsWO settings, final ExecutionMonitor exec,
            final boolean saveData) throws IOException,
            CanceledExecutionException {
        if (node.getNrOutPorts() == 0 || node.isAutoExecutable()) {
            return;
        }
        final int portCount = node.getNrOutPorts();
        NodeSettingsWO portSettings = settings.addNodeSettings("ports");
        exec.setMessage("Saving outport data");
        for (int i = 0; i < portCount; i++) {
            String portName = "port_" + i;
            ExecutionMonitor subProgress =
                    exec.createSubProgress(1 / (double)portCount);
            NodeSettingsWO singlePortSetting =
                    portSettings.addNodeSettings(portName);
            singlePortSetting.addInt("index", i);
            PortObjectSpec spec = node.getOutputSpec(i);
            PortObject object = node.getOutputObject(i);
            String portDirName;
            if (spec != null || object != null) {
                portDirName = portName;
                ReferencedFile portDirRef = 
                    new ReferencedFile(nodeDirRef, portDirName);
                File portDir = portDirRef.getFile();
                subProgress.setMessage("Cleaning directory "
                        + portDir.getAbsolutePath());
                FileUtil.deleteRecursively(portDir);
                portDir.mkdir();
                if (!portDir.isDirectory() || !portDir.canWrite()) {
                    throw new IOException("Can not write port directory "
                            + portDir.getAbsolutePath());
                }
                savePort(node, portDir, singlePortSetting, 
                        subProgress, i, saveData);
            } else {
                portDirName = null;
            }
            singlePortSetting.addString("port_dir_location", portDirName);
            subProgress.setProgress(1.0);
        }
    }
    
    protected void savePort(final Node node, final File portDir, 
            final NodeSettingsWO settings, final ExecutionMonitor exec, 
            final int portIdx, final boolean saveData) 
            throws IOException, CanceledExecutionException {
        PortObjectSpec spec = node.getOutputSpec(portIdx);
        settings.addString("port_spec_class", 
                spec != null ? spec.getClass().getName() : null);
        PortObject object = node.getOutputObject(portIdx);
        boolean isSaveObject = saveData && object != null;
        settings.addString("port_object_class", isSaveObject ? object
                .getClass().getName() : null);
        if (node.getOutputType(portIdx).equals(BufferedDataTable.TYPE)) {
            assert object == null 
                || object instanceof BufferedDataTable 
                : "Expected BufferedDataTable, got " 
                    + object.getClass().getSimpleName();
            // executed and instructed to save data
            if (saveData && object != null) {
                ((BufferedDataTable)object).save(portDir, exec);
            } else if (spec != null) {
                BufferedDataTable.saveSpec(
                        (DataTableSpec)spec, portDir);
            }
        } else {        
            exec.setMessage("Saving specification");
            if (spec != null) {
                String specDirName = "spec";
                File specDir = new File(portDir, specDirName);
                if (!specDir.mkdir()) {
                    throw new IOException("Can't create directory "
                            + specDir.getAbsolutePath());
                }
                settings.addString("port_spec_location", specDirName);
                if (spec != null) {
                    PortObjectSpecSerializer serializer =
                            getPortObjectSpecSerializer(spec.getClass());
                    serializer.savePortObjectSpec(spec, specDir);
                }
            }
            if (isSaveObject) {
                String objectDirName = null;
                objectDirName = "object";
                File objectDir = new File(portDir, objectDirName);
                if (!objectDir.mkdir()) {
                    throw new IOException("Can't create directory "
                            + objectDir.getAbsolutePath());
                }
                settings.addString("port_object_location", objectDirName);
                // object is BDT, but port type is not BDT.TYPE - still though..
                if (object instanceof BufferedDataTable) {
                    saveBufferedDataTable(
                            (BufferedDataTable)object, objectDir, exec);
                } else if (object instanceof ModelContent) {
                    saveModelContent((ModelContent)object, objectDir, exec);
                } else {
                    PortObjectSerializer serializer =
                        getPortObjectSerializer(object.getClass());
                    serializer.savePortObject(object, objectDir, exec);
                }
            }
        }
    }

    private void saveBufferedDataTable(final BufferedDataTable table,
            final File directory, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        table.save(directory, exec);
    }

    private void saveModelContent(final ModelContent content,
            final File directory, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        content.save(directory, exec);
    }
    
    protected void saveHasContent(final Node node, 
            final NodeSettingsWO settings) {
        boolean hasContent = node.hasContent();
        settings.addBoolean("hasContent", hasContent);
    }
    
    protected void saveNodeMessage(final Node node,
            final NodeSettingsWO settings) {
        NodeMessage message = node.getNodeMessage();
        if (message != null) {
            NodeSettingsWO sub = settings.addNodeSettings("node_message");
            sub.addString("type", message.getMessageType().name());
            sub.addString("message", message.getMessage());
        }
    }
    
    protected void saveNodeInternDirectory(final Node node,
            final File nodeInternDir, final NodeSettingsWO settings,
            final ExecutionMonitor exec) throws CanceledExecutionException {
        node.saveInternals(nodeInternDir, exec);
    }

    protected void saveCustomName(final Node node, final NodeSettingsWO settings) {
        settings.addString(CFG_NAME, node.getName());
    }
    
    /** {@inheritDoc} */
    @Override
    protected boolean loadIsExecuted(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return false;
    }
    
    /** {@inheritDoc} */
    @Override
    protected boolean loadHasContent(NodeSettingsRO settings)
            throws InvalidSettingsException {
        return settings.getBoolean("hasContent");
    }
    
    /** {@inheritDoc} */
    @Override
    protected boolean loadIsConfigured(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected void loadPorts(final Node node,
            final ExecutionMonitor exec, final NodeSettingsRO settings,
            final Map<Integer, BufferedDataTable> loadTblRep, final HashMap<Integer, ContainerTable> tblRep)
            throws IOException, InvalidSettingsException,
            CanceledExecutionException {
        if (node.getNrOutPorts() == 0 || node.isAutoExecutable()) {
            return;
        }
        final int portCount = node.getNrOutPorts();
        NodeSettingsRO portSettings = settings.getNodeSettings("ports");
        exec.setMessage("Reading outport data");
        for (String key : portSettings.keySet()) {
            NodeSettingsRO singlePortSetting =
                    portSettings.getNodeSettings(key);
            ExecutionMonitor subProgress =
                    exec.createSubProgress(1 / (double)portCount);
            int index = singlePortSetting.getInt("index");
            if (index < 0 || index >= node.getNrOutPorts()) {
                throw new InvalidSettingsException(
                        "Invalid outport index in settings: " + index);
            }
            String portDirN = singlePortSetting.getString("port_dir_location");
            if (portDirN != null) {
                ReferencedFile portDir = 
                    new ReferencedFile(getNodeDirectory(), portDirN);
                subProgress.setMessage("Port " + index);
                loadPort(node, portDir, singlePortSetting, 
                        subProgress, index, loadTblRep, tblRep);
            }
            subProgress.setProgress(1.0);
        }
    }

    protected void loadPort(final Node node, final ReferencedFile portDir,
            final NodeSettingsRO settings, final ExecutionMonitor exec,
            final int portIdx, final Map<Integer, BufferedDataTable> loadTblRep,
            final HashMap<Integer, ContainerTable> tblRep) throws IOException, 
            InvalidSettingsException, CanceledExecutionException {
        String specClass = settings.getString("port_spec_class");
        String objectClass = settings.getString("port_object_class");
        PortType designatedType = node.getOutputType(portIdx);
        PortObjectSpec spec = null;
        PortObject object = null;
        if (node.getOutputType(portIdx).equals(BufferedDataTable.TYPE)) {
            if (specClass != null && !specClass.equals(BufferedDataTable.TYPE.
                    getPortObjectSpecClass().getName())) {
                throw new IOException("Actual spec class \"" + specClass
                        + "\", expected \"" + BufferedDataTable.TYPE.
                        getPortObjectSpecClass().getName() + "\"");
            }
            if (objectClass != null && !objectClass.equals(
                    BufferedDataTable.TYPE.getPortObjectClass().getName())) {
                throw new IOException("Actual object class \"" + objectClass
                        + "\", expected \"" + BufferedDataTable.TYPE.
                            getPortObjectClass().getName() + "\"");
            }
            if (objectClass != null) {
                object = loadBufferedDataTable(portDir, exec, loadTblRep, tblRep);
                ((BufferedDataTable)object).setOwnerRecursively(node);
                spec = ((BufferedDataTable)object).getDataTableSpec();
            } else if (specClass != null) {
                spec = BufferedDataTable.loadSpec(portDir);
            }
        } else {        
            exec.setMessage("Loading specification");
            if (specClass != null) {
                Class<?> cl;
                try {
                    cl = GlobalClassCreator.createClass(specClass);
                } catch (ClassNotFoundException e) {
                    throw new IOException(
                            "Can't load class \"" + specClass + "\"", e);
                }
                if (!PortObjectSpec.class.isAssignableFrom(cl)) {
                    throw new IOException("Class \"" + cl.getSimpleName()
                            + "\" does not a sub-class \""
                            + PortObjectSpec.class.getSimpleName() + "\"");
                }
                ReferencedFile specDirRef = new ReferencedFile(
                        portDir, settings.getString("port_spec_location"));
                File specDir = specDirRef.getFile();
                if (!specDir.isDirectory()) {
                    throw new IOException("Can't read directory "
                            + specDir.getAbsolutePath());
                }
                PortObjectSpecSerializer serializer = 
                    getPortObjectSpecSerializer(
                            (Class<? extends PortObjectSpec>)cl);
                spec = serializer.loadPortObjectSpec(specDir);
            }
            if (objectClass != null) {
                Class<?> cl;
                try {
                    cl = GlobalClassCreator.createClass(objectClass);
                } catch (ClassNotFoundException e) {
                    throw new IOException("Can't load port object class \"" 
                            + objectClass + "\"", e);
                }
                if (!PortObject.class.isAssignableFrom(cl)) {
                    throw new IOException("Class \"" + cl.getSimpleName()
                            + "\" does not a sub-class \""
                            + PortObject.class.getSimpleName() + "\"");
                }
                ReferencedFile objectDirRef = new ReferencedFile(
                        portDir, settings.getString("port_object_location"));
                File objectDir = objectDirRef.getFile();
                if (!objectDir.isDirectory()) {
                    throw new IOException("Can't read directory "
                            + objectDir.getAbsolutePath());
                }
                if (BufferedDataTable.class.equals(cl)) {
                    // can't be true, however as BDT can only be saved
                    // for adequate port types (handled above)
                    // we leave the code here for future versions..
                    object = loadBufferedDataTable(
                            objectDirRef, exec, loadTblRep, tblRep);
                    ((BufferedDataTable)object).setOwnerRecursively(node);
                } else if (ModelContent.class.isAssignableFrom(cl)) {
                    object = loadModelContent(objectDirRef, exec, 
                            (Class<? extends ModelContent>)cl);
                } else {
                    PortObjectSerializer serializer =
                        getPortObjectSerializer(
                                (Class<? extends PortObject>)cl);
                    object = serializer.loadPortObject(objectDir, exec);
                }
            }
        }
        if (spec != null) {
            if (!designatedType.getPortObjectSpecClass().isInstance(spec)) {
                throw new IOException("Actual port spec type (\""
                        + spec.getClass().getSimpleName()
                        + "\") does not match designated one (\""
                        + designatedType.getPortObjectSpecClass()
                                .getSimpleName() + "\")");
            }
        }
        if (object != null) {
            if (!designatedType.getPortObjectClass().isInstance(object)) {
                throw new IOException("Actual port object type (\""
                        + object.getClass().getSimpleName()
                        + "\") does not match designated one (\""
                        + designatedType.getPortObjectClass()
                        .getSimpleName() + "\")");
            }
        }
        setPortObjectSpec(portIdx, spec);
        setPortObject(portIdx, object);
    }

    private BufferedDataTable loadBufferedDataTable(
            final ReferencedFile objectDir,
            final ExecutionMonitor exec, final Map<Integer, BufferedDataTable> loadTblRep,
            final HashMap<Integer, ContainerTable> tblRep)
            throws CanceledExecutionException, IOException,
            InvalidSettingsException {
        return BufferedDataTable.loadFromFile(objectDir, /* ignored in 1.2+ */
        null, exec, loadTblRep, tblRep);
    }

    private <T extends ModelContent> T loadModelContent(
            final ReferencedFile objectDir,
            final ExecutionMonitor exec, final Class<T> cl)
    throws CanceledExecutionException, IOException, InvalidSettingsException {
        T newInstance;
        try {
            Constructor<T> c;
            c = cl.getDeclaredConstructor();
            c.setAccessible(true);
            newInstance = c.newInstance();
        } catch (Exception e) {
            throw new IOException("Unable to call constructor on class \"" 
                    + cl.getSimpleName() + "\"", e);
        }
        newInstance.load(objectDir.getFile(), exec);
        return newInstance;
    }
    
    private static final Map<Class<? extends PortObjectSpec>, PortObjectSpecSerializer<?>> PORT_SPEC_SERIALIZER_MAP =
            new HashMap<Class<? extends PortObjectSpec>, PortObjectSpecSerializer<?>>();

    @SuppressWarnings("unchecked")
    // access to CLASS_TO_SERIALIZER_MAP
    static <T extends PortObjectSpec> PortObjectSpecSerializer<T> getPortObjectSpecSerializer(
            final Class<T> cl) {
        if (PORT_SPEC_SERIALIZER_MAP.containsKey(cl)) {
            return PortObjectSpecSerializer.class.cast(PORT_SPEC_SERIALIZER_MAP
                    .get(cl));
        }
        PortObjectSpecSerializer<T> result;
        try {
            result =
                    SerializerMethodLoader.getSerializer(cl,
                            PortObjectSpecSerializer.class,
                            "getPortObjectSpecSerializer");
        } catch (NoSuchMethodException e) {
            LOGGER.coding("Errors while accessing serializer object", e);
            throw new RuntimeException(e);
        }
        PORT_SPEC_SERIALIZER_MAP.put(cl, result);
        return result;
    }

    private static final Map<Class<? extends PortObject>, PortObjectSerializer<?>> PORT_OBJECT_SERIALIZER_MAP =
            new HashMap<Class<? extends PortObject>, PortObjectSerializer<?>>();

    @SuppressWarnings("unchecked")
    // access to CLASS_TO_SERIALIZER_MAP
    static <T extends PortObject> PortObjectSerializer<T> getPortObjectSerializer(
            final Class<T> cl) {
        if (PORT_OBJECT_SERIALIZER_MAP.containsKey(cl)) {
            return PortObjectSerializer.class.cast(PORT_OBJECT_SERIALIZER_MAP
                    .get(cl));
        }
        PortObjectSerializer<T> result;
        try {
            result =
                    SerializerMethodLoader.getSerializer(cl,
                            PortObjectSerializer.class,
                            "getPortObjectSerializer");
        } catch (NoSuchMethodException e) {
            LOGGER.coding("Errors while accessing serializer object", e);
            throw new RuntimeException(e);
        }
        PORT_OBJECT_SERIALIZER_MAP.put(cl, result);
        return result;
    }

}
