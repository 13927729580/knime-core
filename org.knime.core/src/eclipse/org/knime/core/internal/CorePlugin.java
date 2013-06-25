/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2013
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
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
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
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
 * -------------------------------------------------------------------
 *
 * History
 *   Oct 13, 2006 (wiswedel): created
 */
package org.knime.core.internal;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.svgexport.WorkflowSVGExport;
import org.knime.core.util.pathresolve.ResolverUtil;
import org.knime.core.util.pathresolve.URIToFileResolve;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Plugin class that is initialized when the plugin project is started. It will
 * set the workspace path as KNIME home dir in the KNIMEConstants utility class.
 * @author wiswedel, University of Konstanz
 */
public class CorePlugin implements BundleActivator {
    private static CorePlugin instance;

    private static class KNIMEPathInitializer {
        public static void initialize() {
            try {
                URL workspaceURL =
                   org.eclipse.core.runtime.Platform.getInstanceLocation().getURL();
                if (workspaceURL.getProtocol().equalsIgnoreCase("file")) {
                    // we can create our home only in local workspaces
                    File workspaceDir = new File(workspaceURL.getPath());
                    File metaDataFile = new File(workspaceDir, ".metadata");
                    if (!metaDataFile.exists()) {
                        metaDataFile.mkdir();
                    }
                    File knimeHomeDir = new File(metaDataFile, "knime");
                    if (!knimeHomeDir.exists()) {
                        knimeHomeDir.mkdir();
                    }
                    KNIMEPath.setKNIMEHomeDir(knimeHomeDir.getAbsoluteFile());
                    KNIMEPath.setWorkspaceDir(workspaceDir.getAbsoluteFile());
                }

            } catch (Exception e) {
                // the logger will use the "user.dir" as knime home, unfortunately.
                NodeLogger.getLogger(KNIMEPathInitializer.class).warn(
                        "Can't init knime home dir to workspace path.", e);
            }

        }
    }

    /** see {@link #setWrapColumnHeaderInTableViews(boolean)}. */
    private boolean m_isWrapColumnHeaderInTableViews = false;

    /** A property controlled by the UI preference page. We need a field in the core plugin as it does not access
     * to any UI plugin. We rely on the UI plugin to init/update this field.
     * @param value the isWrapColumnHeaderInTableViews to set
     * @noreference This method is not intended to be referenced by clients.
     * @since 2.8
     */
    public final void setWrapColumnHeaderInTableViews(final boolean value) {
        m_isWrapColumnHeaderInTableViews = value;
    }

    /** see {@link #setWrapColumnHeaderInTableViews(boolean)}.
     * @return the isWrapColumnHeaderInTableViews
     * @noreference This method is not intended to be referenced by clients.
     * @since 2.8
     */
    public final boolean isWrapColumnHeaderInTableViews() {
        return m_isWrapColumnHeaderInTableViews;
    }

    private ServiceTracker<WorkflowSVGExport, WorkflowSVGExport> m_svgExportServiceTracker;

    /** The service instance to save a workflow as svg. Needed during workflow-saving. The returned instance is null
     * if no such service is registered (editor.svgexport plugin not installed or running in headless mode).
     * @return The service instance or null.
     * @since 2.8
     */
    public WorkflowSVGExport getWorkflowSVGExport() {
        if (m_svgExportServiceTracker != null) {
            return m_svgExportServiceTracker.getService();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(final org.osgi.framework.BundleContext context)
        throws Exception {

        instance = this;
        m_svgExportServiceTracker = new ServiceTracker<WorkflowSVGExport, WorkflowSVGExport>(context,
                WorkflowSVGExport.class, null);
        m_svgExportServiceTracker.open();

        try {
            Class.forName("org.eclipse.core.runtime.Platform");
            KNIMEPathInitializer.initialize();
        }
        catch (ClassNotFoundException e) {

        }

        /* Unfortunately we have to activate the plugin
         * org.eclipse.ecf.filetransfer explicitly by accessing one of the
         * contained classed. This will trigger the initialization of the
         * extension point org.eclipse.ecf.filetransfer.urlStreamHandlerService
         * contained in the plugin which is necessary for registering the
         * "knime" URL protocol. */
        try {
            Class.forName("org.eclipse.ecf.filetransfer.IFileTransfer");
        }
        catch (ClassNotFoundException e) {
        }
    }

    /** {@inheritDoc} */
    @Override
    public void stop(final BundleContext context) throws Exception {
        instance = null;
        m_svgExportServiceTracker.close();
        m_svgExportServiceTracker = null;
    }

    /**
     * Returns the singleton instance of this plugin.
     *
     * @return the plugin
     */
    public static CorePlugin getInstance() {
        return instance;
    }

    /** Fetches a service implementing the {@link URIToFileResolve} interface
     * and returns the resolved file.
     * @param uri The URI to resolve
     * @return The local file underlying the URI (if any)
     * @throws IOException If no service is registered or the URI can't be
     * resolved.
     *
     * @deprecated Use {@link ResolverUtil#resolveURItoLocalFile(URI)} instead
     */
    @Deprecated
    public static File resolveURItoLocalFile(final URI uri) throws IOException {
        return ResolverUtil.resolveURItoLocalFile(uri);
    }

    /** Fetches a service implementing the {@link URIToFileResolve} interface
     * and returns the resolved file.
     * @param uri The URI to resolve
     * @return The local file or temporary copy of a remote file underlying the
     *      URI (if any)
     * @throws IOException If no service is registered or the URI can't be
     *      resolved.
     * @deprecated Use {@link ResolverUtil#resolveURItoLocalOrTempFile(URI)} instead
     */
    @Deprecated
    public static File resolveURItoLocalOrTempFile(final URI uri)
            throws IOException {
        return ResolverUtil.resolveURItoLocalOrTempFile(uri);
    }
}
