/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Mar 29, 2008 (wiswedel): created
 */
package org.knime.core.node.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.apache.commons.lang3.ArrayUtils;
import org.knime.core.node.FlowVariableModel;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.base.AbstractConfigEntry;
import org.knime.core.node.config.base.ConfigBase;
import org.knime.core.node.config.base.ConfigBaseRO;
import org.knime.core.node.config.base.ConfigBooleanEntry;
import org.knime.core.node.config.base.ConfigByteEntry;
import org.knime.core.node.config.base.ConfigCharEntry;
import org.knime.core.node.config.base.ConfigDoubleEntry;
import org.knime.core.node.config.base.ConfigEntries;
import org.knime.core.node.config.base.ConfigFloatEntry;
import org.knime.core.node.config.base.ConfigIntEntry;
import org.knime.core.node.config.base.ConfigLongEntry;
import org.knime.core.node.config.base.ConfigPasswordEntry;
import org.knime.core.node.config.base.ConfigShortEntry;
import org.knime.core.node.config.base.ConfigStringEntry;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.FlowVariable.Type;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.VariableType.BooleanArrayType;
import org.knime.core.node.workflow.VariableType.BooleanType;
import org.knime.core.node.workflow.VariableType.DoubleArrayType;
import org.knime.core.node.workflow.VariableType.DoubleType;
import org.knime.core.node.workflow.VariableType.IntArrayType;
import org.knime.core.node.workflow.VariableType.IntType;
import org.knime.core.node.workflow.VariableType.InvalidConfigEntryException;
import org.knime.core.node.workflow.VariableType.LongArrayType;
import org.knime.core.node.workflow.VariableType.LongType;
import org.knime.core.node.workflow.VariableType.StringArrayType;
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.core.node.workflow.VariableTypeRegistry;

/**
 * Config editor that keeps a mask of variables to overwrite existing settings.
 * This class is used to modify node settings with values assigned from
 * flow variables. It also keeps a list of &quot;exposed variables&quot;, that
 * is each individual setting can be exported as a new variable.
 * <p>This class is not meant to be used anywhere else than in the KNIME
 * framework classes.
 * @author Bernd Wiswedel, University of Konstanz
 */
@SuppressWarnings("serial")
public final class ConfigEditTreeModel extends DefaultTreeModel {

    /**
     * The version number is stored at the top of the variable settings tree. Version 2 introduced Boolean, Long, and
     * array flow variables. Since version 1 only supported types String, Int, and Double, back then Boolean settings
     * were exported as String variables and Long settings were exported as Int variables. With the new types of version
     * 2, we can do better. But for reasons of backwards compatibility, we have to stick with how we did it earlier if
     * we find no version (or version 1) in the variable settings.
     */
    private enum Version {
            /**
             * AP 4.0 and earlier - supports only flow variables of types String, Double, and Integer
             */
            V_2008_04_08(1),
            /**
             * AP 4.1 - introduction of Boolean, Long, and array flow variables
             */
            V_2019_09_13(2);

        private final int m_number;

        private Version(final int number) {
            m_number = number;
        }

        private int getNumber() {
            return m_number;
        }
    }

    private static final Version CURRENT_VERSION = Version.V_2019_09_13;

    private static final String VERSION_KEY = "version";

    private static final String TREE_KEY = "tree";

    private final CopyOnWriteArrayList<ConfigEditTreeEventListener> m_listeners;

    /** Factory method that parses the settings tree and constructs a new
     * object of this class. It will use the mask as given by the second
     * argument (which may be null, however).
     * @param settingsTree The original settings object.
     * @param variablesMask The variables mask.
     * @return a new object of this class.
     * @throws InvalidSettingsException If setting can't be parsed
     * @noreference This method is not intended to be referenced by clients.
     */
    public static ConfigEditTreeModel create(final ConfigBase settingsTree,
            final ConfigBaseRO variablesMask) throws InvalidSettingsException {
        // if we don't find the version number in the variable tree, we're reading a variable tree exported in version 1
        final Version version = Version.valueOf(variablesMask.getString(VERSION_KEY, Version.V_2008_04_08.name()));
        final ConfigBaseRO variableTree = version.equals(Version.V_2008_04_08) || !variablesMask.containsKey(TREE_KEY)
            ? variablesMask : variablesMask.getConfigBase(TREE_KEY);
        final ConfigEditTreeNode rootNode = new ConfigEditTreeNode(settingsTree, null, version);
        recursiveAdd(rootNode, settingsTree, variableTree);
        final ConfigEditTreeModel result = new ConfigEditTreeModel(rootNode);
        rootNode.setTreeModel(result); // allows event propagation
        result.getRoot().readVariablesFrom(variableTree, false);
        return result;
    }

    /** Parses a settings tree and creates an empty mask
     * (for later modification).
     * @param settingsTree to be parsed.
     * @return a new object of this class.
     */
    public static ConfigEditTreeModel create(final ConfigBase settingsTree) {
        // if we create an empty mask, we set the version number to the current version
        final ConfigEditTreeNode rootNode = new ConfigEditTreeNode(settingsTree, null, CURRENT_VERSION);
        recursiveAdd(rootNode, settingsTree, null);
        final ConfigEditTreeModel result = new ConfigEditTreeModel(rootNode);
        rootNode.setTreeModel(result); // allows event propagation
        return result;
    }

