package com.dsv;

import io.javalin.Javalin;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class Main {
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
        String serverIp = getServerIp();
        
        Javalin app = Javalin.create()
            .get("/", ctx -> {
                System.out.println("Endpoint byl zavolán!");
                ctx.result("Hello World from Javalin!");
            })
            .post("/rabbitmq", ctx -> {
                System.out.println("Endpoint rabbitmq byl zavolán!");
                try {
                    ConnectionFactory factory = new ConnectionFactory();
                    factory.setHost("192.168.64.4");
                    factory.setPort(5672);
                    factory.setUsername("guest");
                    factory.setPassword("guest");

                    Connection connection = factory.newConnection();
                    Channel channel = connection.createChannel();
                    
                    String queueName = "test-queue";
                    channel.queueDeclare(queueName, false, false, false, null);
                    
                    String message = "Message from /rabbitmq endpoint";
                    channel.basicPublish("", queueName, null, message.getBytes());
                    
                    channel.close();
                    connection.close();
                    
                    ctx.result("success");
                } catch (Exception e) {
                    System.err.println("Chyba při odesílání do RabbitMQ: " + e.getMessage());
                    ctx.result("error");
                }
            })
            .start(7070);


        System.out.println("Server běží na http://" + serverIp + ":7070");
    }
}