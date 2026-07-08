package com.zzy.drai;

import com.zzy.drai.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class DraiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DraiApplication.class, args);
    }
}
