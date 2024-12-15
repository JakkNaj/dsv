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

# Konfigurace RabbitMQ
sudo bash -c "cat > /etc/rabbitmq/rabbitmq.conf" << EOF
cluster_formation.peer_discovery_backend = classic_config
cluster_formation.classic_config.nodes.1 = rabbit@rabbit1
cluster_formation.classic_config.nodes.2 = rabbit@rabbit2
EOF

# Restart služby
sudo systemctl restart rabbitmq-server

# Vytvoření uživatele a nastavení oprávnění
sudo rabbitmqctl add_user myuser mypassword
sudo rabbitmqctl set_user_tags myuser administrator
sudo rabbitmqctl set_permissions -p / myuser ".*" ".*" ".*"

# Nastavení HA policy
sudo rabbitmqctl set_policy ha-all ".*" '{"ha-mode":"all"}'

# Výpis Erlang cookie pro použití na druhém serveru
echo "Zkopírujte následující Erlang cookie na druhý server:"
sudo cat /var/lib/rabbitmq/.erlang.cookie