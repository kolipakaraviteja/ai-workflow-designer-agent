package com.aiworkflow.designer.knowledge.config;

import com.aiworkflow.designer.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeInitializationConfig {

    private final KnowledgeService knowledgeService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeKnowledgeBase() {
        try {
            log.info("Initializing BPMN knowledge base on application startup");
            knowledgeService.initializeDefaultKnowledge();
        } catch (Exception e) {
            log.error("Failed to initialize knowledge base", e);
            // Don't fail the application startup if knowledge initialization fails
        }
    }
}
