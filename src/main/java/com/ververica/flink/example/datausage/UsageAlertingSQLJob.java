package com.ververica.flink.example.datausage;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

import com.ververica.flink.example.datausage.sources.AccountUpdateGenerator;

public class UsageAlertingSQLJob {
    public static void main(String[] args) throws Exception {

        /******************************************************************************************
         * Setting up the environment
         ******************************************************************************************/

        final Configuration flinkConfig = new Configuration();
        final StreamExecutionEnvironment env =
                StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(flinkConfig);
        env.setParallelism(4);

        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);


        /******************************************************************************************
         * Creating a table containing the usage data with the Kafka connector
         ******************************************************************************************/

        tEnv.executeSql(
                String.join(
                        "\n",
                        "CREATE TABLE usage (",
                        "  account STRING,",
                        "  bytesUsed BIGINT,",
                        "  ts TIMESTAMP_LTZ(3) METADATA FROM 'timestamp',",
                        "  WATERMARK FOR ts AS ts",
                        ") WITH (",
                        "  'connector' = 'kafka',",
                        "  'topic' = 'input',",
                        "  'properties.bootstrap.servers' = 'localhost:9092',",
                        "  'scan.startup.mode' = 'earliest-offset',",
                        "  'format' = 'json'",
                        ")"));


        /******************************************************************************************
         * Creating a data stream for the account updates from the generator
         ******************************************************************************************/

        DataStream<Row> accountUpdateStream =
                env.addSource(new AccountUpdateGenerator())
                        .returns(AccountUpdateGenerator.typeProduced());


        /******************************************************************************************
         * Setting up a schema for the account updates
         ******************************************************************************************/

        Schema accountUpdateSchema =
                Schema.newBuilder()
                        .column("id", "STRING NOT NULL")
                        .column("quota", "BIGINT")
                        .column("ts", "TIMESTAMP_LTZ(3)")
                        .watermark("ts", "SOURCE_WATERMARK()")
                        .primaryKey("id")
                        .build();


        /******************************************************************************************
         * Turning the account updates data stream into a table and creating a temporary view
         ******************************************************************************************/

        Table accountUpdates = tEnv.fromChangelogStream(accountUpdateStream, accountUpdateSchema);

        tEnv.createTemporaryView("account", accountUpdates);


        /******************************************************************************************
         * Creating a table for the enriched records
         ******************************************************************************************/

        Table enrichedRecords =
                tEnv.sqlQuery(
                        String.join(
                                "\n",
                                "SELECT",
                                "  usage.account AS account,",
                                "  usage.bytesUsed AS bytesUsed,",
                                "  account.quota AS quota,",
                                "  usage.ts AS ts,",
                                "  EXTRACT(YEAR from usage.ts) AS billingYear,",
                                "  EXTRACT(MONTH from usage.ts) AS billingMonth",
                                "FROM usage JOIN account FOR SYSTEM_TIME AS OF usage.ts",
                                "ON usage.account = account.id"));

        tEnv.createTemporaryView("enrichedRecords", enrichedRecords);


        /******************************************************************************************
         * Creating, executing and printing a join to get the users exceeding the quota
         ******************************************************************************************/

        tEnv.sqlQuery(
                        String.join(
                                "\n",
                                "SELECT account, MAX(ts), billingYear, billingMonth, SUM(bytesUsed), quota",
                                "FROM enrichedRecords",
                                "GROUP BY account, billingYear, billingMonth, quota",
                                "HAVING sum(bytesUsed) > 0.9 * quota"))
                .execute()
                .print();


        /******************************************************************************************
         * Executing the job
         ******************************************************************************************/

        env.execute("UsageAlertingSQLJob");
    }
}
