package osm2wkt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Vector;
import java.text.DecimalFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerConfigurationException;

import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.Pseudograph;
import org.jgrapht.graph.WeightedPseudograph;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import osm2wkt.exports.*;

public class Osm2Wkt {

	private final static String XML_TAG_OSM 	= "osm";
	private final static String XML_TAG_NODE 	= "node";
	private final static String XML_TAG_ID 		= "id";
	private final static String XML_TAG_LAT 	= "lat";
	private final static String XML_TAG_LON 	= "lon";
	private final static String XML_TAG_WAY 	= "way";
	private final static String XML_TAG_ND 		= "nd";
	private final static String XML_TAG_REF 	= "ref";
	private final static String FILE_EXT_WKT	= "wkt";
	private final static String FILE_EXT_OSM	= "osm";
	private final static String WKT_TAG_BEGIN	= "LINESTRING (";
	private final static String WKT_TAG_IBEGIN  = "LINESTRING";
	private final static String WKT_TAG_BRACK1  = "(";
	private final static String WKT_TAG_BRACK2  = ")";
	private final static String WKT_TAG_END		= ")";
	private final static String WKT_TAG_BREAK	= "\n";
	private final static String WKT_TAG_MARKADD	= " ";
	private final static String WKT_TAG_MARKSEP1 = ",";
	private final static String WKT_TAG_MARKSEP2 = " ";

	private WeightedPseudograph<Long, DefaultWeightedEdge> weightedGraph;
	private WeightedPseudograph<Long, DefaultWeightedEdge> testweightedGraph;
	private HashSet<Long> fixCompletenessAddedLandmarks = new HashSet<Long>();
	private HashSet<String> edgesAlreadyAdded = new HashSet<String>();
	static int precisonFloating = 3; // use 3 decimals after comma for rounding
	double epsilon = 0.0001;
	String Snum = new String();

	private class Landmark {
		long id = 0;
		double latitude = 0;
		double longitude = 0;
		double x = 0;
		double y = 0;		

		public boolean equals(Object o) {
			if(this == o) return true;
			if (o == null) return false;
			if(!this.getClass().isInstance(o)) return false;
			Landmark ol = (Landmark)o;

			return (this.id == ol.id );
		}
	}

	private HashMap<Long,Vector<Long>> streets = new HashMap<Long,Vector<Long>>();
	private HashMap<Long,Landmark> landmarks = new HashMap<Long,Landmark>();

	private Long nextLandmarkIndex(){
		Long i=new Long(landmarks.size());
		for( ; true; i++){
			if(landmarks.containsKey(i) == false && i > 0) 
			{	
				//System.out.println("i=" + i);
				return i;
			}
		}
	}

	public static double round(double d, int decimalPlace){
		BigDecimal bd = new BigDecimal(Double.toString(d));
		bd = bd.setScale(decimalPlace,BigDecimal.ROUND_HALF_UP);
		return bd.doubleValue();
	}

