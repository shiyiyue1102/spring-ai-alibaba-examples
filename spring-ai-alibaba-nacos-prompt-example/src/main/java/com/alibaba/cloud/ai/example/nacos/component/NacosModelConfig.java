package com.alibaba.cloud.ai.example.nacos.component;

import com.alibaba.cloud.nacos.annotation.NacosConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

public class NacosModelConfig {


	private String model;
	private String apiKey;
	private String baseUrl;
	private String temperature;
}
