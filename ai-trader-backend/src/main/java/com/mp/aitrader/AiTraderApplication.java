package com.mp.aitrader;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@MapperScan({"com.mp.aitrader.mapper", "com.mp.aitrader.conversation.mapper", "com.mp.aitrader.memory.mapper", "com.mp.aitrader.knowledge.mapper", "com.mp.aitrader.context.mapper"})
@EnableTransactionManagement //开启注解方式的事务管理
public class AiTraderApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiTraderApplication.class, args);
    }

}
