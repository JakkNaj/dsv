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

#nastartování RabbitMQ
sudo systemctl start rabbitmq-server

# Přidání hostů
echo "$RABBIT1_IP   rabbit1
$RABBIT2_IP rabbit2" | sudo tee -a /etc/hosts

# Vytvoření uživatele a nastavení oprávnění
sudo rabbitmqctl add_user myuser mypassword
sudo rabbitmqctl set_user_tags myuser administrator
sudo rabbitmqctl set_permissions -p / myuser ".*" ".*" ".*"

# Nastavení HA policy
sudo rabbitmqctl set_policy ha-all ".*" '{"ha-mode":"all","ha-sync-mode":"automatic"}'

# Výpis Erlang cookie pro použití na druhém serveru
echo "Zkopírujte následující Erlang cookie na druhý server:"
sudo cat /var/lib/rabbitmq/.erlang.cookie