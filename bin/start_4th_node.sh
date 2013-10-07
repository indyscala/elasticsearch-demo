#!/bin/bash

NUM=3
JAVA_HOME=/usr/lib/jvm/java-6-openjdk \
PATH=/usr/bin:/usr/lib/jvm/java-6-openjdk/bin \
./bin/elasticsearch -f -Xmx128m -Xms128m \
  -Des.index.storage.type=memory \
  -Des.network.host=127.0.0.1 \
  -Des.cluster.name=3node \
  -Des.node.name=node$NUM
