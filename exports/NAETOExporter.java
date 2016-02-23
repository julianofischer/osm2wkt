package osm2wkt.exports;

/* ------------------
 * NAETOExporter.java
 * ------------------
 *
 *	Note : in attributes :
 *			a) "weight" is the preferred strenght of the bond/edge
 *			b) "len" is the preferred length of the edge
 *
 *	but this is only with naeto with DOT we have weight as the integer cost of stretching the edge
 *	        
 */ 

import java.io.*;
import java.lang.Class;

import org.jgrapht.*;
import org.jgrapht.ext.EdgeNameProvider;
import org.jgrapht.ext.IntegerNameProvider;
import org.jgrapht.ext.VertexNameProvider;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.WeightedPseudograph;


/**
 * Exports a graph into a NAETO file.
 *
 * <p>For a description of the format see <a
 * href="http://en.wikipedia.org/wiki/DOT_language">
 * http://en.wikipedia.org/wiki/DOT_language</a>.</p>
 *
 * @author blah 
 */

public class NAETOExporter<V, E>
{
    //~ Instance fields --------------------------------------------------------

    private VertexNameProvider<V> vertexIDProvider;
    private VertexNameProvider<V> vertexLabelProvider;
    private EdgeNameProvider<E> edgeLabelProvider;

    //~ Constructors -----------------------------------------------------------

    /**
     * Constructs a new NAETOExporter object with an integer name provider for the
     * vertex IDs and null providers for the vertex and edge labels.
     */
    public NAETOExporter()
    {
        this(new IntegerNameProvider<V>(), null, null);
    }

    /**
     * Constructs a new NAETOExporter object with the given ID and label
     * providers.
     *
     * @param vertexIDProvider for generating vertex IDs. Must not be null.
     * @param vertexLabelProvider for generating vertex labels. If null, vertex
     * labels will not be written to the file.
     * @param edgeLabelProvider for generating edge labels. If null, edge labels
     * will not be written to the file.
     */
    public NAETOExporter(
        VertexNameProvider<V> vertexIDProvider,
        VertexNameProvider<V> vertexLabelProvider,
        EdgeNameProvider<E> edgeLabelProvider)
    {
        this.vertexIDProvider = vertexIDProvider;
        this.vertexLabelProvider = vertexLabelProvider;
        this.edgeLabelProvider = edgeLabelProvider;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Exports a graph into a plain text file in NAETO format.
     *
     * @param writer the writer to which the graph to be exported
     * @param g the graph to be exported
     */
    public void export(Writer writer, WeightedPseudograph<V, E> g)
    {
        PrintWriter out = new PrintWriter(writer);
        String indent = "  ";
        String connector;
		

        if (g instanceof DirectedGraph) {
			// this will never get executed 
            out.println("digraph G {");
            connector = " -> ";
        } else {
            out.println("graph G {");
            connector = " -- ";
        }

        for (V v : g.vertexSet()) {
            out.print(indent + getVertexID(v));

            if (vertexLabelProvider != null) {
                out.print(
                    " [label = \"" + vertexLabelProvider.getVertexName(v)
                    + "\"]");
            }

            out.println(";");
        }

        for (E e : g.edgeSet()) {
            String source = getVertexID(g.getEdgeSource(e));
            String target = getVertexID(g.getEdgeTarget(e));

			// change : line commented
            /*out.print(indent + source + connector + target);*/
			// end of change

			// change  : line added
			//out.print("name of the class of edge : " + e.getClass().getName());
			out.print(indent + source + connector + target + indent + "[len=" + g.getEdgeWeight(e) + "]");
			// end of change

            if (edgeLabelProvider != null) {
                out.print(
                    " [label = \"" + edgeLabelProvider.getEdgeName(e) + "\"]");
            }

            out.println(";");
        }

        out.println("}");

        out.flush();
    }

    /**
     * Return a valid vertex ID (with respect to the .dot language definition as
     * described in http://www.graphviz.org/doc/info/lang.html Quoted from above
     * mentioned source: An ID is valid if it meets one of the following
     * criteria:
     *
     * <ul>
     * <li>any string of alphabetic characters, underscores or digits, not
     * beginning with a digit;
     * <li>a number [-]?(.[0-9]+ | [0-9]+(.[0-9]*)? );
     * <li>any double-quoted string ("...") possibly containing escaped quotes
     * (\");
     * <li>an HTML string (<...>).
     * </ul>
     *
     * @throws RuntimeException if the given <code>vertexIDProvider</code>
     * didn't generate a valid vertex ID.
     */
    private String getVertexID(V v)
    {
        // TODO jvs 28-Jun-2008:  possible optimizations here are
        // (a) only validate once per vertex
        // (b) compile regex patterns

        // use the associated id provider for an ID of the given vertex
        String idCandidate = vertexIDProvider.getVertexName(v);

        // now test that this is a valid ID
        boolean isAlphaDig = idCandidate.matches("[a-zA-Z]+([\\w_]*)?");
        boolean isDoubleQuoted = idCandidate.matches("\".*\"");
        boolean isDotNumber =
            idCandidate.matches("[-]?([.][0-9]+|[0-9]+([.][0-9]*)?)");
        boolean isHTML = idCandidate.matches("<.*>");

        if (isAlphaDig || isDotNumber || isDoubleQuoted || isHTML) {
            return idCandidate;
        }

        throw new RuntimeException(
            "Generated id '" + idCandidate + "'for vertex '" + v
            + "' is not valid with respect to the .dot language");
    }
}

// End NAETOExporter.java
