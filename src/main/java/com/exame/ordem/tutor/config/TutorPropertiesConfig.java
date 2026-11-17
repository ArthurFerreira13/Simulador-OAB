package com.exame.ordem.tutor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        TutorProperties.class,
        CascataProperties.class,
        RagProperties.class,
        SemanticCacheProperties.class   // ← novo

})
class TutorPropertiesConfig {
}