/* 
 * --------------------------------------------------------------------- *
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
 * --------------------------------------------------------------------- *
 */
package org.knime.core.node;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Interface for factories summarizing <code>NodeModel</code>,
 * <code>NodeView</code>, and <code>NodeDialogPane</code> for a specific
 * <code>Node</code> implementation.
 * 
 * @author Michael Berthold, University of Konstanz
 */
public abstract class NodeFactory {
    private static final List<String> LOADED_NODE_FACTORIES =
            new ArrayList<String>();

    private static final List<String> RO_LIST =
            Collections.unmodifiableList(LOADED_NODE_FACTORIES);

    /**
     * Enum for all node types.
     * 
     * @author Thorsten Meinl, University of Konstanz
     */
    public static enum NodeType {
        /** A data producing node. */
        Source,
        /** A data consuming node. */
        Sink,
        /** A learning node. */
        Learner,
        /** A predicting node. */
        Predictor,
        /** A data manipulating node. */
        Manipulator,
        /** A visualizing node. */
        Visualizer,
        /** A meta node. */
        Meta,
        /** All other nodes. */
        Other,
        /** If not specified. */
        Unknown
    }

    // The logger for static methods
    private static final NodeLogger LOGGER =
            NodeLogger.getLogger(NodeFactory.class);

    private final String m_nodeName;

    private final String m_shortDescription;

    /* port names */
    private List<String> m_inDataPorts;

    private List<String> m_outDataPorts;

    private List<String> m_modelIns;

    private List<String> m_modelOuts;

    /* port descriptions */
    private List<String> m_inDataPortsDesc;

    private List<String> m_outDataPortsDesc;

    private List<String> m_modelInsDesc;

    private List<String> m_modelOutsDesc;

    private List<Element> m_views;

    private final URL m_icon;

    private NodeType m_type;

    private final Element m_knimeNode;

    private final String m_fullAsHTML;

    private boolean m_hasXMLBeenValidated = false;

    private static DocumentBuilder parser;

    private static Transformer transformer;

    private static URL defaultIcon = null;

    private final NodeLogger m_logger = NodeLogger.getLogger(getClass());

    static {
        try {
            String imagePath = "./default.png";
            if (!imagePath.startsWith("/")) {
                imagePath =
                        NodeFactory.class.getPackage().getName().replace('.',
                                '/')
                                + "/" + imagePath;
            }

            URL iconURL =
                    NodeFactory.class.getClassLoader().getResource(imagePath);

            defaultIcon = iconURL;
        } catch (Exception ioe) {
            LOGGER.error("Default icon could not be read.");
        }
    }

