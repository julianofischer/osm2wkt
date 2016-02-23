package osm2wkt.exports;

/* ------------------
 * GraphMLExporter.java
 * ------------------
 */

import java.io.*;

import javax.xml.transform.*;
import javax.xml.transform.sax.*;
import javax.xml.transform.stream.*;

import org.jgrapht.*;
import org.jgrapht.ext.EdgeNameProvider;
import org.jgrapht.ext.IntegerEdgeNameProvider;
import org.jgrapht.ext.IntegerNameProvider;
import org.jgrapht.ext.VertexNameProvider;

import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * Exports a graph into a GraphML file.
 *
 * <p>For a description of the format see <a
 * href="http://en.wikipedia.org/wiki/GraphML">
 * http://en.wikipedia.org/wiki/GraphML</a>.</p>
 *
 * @author blah2 
 */
public class GraphMLExporter<V, E>
{
    //~ Instance fields --------------------------------------------------------

    private VertexNameProvider<V> vertexIDProvider;
    private VertexNameProvider<V> vertexLabelProvider;
    private EdgeNameProvider<E> edgeIDProvider;
    private EdgeNameProvider<E> edgeLabelProvider;

    //~ Constructors -----------------------------------------------------------

    /**
     * Constructs a new GraphMLExporter object with integer name providers for
     * the vertex and edge IDs and null providers for the vertex and edge
     * labels.
     */
    public GraphMLExporter()
    {
        this(
            new IntegerNameProvider<V>(),
            null,
            new IntegerEdgeNameProvider<E>(),
            null);
    }

