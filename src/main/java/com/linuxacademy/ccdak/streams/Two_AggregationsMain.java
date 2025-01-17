package com.linuxacademy.ccdak.streams;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;

public class Two_AggregationsMain {

    public static void main(String[] args) {
        // Set up the configuration.
        final Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "aggregations-example");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        // Since the input topic uses Strings for both key and value, set the default Serdes to String.
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        // Get the source stream.
        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> source = builder.stream("aggregations-input-topic");

        //----------------------------------------------------------------------------------//
        //                        group by key
        //----------------------------------------------------------------------------------//

        // Group the source stream by the existing Key.
        KGroupedStream<String, String> groupedStream = source.groupByKey();
        // 1. aggregate by key
        // Create an aggregation that totals the length
        // in characters of the value for all records sharing the same key.

        KTable<String, Integer> aggregatedTable = groupedStream.aggregate(
            () -> 0,
            (aggKey, newValue, aggValue) -> aggValue + newValue.length(), // character count
            Materialized.with(Serdes.String(), Serdes.Integer())); // create the data store

        aggregatedTable.toStream().to("aggregations-output-charactercount-topic",
                Produced.with(Serdes.String(), Serdes.Integer())); // to another topic

        //e.g. b: hello, b:world => b: 10
        // count the total char of concated string of value
        //----------------------------------------------------------------------------------//
        // 2. count
        // Count the number of records for each key.
        KTable<String, Long> countedTable = groupedStream
                .count(Materialized.with(Serdes.String(), Serdes.Long()));

        countedTable.toStream().to("aggregations-output-count-topic",
                Produced.with(Serdes.String(), Serdes.Long()));
        // b: 2 (count the frequency of keys)
        //----------------------------------------------------------------------------------//

        // 3. reduce
        // Combine the values of all records with the same key into a string separated by spaces.
        KTable<String, String> reducedTable = groupedStream
                .reduce((aggValue, newValue)
                -> aggValue + " " + newValue);

        reducedTable.toStream().to("aggregations-output-reduce-topic");
        // b: hello world (concat the string with space)
        //----------------------------------------------------------------------------------//

        final Topology topology = builder.build();
        final KafkaStreams streams = new KafkaStreams(topology, props);
        // Print the topology to the console.
        System.out.println(topology.describe());
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach a shutdown handler to catch control-c and terminate the application gracefully.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (final Throwable e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        System.exit(0);
    }

}
