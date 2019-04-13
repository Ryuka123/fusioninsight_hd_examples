package com.huawei.bigdata.spark.examples

import java.io.IOException

import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext

/**
  * load data to table.
  */
object TableInputData {
  def main(args: Array[String]) {
    // Create the configuration parameter to connect the HBase.
    val conf = new SparkConf()
    val sc = new SparkContext(conf)
    val hbConf = HBaseConfiguration.create(sc.hadoopConfiguration)

    // Declare the information of the table.
    val table: HTable = null
    val tableName = "shb1"
    val familyName = Bytes.toBytes("info");
    var connection: Connection = null
    try {
      // Connect to the HBase.
      connection = ConnectionFactory.createConnection(hbConf);
      // Obtain the table object.
      val table = connection.getTable(TableName.valueOf(tableName));
      val data = sc.textFile(args(0)).map { line =>
        val value = line.split(",")
        (value(0), value(1), value(2), value(3))
      }.collect()

      var i = 0
      for (line <- data) {
        val put = new Put(Bytes.toBytes("row" + i));
        put.addColumn(familyName, Bytes.toBytes("c11"), Bytes.toBytes(line._1))
        put.addColumn(familyName, Bytes.toBytes("c12"), Bytes.toBytes(line._2))
        put.addColumn(familyName, Bytes.toBytes("c13"), Bytes.toBytes(line._3))
        put.addColumn(familyName, Bytes.toBytes("c14"), Bytes.toBytes(line._4))
        i += 1
        table.put(put)
      }
    } catch {
      case e: IOException =>
        e.printStackTrace();
    } finally {
      if (table != null) {
        try {
          //Close the HTable.
          table.close();
        } catch {
          case e: IOException =>
            e.printStackTrace();
        }
      }
      if (connection != null) {
        try {
          // Close the HBase connection.
          connection.close();
        } catch {
          case e: IOException =>
            e.printStackTrace();
        }
      }
      sc.stop()
    }
  }
}
