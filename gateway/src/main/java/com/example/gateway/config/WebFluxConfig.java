package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * WebFlux 配置
 * 配置静态资源和路由
 */
@Configuration
public class WebFluxConfig {

    /**
     * 配置静态资源路由
     * 根路径 / 映射到 index.html
     */
    @Bean
    public RouterFunction<ServerResponse> staticResourceRouter() {
        return RouterFunctions.route()
                .GET("/", request -> ServerResponse.ok()
                        .bodyValue(new ClassPathResource("static/index.html")))
                .build();
    }
}
