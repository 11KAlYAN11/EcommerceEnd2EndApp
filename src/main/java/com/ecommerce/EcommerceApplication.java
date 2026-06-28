package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Entry point for the Ecommerce Backend application.
 *
 * @SpringBootApplication is shorthand for three annotations:
 *
 *   @SpringBootConfiguration
 *     → Marks this as a configuration class.
 *       Spring can define @Bean methods here.
 *
 *   @EnableAutoConfiguration
 *     → Tells Spring Boot to auto-configure itself based on
 *       what's on the classpath. Added spring-boot-starter-web?
 *       Auto-configures embedded Tomcat and Jackson.
 *
 *   @ComponentScan
 *     → Scans com.ecommerce and ALL sub-packages for:
 *       @Component, @Service, @Repository, @Controller, @RestController
 *       and registers them as Spring Beans.
 *
 * SpringApplication.run():
 *   1. Creates ApplicationContext (the IoC container)
 *   2. Registers all beans found by ComponentScan
 *   3. Starts embedded Tomcat on port 8080
 *   4. Application is ready to serve requests
 */
@SpringBootApplication
@EnableJpaAuditing
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
