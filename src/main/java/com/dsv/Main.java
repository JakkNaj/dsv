package com.dsv;

import io.javalin.Javalin;

public class Main {
    public static void main(String[] args) {
        Javalin app = Javalin.create(/*config*/)
            .get("/", ctx -> {
                System.out.println("Endpoint byl zavolán!");
                ctx.result("Hello World from Javalin!");
            })
            .start(7070);

        System.out.println("Server běží na http://localhost:7070");
    }
}