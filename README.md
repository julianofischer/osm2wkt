A program for converting OpenStreetMap maps into WKT files that are compatible with the ONE simulator.

Originally hosted [here](http://www.tm.kit.edu/~mayer/osm2wkt/ "osm2wkt") and [here](http://www.chrismc.de/osm2wkt/).
Developed by [Christoph P. Mayer](https://telematics.tm.kit.edu/english/staff_mayer.php).

#osm2wkt
##openstreetmap to wkt conversion
###→ what
osm2wkt has been written from the need to use real-world maps in the ONE DTN simulator

###→ why
Getting accurate and detailed map material is complicated and often license restricted. OpenStreetMap provides a good source for free map data to use in DTN simulations using ONE. Unfortunately, OpenStreetMap uses its own XML-based language, ONE uses the WKT format.

###→ what’s the problem?
OpenStreetMap allows exporting of map views from its website (select the ‘Export’tab and use ‘OpenStreetMap-XML-Data’). Converting such XML-based maps to WKT is not that difficult, however, the map data is not useable out of the box and requires post-processing: removal of unconnected street parts, and unconnected map partitions, and fixing of street crossings which cross geographically, but have no common fix in the street data. Such post-processing is crucial for using maps in a simulator as nodes may be initially places on unconnected streets or partitions, and nodes don’t recognize that they can turn on street crossing if both streets don’t have a common vertex.

###→ osm2wkt?
osm2wkt converts files in OpenStreetMap XML Language to WKT. It performs conversion of streets, detects and removes partitioned map parts, and fixes missing data.

###→ usage
generate+cleanup from osm: >> java -jar ./osm2wkt.jar mapfile.osm
cleanup from wkt         : >> java -jar ./osm2wkt.jar mapfile.wkt
options: 
	-o outputfile - write output to given file
	-a - append to output file
	-t X Y - translate map by x=X and y=Y meters
During execution you will be asked a couple of questing on the detail of repairing of the maps. Note, that some of the algorithms are currently not as efficient that therefore some map optimizations may take very very long!

If you need to postprocess the WKT file, you can e.g. use OpenJump.

Finally, there is a larger set of graph exporters in case you need any other formats. They are provided with the source package, but you have to comment in respective lines in the Java code to enable them. Available are: DOT, GraphML, GML, text Matrix, and Naeto.

###→ who, and citation
osm2wkt has been hacked by Christoph Mayer

Thanks to Prateek Gaur for bugfixes and enhancements for exporters during his internship at KIT.

If you use osm2wkt or maps provided on this site in your work, please cite the following:

    @MISC{mayer2010osm,
      author = {Christoph P. Mayer},
      title = {osm2wkt - OpenStreetMap to WKT Conversion},
      howpublished = {http://www.chrismc.de/osm2wkt},
      year = {2010}
    }
