#!/bin/bash

JAVA_HOME=/usr/lib/jvm/java-6-openjdk

NODE_COUNT=$1
shift

CLUSTER_NAME=${NODE_COUNT}node

ES_DIR=elasticsearch-0.90.5

ES_OPTS="-f -Xmx128m -Xms128m"
ES_OPTS="$ES_OPTS -Des.cluster.name=$CLUSTER_NAME"
ES_OPTS="$ES_OPTS -Des.network.host=127.0.0.1"
ES_OPTS="$ES_OPTS $@"
#ES_OPTS="$ES_OPTS -Des.index.store.type=memory"

ES_ENV="JAVA_HOME=$JAVA_HOME PATH=/usr/bin:$JAVA_HOME/bin"

ES_STARTUP_DELAY=4

WORK_DIR=$(dirname $(pwd)/$0/)/../$ES_DIR
cd $WORK_DIR

# start tmux with 3 windows and a foreground ES node in each
tmux start-server
tmux new-session -d -s es -n node0

MAX_NUM=$(($NODE_COUNT - 1))

for n in $(seq 1 $MAX_NUM); do
  tmux new-window -t es:$n -n node$n
done
tmux set-option -t es -g allow-rename off

for n in $(seq 0 $MAX_NUM); do
  echo "Setting up node $n..."
  tmux send-keys -t es:$n "unsetopt correct_all" C-m  # need to disable ZSH autocorrect globally
  tmux send-keys -t es:$n "sleep $(($ES_STARTUP_DELAY * $n))" C-m     # start nodes slowly
  tmux send-keys -t es:$n "NUM=$n" C-m
  tmux send-keys -t es:$n "figlet node \$NUM" C-m
  tmux send-keys -t es:$n "$ES_ENV ./bin/elasticsearch $ES_OPTS -Des.node.name=node\$NUM" C-m
done

tmux attach-session -t es
tmux select-window -t es:0

