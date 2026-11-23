package com.exame.ordem.tutor.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

final class GeminiApiKeyConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment()
                .getProperty("app.tutor.embedding.gemini-api-key"));
    }
}
