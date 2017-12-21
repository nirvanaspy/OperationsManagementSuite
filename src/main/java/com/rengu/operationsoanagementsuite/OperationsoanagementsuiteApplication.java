package com.rengu.operationsoanagementsuite;

import com.rengu.operationsoanagementsuite.Thread.UDPReceiveThread;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OperationsoanagementsuiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperationsoanagementsuiteApplication.class, args);
        // 启动UDP监听线程
        new UDPReceiveThread().run();
    }
}
