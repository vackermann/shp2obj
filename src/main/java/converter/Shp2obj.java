package converter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
  public static double globalMinX;
  public static double globalMinY;
  public static double scaleFactor;

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
      writer.write("mtllib material.mtl\n");

      FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);

      try (FeatureIterator<SimpleFeature> features = collection.features()) {
        initShapefileCoordinateSystemBoundaries(collection);
        while (features.hasNext()) {
          SimpleFeature feature = features.next();
          writer.write(featureToObjGroup(feature));
        }
      }
      writer.write(groundBoundariesToObj(collection));
    }
  }

    public static String featureToObjGroup(SimpleFeature feature){
      //System.out.println(feature.getIdentifier().getID());
      GeometryAttribute featureDefaultGeometryProperty = feature.getDefaultGeometryProperty();
      MultiPolygon multipolygon = (MultiPolygon) featureDefaultGeometryProperty.getValue();
      Coordinate[] coordinates = deleteInnerPoints(multipolygon.getCoordinates());

      Polygon poly = (Polygon)multipolygon.getGeometryN(0);
      Coordinate[] coords = poly.getExteriorRing().getCoordinates();

      double height = (double) feature.getAttribute("_mean");
      if (height<1) {
        height = defaultHeight;
      }

      String result= "o "+feature.getID()+"\nusemtl Building_"+getRandomIntWithRange(1, 10)+"\n";
      //String groundFace = "f";
      String roofFace = "f";

      int i;
      for (i = 0; i<coordinates.length; i++) {
        Coordinate c = coordinates[i];
        result=result+coordinateToVertexdescription(toLocalCoordinateSystem(c))+coordinateToVertexdescription(toLocalCoordinateSystem(createLiftedCoordinate(c, height)));
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
      result=result+roofFace+"\n";
      System.out.print(result);
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
      Coordinate localUpperLeftCorner = toLocalCoordinateSystem(new Coordinate(boundingBox.getMinX(), boundingBox.getMaxY(), 0));
      Coordinate localBottomLeftCorner = toLocalCoordinateSystem(new Coordinate(boundingBox.getMinX(), boundingBox.getMinY(), 0));
      Coordinate localUpperRightCorner = toLocalCoordinateSystem(new Coordinate(boundingBox.getMaxX(), boundingBox.getMaxY(), 0));
      Coordinate localBottomRightCorner = toLocalCoordinateSystem(new Coordinate(boundingBox.getMaxX(), boundingBox.getMinY(), 0));
      String result= "o ground"+"\nusemtl Terrain\n";
      result = result + coordinateToVertexdescription(localUpperLeftCorner)+coordinateToVertexdescription(localBottomLeftCorner)+coordinateToVertexdescription(localUpperRightCorner)+coordinateToVertexdescription(localBottomRightCorner);
      return result+"f -1 -2 -4 -3\n";
    }

  public static void initShapefileCoordinateSystemBoundaries(FeatureCollection collection) {
    BoundingBox boundingBox = collection.getBounds();
    globalMinX = boundingBox.getMinX();
    globalMinY = boundingBox.getMinY();
    scaleFactor = 100/boundingBox.getWidth(); //For a local CS with x-values between 0 and 100
  }

    public static Coordinate toLocalCoordinateSystem(Coordinate coordinate) {
      return scaleCoordinateToLocalCoordinateSystem(translateCoordinateToLocalCoordinateSystem(coordinate));
    }

    public static Coordinate translateCoordinateToLocalCoordinateSystem(Coordinate coordinate){
      return new Coordinate(coordinate.x-globalMinX, coordinate.y-globalMinY, coordinate.z);
    }

    public static Coordinate scaleCoordinateToLocalCoordinateSystem(Coordinate coordinate){
      return new Coordinate(coordinate.x* scaleFactor, coordinate.y* scaleFactor, coordinate.z* scaleFactor);
    }

    public static int getRandomIntWithRange(int lowerBound, int upperBound) {
      Random generator = new Random();
      return generator.nextInt(upperBound-lowerBound) + lowerBound +1;
    }

  }