    /** Recursive construction of tree. */
    private static void recursiveAdd(final ConfigEditTreeNode treeNode, final ConfigBase configValue,
        final ConfigBaseRO variableValue) {

        assert (configValue == treeNode.getUserObject().m_configEntry);

        for (final String childKey : configValue.keySet()) {
            final AbstractConfigEntry childValue = configValue.getEntry(childKey);

            // determine the variable value (if any) of the child and whether the variable value has grandchildren
            ConfigBase childVariableValue = null;
            boolean childVariableValueHasGrandchildren = false;
            if (variableValue != null) {
                try {
                    childVariableValue = variableValue.getConfigBase(childKey);
                    for (final String variableKey : childVariableValue.keySet()) {
                        if (childVariableValue.getEntry(variableKey).getType() == ConfigEntries.config) {
                            childVariableValueHasGrandchildren = true;
                        }
                    }
                } catch (InvalidSettingsException e) {
                }
            }

            // determine the array subtype (if any) of the child
            // if the child is an array, don't expand on it further
            // instead interpret it as a leaf even though it is a config and actually has children
            // the only exception is if the child has descendants in the variable tree
            ConfigEntries childArraySubtype = null;
            if (!childVariableValueHasGrandchildren && childValue.getType() == ConfigEntries.config) {
                childArraySubtype = getChildArraySubtype(configValue, childKey);
            }

            // determine if the child is an internal config
            final boolean childIsInternals =
                !childVariableValueHasGrandchildren && childKey.endsWith(SettingsModel.CFGKEY_INTERNAL);

            // if the child is an internals config, don't add it to the tree and don't consider its children
            // the only exception is if the child has descendants in the variable tree
            if (!childIsInternals) {
                final ConfigEditTreeNode childTreeNode =
                    new ConfigEditTreeNode(childValue, childArraySubtype, treeNode.m_version);
                treeNode.add(childTreeNode);
                if (childValue.getType() == ConfigEntries.config && childArraySubtype == null) {
                    recursiveAdd(childTreeNode, (ConfigBase)childValue, childVariableValue);
                }
            }
        }
    }

    private static ConfigEntries getChildArraySubtype(final ConfigBase configValue, final String childKey) {
        if (configValue.getStringArray(childKey, (String[])null) != null) {
            return ConfigEntries.xstring;
        } else if (configValue.getCharArray(childKey, (char[])null) != null) {
            return ConfigEntries.xchar;
        } else if (configValue.getBooleanArray(childKey, (boolean[])null) != null) {
            return ConfigEntries.xboolean;
        } else if (configValue.getByteArray(childKey, (byte[])null) != null) {
            return ConfigEntries.xbyte;
        } else if (configValue.getShortArray(childKey, (short[])null) != null) {
            return ConfigEntries.xshort;
        } else if (configValue.getIntArray(childKey, (int[])null) != null) {
            return ConfigEntries.xint;
        } else if (configValue.getLongArray(childKey, (long[])null) != null) {
            return ConfigEntries.xlong;
        } else if (configValue.getFloatArray(childKey, (float[])null) != null) {
            return ConfigEntries.xfloat;
        } else if (configValue.getDoubleArray(childKey, (double[])null) != null) {
            return ConfigEntries.xdouble;
        } else {
            return null;
        }
    }

    /**
     * Determines whether a flow variable at hand (represented by its
     * actual type) can be converted into a desired type. In short: string
     * accepts also double and integer, double accepts integer, integer only
     * accepts integer.
     *
     * @param desiredType The type that is requested.
     * @param actualType The actual type.
     * @return If a flow variable of type <code>actualType</code> can be
     * used to represent a flow variable of type <code>desiredType</code>.
     */
    @Deprecated
    public static boolean doesTypeAccept(final Type desiredType,
            final Type actualType) {
        if (desiredType == null || actualType == null) {
            throw new NullPointerException("Arguments must not be null");
        }
        switch (desiredType) {
            case STRING:
                return true;
            case DOUBLE:
                return Type.DOUBLE == actualType || Type.INTEGER == actualType;
            case INTEGER:
                return Type.INTEGER == actualType;
            case CREDENTIALS:
                return false;
            case OTHER:
                return false;
            default:
                assert false : "unknown type: " + desiredType;
                return false;
        }
    }

    /** @param rootNode root node. */
    private ConfigEditTreeModel(final ConfigEditTreeNode rootNode) {
        super(rootNode);
        m_listeners = new CopyOnWriteArrayList<>();
    }

    /** @return true if there is any mask (overwriting settings) below this
     * branch of the tree. */
    public boolean hasConfiguration() {
        return getRoot().hasConfiguration();
    }

    /** @return name of user parameters that are overwritten by a variable
     * (= combo boxes that have a variable selected). This is then
     * indicated in the status bar of the node dialog.
     */
    public Set<String> getVariableControlledParameters() {
        Set<String> result = new LinkedHashSet<>();
        getRoot().addVariableControlledParameters(result);
        return result;
    }

    /** Write the mask to a config object (for storage in node settings object).
     * @param config To save to. */
    public void writeVariablesTo(final ConfigBase config) {
        // false = do not write "model" as root
        getRoot().writeVariablesTo(config, false);
    }

    /** Modifies the first argument to reflect the values of the mask
     * represented by this object.
     * @param settingsTree settings tree to modify (supposed to have
     * equivalent tree structure)
     * @param variables The map of variables-values to apply.
     * @return A list of exposed variables
     * @throws InvalidSettingsException If reading fails
     */
    public List<FlowVariable> overwriteSettings(final Config settingsTree,
            final Map<String, FlowVariable> variables)
        throws InvalidSettingsException {
        return getRoot().overwriteSettings(settingsTree, variables, false);
    }

    /** {@inheritDoc} */
    @Override
    public ConfigEditTreeNode getRoot() {
        return (ConfigEditTreeNode)super.getRoot();
    }

