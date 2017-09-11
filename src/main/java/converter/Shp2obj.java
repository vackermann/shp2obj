package converter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;

import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.geometry.BoundingBox;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Created by vanessa on 31.08.17.
 */
public class Shp2obj {

  public static double defaultHeight = 5;

  public static void main(String[] args) throws Exception {
    //File file = JFileDataStoreChooser.showOpenFile("shp", null);
    File file = new File("data/TL_building_clipped.shp");
    if (!file.exists()) {
      throw new FileNotFoundException("Failed to find file: " + file.getAbsolutePath());
    }
    Map<String, Object> map = new HashMap<>();
    map.put("url", file.toURI().toURL());

    DataStore dataStore = DataStoreFinder.getDataStore(map);
    String typeName = dataStore.getTypeNames()[0];
    System.out.println(typeName);

    FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
    Filter filter = Filter.INCLUDE; // ECQL.toFilter("BBOX(THE_GEOM, 10,20,30,40)")

    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream("output.obj"), "utf-8"))) {
      writer.write("# Simple Wavefront file\n");

      FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);

      try (FeatureIterator<SimpleFeature> features = collection.features()) {
        while (features.hasNext()) {
          SimpleFeature feature = features.next();
          writer.write(featureToObjGroup(feature));
        }
      }
      writer.write(groundBoundariesToObj(collection));
    }
  }

    public static String featureToObjGroup(SimpleFeature feature){
      GeometryAttribute featureDefaultGeometryProperty = feature.getDefaultGeometryProperty();
      MultiPolygon multipolygon = (MultiPolygon) featureDefaultGeometryProperty.getValue();
      Coordinate[] coordinates = deleteInnerPoints(multipolygon.getCoordinates());

      Polygon poly = (Polygon)multipolygon.getGeometryN(0);
      Coordinate[] coords = poly.getExteriorRing().getCoordinates();

      double height = (double) feature.getAttribute("_mean");
      if (height<1) {
        height = defaultHeight;
      }

      String result= "";
      String groundFace = "f";
      String roofFace = "f";

      int i;
      for (i = 0; i<coordinates.length; i++) {
        Coordinate c = coordinates[i];
        result=result+coordinateToVertexdescription(c)+coordinateToVertexdescription(createLiftedCoordinate(c, height));
        //Create face between four previous created vertices (=wall)
        if (i>0) {
          result=result+"f -1 -2 -4 -3 \n";
        }
        //groundFace += " -"+(2*i+2); //-2 -4 ....
        roofFace += " -"+(2*i+1); //-1 -3 ...
      }
      //Add face between first and last two created vertices (=wall)
      if (i>=4) {
        result = result+"f -1 -2 -"+(2*i)+" -"+(2*i-1)+"\n";
      }
      //result=result+groundFace+"\n"+roofFace+"\n";
      result=result+"\n"+roofFace+"\n";
      return result;
    }

    public static Coordinate createLiftedCoordinate(Coordinate coordinate, double height) {
      return new Coordinate(coordinate.x, coordinate.y, height);
    }

    public static String coordinateToVertexdescription(Coordinate coordinate) {
      return new String ("v "+coordinate.x+" "+coordinate.y+" "+coordinate.z + "\n");
    }

    public static Coordinate[] deleteInnerPoints (Coordinate[] coordinates) {
      Coordinate startCoordinate = coordinates[0];
      int i = 1;
      while (!equal3dCoordinates(startCoordinate, coordinates[i])){
        i++;
      }
      return Arrays.copyOf(coordinates, i);
    }

    public static boolean equal3dCoordinates(Coordinate c1, Coordinate c2){
      return (c1.x==c2.x && c1.y==c2.y && c1.z==c2.z);
    }

    public static String groundBoundariesToObj(FeatureCollection collection) {

      BoundingBox boundingBox = collection.getBounds();
      Coordinate upperLeftCorner = new Coordinate(boundingBox.getMinX(), boundingBox.getMaxY(), 0);
      Coordinate bottomLeftCorner = new Coordinate(boundingBox.getMinX(), boundingBox.getMinY(), 0);
      Coordinate upperRightCorner = new Coordinate(boundingBox.getMaxX(), boundingBox.getMaxY(), 0);
      Coordinate bottomRightCorner = new Coordinate(boundingBox.getMaxX(), boundingBox.getMinY(), 0);

      String result = coordinateToVertexdescription(upperLeftCorner)+coordinateToVertexdescription(bottomLeftCorner)+coordinateToVertexdescription(upperRightCorner)+coordinateToVertexdescription(bottomRightCorner);
      return result+"f -1 -2 -4 -3\n";
    }


  }