import MapFunctions.*;
import Model.Read;
import TransformFunctions.PipeToBlast;
import TransformFunctions.SaveToElastic;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.elasticsearch.spark.streaming.api.java.JavaEsSparkStreaming;
import org.spark_project.guava.collect.ImmutableMap;

import java.util.ArrayList;

public class Streaming {

    public JavaSparkContext ssc;

    public static void main(String[] args) throws InterruptedException {

        SparkConf conf = new SparkConf().setAppName("fileStreaming").setMaster("local[*]");
        conf.set("es.index.auto.create", "true");
        conf.set("es.nodes", "localhost");
        conf.set("es.port", "9200");
        conf.set("es.net.http.auth.user", "");
        conf.set("es.net.http.auth.pass", "");
        conf.set("es.resource", "sparkstreaming");
        conf.set("es.nodes.wan.only", "true");
        JavaStreamingContext ssc = new JavaStreamingContext(conf, new Duration(10000));
        //JavaDStream<String> stream = ssc.textFileStream("/home/vanessa/Masterarbeit/workdir/sequences");
        JavaDStream<String> stream = ssc.textFileStream("/vol/MA_Data/sequences");
        JavaDStream<String> fastq = stream.map(new ReadFastq()).filter(x -> x!=null);
        JavaDStream<Read> reads = fastq.map(new ToReadObject()).filter(x -> x!=null).map(new CalculateGCContent());
        JavaDStream<String> savedReads = reads.transform(new SaveToElastic()).map(new ToFasta());
        //savedReads.cache();
        JavaDStream<String> results = savedReads.transform(new PipeToBlast()).map(new GetBlastResultJsonSingleReport()).filter(x -> x!=null);

        //results.cache();
        //results.print();
        //results.dstream().saveAsTextFiles("file:///home/vanessa/Masterarbeit/workdir/test", "txt");
        JavaEsSparkStreaming.saveJsonToEs(results, "sparkblastresults", ImmutableMap.of("es.mapping.id","report.results.search.query_title","es.mapping.exclude","qseq, hseq, midline"));
        //JavaEsSparkStreaming.saveToEs(readsblast, "sparkstreaming");

        ssc.start();
        ssc.awaitTermination();

    }

}
