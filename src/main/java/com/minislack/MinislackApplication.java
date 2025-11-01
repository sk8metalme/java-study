package com.minislack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MiniSlack - Slack風チャットアプリケーション
 * オニオンアーキテクチャを採用したSpring Bootアプリケーション
 */
@SpringBootApplication
public class MinislackApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinislackApplication.class, args);
    }
}
