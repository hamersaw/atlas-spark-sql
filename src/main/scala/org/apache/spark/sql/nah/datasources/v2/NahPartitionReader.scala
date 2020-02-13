package org.apache.spark.sql.nah.datasources.v2

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.execution.datasources.csv.CSVOptions
import org.apache.spark.sql.sources.v2.reader.InputPartitionReader
import org.apache.spark.sql.types.StructType

import java.io.{BufferedInputStream, ByteArrayInputStream, DataInputStream, DataOutputStream, FileOutputStream}
import java.net.{InetAddress, Socket}
import java.util.Scanner

import scala.collection.JavaConversions._
import scala.util.control.Breaks._

class NahPartitionReader(dataSchema: StructType, requiredSchema: StructType, 
    blockId: Long, offset: Long, length: Long, locations: Array[String],
    ports: Array[Int]) extends InputPartitionReader[InternalRow] {
  //val outputStream = new FileOutputStream("/tmp/nahspark-" + blockId + ".txt")
  var recordCount = 0l
  var parseTotal = 0l

  val csvOptions = new CSVOptions(Map(), false, "TODO - time zone")

  val parser = new CsvParser(dataSchema, requiredSchema, csvOptions)

  val inputStream = {
    val start = System.currentTimeMillis
    val blockData = new Array[Byte](length.toInt)

    // determine ipAddress and port - favor local machine
    var index = 0 
    val localIpAddress = InetAddress.getLocalHost().getHostAddress()
    for ((location, i) <- locations.zipWithIndex) {
      if (location == localIpAddress) {
        index = i
      }
    }

    val (ipAddress, port) = (locations(index), ports(index))
    //println("read block " + blockId + " from " + ipAddress + ":" + port)

    val socket = new Socket(ipAddress, port)
    val dataOut = new DataOutputStream(socket.getOutputStream)
    val dataIn = new DataInputStream(socket.getInputStream)

    // retrieve block from host
    dataOut.writeShort(28);         // protocol version
    dataOut.write(83);              // op - ReadBlockDirect
    dataOut.write(0);               // protobuf length
    dataOut.writeLong(blockId);     // block id
    dataOut.writeLong(offset);      // offset
    dataOut.writeLong(length);      // length

    var dataIndex = 0;
    var bytesRead = 0;
    while (dataIndex < blockData.length) {
      bytesRead = dataIn.read(blockData, dataIndex,
        blockData.length - dataIndex)
      dataIndex += bytesRead
    }

    // send success indicator
    dataOut.writeByte(0);

    // close streams
    dataIn.close
    dataOut.close
    socket.close

    val duration = System.currentTimeMillis - start
    //outputStream.write(("readLength:" + length 
    //  + " readDuration:" + duration + "ms").getBytes)
    println("read " + length  + " bytes in " + duration + "ms")

    // open input streams
    new ByteArrayInputStream(blockData)
  }

  val bufferedInputStream = new BufferedInputStream(this.inputStream)
  val scanner = new Scanner(this.bufferedInputStream, "UTF-8")

  var row = parseNextLine
  protected def parseNextLine(): InternalRow = {
    if (this.scanner.hasNextLine) {
      val line = this.scanner.nextLine
      parser.parse(line)
    } else {
      null
    }
  }

  override def next: Boolean = {
    //this.scanner.hasNextLine
    this.row != null
  }

  override def get: InternalRow = {
    /*val start = System.currentTimeMillis

    val line = this.scanner.nextLine
    val parsed = parser.parse(line)

    recordCount += 1
    parseTotal += System.currentTimeMillis - start

    parsed*/
    val returnRow = this.row
    this.row = parseNextLine
    returnRow
  }

  override def close = {
    println("recordCount: " + recordCount + "")
    println("parseTotal: " + parseTotal + "ms")
    //outputStream.write((" recordCount:" + recordCount + "").getBytes())
    //outputStream.write((" parseTotal:" + parseTotal + "ms").getBytes())
    //outputStream.flush
    //outputStream.close

    this.scanner.close
    this.bufferedInputStream.close
    this.inputStream.close
  }
}
