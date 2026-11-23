package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.config.TutorProperties;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
class FreeLlmApiLlmProvider implements LlmProvider {

    private final StreamingChatModel model;
    private final int maxOutputTokens;

    FreeLlmApiLlmProvider(@Qualifier("freeLlmApiStreamingChatModel") StreamingChatModel model,
                          TutorProperties properties) {
        this.model = model;
        this.maxOutputTokens = properties.freellmapi().maxTokens();
    }

    @Override public StreamingChatModel streaming() { return model; }
    @Override public String name() { return "freellmapi"; }
    @Override public long ttftMs() { return 30_000L; }
    @Override public int maxOutputTokens() { return maxOutputTokens; }
}