	private boolean readOsm(String filename){
		System.out.println("reading in openstreetmap xml ...");

		try {
			// check if file exists
			File file = new File(filename);
			if(!file.exists()){
				System.out.println("osm file " + filename + " does not exist");
				return false;
			}

			// read in xml
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			doc.getDocumentElement().normalize();

			// check for valid openstreetmap xml root tag
			// this works because we are currently at the root element
			/*
			 *	Even this might work  
			 *  Element root = doc.getDocumentElement();
			 *
			 */ 
			if(! doc.getDocumentElement().getNodeName().equals(XML_TAG_OSM)){
				System.out.println("invalid osm file, root element is " 
						+ doc.getDocumentElement().getNodeName()
						+ " but should be " + XML_TAG_OSM);
				return false;
			}

			// read in all landmarks
			NodeList landmarkList = doc.getElementsByTagName(XML_TAG_NODE);
			for( int s=0; s<landmarkList.getLength(); s++ ){
				Node markNode = landmarkList.item(s);
				if( markNode.getNodeType() != Node.ELEMENT_NODE ) continue;
				Element markElement = (Element)markNode;   
				// http://stackoverflow.com/questions/132564/whats-the-difference-between-an-element-and-a-node-in-xml

				Attr idAttr = markElement.getAttributeNode(XML_TAG_ID);
				Attr idLat = markElement.getAttributeNode(XML_TAG_LAT);
				Attr idLon = markElement.getAttributeNode(XML_TAG_LON);

				if(idAttr == null || idLat == null || idLon == null){
					System.out.println("missing attribute in landmark " 
							+ markNode.getNodeValue());       
					continue;
				}

				Landmark landObj = new Landmark();
				landObj.id = Long.valueOf(idAttr.getValue());
				landObj.latitude = Double.valueOf(idLat.getValue());
				landObj.longitude = Double.valueOf(idLon.getValue());

				landmarks.put(landObj.id, landObj);
			}

			// read in all streets
			NodeList wayList = doc.getElementsByTagName(XML_TAG_WAY);
			for( int s=0; s<wayList.getLength(); s++ ){
				Node wayNode = wayList.item(s);
				if( wayNode.getNodeType() != Node.ELEMENT_NODE ) continue;
				Element wayElement = (Element)wayNode;

				Attr idAttr = wayElement.getAttributeNode(XML_TAG_ID);
				if(idAttr == null){
					System.out.println("missing attribute in street "
							+ wayNode.getNodeValue());
					continue;
				}

				Long streetId = Long.valueOf(idAttr.getValue());
				Vector<Long> streetLandmarks = new Vector<Long>();

				// get landmarks for this street
				NodeList ndList = wayNode.getChildNodes();
				for( int t=0; t<ndList.getLength(); t++){
					Node ndNode = ndList.item(t);
					if( ndNode.getNodeType() != Node.ELEMENT_NODE ) continue;
					if( ndNode.getNodeName() != XML_TAG_ND) continue;
					Element ndElement = (Element)ndNode;

					Attr refAttr = ndElement.getAttributeNode(XML_TAG_REF);
					if(refAttr == null){
						System.out.println("missing attribute in street landmark "
								+ ndNode.getNodeValue());
					}

					streetLandmarks.add(Long.valueOf(refAttr.getValue()));
				}

				// if we found landmarks for this street add street
				if(!streetLandmarks.isEmpty()){
					streets.put(streetId, streetLandmarks);
				}else{
					System.out.println("found no landmark childs for street " 
							+ wayNode.getNodeValue());
				}
			}

		} catch (Exception e) {
			System.out.println("reading osm file failed: " + e.getLocalizedMessage());
			e.printStackTrace();
			return false;
		}

		System.out.println("parsing osm found " + streets.size() + " streets and " 
				+ landmarks.size() + " landmarks");

		return true;
	}

	//****************************************************************************
	// taken from source code of ONE simulator

	protected void skipUntil(Reader r, char until) throws IOException {
		char c;
		do {
			c = (char)r.read();
		} while (c != until && c != (char)-1);
	}

	public String readNestedContents(Reader r) throws IOException {
		StringBuffer contents = new StringBuffer();
		int parOpen; // nrof open parentheses
		char c = '\0';

		skipUntil(r,'(');
		parOpen = 1;

		while (c != (char)-1 && parOpen > 0) {
			c = (char)r.read();
			if (c == '(') {
				parOpen++;
			}
			if (c == ')') {
				parOpen--;
			}
			if (Character.isWhitespace(c)) {
				c = ' '; // convert all whitespace to basic space
			}
			contents.append(c);
		}

		contents.deleteCharAt(contents.length()-1);	// remove last ')'
		return contents.toString();
	}

	//****************************************************************************

	private boolean readWkt(String filename){                      
		System.out.println("reading in wkt format ...");		   

		try {
			// check is file exists
			File file = new File(filename);
			if(!file.exists()){
				System.out.println("wkt file " + filename + " does not exist");
				return false;
			}

			FileReader freader = new FileReader(file);
			BufferedReader reader = new BufferedReader(freader);
			long markid = 0;
			long streetid = 0;

			while(reader.ready()){

				String line = readNestedContents(reader);
				line = line.trim();
				if(line.length() == 0) continue;

				String parts[] = line.split(WKT_TAG_MARKSEP1);
				Vector<Long> street = new Vector<Long>();

				for(String item : parts){
					item = item.trim();
					String onetwo[] = item.split(WKT_TAG_MARKSEP2);
					if(onetwo.length != 2){
						System.out.println("invalid coordinate pair: " + item);
						continue;
					}

					double x = Double.parseDouble(onetwo[0]);
					double y = Double.parseDouble(onetwo[1]);

					// known landmark or new one?
					long currentmark = -1;
					for(Long i : landmarks.keySet()){
						Landmark m = landmarks.get(i);

						//if(this.plainDistance(m.x, m.y, x, y) < 10){
						if(Math.abs(m.x - x) < epsilon && Math.abs(m.y - y) < epsilon){
							currentmark = i;
							break;
						}
					}

					// need to generate new landmark
					if(currentmark == -1){
						Landmark lm = new Landmark();
						currentmark = markid++;
						lm.id = currentmark;
						lm.x = x;
						lm.y = y;
						landmarks.put(lm.id, lm);
						street.add(currentmark);
					}else
						street.add(currentmark);

				} //for(String item : parts)

				streets.put(streetid++, street);

			} 

		}catch(Exception e){
			System.out.println("reading wkt file failed: " + e.getLocalizedMessage());
			e.printStackTrace();
			return false;
		}

		System.out.println("parsing wkt found " + streets.size() + " streets");
		return true;
	}