    /** Get the child tree node associated with the key path.
     * Returns null if there is no child.
     * @param keyPath The path the child.
     * @return the child (or a children's child) for the given key path.
     */
    public ConfigEditTreeNode findChildForKeyPath(final String[] keyPath) {
        ConfigEditTreeNode current = getRoot();
        for (String key : keyPath) {
            ConfigEditTreeNode newCurrent = null;
            for (Enumeration<?> e = current.children(); e.hasMoreElements();) {
                ConfigEditTreeNode c = (ConfigEditTreeNode)e.nextElement();
                if (c.getConfigEntry().getKey().equals(key)) {
                    newCurrent = c;
                    break;
                }
            }
            if (newCurrent == null) {
                return null;
            }
            current = newCurrent;
        }
        return current;
    }

    /** Updates this tree with the settings available in the argument list. This
     * becomes necessary if a dialog provides its "expert" settings also via
     * small button attached to different controls. This method ensures that the
     * tree and the button model are in sync.
     * @param variableModels The models that were registered at the dialog.
     */
    public void update(final Collection<FlowVariableModel> variableModels) {
        ConfigEditTreeNode rootNode = getRoot();
        for (FlowVariableModel model : variableModels) {
            rootNode.update(model);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getRoot().toString();
    }

    /** Adds new listener.
     * @param listener to be added.
     */
    public void addConfigEditTreeEventListener(
            final ConfigEditTreeEventListener listener) {
        m_listeners.add(listener);
    }

    /** Removes a registered listener.
     * @param listener to be removed.
     */
    public void removeConfigEditTreeEventListener(
            final ConfigEditTreeEventListener listener) {
        m_listeners.remove(listener);
    }

    /** Fire an event.
     * @param treePath The tree path that has changed.
     * @param useVariable The new variable, which overwrites the user settings.
     * @param exposeVariableName The variable name to expose.
     */
    void fireConfigEditTreeEvent(final String[] treePath,
            final String useVariable, final String exposeVariableName) {
        ConfigEditTreeEvent event = new ConfigEditTreeEvent(
                this, treePath, useVariable, exposeVariableName);
        for (ConfigEditTreeEventListener l : m_listeners) {
            l.configEditTreeChanged(event);
        }
    }


    /** Single Tree node implementation. */
    public static final class ConfigEditTreeNode extends DefaultMutableTreeNode {
        private final ConfigEntries m_arraySubType;

        private final Version m_version;

        /** The tree model, which is null for all nodes except for the root.
         * It is set after the tree nodes are constructed. Used to propagate
         * events. */
        private ConfigEditTreeModel m_treeModel;

        /**
         * Constructs new tree node based on a representative config entry.
         *
         * @param entry to wrap
         * @param arraySubType the array type of this node, possibly null
         * @param the version number of this tree node
         */
        ConfigEditTreeNode(final AbstractConfigEntry entry, final ConfigEntries arraySubType, final Version version) {
            super(new Wrapper(entry, !(entry instanceof Config) || arraySubType != null));
            m_arraySubType = arraySubType;
            m_version = version;
            setAllowsChildren(!getUserObject().isLeaf());
        }

        /**
         * @param treeModel the treeModel to set
         */
        void setTreeModel(final ConfigEditTreeModel treeModel) {
            m_treeModel = treeModel;
        }

        /** {@inheritDoc} */
        @Override
        public void setUserObject(final Object arg) {
            throw new IllegalStateException("Not intended to be called");
        }

        /** {@inheritDoc} */
        @Override
        public Wrapper getUserObject() {
            return (Wrapper)super.getUserObject();
        }

        /** {@inheritDoc} */
        @Override
        public ConfigEditTreeNode getRoot() {
            return (ConfigEditTreeNode)super.getRoot();
        }

        /** @return associated config entry. */
        public AbstractConfigEntry getConfigEntry() {
            return getUserObject().m_configEntry;
        }

        Optional<ConfigEntries> getArraySubType() {
            return Optional.ofNullable(m_arraySubType);
        }

        /** @param value the new variable to use. */
        public void setUseVariableName(final String value) {
            String newValue = value;
            if (value == null || value.length() == 0) {
                newValue = null;
            }
            if (!Objects.equals(
                    getUserObject().m_useVarName, newValue)) {
                getUserObject().m_useVarName = value;
                fireEvent();
            }
        }

        /** @return the new variable to use. */
        public String getUseVariableName() {
            return getUserObject().m_useVarName;
        }

        /** @param variableName The name of the variable, which represents this
         * node's value. */
        public void setExposeVariableName(final String variableName) {
            String newValue = variableName;
            if (variableName == null || variableName.length() == 0) {
                newValue = null;
            }
            if (!Objects.equals(getUserObject().m_exposeVarName,
                    newValue)) {
                getUserObject().m_exposeVarName = newValue;
                fireEvent();
            }
        }

        /** @return the exported variable name. */
        public String getExposeVariableName() {
            return getUserObject().m_exposeVarName;
        }

        private void fireEvent() {
            TreeNode[] path = getPath();
            String[] keyPath = new String[path.length - 1];
            for (int i = 0; i < keyPath.length; i++) {
                ConfigEditTreeNode node = (ConfigEditTreeNode)path[i + 1];
                keyPath[i] = node.getConfigEntry().getKey();
            }
            if (getRoot().m_treeModel != null) {
                getRoot().m_treeModel.fireConfigEditTreeEvent(
                        keyPath, getUseVariableName(), getExposeVariableName());
            }
        }

        /** Implementation of {@link ConfigEditTreeModel#hasConfiguration()}.
         * @return if mask exists in this node or any child node. */
        public boolean hasConfiguration() {
            if (getUserObject().isLeaf()) {
                return isOverwrittenByVariable()
                || getExposeVariableName() != null;
            }
            for (Enumeration<?> e = children(); e.hasMoreElements();) {
                ConfigEditTreeNode c = (ConfigEditTreeNode)e.nextElement();
                if (c.hasConfiguration()) {
                    return true;
                }
            }
            return false;
        }

        /** Implementation of
         * {@link ConfigEditTreeModel#getVariableControlledParameters()}.
         * @param toAdd List to add to. */
        public void addVariableControlledParameters(final Set<String> toAdd) {
            if (getUserObject().isLeaf()) {
                if (isOverwrittenByVariable()) {
                    toAdd.add(getUserObject().getKey());
                }
            } else {
                for (Enumeration<?> e = children(); e.hasMoreElements();) {
                    ConfigEditTreeNode c = (ConfigEditTreeNode)e.nextElement();
                    c.addVariableControlledParameters(toAdd);
                }
            }
        }

        /**
         * @return <code>true</code> if the associated setting is overwritten by a flow variable
         */
        private boolean isOverwrittenByVariable() {
            return getUseVariableName() != null;
        }

        /** Implements the functionality described in the
         * {@link ConfigEditTreeModel#update(FlowVariableModel)} method.
         * @param model The model that provides the update.
         */
        void update(final FlowVariableModel model) {
            // TODO couldn't this be implemented in ConfigEditTreeModel as
            // ConfigEditTreeNode node = findChildForKeyPath(model.getKeys());
            // if (node != null) {
            //      node.setUseVariableName(model.getInputVariableName());
            //      node.setUseExposeName(model.getOutputVariableName());
            Enumeration<?> e = children();
            int k = 0;
            while (e.hasMoreElements() && (k < model.getKeys().length)) {
                ConfigEditTreeNode c = (ConfigEditTreeNode)e.nextElement();
                if (c.getConfigEntry().getKey().equals(model.getKeys()[k])) {
                    if ((k + 1) == model.getKeys().length) {
                        // reached last entry of hierarchy: apply settings
                        c.setUseVariableName(model.getInputVariableName());
                        c.setExposeVariableName(model.getOutputVariableName());
                    } else {
                        // dive deeper into hierarchy of keys
                        k++;
                        e = c.children();
                    }
                }
            }
        }

        private static final String CFG_USED_VALUE = "used_variable";
        private static final String CFG_EXPOSED_VALUE = "exposed_variable";

        /** Persistence method to restore the tree. */
        private void readVariablesFrom(final ConfigBaseRO variableTree,
                final boolean readThisEntry) throws InvalidSettingsException {
            ConfigBaseRO subConfig;
            if (readThisEntry) {
                String key = getConfigEntry().getKey();
                if (!variableTree.containsKey(key)) {
                    return;
                }
                subConfig = variableTree.getConfigBase(key);
            } else {
                subConfig = variableTree;
            }
            if (getUserObject().isLeaf()) {
                setUseVariableName(subConfig.getString(CFG_USED_VALUE));
                setExposeVariableName(subConfig.getString(CFG_EXPOSED_VALUE));
            } else {
                for (Enumeration<?> e = children(); e.hasMoreElements();) {
                    ConfigEditTreeNode c = (ConfigEditTreeNode)e.nextElement();
                    c.readVariablesFrom(subConfig, true);
                }
            }
        }

        /** Persistence method to save the tree. */
        private void writeVariablesTo(final ConfigBase variableTree,
                final boolean writeThisEntry) {
            if (!hasConfiguration()) {
                return;
            }
            ConfigBase subConfig;
            if (writeThisEntry) {
                String key = getConfigEntry().getKey();
                subConfig = variableTree.addConfigBase(key);
            } else {
                if (m_version == Version.V_2008_04_08) {
                    subConfig = variableTree;
                } else {
                    variableTree.addString(VERSION_KEY, m_version.name());
                    subConfig = variableTree.addConfigBase(TREE_KEY);
                }
            }
            if (getUserObject().isLeaf()) {
                subConfig.addString(CFG_USED_VALUE, getUseVariableName());
                subConfig.addString(CFG_EXPOSED_VALUE, getExposeVariableName());
            } else {
                for (Enumeration<?> e = children(); e.hasMoreElements();) {
                    ConfigEditTreeNode c = (ConfigEditTreeNode)e.nextElement();
                    c.writeVariablesTo(subConfig, true);
                }
            }
        }

        /** Implementation of {@link ConfigEditTreeModel#overwriteSettings(
         * Config, Map)}, see above method description for details. */
        private List<FlowVariable> overwriteSettings(final Config counterpart,
                final Map<String, FlowVariable> variables,
                final boolean isCounterpartParent)
            throws InvalidSettingsException {
            if (!hasConfiguration()) {
                return Collections.emptyList();
            }
            List<FlowVariable> result = null;
            AbstractConfigEntry thisEntry = getConfigEntry();
            String key = thisEntry.getKey();
            final AbstractConfigEntry original;
            if (isCounterpartParent) {
                original = counterpart.getEntry(key);
            } else {
                original = counterpart;
            }
            CheckUtils.checkSetting(original != null, "No matching element found for entry with key: %s", key);
            @SuppressWarnings("null") // the above check ensures that original is not null
            ConfigEntries originalType = original.getType();
            CheckUtils.checkSetting(originalType == thisEntry.getType(),
                "Non matching config elements for key \"%s\", %s vs. %s", key, originalType, thisEntry.getType());
            if (isOverwrittenByVariable()) {
//                overwriteWithVariable(counterpart, variables, key, originalType);
                overwriteWithVariableNew(counterpart, variables, key);
            }
            String newVar = getExposeVariableName();
            if (newVar != null) {
                assert isLeaf() && isCounterpartParent;
                result = getExposedVariables(counterpart, key, newVar);
            }

            if (!isLeaf()) {
                result = recursivelyOverwriteSettingsInChildren(variables, result, original);
            }
            if (result == null) {
                result = Collections.emptyList();
            }
            return result;
        }

        private void overwriteWithVariableNew(final Config counterpart, final Map<String, FlowVariable> variables,
            final String key) throws InvalidSettingsException {
            final String varName = getUseVariableName();
            try {
                VariableTypeRegistry.overwriteWithVariable(counterpart, key,
                    getVariable(varName, variables));
            } catch (InvalidConfigEntryException ex) {
                throw new InvalidSettingsException(ex.getErrorMessageWithVariableName(varName).orElse(ex.getMessage()),
                    ex);
            }
        }

        private void overwriteWithVariable(final Config counterpart, final Map<String, FlowVariable> variables,
            final String key, final ConfigEntries originalType) throws InvalidSettingsException {
            final String varName = getUseVariableName();
            switch (originalType) {
                case xboolean:
                    overwriteBoolean(counterpart, variables, key, varName);
                    break;
                case xchar:
                    overwriteChar(counterpart, variables, key, varName);
                    break;
                case xtransientstring:
                    counterpart.addTransientString(key, getStringVariable(varName, variables));
                    break;
                case xstring:
                    counterpart.addString(key, getStringVariable(varName, variables));
                    break;
                case xlong:
                    counterpart.addLong(key, getLongVariable(varName, variables));
                    break;
                case xint:
                case xshort:
                case xbyte:
                    overwriteShortIntByte(counterpart, variables, key, originalType, varName);
                    break;
                case xfloat:
                    counterpart.addFloat(key, (float)getDoubleVariable(varName, variables));
                    break;
                case xdouble:
                    counterpart.addDouble(key, getDoubleVariable(varName, variables));
                    break;
                case config:
                    if (m_arraySubType != null) {
                        overwriteArray(counterpart, variables, key, originalType, varName);
                        break;
                    }
                default:
                    assert false : "Unreachable case: " + originalType;
            }
        }

        private void overwriteArray(final Config counterpart, final Map<String, FlowVariable> variables,
            final String key, final ConfigEntries originalType, final String varName) throws InvalidSettingsException {
            switch (m_arraySubType) {
                case xbyte:
                case xshort:
                case xint:
                    // TODO is it actually valid to overwrite a byte/short array with an int array?
                    counterpart.addIntArray(key, getIntArrayVariable(varName, variables));
                    break;
                case xlong:
                    counterpart.addLongArray(key, getLongArrayVariable(varName, variables));
                    break;
                case xfloat:
                case xdouble:
                    // TODO is it actually valid to overwrite a float array with a double array?
                    counterpart.addDoubleArray(key, getDoubleArrayVariable(varName, variables));
                    break;
                case xboolean:
                    counterpart.addBooleanArray(key, getBooleanArrayVariable(varName, variables));
                    break;
                case xchar:
                case xstring:
                    // TODO is it actually valid to overwrite a char array with a string array?
                    counterpart.addStringArray(key, getStringArrayVariable(varName, variables));
                    break;
                default:
                    assert false : "Unreachable case: " + originalType;
            }
        }

        private static void overwriteShortIntByte(final Config counterpart, final Map<String, FlowVariable> variables,
            final String key, final ConfigEntries originalType, final String varName) throws InvalidSettingsException {
            final int value = getIntVariable(varName, variables);
            switch (originalType) {
                case xint:
                    counterpart.addInt(key, value);
                    break;
                case xshort:
                    // FIXME why is the value overwritten before we check if that is even possible?
                    counterpart.addShort(key, (short)value);
                    CheckUtils.checkSetting(value >= Short.MIN_VALUE && value <= Short.MAX_VALUE,
                        "Value of variable \"%s\" can't be cast to %s(settings parameter \"%s\")"
                            + ", out of range: %s",
                        varName, originalType, key, value);
                    break;
                case xbyte:
                    // FIXME why is the value overwritten before we check if that is even possible?
                    counterpart.addByte(key, (byte)value);
                    CheckUtils.checkSetting(value >= Byte.MIN_VALUE || value > Byte.MAX_VALUE,
                        "Value of variable \"%s\" can't be cast to %s(settings parameter \"%s\"), out of range: %s",
                        varName, originalType, key, value);
                    break;
                default:
                    assert false : "Unreachable case";
            }
        }

        private static void overwriteChar(final Config counterpart, final Map<String, FlowVariable> variables,
            final String key, final String varName) throws InvalidSettingsException {
            final String charS = getStringVariable(varName, variables);
            CheckUtils.checkSetting(charS != null && charS.length() == 1,
                "Unable to parse \"%s\" (variable \"%s\") as char (settings parameter \"%s\")", charS, varName, key);
            @SuppressWarnings("null") // the above check ensures that charS is not null and has at least one character
            char charAt = charS.charAt(0);
            counterpart.addChar(key, charAt);
        }

        private static void overwriteBoolean(final Config counterpart, final Map<String, FlowVariable> variables,
            final String key, final String varName) throws InvalidSettingsException {
            if (getVariable(varName, variables).getVariableType() == StringType.INSTANCE) {
                final String bool = getStringVariable(varName, variables);
                CheckUtils.checkSetting(bool != null, "Value of \"%s\" is null", varName);
                @SuppressWarnings("null") // the above check ensures the bool is not null
                final String lowerCaseBool = bool.toLowerCase();
                CheckUtils.checkSetting(lowerCaseBool.equals("true") || lowerCaseBool.equals("false"),
                    "Unable to parse \"%s\" (variable \"%s\") as boolean expression (settings parameter \"%s\")",
                    lowerCaseBool, varName, key);
                counterpart.addBoolean(key, Boolean.parseBoolean(bool));
            } else {
                counterpart.addBoolean(key, getBooleanVariable(varName, variables));
            }
        }

        private List<FlowVariable> getExposedVariables(final Config counterpart,
            final String key, final String newVar) throws InvalidSettingsException {
//            FlowVariable exposed = resolveExposedVariable(counterpart, key, newVar, newValue);
            FlowVariable exposed = resolveExposedVariableNew(counterpart, key, newVar);
            final List<FlowVariable> result = new ArrayList<>();
            result.add(exposed);
            return result;
        }

        private FlowVariable resolveExposedVariableNew(final Config counterpart, final String key, final String newVar)
            throws InvalidSettingsException {
            final AbstractConfigEntry newValue = counterpart.getEntry(key);
            final ConfigEntries configType = newValue.getType();
            if (m_version == Version.V_2008_04_08 && configType == ConfigEntries.xboolean) {
                return new FlowVariable(newVar, Boolean.toString(((ConfigBooleanEntry)newValue).getBoolean()));
            } else if (m_version == Version.V_2008_04_08 && configType == ConfigEntries.xlong) {
                final long l = ((ConfigLongEntry)newValue).getLong();
                if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                    throw new InvalidSettingsException(
                        "Can't export value \"" + l + "\" as "
                                + "variable \"" + newVar + "\", out of range");
                }
                return new FlowVariable(newVar, (int)l);
            } else {
                try {
                    return VariableTypeRegistry.getInstance().createFromConfig(newVar, counterpart, key);
                } catch (InvalidConfigEntryException ex) {
                    throw new InvalidSettingsException(
                        ex.getErrorMessageWithVariableName(newVar).orElse(ex.getMessage()), ex);
                }
            }
        }

        private FlowVariable resolveExposedVariable(final Config counterpart, final String key, final String newVar,
            final AbstractConfigEntry newValue) throws InvalidSettingsException {
            switch (newValue.getType()) {
            case xboolean:
                    return exposeBoolean(newVar, newValue);
            case xstring:
                String s = ((ConfigStringEntry)newValue).getString();
                return new FlowVariable(newVar, s);
            case xchar:
                char c = ((ConfigCharEntry)newValue).getChar();
                return new FlowVariable(newVar, Character.toString(c));
            case xbyte:
                byte by = ((ConfigByteEntry)newValue).getByte();
                return new FlowVariable(newVar, by);
            case xshort:
                short sh = ((ConfigShortEntry)newValue).getShort();
                return new FlowVariable(newVar, sh);
            case xint:
                int i = ((ConfigIntEntry)newValue).getInt();
                return new FlowVariable(newVar, i);
            case xlong:
                    return exposeLong(newVar, newValue);
            case xfloat:
                float f = ((ConfigFloatEntry)newValue).getFloat();
                return new FlowVariable(newVar, f);
            case xdouble:
                double d = ((ConfigDoubleEntry)newValue).getDouble();
                return new FlowVariable(newVar, d);
            case xpassword:
                String pass = ((ConfigPasswordEntry)newValue).getPassword();
                return new FlowVariable(newVar, pass);
            case config:
                    return exposeArray(counterpart, key, newVar, newValue);
            default:
                    throw new InvalidSettingsException(
                        String.format("Can't export %s as variable \"%s\"", newValue.getType(), newVar));
            }
        }

        private FlowVariable exposeArray(final Config counterpart, final String key, final String newVar,
            final AbstractConfigEntry newValue) throws InvalidSettingsException {
            if (m_arraySubType != null) {
                switch (m_arraySubType) {
                    case xstring:
                        final String[] strings = counterpart.getStringArray(key);
                        return new FlowVariable(newVar, StringArrayType.INSTANCE, strings);
                    case xchar:
                        final char[] chars = counterpart.getCharArray(key);
                        final String[] charsAsStrings = IntStream.range(0, chars.length)//
                                .mapToObj(idx -> Character.toString(chars[idx]))//
                                .toArray(String[]::new);
                        return new FlowVariable(newVar, StringArrayType.INSTANCE, charsAsStrings);
                    case xboolean:
                        final Boolean[] booleans = ArrayUtils.toObject(counterpart.getBooleanArray(key));
                        return new FlowVariable(newVar, BooleanArrayType.INSTANCE, booleans);
                    case xbyte:
                        final byte[] bytes = counterpart.getByteArray(key);
                        final Integer[] bytesAsInts = IntStream.range(0, bytes.length)//
                                .mapToObj(idx -> Integer.valueOf(bytes[idx]))//
                                .toArray(Integer[]::new);
                        return new FlowVariable(newVar, IntArrayType.INSTANCE, bytesAsInts);
                    case xshort:
                        final short[] shorts = counterpart.getShortArray(key);
                        final Integer[] shortsAsInts = IntStream.range(0, shorts.length)//
                                .mapToObj(idx -> Integer.valueOf(shorts[idx]))//
                                .toArray(Integer[]::new);
                        return new FlowVariable(newVar, IntArrayType.INSTANCE, shortsAsInts);
                    case xint:
                        final Integer[] ints = ArrayUtils.toObject(counterpart.getIntArray(key));
                        return new FlowVariable(newVar, IntArrayType.INSTANCE, ints);
                    case xlong:
                        final Long[] longs = ArrayUtils.toObject(counterpart.getLongArray(key));
                        return new FlowVariable(newVar, LongArrayType.INSTANCE, longs);
                    case xfloat:
                        final float[] floats = counterpart.getFloatArray(key);
                        final Double[] floatsAsDoubles = IntStream.range(0, floats.length)//
                                .mapToObj(idx -> Double.valueOf(floats[idx]))//
                                .toArray(Double[]::new);
                        return new FlowVariable(newVar, DoubleArrayType.INSTANCE, floatsAsDoubles);
                    case xdouble:
                        final Double[] doubles = ArrayUtils.toObject(counterpart.getDoubleArray(key));
                        return new FlowVariable(newVar, DoubleArrayType.INSTANCE, doubles);
                    default:
                            throw new InvalidSettingsException(
                                String.format("Can't export %s with array subtype %s as variable \"%s\"",
                                    newValue.getType(), m_arraySubType, newVar));
                }
            } else {
                throw new InvalidSettingsException(
                    String.format("Can't export %s as variable \"%s\"", newValue.getType(), newVar));
            }
        }

        private FlowVariable exposeLong(final String newVar, final AbstractConfigEntry newValue)
            throws InvalidSettingsException {
            long l = ((ConfigLongEntry)newValue).getLong();
            switch (m_version) {
                case V_2008_04_08:
                    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                        throw new InvalidSettingsException(
                            "Can't export value \"" + l + "\" as "
                                    + "variable \"" + newVar + "\", out of range");
                    }
                    return new FlowVariable(newVar, (int)l);
                case V_2019_09_13:
                default:
                    return new FlowVariable(newVar, LongType.INSTANCE, l);
            }
        }

