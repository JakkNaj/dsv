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
    private static NodeConfig nodeConfig;
    
    private static void loadConfig() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = Main.class.getClassLoader()
                .getResourceAsStream("config.yml");
            config = yaml.loadAs(inputStream, AppConfig.class);
            String serverIp = getOwnIp();
            nodeConfig = config.getNodes().get(serverIp);
            nodeId = nodeConfig.getId();

        } catch (Exception e) {
            System.err.println("Chyba při načítání konfigurace: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void setupController() {
        int port = nodeConfig.getPort();
        startServer(port);
    }
    
    private static void setupServices() {
        resourceManager = new ResourceManager(nodeId + "-resource", nodeId);
        messageService = new MessageService(channel, nodeId, EXCHANGE_NAME, resourceManager);
        log.info("MessageService initialized");
    }
    
    private static void setupRabbitMQConnection() {
        try {
            RabbitConfig rmqConfig = config.getRabbitmq();
            
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rmqConfig.getHost());
            factory.setPort(rmqConfig.getPort());
            factory.setUsername(rmqConfig.getUsername());
            factory.setPassword(rmqConfig.getPassword());
            
            Connection connection = factory.newConnection();
            channel = connection.createChannel();
            
            // Deklarace exchange
            channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
            
            log.info("RabbitMQ connection established");
        } catch (Exception e) {
            log.error("RabbitMQ connection error: {}", e.getMessage());
            throw new RuntimeException("Failed to setup RabbitMQ connection", e);
        }
    }
    
    private static void setupRabbitMQConsumer() {
        try {
            String queueName = nodeId + "-queue";
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, EXCHANGE_NAME, nodeId + ".#");
            
            channel.basicQos(1);
            channel.basicConsume(queueName, false, (consumerTag, delivery) -> {
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
            
            log.info("RabbitMQ consumer setup for queue: {}", queueName);
        } catch (Exception e) {
            log.error("RabbitMQ consumer setup error: {}", e.getMessage());
            throw new RuntimeException("Failed to setup RabbitMQ consumer", e);
        }
    }
    
    private static void startServer(int port) {
        new NodeController(nodeId, messageService, resourceManager, port);
        log.info("{} běží na http://{}:{}", nodeId, getOwnIp(), port);
    }

    public static void main(String[] args) {
        new File("logs").mkdirs();
        
        loadConfig();
        setupRabbitMQConnection();  
        setupServices();            
        setupRabbitMQConsumer();   
        setupController();
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