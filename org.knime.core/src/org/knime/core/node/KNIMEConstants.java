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
 *   10.08.2005 (bernd): created
 */
package org.knime.core.node;

import java.io.File;
import java.util.Locale;

import javax.swing.ImageIcon;

import org.knime.core.internal.KNIMEPath;
import org.knime.core.util.ThreadPool;

/**
 * Class that hold static values about the knime platform. So far only the knime
 * directory, which is located in <code>$HOME$/.knime</code>, the welcome
 * message, and a icon are provided.
 * 
 * @author Bernd Wiswedel, University of Konstanz
 */
public final class KNIMEConstants {
    /** KNIME's major release number. */
    public static final int MAJOR = 1;
    /** KNIME's minor release number. */
    public static final int MINOR = 2;
    /** KNIME's revision number. */
    public static final int REV = 0; // Please change this on upgrade: 1.2.0 
    
    // IMPORTANT: Remember to also update the NodeLogger welcome screen with
    // the current version and the prerequisites
    /** Workflow file version. */
    public static final String VERSION = MAJOR + "." + MINOR + "." + REV;

    /**
     * The name of the system property whose value is - if set - used as knime
     * home directory. If no (or an invalid) value is set, ~user/knime will be
     * used instead. To set the knime home dir from the command line, use
     * -Dknime.home=&lt;absolutePathToNewHomeDir&gt;.
     */
    public static final String KNIME_HOME_PROPERTYNAME = "knime.home";

    /**
     * KNIME home directory.
     */
    private static File knimeHomeDir;

    /**
     * <i>Welcome to KNIME Konstanz Information Miner</i>.
     */
    public static final String WELCOME_MESSAGE =
      "******************************************************************\n"
    + "***  Welcome to KNIME v1.2.0 - the Konstanz Information Miner  ***\n"
    + "*** Copyright, 2003-2007, Uni Konstanz and KNIME GmbH, Germany ***\n"
    + "******************************************************************\n";

    /** Path to the <i>knime.png</i> icon. */
    private static final String KNIME_ICON_PATH =
            KNIMEConstants.class.getPackage().getName().replace('.', '/')
                    + "/knime.png";

    /** Icon 16 times 16 pixel. */
    public static final ImageIcon KNIME16X16;

    /** Load icon. */
    static {
        File knimeHome = KNIMEPath.getKNIMEHomeDirPath();
        knimeHomeDir = knimeHome;
        ImageIcon icon;
        try {
            ClassLoader loader = KNIMEConstants.class.getClassLoader();
            icon = new ImageIcon(loader.getResource(KNIME_ICON_PATH));
        } catch (Exception e) {
            icon = null;
        }
        KNIME16X16 = icon;
        // we prefer to have all gui-related locales being set to us-standard,
        try {
            Locale.setDefault(Locale.US);
        } catch (Exception e) {
            // do nothing.
        }

        int maxThreads = Runtime.getRuntime().availableProcessors() + 2;
        String maxThreadsString = 
            System.getProperty("org.knime.core.maxThreads");
        try {
            if (maxThreadsString != null && maxThreadsString.length() > 0) {
                int val = Integer.parseInt(maxThreadsString);
                if (val <= 0) {
                    throw new NumberFormatException("Not positive");
                }
                maxThreads = val;
            }
        } catch (NumberFormatException nfe) {
            // no NodeLogger available yet!
            System.err.println("Unable to parse system property "
                    + "\"org.knime.core.maxThreads\" (\"" + maxThreadsString 
                    + "\") as number: " + nfe.getMessage());
        }
        GLOBAL_THREAD_POOL = new ThreadPool(maxThreads);
    }

    /** The global thread pool from which all threads should be taken. */
    public static final ThreadPool GLOBAL_THREAD_POOL;

    /**
     * The directory where knime will put log files and configuration files. If
     * started in eclipse, this is usually ${workspace_path}/.metadata/knime.
     * Otherwise it's in the current working directory. This variable does not
     * have a trailing file separator character.
     * 
     * @return The KNIME home dir.
     */
    public static final String getKNIMEHomeDir() {
        return knimeHomeDir.getAbsolutePath();
    }

    /**
     * Hides public constructor.
     */
    private KNIMEConstants() {
    }
}