    /**
     * Instantiates the parser and the transformer for processing the xmll node
     * description. Prints log message if that fails.
     */
    private static void instantiateParser() {
        // temporarily changing the class loder here is to prevent that some
        // external library is foisting us an incompatible XML library;
        // using the system class loader should make sure, the the platform
        // default XML classes are used
        ClassLoader def = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(
                    ClassLoader.getSystemClassLoader());
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();

            // sets validation with DTD file
            f.setValidating(true);

            parser = f.newDocumentBuilder();

            DefaultHandler dh = new DefaultHandler() {
                @Override
                public InputSource resolveEntity(final String pubId,
                        final String sysId) throws IOException, SAXException {
                    if ((pubId != null)
                            && pubId.equals("-//UNIKN//DTD KNIME Node 1.0//EN")) {
                        String path = NodeFactory.class.getPackage().getName();
                        path = path.replace('.', '/') + "/Node.dtd";
                        InputStream in =
                                NodeFactory.class.getClassLoader()
                                        .getResourceAsStream(path);
                        return new InputSource(in);
                    } else {
                        return super.resolveEntity(pubId, sysId);
                    }
                }
            };
            parser.setEntityResolver(dh);
            // parser.setErrorHandler(dh);

            StreamSource stylesheet =
                    new StreamSource(NodeFactory.class.getClassLoader()
                            .getResourceAsStream(
                                    NodeFactory.class.getPackage().getName()
                                            .replace('.', '/')
                                            + "/FullNodeDescription.xslt"));

            transformer =
                    TransformerFactory.newInstance().newTemplates(stylesheet)
                            .newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        } catch (ParserConfigurationException ex) {
            NodeLogger.getLogger(NodeFactory.class).error(ex.getMessage(), ex);
        } catch (TransformerConfigurationException ex) {
            NodeLogger.getLogger(NodeFactory.class).error(ex.getMessage(), ex);
        } catch (TransformerFactoryConfigurationError ex) {
            NodeLogger.getLogger(NodeFactory.class).error(ex.getMessage(), ex);
        } finally {
            Thread.currentThread().setContextClassLoader(def);
        }
    }

    /**
     * Constructor for use in subclasses that can be used to avoid checking the
     * XML file. Please think twice if you really need to use this constructor.
     * 
     * @param checkXML <code>true</code> if the XML file should be checked
     *            (this is the default when using the standard constructor),
     *            <code>false</code> otherwise
     */
    protected NodeFactory(final boolean checkXML) {
        this();
        m_hasXMLBeenValidated = !checkXML;
    }

    /**
     * Creates a new <code>NodeFactory</code> and tries to read to properties
     * file named <code>Node.xml</code> in the same package as the factory.
     */
    protected NodeFactory() {
        if (parser == null) {
            instantiateParser();
        }

        ClassLoader loader = getClass().getClassLoader();
        InputStream propInStream;
        String path;
        Class<?> clazz = getClass();

        do {
            path = clazz.getPackage().getName();
            path =
                    path.replace('.', '/') + "/" + clazz.getSimpleName()
                            + ".xml";

            propInStream = loader.getResourceAsStream(path);
            clazz = clazz.getSuperclass();
        } while ((propInStream == null) && (clazz != Object.class));

        // fall back node name if no xml file available or invalid.
        String defaultNodeName = getClass().getSimpleName();
        if (defaultNodeName.endsWith("NodeFactory")) {
            defaultNodeName =
                    defaultNodeName.substring(0, defaultNodeName.length()
                            - "NodeFactory".length());
        } else if (defaultNodeName.endsWith("Factory")) {
            defaultNodeName =
                    defaultNodeName.substring(0, defaultNodeName.length()
                            - "Factory".length());
        }
        if (propInStream == null) {
            m_logger.error("Could not find XML description "
                    + "file for node '" + getClass().getName() + "'");
            m_shortDescription = "No description available";
            m_knimeNode = null;
            m_icon = null;
            m_nodeName = defaultNodeName;
            m_fullAsHTML =
                    "<html><body><font color=\"red\">NO XML FILE!"
                            + "</font></body></html>";
        } else {
            Document doc = null;
            Exception exception = null;
            try {
                synchronized (parser) {
                    parser.setErrorHandler(new DefaultHandler() {
                        @Override
                        public void error(final SAXParseException ex)
                                throws SAXException {
                            m_logger.coding("XML node file does not conform "
                                    + "with DTD: " + ex.getMessage(), ex);
                        }
                    });
                    doc = parser.parse(new InputSource(propInStream));
                }
            } catch (SAXException ex) {
                exception = ex;
            } catch (IOException ex) {
                exception = ex;
            }
            if (exception != null) {
                m_logger.coding(exception.getMessage() + " (" + path + ")",
                        exception);
                m_shortDescription = "No description available";
                m_knimeNode = null;
                m_icon = null;
                m_nodeName = defaultNodeName;
                m_fullAsHTML =
                        "<html><body><font color=\"red\">"
                                + "INVALID XML FILE!</font><br/>"
                                + exception.getClass().getName() + ": "
                                + exception.getMessage() + "</body></html>";
                return;
            }
            m_knimeNode = doc.getDocumentElement();
            m_icon = readIconFromXML();

            try {
                m_type = NodeType.valueOf(m_knimeNode.getAttribute("type"));
            } catch (IllegalArgumentException ex) {
                m_logger.coding("Unknown node type '"
                        + m_knimeNode.getAttribute("type") + "'");
                m_type = NodeType.Unknown;
            }

            String nodeName = readNameFromXML();
            if (nodeName == null || nodeName.length() == 0) {
                m_logger.coding("Unable to read \"name\" tag from XML");
                m_nodeName = defaultNodeName;
            } else {
                m_nodeName = nodeName;
            }
            String shortDescription = readShortDescriptionFromXML();
            if (shortDescription == null || shortDescription.length() == 0) {
                m_logger.coding("Unable to read \"shortDescription\" "
                        + "tag from XML");
                m_shortDescription = "Unknown node";
            } else {
                m_shortDescription = shortDescription;
            }
            readPortsFromXML();
            readViewsFromXML();
            m_fullAsHTML = readFullDescription();
            // DO NOT call "checkConsistency(createNodeModel());" here as that
            // would call an abstract method from within the constructor -
            // local fields in the derived NodeFactory have not been initialized
        }
        addLoadedFactory(this.getClass());
    }

    private static final Pattern ICON_PATH_PATTERN =
            Pattern.compile("[^\\./]+/\\.\\./");

    /**
     * Reads the icon tag from the xml and returns the icon. If not available or
     * the icon is not readable, an default icon is returned. This method is
     * called from the constructor.
     * <p>
     * This method does not return null as the icon is optional, i.e. it doesn't
     * hurt if it is missing.
     * 
     * @return The icon as given in the xml attribute <i>icon</i>.
     */
    private URL readIconFromXML() {
        String imagePath = m_knimeNode.getAttribute("icon");
        imagePath = imagePath.replaceAll("//", "/");

        if (imagePath.startsWith("./")) {
            imagePath = imagePath.substring("./".length());
        }
        if (!imagePath.startsWith("/")) {
            imagePath =
                    getClass().getPackage().getName().replace('.', '/') + "/"
                            + imagePath;

            Matcher m = ICON_PATH_PATTERN.matcher(imagePath);
            while (m.find()) {
                imagePath = imagePath.replaceAll("[^./]+/../", "");
                m = ICON_PATH_PATTERN.matcher(imagePath);
            }
        }

        URL iconURL = getClass().getClassLoader().getResource(imagePath);

        return iconURL;
    }

    /**
     * Read the name of the node from the xml file. If the tag is not available,
     * returns <code>null</code>. This method is called from the constructor.
     * 
     * @return The name as defined in the xml or null if that fails.
     */
    private String readNameFromXML() {
        Node w3cNode = m_knimeNode.getElementsByTagName("name").item(0);
        if (w3cNode == null) {
            return null;
        }
        Node w3cNodeChild = w3cNode.getFirstChild();
        if (w3cNodeChild == null) {
            return null;
        }
        return w3cNodeChild.getNodeValue();

    }

    /**
     * Read the short description of the node from the xml file. If the tag is
     * not available, returns <code>null</code>. This method is called from
     * the constructor.
     * 
     * @return The short description as defined in the xml or null if that
     *         fails.
     */
    private String readShortDescriptionFromXML() {
        Node w3cNode =
                m_knimeNode.getElementsByTagName("shortDescription").item(0);
        if (w3cNode == null) {
            return null;
        }
        Node w3cNodeChild = w3cNode.getFirstChild();
        if (w3cNodeChild == null) {
            return null;
        }
        return w3cNodeChild.getNodeValue();
    }

    /**
     * Read the port descriptions of the node from the xml file. If an error
     * occurs (no such element in the xml, parsing exception ...), a coding
     * problem is reported to the node logger.
     */
    private void readPortsFromXML() {
        Node w3cNode = m_knimeNode.getElementsByTagName("ports").item(0);
        if (w3cNode == null) {
            return;
        }
        NodeList w3cNodeChildren = w3cNode.getChildNodes();
        for (int i = 0; i < w3cNodeChildren.getLength(); i++) {
            if (!(w3cNodeChildren.item(i) instanceof Element)) {
                continue;
            }
            Element port = (Element)w3cNodeChildren.item(i);
            // attempt to read index - this attribute will be used in the
            // addToPortDescription method, make it fail fast here!
            String indexString = port.getAttribute("index");
            try {
                int index = Integer.parseInt(indexString);
                if (index < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException nfe) {
                m_logger.coding("Illegal index \"" + indexString
                        + "\" in port description");
                continue;
            }
            if (port.getNodeName().equals("dataIn")) {
                if (m_inDataPorts == null) {
                    m_inDataPorts = new ArrayList<String>(4);
                }
                if (m_inDataPortsDesc == null) {
                    m_inDataPortsDesc = new ArrayList<String>(4);
                }
                addToPort(m_inDataPorts, m_inDataPortsDesc, port);
            } else if (port.getNodeName().equals("dataOut")) {
                if (m_outDataPorts == null) {
                    m_outDataPorts = new ArrayList<String>(4);
                }
                if (m_outDataPortsDesc == null) {
                    m_outDataPortsDesc = new ArrayList<String>(4);
                }
                addToPort(m_outDataPorts, m_outDataPortsDesc, port);
            } else if (port.getNodeName().equals("predParamIn")
                    || port.getNodeName().equals("modelIn")) {
                if (port.getNodeName().equals("predParamIn")) {
                    m_logger.coding("Do not use <predParamIn> any more, use "
                            + "<modelIn> instead");
                }

                if (m_modelIns == null) {
                    m_modelIns = new ArrayList<String>(4);
                }
                if (m_modelInsDesc == null) {
                    m_modelInsDesc = new ArrayList<String>(4);
                }
                addToPort(m_modelIns, m_modelInsDesc, port);
            } else if (port.getNodeName().equals("predParamOut")
                    || port.getNodeName().equals("modelOut")) {
                if (port.getNodeName().equals("predParamOut")) {
                    m_logger.coding("Do not use <predParamOut> any more, use "
                            + "<modelOut> instead");
                }
                if (m_modelOuts == null) {
                    m_modelOuts = new ArrayList<String>(4);
                }
                if (m_modelOutsDesc == null) {
                    m_modelOutsDesc = new ArrayList<String>(4);
                }
                addToPort(m_modelOuts, m_modelOutsDesc, port);
            }
        }

        int nullIndex;
        if (m_inDataPorts != null) {
            // look for null descriptions and print error if found
            nullIndex = m_inDataPorts.indexOf(null);
            if (nullIndex >= 0) {
                m_logger.coding("No description for input port " + nullIndex
                        + ".");
            }
        }

        if (m_outDataPorts != null) {
            nullIndex = m_outDataPorts.indexOf(null);
            if (nullIndex >= 0) {
                m_logger.coding("No description for output port " + nullIndex
                        + ".");
            }
        }

        if (m_modelIns != null) {
            nullIndex = m_modelIns.indexOf(null);
            if (nullIndex >= 0) {
                m_logger.coding("No description for prediction input port "
                        + nullIndex + ".");
            }
        }

        if (m_modelOuts != null) {
            nullIndex = m_modelOuts.indexOf(null);
            if (nullIndex >= 0) {
                m_logger.coding("No description for prediction output port "
                        + nullIndex + ".");
            }
        }
    }

    /**
     * Read the view descriptions of the node from the xml file. If an error
     * occurs (no such element in the xml, parsing exception ...), a coding
     * problem is reported to the node logger.
     */
    private void readViewsFromXML() {
        Node w3cNode = m_knimeNode.getElementsByTagName("views").item(0);
        if (w3cNode == null) {
            return;
        }
        m_views = new ArrayList<Element>(4);
        NodeList allViews = ((Element)w3cNode).getElementsByTagName("view");
        for (int i = 0; i < allViews.getLength(); i++) {
            Element view = (Element)allViews.item(i);
            // attempt to read index
            String indexString = view.getAttribute("index");
            int index;
            try {
                index = Integer.parseInt(indexString);
                if (index < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException nfe) {
                m_logger.coding("Invalid index \"" + indexString
                        + "\" for view description.");
                continue;
            }
            // make sure the description fits!
            for (int k = m_views.size(); k <= index; k++) {
                m_views.add(null);
            }
            if (m_views.get(index) != null) {
                m_logger.coding("Duplicate view description in "
                        + "XML for index " + index + ".");
            }
            m_views.set(index, view);
        }
    }

    private void addToPort(final List<String> nameList,
            final List<String> descList, final Element port) {
        int index = Integer.parseInt(port.getAttribute("index"));
        for (int k = nameList.size(); k <= index; k++) {
            nameList.add("");
        }
        if (port.getAttribute("name").length() > 0) {
            nameList.set(index, port.getAttribute("name").trim());
        }
        for (int k = descList.size(); k <= index; k++) {
            descList.add(nameList.get(k));
        }
        Node w3cNode = port.getFirstChild();
        if (w3cNode == null) {
            return;
        }
        String value = w3cNode.getNodeValue();
        if (value == null || value.length() == 0) {
            return;
        }
        descList.set(index, value.trim().replaceAll("(?:\\s+|\n)", " "));
    }

    private String readFullDescription() {
        StreamResult result = new StreamResult(new StringWriter());
        DOMSource source = new DOMSource(m_knimeNode);
        try {
            transformer.transform(source, result);
        } catch (TransformerException ex) {
            m_logger.coding("Unable to process fullDescription in " + "xml: "
                    + ex.getMessage(), ex);
        }
        return result.getWriter().toString();
    }

    /**
     * Returns the name of this node.
     * 
     * @return the node's name.
     */
    public final String getNodeName() {
        return m_nodeName;
    }

    /**
     * Returns a name for an input port.
     * 
     * @param index the index of the input port, starting at 0
     * @return an input port description
     */
    public String getInportDataName(final int index) {
        if (m_inDataPorts == null) {
            return "";
        } else {
            return m_inDataPorts.get(index);
        }
    }

    /**
     * Returns a name for an output port.
     * 
     * @param index the index of the output port, starting at 0
     * @return an output port description
     */
    public String getOutportDataName(final int index) {
        if (m_outDataPorts == null) {
            return "";
        } else {
            return m_outDataPorts.get(index);
        }
    }

    /**
     * Returns a name for an predictor parameter input port.
     * 
     * @param index the index of the input port, starting at 0
     * @return an predictor parameter input port description
     */
    public String getInportModelName(final int index) {
        if (m_modelIns == null) {
            return "";
        } else {
            return m_modelIns.get(index);
        }
    }

    /**
     * Returns a name for an predictor parameter output port.
     * 
     * @param index the index of the output port, starting at 0
     * @return an predictor parameter output port description
     */
    public String getOutportModelName(final int index) {
        if (m_modelOuts == null) {
            return "";
        } else {
            return m_modelOuts.get(index);
        }
    }

    /**
     * Returns a description for an input port.
     * 
     * @param index the index of the input port, starting at 0
     * @return an input port description
     */
    public final String getInportDataDescription(final int index) {
        if (m_inDataPortsDesc == null) {
            return "No description available";
        } else {
            return m_inDataPortsDesc.get(index);
        }
    }

    /**
     * Returns a description for an output port.
     * 
     * @param index the index of the output port, starting at 0
     * @return an output port description
     */
    public final String getOutportDataDescription(final int index) {
        if (m_outDataPortsDesc == null) {
            return "No description available";
        } else {
            return m_outDataPortsDesc.get(index);
        }
    }

    /**
     * Returns a description for an predictor parameter input port.
     * 
     * @param index the index of the input port, starting at 0
     * @return an predictor parameter input port description
     */
    public final String getInportModelDescription(final int index) {
        if (m_modelInsDesc == null) {
            return "No description available";
        } else {
            return m_modelInsDesc.get(index);
        }
    }

    /**
     * Returns a description for an predictor parameter output port.
     * 
     * @param index the index of the output port, starting at 0
     * @return an predictor parameter output port description
     */
    public final String getOutportModelDescription(final int index) {
        if (m_modelOutsDesc == null) {
            return "No description available";
        } else {
            return m_modelOutsDesc.get(index);
        }
    }

    /**
     * Returns a description for a view.
     * 
     * @param index the index of the view, starting at 0
     * @return a view description
     */
    protected final String getViewDescription(final int index) {
        Element e;
        if ((m_views == null) || (index >= m_views.size())
                || ((e = m_views.get(index)) == null)) {
            return "No description available";
        } else {
            return e.getFirstChild().getNodeValue().trim().replaceAll(
                    "(?:\\s+|\n", " ");
        }
    }

    /**
     * Creates and returns a new instance of the node's corresponding model.
     * 
     * @return A new NodeModel for this node. Never <code>null</code>!
     */
    public abstract NodeModel createNodeModel();

    /**
     * Access method for <code>createNodeModel()</code>. This method will
     * also do sanity checks for the correct labeling of the port description:
     * The port count (in, out, modelIn, modelOut) is only available in the
     * NodeModel. The first time, this method is called, the port count is
     * retrieved from the NodeModel and the xml description is validated against
     * the info from the model. If inconsistencies are identified, log messages
     * will be written and the full description of the node is adapted such that
     * the user (preferably the implementor) immediately sees the problem.
     * 
     * @return The model as from createNodeModel()
     */
    final NodeModel callCreateNodeModel() {
        NodeModel result = createNodeModel();
        if (!m_hasXMLBeenValidated) {
            m_hasXMLBeenValidated = true;
            checkConsistency(result);
        }
        return result;
    }

    /**
     * Returns the number of possible views.
     * 
     * @return The number of views available for this node.
     * @see #createNodeView(int,NodeModel)
     */
    protected abstract int getNrNodeViews();

    /**
     * Returns the node name as view name, the index is not considered.
     * 
     * @param index The view index,
     * @return A node view name.
     */
    protected final String getNodeViewName(final int index) {
        Element e;
        if ((m_views == null) || (index >= m_views.size())
                || ((e = m_views.get(index)) == null)) {
            return "NoName";
        } else {
            return e.getAttribute("name");
        }
    }

    /**
     * Creates and returns a new node view for the given index.
     * 
     * @param viewIndex The index for the view to create.
     * @param nodeModel The underlying model.
     * @return A new node view for the given index.
     * @throws IndexOutOfBoundsException If the <code>viewIndex</code> is out
     *             of range.
     * 
     * @see #getNrNodeViews()
     */
    public abstract NodeView createNodeView(final int viewIndex,
            final NodeModel nodeModel);

    /**
     * Returns <code>true</code> if the <code>Node</code> provided a dialog.
     * 
     * @return <code>true</code> if a <code>NodeDialogPane</code> is
     *         available.
     */
    protected abstract boolean hasDialog();

    /**
     * Creates and returns a new node dialog pane.
     * 
     * @return The new node dialog pane.
     */
    protected abstract NodeDialogPane createNodeDialogPane();

    /**
     * @return A short description (like 50 characters) of the functionality the
     *         corresponding node provides. This string should not contain any
     *         formatting or html specific parts or characters.
     */
    public final String getNodeOneLineDescription() {
        return m_shortDescription;
    }

    /**
     * Returns the icon for the node.
     * 
     * @return the node's icon
     */
    public final URL getIcon() {
        return m_icon;
    }

    /**
     * Returns the formatted html source as given in the node factory's xml
     * description. The xml content is processed with a stylesheet that layouts
     * all available information.
     * 
     * @return An html string containing a full description of the node's
     *         functionality, all parameters, inport data, output of the node,
     *         and views.
     */
    public final String getNodeFullHTMLDescription() {
        return m_fullAsHTML;
    }

    /**
     * Called when the NodeModel is instantiated the first time. We do some
     * sanity checks here, for instance: Do the number of ports in the xml match
     * with the port count in the node model...
     * 
     * @param m The NodeModel to check against.
     */
    private void checkConsistency(final NodeModel m) {
        if ((m.getNrDataIns() > 0)
                && ((m_inDataPorts == null) || (m.getNrDataIns() != m_inDataPorts
                        .size()))) {
            m_logger.coding("Missing or surplus input port name");
        }
        if ((m.getNrDataOuts() > 0)
                && ((m_outDataPorts == null) || (m.getNrDataOuts() != m_outDataPorts
                        .size()))) {
            m_logger.coding("Missing or surplus output port name");
        }
        if ((m.getNrModelIns() > 0)
                && ((m_modelIns == null) || (m.getNrModelIns() != m_modelIns
                        .size()))) {
            m_logger.coding("Missing or surplus predictor input port name");
        }
        if ((m.getNrModelOuts() > 0)
                && ((m_modelOuts == null) || m.getNrModelOuts() != m_modelOuts
                        .size())) {
            m_logger.coding("Missing or surplus predictor output port name");
        }
        if ((getNrNodeViews() > 0)
                && ((m_views == null) || getNrNodeViews() != m_views.size())) {
            m_logger.coding("Missing or surplus view description");
        }

        if (m_inDataPorts != null) {
            for (int i = 0; i < m_inDataPorts.size(); i++) {
                if (m_inDataPorts.get(i) == null) {
                    m_logger.coding("Missing description for input port " + i);
                }
            }
        }

        if (m_outDataPorts != null) {
            for (int i = 0; i < m_outDataPorts.size(); i++) {
                if (m_outDataPorts.get(i) == null) {
                    m_logger.coding("Missing description for output port " + i);
                }
            }
        }

        if (m_modelIns != null) {
            for (int i = 0; i < m_modelIns.size(); i++) {
                if (m_modelIns.get(i) == null) {
                    m_logger.coding("Missing description for predictor input"
                            + " port " + i);
                }
            }
        }

        if (m_modelOuts != null) {
            for (int i = 0; i < m_modelOuts.size(); i++) {
                if (m_modelOuts.get(i) == null) {
                    m_logger.coding("Missing description for predictor output"
                            + " port " + i);
                }
            }
        }

        if (m_views != null) {
            for (int i = 0; i < m_views.size(); i++) {
                if (m_views.get(i) == null) {
                    m_logger.coding("Missing description for view " + i);
                }
            }
        }
    }

    /**
     * Returns the type of the node.
     * 
     * @return the node's type
     */
    public NodeType getType() {
        return m_type;
    }

    /**
     * Returns the default icon for nodes that do not define their own.
     * 
     * @return an URL to the default icon
     */
    public static URL getDefaultIcon() {
        return defaultIcon;
    }

    /**
     * Returns a collection of all loaded node factories.
     * 
     * @return a collection array of fully qualified node factory class names
     */
    public static List<String> getLoadedNodeFactories() {
        return RO_LIST;
    }

    /**
     * Adds the given factory class to the list of loaded factory classes.
     * 
     * @param factoryClass a factory class
     */
    public static void addLoadedFactory(
            final Class<? extends NodeFactory> factoryClass) {
        LOADED_NODE_FACTORIES.add(factoryClass.getName());
    }
}
