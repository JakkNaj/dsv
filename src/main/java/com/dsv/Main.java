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

public class Main {
    private static Channel channel;
    private static String nodeId;
    
    private static String getQueueName(String targetNode) {
        return targetNode + "-queue";
    }
    
    private static void setupRabbitMQ() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("192.168.3.228");
            factory.setPort(5672);
            factory.setUsername("myuser");
            factory.setPassword("mypassword");

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

    private static String getServerIp() {
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

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Použití: java -jar app.jar <node1|node2>");
            System.exit(1);
        }
        nodeId = args[0];
        
        setupRabbitMQ();
        String serverIp = getServerIp();
        
        Javalin app = Javalin.create()
            .get("/", ctx -> {
                ctx.result(nodeId + " is running!");
            })
            .post("/send/:targetNode", ctx -> {
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
            .start(7070);

        System.out.println(nodeId + " běží na http://" + serverIp + ":7070");
    }
}