/* 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2006
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 *
 */
package de.unikn.knime.core.node.config;

import java.io.Serializable;
import java.util.Enumeration;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

/**
 * Abstract Config entry holding only a Config entry type. Deriving classes must
 * store the corresponding value and implement the toStringValue() method.
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
abstract class AbstractConfigEntry implements Serializable, TreeNode {
    
    /** The type of the stored value. */
    private final ConfigEntries m_type;
    
    private AbstractConfigEntry m_parent = null;

    private final String m_key;
    
    /**
     * Creates a new Config entry by the given key and type.
     * @param type enum type within the <code>ConfigEntries</code>
     * @param key The key under which this value is added.
     * @throws IllegalArgumentException If the type is null.
     */
    AbstractConfigEntry(final ConfigEntries type, final String key) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "Config entry type can't be null.");
        }
        m_type = type;
        m_key = checkKey(key);
    }
    
    /**
     * Check key on null and if empty.
     * @param key The key to check.
     * @return The original key.
     */
    private static final String checkKey(final String key) {
        if (key == null || key.trim().length() == 0) {
            throw new IllegalArgumentException("Key must not be empty: " + key);
        }
        return key;
    }
    
    /**
     * @return The key for this value.
     */
    public final String getKey() {
        return m_key;
    }
    
    /**
     * Sets the parent for this entry which is null if not available.
     * @param parent The new parent of the entry.
     */
    final void setParent(final AbstractConfigEntry parent) {
        m_parent = parent;
    }
    
    /*
     * Reviewers (PO and CS): The current key should be renamed to "identifier"
     * or something similar. A new getKey method should return a key for the
     * hashmap, which must be a combination of the type and the identifier.
     * This will resolve the problem of replacing entries with the same 
     * identifier but different keys. The equals ans hashCode methods must be 
     * adopted accordingly.
     */
    
    /**
     * @return This Config's type.
     */
    final ConfigEntries getType() {
        return m_type;
    }
    
    /**
     * String summary of this object including key, type, and value.
     * @return key + type + value
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return getKey() + " [" + getType().name() + "] -> " + toStringValue();
    }
    
    /**
     * Returns a String representation for this Config entry which is the used 
     * to re-load this Config entry.
     * @return A String representing this Config entry which can be null.
     */
    abstract String toStringValue();
    
    /**
     * Config entries are equal if they are identical.
     * @param o The other object to check against.
     * @return true, if <code>isIdentical(AbstractConfigEntry)</code> returns 
     *         true.
     * @see #isIdentical(AbstractConfigEntry)
     */
    public final boolean equals(final Object o) {
        if (o == null) {
            return false;
        }
        if (o.getClass() != this.getClass()) {
            return false;
        }
        return isIdentical((AbstractConfigEntry) o);
    }

    /**
     * Checks the identity of two config entries. They are identical if they 
     * have the same name, are of the same name, and have identical values which
     * is checked by <code>hasIdenticalValue</code>, implemented in the derived 
     * classes.
     * @param ace Entry to check if identical.
     * @return true, if name, type, and value are identical.
     */
    public final boolean isIdentical(final AbstractConfigEntry ace) {
        return m_type.name() == ace.m_type.name()
            && m_key.equals(ace.m_key) 
            && hasIdenticalValue(ace);
    }
    
    /**
     * Derived classes must compare their value with the value in the passed
     * argument (on equality). They can safely assume that the specified object
     * has the same java class, the same type and key.
     * 
     * @param ace the argument to compare the value with
     * @return true if the specified argument stores the same value as this.
     */
    abstract boolean hasIdenticalValue(AbstractConfigEntry ace);
    
    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }
    
    // tree node methods
    
    /**
     * @param childIndex Not is use.
     * @return null, always.
     */
    public TreeNode getChildAt(final int childIndex) {
        return null;
    }

    /**
     * @return 0, always.
     * @see javax.swing.tree.TreeNode#getChildCount()
     */
    public int getChildCount() {
        return 0;
    }

    
    /**
     * @return The parent Config of this object.
     * @see javax.swing.tree.TreeNode#getParent()
     */
    public final TreeNode getParent() {
        return m_parent;
    }

    /**
     * @param node Not in use.
     * @return -1, always.
     * @see javax.swing.tree.TreeNode#getIndex(javax.swing.tree.TreeNode)
     */
    public int getIndex(final TreeNode node) {
        return -1;
    }

    /**
     * @return true, if not a leaf, always.
     * @see #isLeaf() 
     * @see javax.swing.tree.TreeNode#getAllowsChildren()
     */
    public final boolean getAllowsChildren() {
        return !isLeaf();
    }

    /**
     * @return true, always.
     * @see javax.swing.tree.TreeNode#isLeaf()
     */
    public boolean isLeaf() {
        return true;
    }

    /**
     * @return An empty enumeration.
     * @see DefaultMutableTreeNode#EMPTY_ENUMERATION
     * @see javax.swing.tree.TreeNode#children()
     */
    public Enumeration<TreeNode> children() {
        return DefaultMutableTreeNode.EMPTY_ENUMERATION;
    }
    
}
