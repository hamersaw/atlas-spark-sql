package com.bushpath.atlas.spark.sql.util;

import org.locationtech.jts.geom.{Coordinate, CoordinateSequence, Geometry, GeometryFactory, LinearRing, LineString, Point, Polygon}
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import java.io.{DataInputStream, DataOutputStream}

object Serializer {
  final val lineString = 0x01
  final val point = 0x02
  final val polygon = 0x03

  final val geometryFactory = new GeometryFactory();

  def deserialize(in: DataInputStream): Geometry = {
    val geometryType = in.read().asInstanceOf[Byte]
    geometryType match {
      case this.lineString => {
        // deserialize line string
        val coordinateSequence = deserializeCoordinateSequence(in)
        new LineString(coordinateSequence, this.geometryFactory)
      };
      case this.point => {
        // deserialize point
        val coordinateSequence = deserializeCoordinateSequence(in)
        new Point(coordinateSequence, this.geometryFactory)
      };
      case this.polygon => {
        // deserialize polygon
        val coordinateSequence = deserializeCoordinateSequence(in)
        val exteriorRing = new LinearRing(coordinateSequence,
          this.geometryFactory)

        val numInteriorRing = in.readInt
        val interiorRingArray = new Array[LinearRing](numInteriorRing)
        for (i <- 0 to numInteriorRing) {
          val coordinateSequence = deserializeCoordinateSequence(in)
          val interiorRing = new LinearRing(coordinateSequence,
            this.geometryFactory)

          interiorRingArray(i) = interiorRing
        }

        new Polygon(exteriorRing, interiorRingArray, this.geometryFactory)
      };
    }
  }

  private def deserializeCoordinateSequence(in: DataInputStream)
      : CoordinateSequence = {
    val numPoints = in.readInt
    val coordinateSequence = new CoordinateArraySequence(numPoints)
    for (i <- 0 to numPoints) {
      val x = in.readDouble
      coordinateSequence.setOrdinate(i, 0, x)

      val y = in.readDouble
      coordinateSequence.setOrdinate(i, 1, y)
    }

    coordinateSequence
  }

  def serialize(geometry: Geometry, out: DataOutputStream): Unit = {
    geometry match {
      case lineString: LineString => {
        // serialize line string
        out.write(this.lineString)
        out.writeInt(lineString.getNumPoints)
        for (i <- 0 to lineString.getNumPoints) {
          val point = lineString.getPointN(i)
          out.writeDouble(point.getX)
          out.writeDouble(point.getY)
        }
      };
      case point: Point => {
        // serialize point
        out.write(this.point)
        out.writeInt(1)
        out.writeDouble(point.getX)
        out.writeDouble(point.getY)
      };
      case polygon: Polygon => {
        // serialize polygon
        out.write(this.polygon)
        serialize(polygon.getExteriorRing, out)
        out.writeInt(polygon.getNumInteriorRing)
        for (i <- 0 to polygon.getNumInteriorRing) {
          serialize(polygon.getInteriorRingN(i), out)
        }
      };
    }
  }
}