    /**
     * Constructs a new GraphMLExporter object with the given ID and label
     * providers.
     *
     * @param vertexIDProvider for generating vertex IDs. Must not be null.
     * @param vertexLabelProvider for generating vertex labels. If null, vertex
     * labels will not be written to the file.
     * @param edgeIDProvider for generating vertex IDs. Must not be null.
     * @param edgeLabelProvider for generating edge labels. If null, edge labels
     * will not be written to the file.
     */
    public GraphMLExporter(
        VertexNameProvider<V> vertexIDProvider,
        VertexNameProvider<V> vertexLabelProvider,
        EdgeNameProvider<E> edgeIDProvider,
        EdgeNameProvider<E> edgeLabelProvider)
    {
        this.vertexIDProvider = vertexIDProvider;
        this.vertexLabelProvider = vertexLabelProvider;
        this.edgeIDProvider = edgeIDProvider;
        this.edgeLabelProvider = edgeLabelProvider;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Exports a graph into a plain text file in GraphML format.
     *
     * @param writer the writer to which the graph to be exported
     * @param g the graph to be exported
     */
    public void export(Writer writer, Graph<V, E> g)
        throws SAXException, TransformerConfigurationException
    {
        // Prepare an XML file to receive the GraphML data
        PrintWriter out = new PrintWriter(writer);
        StreamResult streamResult = new StreamResult(out);
        SAXTransformerFactory factory =
            (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        Transformer serializer = handler.getTransformer();
        serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(streamResult);
        handler.startDocument();
        AttributesImpl attr = new AttributesImpl();

        // <graphml>
        handler.startPrefixMapping(
            "xsi",
            "http://www.w3.org/2001/XMLSchema-instance");

        // FIXME: Is this the proper way to add this attribute?
        attr.addAttribute(
            "",
            "",
            "xsi:schemaLocation",
            "CDATA",
            "http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd");
        handler.startElement(
            "http://graphml.graphdrawing.org/xmlns",
            "",
            "graphml",
            attr);
        handler.endPrefixMapping("xsi");

        if (vertexLabelProvider != null) {
            // <key> for vertex label attribute
            attr.clear();
            attr.addAttribute("", "", "id", "CDATA", "vertex_label");
            attr.addAttribute("", "", "for", "CDATA", "node");
            attr.addAttribute("", "", "attr.name", "CDATA", "Vertex Label");
            attr.addAttribute("", "", "attr.type", "CDATA", "string");
            handler.startElement("", "", "key", attr);
            handler.endElement("", "", "key");
        }

        if (edgeLabelProvider != null) {
            // <key> for edge label attribute
            attr.clear();
            attr.addAttribute("", "", "id", "CDATA", "edge_label");
            attr.addAttribute("", "", "for", "CDATA", "edge");
            attr.addAttribute("", "", "attr.name", "CDATA", "Edge Label");
            attr.addAttribute("", "", "attr.type", "CDATA", "string");
            handler.startElement("", "", "key", attr);
            handler.endElement("", "", "key");
        }

		// change lines added
		// <key> for edge weight attribute
            attr.clear();
            attr.addAttribute("", "", "id", "CDATA", "d1");
            attr.addAttribute("", "", "for", "CDATA", "edge");
            attr.addAttribute("", "", "attr.name", "CDATA", "weight");
            attr.addAttribute("", "", "attr.type", "CDATA", "double");
            handler.startElement("", "", "key", attr);
            handler.endElement("", "", "key");
		// end of change



        // <graph>
        attr.clear();
        attr.addAttribute(
            "",
            "",
            "edgedefault",
            "CDATA",
            (g instanceof DirectedGraph) ? "directed" : "undirected");
        handler.startElement("", "", "graph", attr);

        // Add all the vertices as <node> elements...
        for (V v : g.vertexSet()) {
            // <node>
            attr.clear();
            attr.addAttribute(
                "",
                "",
                "id",
                "CDATA",
                vertexIDProvider.getVertexName(v));
            handler.startElement("", "", "node", attr);

            if (vertexLabelProvider != null) {
                // <data>
                attr.clear();
                attr.addAttribute("", "", "key", "CDATA", "vertex_label");
                handler.startElement("", "", "data", attr);

                // Content for <data>
                String vertexLabel = vertexLabelProvider.getVertexName(v);
                handler.characters(
                    vertexLabel.toCharArray(),
                    0,
                    vertexLabel.length());

                handler.endElement("", "", "data");
            }

            handler.endElement("", "", "node");
        }

        // Add all the edges as <edge> elements...
        for (E e : g.edgeSet()) {
            // <edge>
            attr.clear();
            attr.addAttribute(
                "",
                "",
                "id",
                "CDATA",
                edgeIDProvider.getEdgeName(e));
            attr.addAttribute(
                "",
                "",
                "source",
                "CDATA",
                vertexIDProvider.getVertexName(g.getEdgeSource(e)));
            attr.addAttribute(
                "",
                "",
                "target",
                "CDATA",
                vertexIDProvider.getVertexName(g.getEdgeTarget(e)));
            handler.startElement("", "", "edge", attr);

            if (edgeLabelProvider != null) {
                // <data>
                attr.clear();
                attr.addAttribute("", "", "key", "CDATA", "edge_label");
                handler.startElement("", "", "data", attr);

                // Content for <data>
                String edgeLabel = edgeLabelProvider.getEdgeName(e);
                handler.characters(
                    edgeLabel.toCharArray(),
                    0,
                    edgeLabel.length());
                handler.endElement("", "", "data");
            }

                // <data> for edge weights
                attr.clear();
                attr.addAttribute("", "", "key", "CDATA", "d1");
                handler.startElement("", "", "data", attr);

                // Content for <data>
                double edgeweight =  g.getEdgeWeight(e);
				String edgeWeight = Double.toString(edgeweight);
                handler.characters(
                    edgeWeight.toCharArray(),
                    0,
                    edgeWeight.length());
                handler.endElement("", "", "data");

            handler.endElement("", "", "edge");
        }

        handler.endElement("", "", "graph");
        handler.endElement("", "", "graphml");
        handler.endDocument();

        out.flush();
    }
}

// End GraphMLExporter.java
