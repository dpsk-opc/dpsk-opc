package com.xiaomizhou.dpsk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xiaomizhou.dpsk.db.mapper")
public class OpcApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpcApplication.class, args);
	}

}
