package com.dsv;

import io.javalin.Javalin;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import com.dsv.config.AppConfig;
import com.dsv.config.NodeConfig;
import com.dsv.config.RabbitConfig;
import com.dsv.messaging.MessageService;
import com.dsv.resource.ResourceManager;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import com.dsv.controller.NodeController;

@Slf4j
public class Main {
    private static Channel channel;
    private static String nodeId;
    private static AppConfig config;
    private static final String EXCHANGE_NAME = "nodes.topic";
    private static MessageService messageService;
    private static ResourceManager resourceManager;
    
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
    
    private static void setupServices() {
        resourceManager = new ResourceManager(nodeId + "-resource", nodeId);
        messageService = new MessageService(channel, nodeId, EXCHANGE_NAME, resourceManager);
        log.info("MessageService initialized");
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
            
            channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
            String myQueue = getQueueName(nodeId);
            channel.queueDeclare(myQueue, true, false, false, null);
            
            channel.queueBind(myQueue, EXCHANGE_NAME, nodeId + ".#");
            
            channel.basicConsume(myQueue, false, (consumerTag, delivery) -> {
                try {
                    messageService.handleMessage(
                        delivery.getEnvelope().getRoutingKey(),
                        delivery.getBody(),
                        delivery.getProperties()
                    );
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                }
            }, consumerTag -> {});
            
            System.out.println("RabbitMQ připojení úspěšně navázáno pro " + nodeId);
            System.out.println("Poslouchám na frontě: " + myQueue);
        } catch (Exception e) {
            log.error("RabbitMQ setup error: {}", e.getMessage());
        }
    }
    
    private static void startServer(int port) {
        new NodeController(nodeId, messageService, resourceManager, port);
        log.info("{} běží na http://{}:{}", nodeId, getOwnIp(), port);
    }

    public static void main(String[] args) {
        new File("logs").mkdirs();
        
        loadConfig();
        setupRabbitMQ();
        setupServices();
        setupNode();
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