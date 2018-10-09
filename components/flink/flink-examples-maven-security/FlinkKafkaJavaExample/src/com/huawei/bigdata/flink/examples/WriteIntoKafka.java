package com.huawei.bigdata.flink.examples;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer010;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;

public class WriteIntoKafka
{
    public static void main(String[] args) throws Exception
    {
        System.out.println("use command as: ");
        System.out.println("./bin/flink run --class com.huawei.bigdata.flink.examples.WriteIntoKafka" + " /opt/test.jar --topic topic-test -bootstrap.servers 9.91.8.218:21005");
        System.out.println("./bin/flink run --class com.huawei.bigdata.flink.examples.WriteIntoKafka" + " /opt/test.jar --topic topic-test -bootstrap.servers 9.91.8.218:21007 --security.protocol SASL_PLAINTEXT --sasl.kerberos.service.name kafka");
        System.out.println("./bin/flink run --class com.huawei.bigdata.flink.examples.WriteIntoKafka" + " /opt/test.jar --topic topic-test -bootstrap.servers 9.91.8.218:21008 --security.protocol SSL --ssl.truststore.location /home/truststore.jks --ssl.truststore.password huawei");
        System.out.println("./bin/flink run --class com.huawei.bigdata.flink.examples.WriteIntoKafka" + " /opt/test.jar --topic topic-test -bootstrap.servers 9.91.8.218:21009 --security.protocol SASL_SSL --sasl.kerberos.service.name kafka --ssl.truststore.location /home/truststore.jks --ssl.truststore.password huawei");
        System.out.println("******************************************************************************************");
        System.out.println("<topic> is the kafka topic name");
        System.out.println("<bootstrap.servers> is the ip:port list of brokers");
        System.out.println("******************************************************************************************");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        ParameterTool paraTool = ParameterTool.fromArgs(args);
        DataStream<String> messageStream = env.addSource(new SimpleStringGenerator());
        messageStream.addSink(new FlinkKafkaProducer010<>(paraTool.get("topic"), new SimpleStringSchema(), paraTool.getProperties()));
        env.execute();
    }

    public static class SimpleStringGenerator implements SourceFunction<String>
    {
        private static final long serialVersionUID = 2174904787118597072L;
        boolean running = true;
        long i = 0;

        @Override
        public void run(SourceContext<String> ctx) throws Exception
        {
            while (running)
            {
                ctx.collect("element-" + (i++));
                Thread.sleep(1000);
            }
        }

        @Override
        public void cancel()
        {
            running = false;
        }
    }
}
