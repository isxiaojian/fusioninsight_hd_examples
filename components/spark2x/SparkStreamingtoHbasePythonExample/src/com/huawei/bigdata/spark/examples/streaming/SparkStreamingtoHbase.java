package com.huawei.bigdata.spark.examples.streaming;


import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.*;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.apache.spark.streaming.*;
import kafka.serializer.StringDecoder;


/**
 * run streaming task and select table1 data from hbase, then update to table1
 */
public class SparkStreamingtoHbase implements Serializable {
  public void streamingtohbase(JavaSparkContext jsc,String checkPointDir, String topics, String brokers) throws Exception {

    Duration batchDuration = Durations.seconds(2);
    JavaStreamingContext jssc = new JavaStreamingContext(jsc, batchDuration);

    // set CheckPoint dir
    if (!"nocp".equals(checkPointDir)) {
      jssc.checkpoint(checkPointDir);
    }

    final String columnFamily = "cf";
    HashMap<String, String> kafkaParams = new HashMap<String, String>();
    kafkaParams.put("metadata.broker.list", brokers);

    String[] topicArr = topics.split(",");
    Set<String> topicSet = new HashSet<String>(Arrays.asList(topicArr));

    // Create direct kafka stream with brokers and topics
    // Receive data from the Kafka and generate the corresponding DStream
    JavaDStream<String> lines = KafkaUtils.createDirectStream(jssc, String.class, String.class,
      StringDecoder.class, StringDecoder.class, kafkaParams, topicSet).map(
      new Function<Tuple2<String, String>, String>() {
        public String call(Tuple2<String, String> tuple2) {
          // map(_._1) is message key, map(_._2) is message value
          return tuple2._2();
        }
      }
    );

    lines.foreachRDD(
      new VoidFunction<JavaRDD<String>>() {
        public void call(JavaRDD<String> rdd) throws Exception {
          rdd.foreachPartition(
            new VoidFunction<Iterator<String>>() {
              public void call(Iterator<String> iterator) throws Exception {
                hBaseWriter(iterator, columnFamily);
              }
            }
          );
        }
      }
    );

    jssc.start();
    jssc.awaitTermination();
  }

  /**
   * do write hbase in executor
   *
   * @param iterator     message
   * @param columnFamily columnFamily
   */
  private static void hBaseWriter(Iterator<String> iterator, String columnFamily) throws IOException {
    Configuration conf = HBaseConfiguration.create();
    Connection connection = null;
    Table table = null;

    try {
      connection = ConnectionFactory.createConnection(conf);
      table = connection.getTable(TableName.valueOf("table1"));

      List<Get> rowList = new ArrayList<Get>();
      while (iterator.hasNext()) {
        Get get = new Get(iterator.next().getBytes());
        rowList.add(get);
      }
      //get data from hbasetable1
      Result[] resultDataBuffer = table.get(rowList);

      //set data for hbasetable1
      List<Put> putList = new ArrayList<Put>();
      for (int i = 0; i < resultDataBuffer.length; i++) {
        String row = new String(rowList.get(i).getRow());
        Result resultData = resultDataBuffer[i];
        if (!resultData.isEmpty()) {
          // get value by column Family and colomn qualifier
          String aCid = Bytes.toString(resultData.getValue(columnFamily.getBytes(), "cid".getBytes()));
          Put put = new Put(Bytes.toBytes(row));

          // calculate result value
          int resultValue = Integer.valueOf(row) + Integer.valueOf(aCid);
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
