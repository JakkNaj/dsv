#!/bin/bash

# Kontrola argumentů
if [ "$#" -ne 2 ]; then
    echo "Použití: $0 <rabbit1_ip> <rabbit2_ip>"
    exit 1
fi

RABBIT1_IP=$1
RABBIT2_IP=$2

# Instalace RabbitMQ
sudo apt-get update
sudo apt-get install -y rabbitmq-server

# Povolení management pluginu
sudo rabbitmq-plugins enable rabbitmq_management

# Přidání hostů
echo "$RABBIT1_IP rabbit1
$RABBIT2_IP rabbit2" | sudo tee -a /etc/hosts

# Zde je potřeba ručně vložit Erlang cookie z prvního serveru
echo "Vložte Erlang cookie z prvního serveru:"
read ERLANG_COOKIE
sudo sh -c "echo $ERLANG_COOKIE > /var/lib/rabbitmq/.erlang.cookie"
sudo chown rabbitmq:rabbitmq /var/lib/rabbitmq/.erlang.cookie
sudo chmod 400 /var/lib/rabbitmq/.erlang.cookie

#nastartování RabbitMQ
sudo systemctl start rabbitmq-server

# Nejdřív zastavíme aplikaci
sudo rabbitmqctl stop_app

# Reset a připojení do clusteru
sudo rabbitmqctl reset
sudo rabbitmqctl join_cluster rabbit@rabbit1
sudo rabbitmqctl start_app

echo "Kontrola stavu clusteru: (lze zavolat i na rabbit1)"
sudo rabbitmqctl cluster_status