	private boolean fixCompleteness(){
		System.out.println("checking landmark completeness for all streets ...");

		for(Vector<Long> l : streets.values()){
			for(Long mark : l){
				if(!landmarks.containsKey(mark)){
					System.out.println("landmarks " + mark + " for street not found");
					return false;
				}
			}
		}

		System.out.println("all required landmarks available for all streets");

		System.out.println("do you want to fix missing landmarks? " +
		"this will take very long but can heavily reduce map partitioning");
		System.out.print("type 'y' or 'n': ");
		int r = 0;
		try {
			r = System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(r != 'y') return true;     // ?? on entering Y why are we returning shouldn't we start comparing

		System.out.println("checking for missing landmarks for crossing street parts. this may take a while ...");

		boolean changed = false;  
		long missingLandmarks = 0;

		do{
			changed = false;

			// walk all streets
			for(Long streetA : streets.keySet()){
				Vector<Long> streetPointsA = streets.get(streetA);

				int indexA = 0;
				long lastAP = -1;
				long currentAP = -1;
				Landmark lastAL = null;
				Landmark currentAL = null;

				// iterate over every street part of the current street
				for(Iterator<Long> iterpA = streetPointsA.iterator(); iterpA.hasNext(); indexA++){
					Long pA = iterpA.next();

					if(lastAP == -1 || lastAL == null){
						lastAP = pA;
						lastAL = landmarks.get(pA);
						continue;
					}

					// street part goes from lastP to currentP
					currentAP = pA;
					currentAL = landmarks.get(pA);

					// check to see if this street part crosses another street part
					// this indicates a missing landmark at this position

					// walk over possible crossing street
					for(Long streetB : streets.keySet()){
						Vector<Long> streetPointsB = streets.get(streetB);

						// don't check street with itself
						if((long)streetA == (long)streetB) continue;


						// unchecked street parts, go...
						int indexB = 0;
						long lastBP = -1;
						long currentBP = -1;
						Landmark lastBL = null;
						Landmark currentBL = null;

						// walk over the street part of possible crossing street
						for(Iterator<Long> iterpB = streetPointsB.iterator(); iterpB.hasNext(); indexB++){
							Long pB = iterpB.next();

							//System.out.println("running " + indexA + " against index " + indexB);

							if(lastBP == -1 || lastBL == null){
								lastBP = pB;
								lastBL = landmarks.get(pB);
								continue;
							}
							currentBP = pB;
							currentBL = landmarks.get(pB);
							// street part from lastBP to currentBP

							// check for crossings in the two street parts
							// [lastAP,currentAP] and [lastBP,currentBP]

							Landmark crossing = checkCrossing(
									lastAL, currentAL,
									lastBL, currentBL
							);

							if(crossing != null){

								// add this id to a set
								if(!streetPointsA.contains(crossing.id)){
									streetPointsA.add(indexA, crossing.id);
									//System.out.println("Adding landmark to street A");
									changed = true;
								}

								if(!streetPointsB.contains(crossing.id)){
									streetPointsB.add(indexB, crossing.id);
									//System.out.println("Adding landmark to street B");
									changed = true;
								}

								if(changed){
									missingLandmarks++;
									fixCompletenessAddedLandmarks.add(crossing.id);	
									break;
								}

							} //if(crossing != null)

							lastBP = currentBP;
							lastBL = currentBL;

						} //for(Iterator<Long> iterpB = streetPointsB.iterator(); iterpB.hasNext(); )

						if(changed) break;

					} //for(Long streetB : streets.keySet())

					// move to next part
					lastAP = currentAP;
					lastAL = currentAL;

					if(changed) break;

				} //for(Iterator<Long> iterpA = streetPointsA.iterator(); iterpA.hasNext(); )

				if(changed) break;

			} //for(Long streetA : streets.keySet())

		}while(changed);

		//print all the landmarks that were added
		//Iterator it = fixCompletenessAddedLandmarks.iterator();
		//while(it.hasNext())
		//{
		//	Long id = (Long) it.next();
		//	System.out.println(" : " + id);
		//}

		System.out.println("inserted " + missingLandmarks 
				+ " missing landmarks. currently have "
				+ landmarks.size() + " landmarks");
		return true;
	}

	private Landmark checkCrossing(Landmark a1, Landmark a2, Landmark b1, Landmark b2){
		// see http://www.ucancode.net/faq/C-Line-Intersection-2D-drawing.htm
		// for 2d line crossing checks

		// line a --> aA*x+aB*y=aC
		double aA = a2.y - a1.y;
		double aB = a1.x - a2.x;
		double aC = aA*a1.x + aB*a1.y;

		// line b --> bA*x+bB*y=bC
		double bA = b2.y - b1.y;
		double bB = b1.x - b2.x;
		double bC = bA*b1.x + bB*b1.y;

		// crossing
		double det = aA*bB - bA*aB;
		if(det == 0) // lines are parallel
			return null; 

		// set precision
		double x = (bB*aC - aB*bC)/det;
		x = Osm2Wkt.round(x, Osm2Wkt.precisonFloating);
		//System.out.println("x = " + x);

		double y = (aA*bC - bA*aC)/det;		
		y = Osm2Wkt.round(y, Osm2Wkt.precisonFloating);
		//System.out.println("y = " + y);

		// check for x validity
		boolean valid =    
			(Math.min(a1.x,a2.x) <= x) && (x <= Math.max(a1.x,a2.x)) && 
			(Math.min(a1.y,a2.y) <= y) && (y <= Math.max(a1.y,a2.y)) && 
			(Math.min(b1.x,b2.x) <= x) && (x <= Math.max(b1.x,b2.x)) && 
			(Math.min(b1.y,b2.y) <= y) && (y <= Math.max(b1.y,b2.y))  ;

		// crossing but not within the line dimensions
		if(!valid) return null;

		// valid crossing -> can we use existing landmark?
		Landmark crossing = null;
		for(Landmark m : landmarks.values()){
			if(Math.abs(m.x - x) < epsilon && Math.abs(m.y - y) < epsilon){
				crossing = m;
				break;
			}
		}

		if(crossing == null){
			crossing = new Landmark();
			crossing.id = nextLandmarkIndex();
			crossing.x = x;
			crossing.y = y;
			landmarks.put(crossing.id, crossing);
		}

		return crossing;
	}

	private boolean transformCoordinates(){
		// in this function we have to restrict the precision we calculate the x, y coordinates
		// of the point to avoid floating point errors
		System.out.println("transforming geographic landmarks ...");

		// search for top,bottom,left,right marks
		// latitude is horizontal from -90 -- 0 -- 90
		// longitude is vertical from -180 -- 0 -- 180
		double latMin, latMax, lonMin, lonMax;

		// initialize switched to move to value in for loop
		latMin = 90; latMax = -90;
		lonMin = 180; lonMax = -180;

		// search for geographic bounds
		for(Landmark l : landmarks.values()){
			if(l.latitude < latMin)  latMin = l.latitude;
			if(l.latitude > latMax)  latMax = l.latitude;
			if(l.longitude < lonMin) lonMin = l.longitude;
			if(l.longitude > lonMax) lonMax = l.longitude;	
		}

		System.out.println("found geographic bounds:"
				+ " latitude from " + latMin + " to " + latMax
				+ " longitude from " + lonMin + " to " + lonMax);


		double width = geoDistance(latMin, lonMin, latMin, lonMax);
		double height = geoDistance(latMin, lonMin, latMax, lonMin);

		System.out.println("geographic area dimensions are: height " 
				+ height + "m, width " + width + "m");

		// put coordinate system to upper left corner with (0,0), output in meters
		for(Landmark l : landmarks.values()){
			l.x = geoDistance(l.latitude, l.longitude, l.latitude, lonMin);
			l.y = geoDistance(l.latitude, l.longitude, latMin, l.longitude);
		}

		return true;
	}

	private double plainDistance(Landmark mark1, Landmark mark2){
		return plainDistance(mark1.x, mark1.y, mark2.x, mark2.y);
	}

	private double plainDistance(double x1,double y1, double x2, double y2){
		double x = x1 - x2;
		double y = y1 - y2;
		double distance = Math.sqrt(x*x + y*y);
		distance = Osm2Wkt.round(distance, Osm2Wkt.precisonFloating);
		return distance;	
	}

	private double geoDistance(double lat1, double lon1, double lat2, double lon2){
		// return distance between two gps fixes in meters
		double R = 6371; 
		double dLat = Math.toRadians(lat2-lat1);
		double dLon = Math.toRadians(lon2-lon1); 
		double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
		Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * 
		Math.sin(dLon/2) * Math.sin(dLon/2); 
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		double distance = (R * c * 1000.0d);
		distance = Osm2Wkt.round(distance, Osm2Wkt.precisonFloating);
		return distance;
	}

	private boolean translate(int x, int y){
		if(x == 0 && y == 0) return true;
		System.out.println("translating map by x=" + x + " and y=" + y);

		for(Landmark mark : landmarks.values()){
			mark.x += x;
			mark.y += y;
		}

		System.out.println("translation done");
		return true;
	}

	private boolean simplifyModel(boolean repair){

		for(int i=1; i<=10; i++){
			System.out.println("simplicatiation run " + i + " of max 10");
			boolean connected = simplifyGraph(repair);
			if(connected) break;
		}

		return true;
	}

	private boolean simplifyGraph(boolean repair){
		System.out.println("simplyfing model, removing unconnected parts ...");

		// create a graph using JGraphT
		Pseudograph<Long, DefaultEdge> graph = new Pseudograph<Long, DefaultEdge>(DefaultEdge.class);

		// add all landmarks as vertexes
		for(Long l : landmarks.keySet()){
			Landmark lm = landmarks.get(l);
			assert((long)l == lm.id);
			graph.addVertex(lm.id);
		}

		// add all streets as edges between landmarks
		for(Long s : streets.keySet()){
			Vector<Long> marks = streets.get(s);
			Landmark last = null;
			Landmark current = null;

			for(Long m : marks) {
				current = landmarks.get(m);
				if(last == null){
					last = current;
					continue;
				}

				assert(graph.containsVertex(last.id) && 
						graph.containsVertex(current.id));
				graph.addEdge(new Long(last.id), new Long(current.id));
				last = current;
			}
		} //for(Long s : streets.keySet())


		// check graph for connectivity, are there unconnected partitions?
		ConnectivityInspector<Long, DefaultEdge> inspector = 
			new ConnectivityInspector<Long, DefaultEdge>(graph);
		if(inspector.isGraphConnected()){
			System.out.println("graph is connected, nothing to simplify");
			return true;
		}

		// we have partitions :(
		System.out.println("graph is not connected, analyzing partitions ...");
		List<Set<Long>> partitions = inspector.connectedSets();
		System.out.print("found " + partitions.size() + " partitions: ");

		// print the different partition sizes
		long count = 0;
		for(Set<Long> partition : partitions){
			System.out.print("[" + partition.size() + "] ");
			count += partition.size();
		}
		System.out.print("\n");		

		// search for the largest partition, this one will be used
		Set<Long> largestpartition = null;
		for(Set<Long> partition : partitions){
			if(largestpartition == null)
				largestpartition = partition;
			else if(partition.size() > largestpartition.size())
				largestpartition = partition;
		}

		System.out.println("selecting largest partition of landmark size " 
				+ largestpartition.size()
				+ ". removing unselected partitions of estimated " 
				+ Math.abs(count-largestpartition.size())
				+ " landmarks");

		if(!repair){
			System.out.println("not reparing");
			return true;
		}


		// have we encountered cases in which the graph came out to be unconnected
		// collect all vertices that are in the other than largest partitions
		HashSet<Long> verticesRemove = new HashSet<Long>();
		for(Set<Long> partition : partitions){
			if(partition.size() == largestpartition.size()) continue;
			verticesRemove.addAll(partition);
		}

		System.out.println("analyzed " + verticesRemove.size() + " landmarks to remove");

		// remove vertices and edges from unused partitions
		int countRemovedLandmarks = 0;
		int countRemovedStreets = 0;

		for(Long vertice : verticesRemove){

			boolean removedL;
			do{
				// remove all landmark 
				removedL = false;
				for(Long lid : landmarks.keySet()){
					if(((long)lid) == ((long)vertice)){
						landmarks.remove(lid); 
						removedL = true;
						countRemovedLandmarks++;
						break;
					}
				}
			}while(removedL);

			boolean removedS;
			do{
				// remove all streets that contain this vertice
				removedS = false;
				for(Long sid : streets.keySet()){
					Vector<Long> street = streets.get(sid);
					if(street.contains(vertice)){
						streets.remove(sid);
						removedS = true;
						countRemovedStreets++;
						break;
					}
				}
			}while(removedS);

		} //for(Long vertice : verticesRemove)

		System.out.println("removed " + countRemovedStreets 
				+ " unconnected streets and " + countRemovedLandmarks 
				+ " unconnected landmarks. know have "
				+ streets.size() + " streets in map built upon "
				+ landmarks.size() + " landmarks");
		return false;
	}

	private boolean writeWkt(String wktfile, boolean append){
		System.out.println("writing wkt file ...");

		try {
			File wkt = new File(wktfile);
			if(!append){
				if(wkt.exists()) wkt.delete();
				wkt.createNewFile();
			}
			FileWriter wktstream = new FileWriter(wkt, append);
			if(append){
				wktstream.append("\n");
				wktstream.append("\n");
				wktstream.append("\n");
			}

			for(Vector<Long> s : streets.values()){
				wktstream.append(WKT_TAG_BEGIN);

				for(int i=0; i<s.size(); i++){
					Long l = s.elementAt(i);
					Landmark mark = landmarks.get(l);
					wktstream.append(mark.x + WKT_TAG_MARKADD + mark.y);
					if(i+1 < s.size()) wktstream.append(WKT_TAG_MARKSEP1 + WKT_TAG_MARKSEP2);
				}

				wktstream.append(WKT_TAG_END + WKT_TAG_BREAK);
			}

			wktstream.close();

		} catch (IOException e) {
			System.out.println("writing wkt file failed: " + e.getLocalizedMessage());
			e.printStackTrace();
			return false;
		}

		System.out.println("writing wkt file done");
		return true;
	}

	private boolean PreparingWeightedGraph(){
		System.out.println("Preparing weighted graph called");

		// create a graph using JGraphT
		weightedGraph = new WeightedPseudograph<Long, DefaultWeightedEdge>(DefaultWeightedEdge.class);
		int numEdgesAdded = 0;

		// add all landmarks as verteces
		for(Long l : landmarks.keySet()){
			Landmark lm = landmarks.get(l);
			assert((long)l == lm.id);
			weightedGraph.addVertex(lm.id);
		}

		// add all streets as edges between landmarks
		for(Long s : streets.keySet()){
			Vector<Long> marks = streets.get(s);
			Landmark last = null;
			Landmark current = null;

			for(Long m : marks){
				current = landmarks.get(m);
				if(last == null){
					last = current;
					continue;
				}

				assert(weightedGraph.containsVertex(last.id) && 
						weightedGraph.containsVertex(current.id));
				// I hope adding edges more than once will not create any problem
				// Yes it creates a lot of trouble ... 
				// 1) the pseudograph implementation screws up the weight parameter
				// 2) this is the not the graph we that we have obtained from osm data
				//    osm data only gives you one edge between two points
				//    For osm we have the specification of two joints which
				//    form the end points of the edges and no specification 
				//    of the edge between the two vertices is provided. So when 
				//    we say we have and edge between two points it the the same 
				//    edge how many ever times we say that and in what order we say that
				String edge1 = (new Long(last.id)).toString() + " " + (new Long(current.id)).toString();
				String edge2 = (new Long(current.id)).toString() + " " + (new Long(last.id)).toString();
				double weight;

				if(!edgesAlreadyAdded.contains(edge1) && !edgesAlreadyAdded.contains(edge2))
				{
					weightedGraph.addEdge(new Long(last.id), new Long(current.id));
					numEdgesAdded += 1;
					// following two vertices are already connected by an edge
					edge1 = (new Long(last.id)).toString() + " " + (new Long(current.id)).toString();
					edgesAlreadyAdded.add(edge1);
					edge2 = (new Long(current.id)).toString() + " " + (new Long(last.id)).toString();
					edgesAlreadyAdded.add(edge2);

					DefaultWeightedEdge weightedEdge = weightedGraph.getEdge(new Long(last.id), new Long(current.id));
					weight = plainDistance(last, current);
					weightedGraph.setEdgeWeight(weightedEdge, weight);

					last = current;
				}
				else
				{
					System.out.println("the edge has already been added to the graph");
				}

				last = current;
			}
		} //for(Long s : streets.keySet())

		return true;  // everything was completed successfully
	}


	//// dot format

	private boolean exportDOT(String destfile){
		destfile = destfile + "." + "SNA_DOT.dat";
		// first open a File  
		// the associate a FileWriter to it 
		// keep appending data to Filewrtier stream 
		// then close the FileWriter
		// I do not know why but probably you do not need to close the file

		File file = new File(destfile);
		try{
			FileWriter dotStream = new FileWriter(file, false); // false as no appending is to be done
			// i have make weightedgraph available to this function
			DOTExporter<Long, DefaultWeightedEdge> dotexport = new DOTExporter<Long, DefaultWeightedEdge>();
			// create a new writer with a new file name
			dotexport.export(dotStream, weightedGraph);
		}catch(java.io.IOException error){
			// whatever
		}
		System.out.println("graph exported in dot format to " + destfile );
		return true;
	}

	//// naeto format

	private boolean exportNAETO(String destfile){
		destfile = destfile + "." + "SNA_NAETO.dat";
		// first open a File  
		// the associate a FileWriter to it 
		// keep appending data to Filewrtier stream 
		// then close the FileWriter
		// I do not know why but probably you do not need to close the file

		File file = new File(destfile);
		try{
			FileWriter naetoStream = new FileWriter(file, false); // false as no appending is to be done
			// i have make weightedgraph available to this function
			NAETOExporter<Long, DefaultWeightedEdge> naetoexport = new NAETOExporter<Long, DefaultWeightedEdge>();
			// create a new writer with a new file name
			naetoexport.export(naetoStream, weightedGraph);
		}catch(java.io.IOException error){
			// whatever
		}
		System.out.println("graph exported in naeto format to " + destfile );
		return true;
	}


	//GraphML
	private boolean exportGraphML(String destfile){
		destfile = destfile + "." + "SNA_GraphML.dat";
		// first open a File  
		// the associate a FileWriter to it 
		// keep appending data to Filewrtier stream 
		// then close the FileWriter
		// I do not know why but probably you do not need to close the file

		File file = new File(destfile);
		try{
			FileWriter graphmlStream = new FileWriter(file, false); // false as no appending is to be done
			// i have make weightedgraph available to this function
			GraphMLExporter<Long, DefaultWeightedEdge> graphmlexport = new GraphMLExporter<Long, DefaultWeightedEdge>();
			// create a new writer with a new file name
			graphmlexport.export(graphmlStream, weightedGraph);
		}catch(java.io.IOException error){
			// whatever
		}catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("graph exported in GraphML format to " + destfile );
		return true;
	}


	// this function is to remove edges with very small weights
	// they are a result of edges being added more than once or
	// edges adding due to floating point comparision error 
	// while fixCompleteness
	private boolean removeBogusEdges()
	{
		System.out.println(" ");
		// ConcurrentModificationException occurs because i have a iterator iterating through
		//                                 the edgeSet and i'm modifing it at the same time
		//                                 this is unacceptable
		// So make a list of these edge while iterating and then remove them from the graph
		ArrayList<DefaultWeightedEdge> removeEdge = new ArrayList<DefaultWeightedEdge>();
		for (DefaultWeightedEdge e : weightedGraph.edgeSet()) 
		{
			double weight = weightedGraph.getEdgeWeight(e);
			if(Math.abs(weight - 1.0 ) < 0.0001 || weight <= 1.0E-09)
			{
				System.out.println("Edge with small weight : " + weight);
				removeEdge.add(e);
			}
			//remove edges in the List
		}

		int size = weightedGraph.edgeSet().size();
		System.out.println("size of the graph before removing edges : " + size);
		size = removeEdge.size();
		System.out.println("total number of unnecessary edges : " + size);

		for(DefaultWeightedEdge edge : removeEdge)
		{
			// checking whether edge has been successfully removed
			weightedGraph.removeEdge(edge);
			if(!weightedGraph.containsEdge(edge))
				System.out.println("edge successfully removed");
			else
				System.out.println("failure in removing the edge ");
		}

		size = weightedGraph.edgeSet().size();
		System.out.println("size of the graph after removing edges : " + size);
		// after the removal the graph may have some partitions so try and get the biggest partitions of these
		return true;	
	}


	private static void printUsage(){
		System.out.println("Usage\n" 
				+ "\t generate+cleanup from osm: >> osm2wkt mapfile.osm" + "\n"
				+ "\t cleanup from wkt         : >> osm2wkt mapfile.wkt" + "\n"
				+ "\t options: " + "\n"
				+ "\t \t -o outputfile - write output to given file" + "\n"
				+ "\t \t -a - append to output file" + "\n"
				+ "\t \t -t X Y - translate map by x=X and y=Y meters" + "\n"
		);	
	}

	public static void main(String[] args) {
		
		System.out.println("osm2wkt v1.2.0- convert " +
		"openstreetmap to wkt - Christoph P. Mayer - mayer@kit.edu");
		if(args.length < 1 || args.length > 7){
			printUsage();
			return;
		}

		boolean append = false;
		int translateX = 0;
		int translateY = 0;
		String file = "";
		String destfile = "";

		try{
			file = args[args.length-1];

			for(int i=0; i<args.length; i++){
				if(args[i].equals("-a")){
					append = true;
				}
				if(args[i].equals("-t")){
					translateX = Integer.parseInt(args[i+1]);
					translateY = Integer.parseInt(args[i+2]);
				}
				if(args[i].equals("-o")){
					destfile = args[i+1];
				}
			}
		}catch(Exception e){
			System.out.println("parsing command line arguments failed");
			printUsage();
			return;
		}

		if(destfile.length() == 0) destfile = file + "." + FILE_EXT_WKT;
		Osm2Wkt obj = new Osm2Wkt();
		String filelower = file.toLowerCase();

		System.out.println("converting file " + file + " ...");

		if(filelower.endsWith(FILE_EXT_OSM)){

			if(!obj.readOsm(file)) 						return;
			if(!obj.transformCoordinates()) 			return;
			// this is where( simplifyGraph ) the graph is constructed 
			// so you can create a weighted graph here and see if we get weird edges
			// that is edges with very small weights or edges with weight of 1 
			// also calculate the number of nodes and edges in the graph at this point
			if(!obj.fixCompleteness()) 					return;
			if(!obj.simplifyModel(true)) 				return; 
			if(!obj.translate(translateX, translateY)) 	return;
			// just after this function gets completed i think the graph 
			// is complete and we can make a pseudo graph out of it and
			// export to a a format which is acceptable by a SNA software
			// also one can input this graph to SNA libraries existing in java itself
			if(!obj.PreparingWeightedGraph()) 			return;
			if(!obj.removeBogusEdges())					return;
			if(!obj.simplifyModel(true))				return;
			//if(!obj.exportDOT(destfile)) 	return;
			//if(!obj.exportNAETO(destfile)) 	return;
			//if(!obj.exportGml(destfile)) 	return;
			//if(!obj.exportGraphML(destfile)) 	return;
			//if(!obj.exportMatrix(destfile)) 	return;
			if(!obj.writeWkt(destfile, append))			return;

		}else if(filelower.endsWith(FILE_EXT_WKT)){

			if(!obj.readWkt(file)) 						return;
			// try and prepare a weighted graph right now
			// and see if there are any bogus edges
			// then clear the graph removeAllEdges removeAllVertices
			// and later whenver you want reconstruct the graph
			if(!obj.fixCompleteness()) 					return;
			if(!obj.simplifyModel(true)) 				return;
			if(!obj.translate(translateX, translateY)) 	return;
			if(!obj.PreparingWeightedGraph()) 	        return;
			if(!obj.removeBogusEdges())					return;
			// fixCompleteness
			if(!obj.fixCompleteness())  				return;
			// simplifyModel
			if(!obj.simplifyModel(true))				return;
			//exporting the graph
			//if(!obj.exportDOT(destfile)) 	return;
			//if(!obj.exportNAETO(destfile)) 	return;
			//if(!obj.exportGraphML(destfile)) 	return;
			//if(!obj.exportGml(destfile)) 	return;
			//if(!obj.exportMatrix(destfile)) 	return;
			//done exporting now writing files 
			if(!obj.writeWkt(destfile, append))			return;
		}else{
			System.out.println("unknown file extension in " + filelower);
			return;
		}

		System.out.println("written to new file " + destfile);
		System.out.println("done!");
	}

}
