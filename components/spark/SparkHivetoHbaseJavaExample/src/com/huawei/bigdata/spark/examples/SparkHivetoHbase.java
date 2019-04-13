package com.huawei.bigdata.spark.examples;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.huawei.bigdata.spark.security.LoginUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.*;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.sql.*;
import org.apache.spark.*;

import org.apache.spark.sql.Row;
import org.apache.spark.sql.hive.HiveContext;

/**
 * 从Hive或者HBase计算数据然后存储到HBase.
 */
public class SparkHivetoHbase {

  public static void main(String[] args) throws Exception {

    String PRNCIPAL_NAME = "lyysxg";//需要修改为实际在manager添加的用户
    String KRB5_CONF = SparkHivetoHbase.class.getClassLoader().getResource("krb5.conf").getPath();
    String KEY_TAB = SparkHivetoHbase.class.getClassLoader().getResource("user.keytab").getPath();
    System.setProperty("java.security.krb5.conf", KRB5_CONF); //指定kerberos配置文件到JVM
    //加载core-site.xml/hdfs-site.xml配置文件
    Configuration conf = new Configuration();
    //加载HDFS服务端配置，包含客户端与服务端对接配置
    conf.addResource(new Path(SparkHivetoHbase.class.getClassLoader().getResource("hdfs-site.xml").getPath()));
    conf.addResource(new Path(SparkHivetoHbase.class.getClassLoader().getResource("core-site.xml").getPath()));

    //需要修改方法中的PRNCIPAL_NAME（用户名）
    //安全模式需要进行kerberos认证，只在系统启动时执行一次。非安全模式可以删除

    //认证相关，安全模式需要，普通模式可以删除
    LoginUtil.login(PRNCIPAL_NAME, KEY_TAB, KRB5_CONF, conf);
    // 通过Spark接口获取表中的数据。
    SparkConf sparkConf = new SparkConf().setAppName("SparkHivetoHbase");
    JavaSparkContext jsc = new JavaSparkContext(sparkConf);
    //Spark SQL执行引擎的一个实例，它与存储在Hive中的数据集成在一起。从类路径上的hive-site.xml读取Hive的配置。
    HiveContext sqlContext = new org.apache.spark.sql.hive.HiveContext(jsc);
    //Sparksql对象，用于操作Sparksql
    DataFrame dataFrame = sqlContext.sql("select name, account from goldusers");

    //遍历hive表中的每个分区并更新hbase表
    //少量数据使用forreach()
    dataFrame.toJavaRDD().foreachPartition(//迭代器
      new VoidFunction<Iterator<Row>>() {
        public void call(Iterator<Row> iterator) throws Exception {
          hBaseWriter(iterator);//调方法进行写入
        }
      }
    );

    jsc.stop();
  }

  /**
   * write to hbase table in exetutor
   *
   * @param iterator partition data from hive table
   */
  private static void hBaseWriter(Iterator<Row> iterator) throws IOException {
    // read hbase
    String tableName = "table2";
    String columnFamily = "info";
    Configuration conf = HBaseConfiguration.create();//使用HBase资源创建配置
    Connection connection = null;
    Table table = null;
    try {
      connection = ConnectionFactory.createConnection(conf);//根据配置创建一个Connection对象
      table = connection.getTable(TableName.valueOf(tableName));//检索用于访问表的Table实现。

      List<Row> table1List = new ArrayList<Row>();
      List<Get> rowList = new ArrayList<Get>();
      while (iterator.hasNext()) {//进行迭代，把从table1的数据放到list中。
        Row item = iterator.next();
        Get get = new Get(item.getString(0).getBytes());//获的第一个位置的数据
        table1List.add(item);
        rowList.add(get);
      }

      // 从table2中获得数据
      Result[] resultDataBuffer = table.get(rowList);

      // set data for hbase
      List<Put> putList = new ArrayList<Put>();

      for (int i = 0; i < resultDataBuffer.length; i++) {
        //遍历table2的每条数据
        Result resultData = resultDataBuffer[i];
        //判空
        if (!resultData.isEmpty()) {
          // 获得hive的数据
          int hiveValue = table1List.get(i).getInt(1);

          // // 通过列族和修饰符去获得hive的值
          String hbaseValue = Bytes.toString(resultData.getValue(columnFamily.getBytes(), "".getBytes()));
          //对象进行put操作，必须首先实例化put。
          Put put = new Put(table1List.get(i).getString(0).getBytes());

           //计算结果值
          int resultValue = hiveValue + Integer.valueOf(hbaseValue);

          //设置数据

          put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("cid"), Bytes.toBytes(String.valueOf(resultValue)));
          putList.add(put);
        }
      }

      if (putList.size() > 0) {
        table.put(putList);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (table != null) {
        try {
          table.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (connection != null) {
        try {
          // Close the HBase connection.
          connection.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