        private FlowVariable exposeBoolean(final String newVar, final AbstractConfigEntry newValue) {
            boolean b = ((ConfigBooleanEntry)newValue).getBoolean();
            switch (m_version) {
                case V_2008_04_08:
                    return new FlowVariable(newVar, Boolean.toString(b));
                case V_2019_09_13:
                default:
                    return new FlowVariable(newVar, BooleanType.INSTANCE, b);
            }
        }

        private List<FlowVariable> recursivelyOverwriteSettingsInChildren(final Map<String, FlowVariable> variables,
            List<FlowVariable> result, final AbstractConfigEntry original) throws InvalidSettingsException {
            for (Enumeration<?> e = children(); e.hasMoreElements();) {
                ConfigEditTreeNode c = (ConfigEditTreeNode)e.nextElement();
                List<FlowVariable> r = c.overwriteSettings(
                        (Config)original, variables, true);
                if (!r.isEmpty()) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.addAll(r);
                }
            }
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            toString(b, "");
            return b.toString();
        }

        /** Recursion method to get a string representation of this tree.
         * @param b to append to
         * @param indent indentation.
         */
        public void toString(final StringBuilder b, final String indent) {
            boolean edited = isOverwrittenByVariable()
                || getExposeVariableName() != null;
            String thisEntry = getUserObject().toString() + (edited ? "*" : "");
            b.append(indent + thisEntry + "\n");
            for (Enumeration<?> e = children(); e.hasMoreElements();) {
                ConfigEditTreeNode c = (ConfigEditTreeNode)e.nextElement();
                c.toString(b, indent + "  ");
            }
        }

        /** Getter method throws exception if variable name does not exist
         * in map. */
        private static FlowVariable getVariable(final String varString,
                final Map<String, FlowVariable> variables)
            throws InvalidSettingsException {
            FlowVariable var = variables.get(varString);
            CheckUtils.checkSetting(var != null, "Unknown variable \"%s\"", varString);
            return var;
        }

        private static boolean getBooleanVariable(final String varString, final Map<String, FlowVariable> variables)
            throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(BooleanType.INSTANCE)) {
                return v.getValue(BooleanType.INSTANCE);
            } else {
                throw createIncompatibleTypeException(varString, v, type, "boolean");
            }
        }

        private static boolean[] getBooleanArrayVariable(final String varString,
            final Map<String, FlowVariable> variables) throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(BooleanArrayType.INSTANCE)) {
                return ArrayUtils.toPrimitive(v.getValue(BooleanArrayType.INSTANCE));
            } else if (type.equals(BooleanType.INSTANCE)) {
                return new boolean[]{v.getValue(BooleanType.INSTANCE)};
            } else {
                throw createIncompatibleTypeException(varString, v, type, "boolean array");
            }
        }

        private static InvalidSettingsException createIncompatibleTypeException(final String varString,
            final FlowVariable v, final VariableType<?> type, final String targetType) {
            return new InvalidSettingsException(
                String.format("Can't evaluate variable \"%s\" as %s expression, it is a %s (\"%s\")", varString,
                    targetType, type, v));
        }


        /** Getter method to get double value. */
        private static double getDoubleVariable(final String varString, final Map<String, FlowVariable> variables)
            throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(DoubleType.INSTANCE)) {
                return v.getValue(DoubleType.INSTANCE);
            } else if (type.equals(LongType.INSTANCE)) {
                return v.getValue(LongType.INSTANCE);
            } else if (type.equals(IntType.INSTANCE)) {
                return v.getValue(IntType.INSTANCE);
            } else {
                throw createIncompatibleTypeException(varString, v, type, "double");
            }
        }

        private static double[] getDoubleArrayVariable(final String varString,
            final Map<String, FlowVariable> variables) throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(DoubleArrayType.INSTANCE)) {
                return ArrayUtils.toPrimitive(v.getValue(DoubleArrayType.INSTANCE));
            } else if (type.equals(LongArrayType.INSTANCE)) {
                return Arrays.stream(v.getValue(LongArrayType.INSTANCE)).mapToDouble(Long::doubleValue)
                    .toArray();
            } else if (type.equals(IntArrayType.INSTANCE)) {
                return Arrays.stream(v.getValue(IntArrayType.INSTANCE)).mapToDouble(Integer::doubleValue)
                    .toArray();
            } else if (type.equals(DoubleType.INSTANCE)) {
                return new double[]{v.getDoubleValue()};
            } else if (type.equals(LongType.INSTANCE)) {
                return new double[]{v.getValue(LongType.INSTANCE)};
            } else if (type.equals(IntType.INSTANCE)) {
                return new double[]{v.getIntValue()};
            } else {
                throw createIncompatibleTypeException(varString, v, type, "double array");
            }
        }

        /** Getter method to get double value. */
        private static long getLongVariable(final String varString, final Map<String, FlowVariable> variables)
            throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(LongType.INSTANCE)) {
                return v.getValue(LongType.INSTANCE);
            } else if (type.equals(IntType.INSTANCE)) {
                return v.getValue(IntType.INSTANCE);
            } else {
                throw createIncompatibleTypeException(varString, v, type, "long");
            }
        }

        private static long[] getLongArrayVariable(final String varString, final Map<String, FlowVariable> variables)
            throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(LongArrayType.INSTANCE)) {
                return ArrayUtils.toPrimitive(v.getValue(LongArrayType.INSTANCE));
            } else if (type.equals(IntArrayType.INSTANCE)) {
                return Arrays.stream(v.getValue(IntArrayType.INSTANCE)).mapToLong(Integer::longValue)
                    .toArray();
            } else if (type.equals(LongType.INSTANCE)) {
                return new long[]{v.getValue(LongType.INSTANCE)};
            } else if (type.equals(IntType.INSTANCE)) {
                return new long[]{v.getIntValue()};
            } else {
                throw createIncompatibleTypeException(varString, v, type, "long array");
            }
        }

        /** Getter method to get int value. */
        private static int getIntVariable(final String varString, final Map<String, FlowVariable> variables)
            throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(IntType.INSTANCE)) {
                return v.getValue(IntType.INSTANCE);
            } else {
                throw createIncompatibleTypeException(varString, v, type, "integer");
            }
        }

        private static int[] getIntArrayVariable(final String varString, final Map<String, FlowVariable> variables)
            throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(IntArrayType.INSTANCE)) {
                return ArrayUtils.toPrimitive(v.getValue(IntArrayType.INSTANCE));
            } else if (type.equals(IntType.INSTANCE)) {
                return new int[]{v.getIntValue()};
            } else {
                throw createIncompatibleTypeException(varString, v, type, "integer array");
            }
        }

        /** Getter method to get string value. */
        private static String getStringVariable(final String varString, final Map<String, FlowVariable> variables)
            throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(StringType.INSTANCE) || type.equals(BooleanType.INSTANCE) || type.equals(IntType.INSTANCE)
                || type.equals(LongType.INSTANCE) || type.equals(DoubleType.INSTANCE)) {
                return v.getValueAsString();
            } else {
                throw createIncompatibleTypeException(varString, v, type, "string");
            }
        }

        private static String[] getStringArrayVariable(final String varString,
            final Map<String, FlowVariable> variables) throws InvalidSettingsException {
            final FlowVariable v = getVariable(varString, variables);
            final VariableType<?> type = v.getVariableType();
            if (type.equals(StringArrayType.INSTANCE)) {
                return v.getValue(StringArrayType.INSTANCE);
            } else if (type.equals(BooleanArrayType.INSTANCE)) {
                return Arrays.stream(v.getValue(BooleanArrayType.INSTANCE)).map(b -> Boolean.toString(b))
                    .toArray(String[]::new);
            } else if (type.equals(DoubleArrayType.INSTANCE)) {
                return Arrays.stream(v.getValue(DoubleArrayType.INSTANCE)).map(d -> Double.toString(d))
                    .toArray(String[]::new);
            } else if (type.equals(LongArrayType.INSTANCE)) {
                return Arrays.stream(v.getValue(LongArrayType.INSTANCE)).map(l -> Long.toString(l))
                    .toArray(String[]::new);
            } else if (type.equals(IntArrayType.INSTANCE)) {
                return Arrays.stream(v.getValue(IntArrayType.INSTANCE)).map(l -> Integer.toString(l))
                    .toArray(String[]::new);
            } else if (type.equals(StringType.INSTANCE) || type.equals(BooleanType.INSTANCE)
                || type.equals(IntType.INSTANCE) || type.equals(LongType.INSTANCE)
                || type.equals(DoubleType.INSTANCE)) {
                return new String[]{v.getValueAsString()};
            } else {
                throw createIncompatibleTypeException(varString, v, type, "string array");
            }
        }
    }

    /** User object in tree node wrapping config entry, used variable name and
     * possibly a variable name for exporting the value as variable. */
    private static final class Wrapper {
        private final AbstractConfigEntry m_configEntry;
        private String m_useVarName;
        private String m_exposeVarName;
        private final boolean m_isLeaf;

        /** @param entry Entry to wrap. */
        Wrapper(final AbstractConfigEntry entry, final boolean isLeaf) {
            m_configEntry = entry;
            m_isLeaf = isLeaf;
        }

        /** @return true if this represents a leaf (not a config object) */
        boolean isLeaf() {
            return m_isLeaf;
        }

        /** @return the key of this config entry. */
        String getKey() {
            return m_configEntry.getKey();
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return m_configEntry.toString();
        }
    }

}
