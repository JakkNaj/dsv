package com.dsv;

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
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import com.dsv.node.Node;
import com.dsv.resource.Resource;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.LoggerFactory;
import com.rabbitmq.client.Address;

@Slf4j
public class Main {
    private static Channel channel;
    private static AppConfig config;
    private static NodeConfig nodeConfig;
    private static final String NODE_EXCHANGE = "nodes.topic";
    private static final String RESOURCE_EXCHANGE = "resources.topic";
    
    private static Node node;
    private static Resource resource;
    
    public static void main(String[] args) {

        new File("logs").mkdirs();
        
        loadConfig();
        setupRabbitMQConnection();
        
        String nodeId = nodeConfig.getId();
        node = new Node(nodeId, channel, NODE_EXCHANGE, config);
        
        if (nodeConfig.isResource()) {
            resource = new Resource(nodeId + "-resource", nodeId, channel, RESOURCE_EXCHANGE, config);
        }
        
        node.start(nodeConfig.getPort());
        if (resource != null) {
            resource.start();
        }
    }
    
    private static void loadConfig() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = Main.class.getClassLoader()
                .getResourceAsStream("config.yml");
            config = yaml.loadAs(inputStream, AppConfig.class);
            String serverIp = getOwnIp();
            nodeConfig = config.getNodes().get(serverIp);

        } catch (Exception e) {
            System.err.println("Chyba při načítání konfigurace: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void setupRabbitMQConnection() {
        try {
            RabbitConfig rmqConfig = config.getRabbitmq();
            
            Address[] addresses = new Address[] {
                new Address(rmqConfig.getHosts().get(0).getHost(), rmqConfig.getHosts().get(0).getPort()),
                new Address(rmqConfig.getHosts().get(1).getHost(), rmqConfig.getHosts().get(1).getPort())
            };
            
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUsername(rmqConfig.getUsername());
            factory.setPassword(rmqConfig.getPassword());
            
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(10000);
            
            factory.setConnectionTimeout(5000);
            factory.setHandshakeTimeout(10000);
            
            Connection connection = factory.newConnection(addresses);
            channel = connection.createChannel();
            
            log.info("RabbitMQ channel established for node: {}", nodeConfig.getId());
        } catch (Exception e) {
            log.error("RabbitMQ connection error: {}", e.getMessage());
            throw new RuntimeException("Failed to setup RabbitMQ connection", e);
        }
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
                    log.info("Found IP address: {}", addr.getHostAddress());
                    return addr.getHostAddress();
                }
            }
        } catch (SocketException e) {
            System.err.println("Nelze získat IP adresu: " + e.getMessage());
        }
        return "0.0.0.0";
    }
}