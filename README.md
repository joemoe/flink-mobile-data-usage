# Flink Mobile Data Usage

## Getting Started

Import this repo into an IDE, such as IntelliJ, as a Maven project.

### KafkaProducerJob

Before running any of these application, first use docker-compose to start
up a Kafka cluster:

```
docker-compose up -d
```

Running `KafkaProducerJob` in your IDE will populate an _input_ topic with UsageRecords.

_To run the jobs in this project in IntelliJ, choose the option
"Include dependencies with Provided scope" in your Run Configuration._

After having run the `KafkaProducerJob` you can run any of the other applications
in this project (all of which expect to read from Kafka).

To watch what's happening in Kafka (using a local installation of Kafka, or
from inside the container):

```bash
./kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic input --property print.timestamp=true
```

### UsageAlertingProcessFunctionJob

While this job is running in the IDE you can use Flink's web UI at http://localhost:8081.

### Setting up a Flink cluster

```bash
curl -O https://dlcdn.apache.org/flink/flink-1.14.0/flink-1.14.0-bin-scala_2.12.tgz
tar -xzf flink-*.tgz
cd flink-1.14.0
ls -la bin/
./bin/start-cluster.sh
```

Then browse to http://localhost:8081.

To see the cluster run the streaming WordCount example:

```bash
./bin/flink run examples/streaming/WordCount.jar
tail log/flink-*-taskexecutor-*.out
```

### Running this project in a cluster

If you have already started a cluster, stop it and reconfigure Flink to have more resources
by modifing `flink-1.14.0/conf/flink-conf.yaml` so that

```yaml
taskmanager.memory.process.size: 3072m
taskmanager.numberOfTaskSlots: 8
```

Then with `flink-1.14.0/bin` in your PATH:

```bash
mvn clean package
start-cluster.sh
flink run -d target/flink-mobile-data-usage-1.0.jar
flink run -d -c com.ververica.flink.example.datausage.UsageAlertingProcessFunctionJob \
  target/flink-mobile-data-usage-1.0.jar --webui false
```

## How to create a new maven project for your own Flink application

```bash
curl https://flink.apache.org/q/quickstart.sh | bash -s 1.14.0
```

See [Project Configuration](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/project-configuration) in the Flink docs for more information.
