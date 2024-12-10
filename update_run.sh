#!/bin/bash

# Aktualizovat kód
echo "Aktualizuji kód..."
git fetch || { echo "Git fetch selhal"; exit 1; }
git pull || { echo "Git pull selhal"; exit 1; }

# Sestavit projekt
echo "Sestavuji projekt..."
mvn clean package || { echo "Maven build selhal"; exit 1; }

# Spustit aplikaci
echo "Spouštím aplikaci..."
java -jar target/node-1.0-SNAPSHOT.jar