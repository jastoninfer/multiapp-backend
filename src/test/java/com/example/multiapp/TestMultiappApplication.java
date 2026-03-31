package com.example.multiapp;

import org.springframework.boot.SpringApplication;

public class TestMultiappApplication {

    public static void main(String[] args) {
        SpringApplication.from(MultiappApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
