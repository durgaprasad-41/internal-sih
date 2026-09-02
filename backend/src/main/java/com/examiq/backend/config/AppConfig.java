package com.examiq.backend.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Lets Jackson serialize JPA entities that still have uninitialized lazy
    // associations (e.g. Rating/Bookmark returned directly by controllers)
    // instead of throwing on the Hibernate proxy type. Uninitialized lazy
    // properties are rendered as null rather than force-loaded.
    @Bean
    public Hibernate6Module hibernate6Module() {
        return new Hibernate6Module();
    }
}
