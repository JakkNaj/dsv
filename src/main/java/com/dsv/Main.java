package com.dsv;

import io.javalin.Javalin;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import com.dsv.config.AppConfig;
import com.dsv.config.NodeConfig;
import com.dsv.config.RabbitConfig;

public class Main {
    private static Channel channel;
    private static String nodeId;
    private static AppConfig config;
    
    private static void loadConfig() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = Main.class.getClassLoader()
                .getResourceAsStream("config.yml");
            config = yaml.loadAs(inputStream, AppConfig.class);
        } catch (Exception e) {
            System.err.println("Chyba při načítání konfigurace: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void setupNode() {
        String serverIp = getOwnIp();
        NodeConfig nodeConfig = config.getNodes().get(serverIp);
        
        if (nodeConfig == null) {
            System.err.println("Pro IP " + serverIp + " není konfigurace!");
            System.exit(1);
        }
        
        nodeId = nodeConfig.getId();
        int port = nodeConfig.getPort();
        
        System.out.println("Node configuration loaded: " + nodeId + " on port " + port);
        startServer(port);
    }
    
    private static void setupRabbitMQ() {
        try {
            RabbitConfig rmqConfig = config.getRabbitmq();
            
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rmqConfig.getHost());
            factory.setPort(rmqConfig.getPort());
            factory.setUsername(rmqConfig.getUsername());
            factory.setPassword(rmqConfig.getPassword());
            
            Connection connection = factory.newConnection();
            channel = connection.createChannel();
            
            // Vytvoříme vlastní frontu pro příjem zpráv
            String myQueue = getQueueName(nodeId);
            channel.queueDeclare(myQueue, false, false, false, null);
            
            // Nastavíme consumera pro naši frontu
            channel.basicQos(1);
            channel.basicConsume(myQueue, true, (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[" + nodeId + "] Přijata zpráva: " + message);
            }, consumerTag -> {});
            
            System.out.println("RabbitMQ připojení úspěšně navázáno pro " + nodeId);
            System.out.println("Poslouchám na frontě: " + myQueue);
        } catch (Exception e) {
            System.err.println("Chyba při připojování k RabbitMQ: " + e.getMessage());
        }
    }
    
    private static void startServer(int port) {
        Javalin app = Javalin.create()
            .get("/", ctx -> {
                ctx.result(nodeId + " is running!");
            })
            .post("/send/{targetNode}", ctx -> {
                String targetNode = ctx.pathParam("targetNode");
                String targetQueue = getQueueName(targetNode);
                String message = ctx.body();
                
                try {
                    channel.queueDeclare(targetQueue, false, false, false, null); // Zajistíme že fronta existuje
                    channel.basicPublish("", targetQueue, null, message.getBytes());
                    ctx.result("Zpráva odeslána z " + nodeId + " do " + targetNode + ": " + message);
                } catch (Exception e) {
                    ctx.status(500).result("Chyba při odesílání: " + e.getMessage());
                }
            })
            .start(port);
            
        System.out.println(nodeId + " běží na http://" + getOwnIp() + ":" + port);
    }

    public static void main(String[] args) {
        loadConfig();
        setupNode();
        setupRabbitMQ();
    }
    
    private static String getQueueName(String targetNode) {
        return targetNode + "-queue";
    }
    
    private static String getOwnIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.getHostAddress().contains(":")) continue;
                    return addr.getHostAddress();
                }
            }
        } catch (SocketException e) {
            System.err.println("Nelze získat IP adresu: " + e.getMessage());
        }
        return "0.0.0.0";
    }
}