package util;

import com.sun.deploy.util.StringUtils;
import org.w3c.dom.*;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class FileReader {

    private final static String WKT_TAG_BEGIN = "LINESTRING (";
    private final static String WKT_TAG_END = ")";
    private final static String WKT_TAG_BREAK = "\n";
    private final static String WKT_TAG_MARKADD = " ";
    private final static String WKT_TAG_MARKSEP1 = ",";
    private final static String WKT_TAG_MARKSEP2 = " ";
    private final static String XML_TAG_OSM = "osm";
    private final static String XML_TAG_NODE = "node";
    private final static String XML_TAG_ID = "id";
    private final static String XML_TAG_LAT = "lat";
    private final static String XML_TAG_LON = "lon";
    private final static String XML_TAG_WAY = "way";
    private final static String XML_TAG_ND = "nd";
    private final static String XML_TAG_REF = "ref";

    public static void readFile() {
        BufferedReader reader;
        try {
            InputStream stream = FileReader.class.getClassLoader().getResourceAsStream("taxidata/Taxi_01.txt");
            reader = new BufferedReader(new InputStreamReader(stream));
            String line = reader.readLine();
            while (line != null) {
                System.out.println(line);
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Landmark> getLandmarksFromFile() {
        BufferedReader reader;
        List<Landmark> landmarks = new ArrayList<Landmark>();
        short longitudePosition = 2;
        short latitudePosition = 3;
        double latMin = 30.8491;
        double latMax = 31.4093;
        double lonMin = 121.0222;
        double lonMax = 121.877;

        try {
            InputStream stream = FileReader.class.getClassLoader().getResourceAsStream("taxidata/Taxi_81044");
            reader = new BufferedReader(new InputStreamReader(stream));
            String line = reader.readLine();
            int index = 0;
            while (line != null) {
                String[] landMarkText = line.split(",");
                if (landMarkText.length > 2) {
                    if (latMin <= Double.valueOf(landMarkText[latitudePosition])
                            && Double.valueOf(landMarkText[latitudePosition]) <= latMax
                            && lonMin <= Double.valueOf(landMarkText[longitudePosition])
                            && Double.valueOf(landMarkText[longitudePosition]) <= lonMax
                    ) {
                        Landmark landmark = new Landmark();
                        landmark.setLongitude(Double.valueOf(landMarkText[longitudePosition]));
                        landmark.setLatitude(Double.valueOf(landMarkText[latitudePosition]));
                        landmark.setId(index);
                        landmarks.add(landmark);
                        index++;
                    }
                }
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return landmarks;
    }


    public static List<Landmark> readOsm(String filename) {
        System.out.println("reading in openstreetmap xml ...");
        List<Landmark> landmarks = new ArrayList<Landmark>();
        HashMap<Long, Vector<Long>> streets = new HashMap<Long, Vector<Long>>();
        try {
            // check if file exists
            //File file = new File(filename);

            File file = new File(util.FileReader.class.getClassLoader().getResource("map.osm").getFile());
            if (!file.exists()) {
                System.out.println("osm file " + filename + " does not exist");
            }

            // read in xml
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setValidating(false);
            dbf.setFeature("http://xml.org/sax/features/namespaces", false);
            dbf.setFeature("http://xml.org/sax/features/validation", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

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
            if (!doc.getDocumentElement().getNodeName().equals(XML_TAG_OSM)) {
                System.out.println("invalid osm file, root element is "
                        + doc.getDocumentElement().getNodeName()
                        + " but should be " + XML_TAG_OSM);
            }

            NodeList wayList = doc.getElementsByTagName(XML_TAG_WAY);
            Stream<Node> wayListNodeStream = IntStream.range(0, wayList.getLength()).mapToObj(wayList::item);
            wayListNodeStream.parallel().forEach(way -> {
                Node wayNode = way;
                if (wayNode.getNodeType() != Node.ELEMENT_NODE) return;
                Element wayElement = (Element) wayNode;

                Attr idAttr = wayElement.getAttributeNode(XML_TAG_ID);
                if (idAttr == null) {
                    System.out.println("missing attribute in street "
                            + wayNode.getNodeValue());
                    return;
                }

                Long streetId = Long.valueOf(idAttr.getValue());
                Vector<Long> streetLandmarks = new Vector<Long>();

                // get landmarks for this street
                NodeList ndList = wayNode.getChildNodes();
                for (int t = 0; t < ndList.getLength(); t++) {
                    Node ndNode = ndList.item(t);
                    if (ndNode.getNodeType() != Node.ELEMENT_NODE) continue;
                    if (ndNode.getNodeName() != XML_TAG_ND) continue;
                    Element ndElement = (Element) ndNode;

                    Attr refAttr = ndElement.getAttributeNode(XML_TAG_REF);
                    if (refAttr == null) {
                        System.out.println("missing attribute in street landmark "
                                + ndNode.getNodeValue());
                    } else {
                        streetLandmarks.add(Long.valueOf(refAttr.getValue()));
                    }

                }

                // if we found landmarks for this street add street
                if (!streetLandmarks.isEmpty()) {
                    streets.put(streetId, streetLandmarks);
                } else {
                    System.out.println("found no landmark childs for street "
                            + wayNode.getNodeValue());
                }
            });

        } catch (Exception e) {
            System.out.println("reading osm file failed: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        return landmarks;
    }

    public static void findFilesInBounds() {
        short longitudePosition = 2;
        short latitudePosition = 3;
        double latMin = 30.8491;
        double latMax = 31.4093;
        double lonMin = 121.0222;
        double lonMax = 121.877;

        List<String> fileNames = new ArrayList<String>();
        AtomicInteger count = new AtomicInteger();

        try {
            try (Stream<Path> paths = Files.walk(Paths.get(FileReader.class.getClassLoader().getResource("alltaxi").getPath()))) {
                paths.filter(Files::isRegularFile)
                        .forEach(name -> {
                            BufferedReader reader;
                            InputStream stream = FileReader.class.getClassLoader().getResourceAsStream("alltaxi/" + name.getFileName().toString());
                            reader = new BufferedReader(new InputStreamReader(stream));
                            String line = null;
                            try {
                                line = reader.readLine();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            int index = 0;
                            if (line!=null) {
                                fileNames.add(name.getFileName().toString());
                            }
                            while (line != null) {
                                String[] landMarkText = line.split(",");
                                if (landMarkText.length > 2) {
                                    if (latMin <= Double.valueOf(landMarkText[latitudePosition])
                                            && Double.valueOf(landMarkText[latitudePosition]) <= latMax
                                            && lonMin <= Double.valueOf(landMarkText[longitudePosition])
                                            && Double.valueOf(landMarkText[longitudePosition]) <= lonMax
                                    ) {
                                        index++;
                                    } else {
                                        count.getAndIncrement();
                                        fileNames.remove(name.getFileName().toString());
                                        break;
                                    }
                                }
                                // read next line
                                try {
                                    line = reader.readLine();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            try {
                                reader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        int totalFileCount = 4316;
        System.out.println("Total file count: " + totalFileCount);
        System.out.println("Available file count: " + (totalFileCount - count.intValue()));
        System.out.println("File names: \n" + StringUtils.join(fileNames, "\n\r"));
    }

    public static boolean writeWkt(String wktfile, HashMap<Long, Landmark> taxiLandmarks) {
        System.out.println("writing wkt file ...");
        try {
            String path = FileReader.class.getClassLoader().getResource("taxidata").getPath();
            File wkt = new File(path + "/" + wktfile);
            if (wkt.exists()) wkt.delete();
            wkt.createNewFile();

            FileWriter wktstream = new FileWriter(wkt, false);

            wktstream.append(WKT_TAG_BEGIN);

            /*for (Map.Entry<Long, Landmark> entry : taxiLandmarks.entrySet()) {
                Landmark landmark = entry.getValue();
                wktstream.append(landmark.x + WKT_TAG_MARKADD + landmark.y);
                if(i+1 < s.size()) wktstream.append(WKT_TAG_MARKSEP1 + WKT_TAG_MARKSEP2);
            }*/

            //xoffset : 2119242.773, yoffset: -439001.373
            double xoffset = 2119242.773;
            for (int i = 0; i < taxiLandmarks.values().size(); i++) {
                Landmark landmark = (Landmark) taxiLandmarks.values().toArray()[i];
                wktstream.append((landmark.x + xoffset) + WKT_TAG_MARKADD + landmark.y);
                if (i + 1 < taxiLandmarks.values().size()) wktstream.append(WKT_TAG_MARKSEP1 + WKT_TAG_MARKSEP2);
            }
            wktstream.append(WKT_TAG_END + WKT_TAG_BREAK);


            wktstream.close();

        } catch (IOException e) {
            System.out.println("writing wkt file failed: " + e.getLocalizedMessage());
            e.printStackTrace();
            return false;
        }

        System.out.println("writing wkt file done");
        return true;
    }

    public static boolean writeWkt(String wktfile, List<Landmark> taxiLandmarks) {
        System.out.println("writing wkt file ...");
        try {
            String path = FileReader.class.getClassLoader().getResource("taxidata").getPath();
            File wkt = new File(path + "/" + wktfile);
            if (wkt.exists()) wkt.delete();
            wkt.createNewFile();

            FileWriter wktstream = new FileWriter(wkt, false);

            wktstream.append(WKT_TAG_BEGIN);

            /*for (Map.Entry<Long, Landmark> entry : taxiLandmarks.entrySet()) {
                Landmark landmark = entry.getValue();
                wktstream.append(landmark.x + WKT_TAG_MARKADD + landmark.y);
                if(i+1 < s.size()) wktstream.append(WKT_TAG_MARKSEP1 + WKT_TAG_MARKSEP2);
            }*/

            for (int i = 0; i < taxiLandmarks.size(); i++) {
                Landmark landmark = (Landmark) taxiLandmarks.get(i);
                wktstream.append(landmark.longitude + WKT_TAG_MARKADD + landmark.latitude);
                if (i + 1 < taxiLandmarks.size()) wktstream.append(WKT_TAG_MARKSEP1 + WKT_TAG_MARKSEP2);
            }
            wktstream.append(WKT_TAG_END + WKT_TAG_BREAK);


            wktstream.close();

        } catch (IOException e) {
            System.out.println("writing wkt file failed: " + e.getLocalizedMessage());
            e.printStackTrace();
            return false;
        }

        System.out.println("writing wkt file done");
        return true;
    }

    public static void readWkt() throws IOException, ExecutionException, InterruptedException {
        InputStream stream = FileReader.class.getClassLoader().getResourceAsStream("map.osm_origin.wkt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line = reader.readLine();
        List<Landmark> landmarks = new ArrayList<>();
        while (line != null) {

            String[] lineString = line.split("LINESTRING");
            String values = lineString[1];
            values = values.trim();
            String coordinateText = values.substring(1, values.length() - 2);
            String[] twoDimensionCoordinate = coordinateText.split(",");
            Arrays.stream(twoDimensionCoordinate).forEach(coordinates -> {
                coordinates = coordinates.trim();
                String[] xy = coordinates.split(" ");
                Landmark landmark = new Landmark();
                landmark.setLongitude(Double.valueOf(xy[0]));
                landmark.setLatitude(Double.valueOf(xy[1]));
                landmarks.add(landmark);
            });

            line = reader.readLine();
        }
        reader.close();


        Map<Double, Landmark> landmarkHypotenuse = new HashMap<>();

        double selectedLon = 2162688.234;
        double selectedLan = 32079.736;

        landmarks.parallelStream().forEach(landmark -> {
            if (landmark != null) {
                landmarkHypotenuse.put(Math.hypot(selectedLon - landmark.getLongitude(), selectedLan - landmark.getLatitude()), landmark);
            }
        });


        OptionalDouble key = landmarkHypotenuse.keySet().stream().mapToDouble(v -> v).min();
        Landmark landmark = landmarkHypotenuse.get(key.getAsDouble());
        System.out.println("suggested x:" + landmark.getLongitude() + ", y:" + landmark.getLatitude());
    }
}
