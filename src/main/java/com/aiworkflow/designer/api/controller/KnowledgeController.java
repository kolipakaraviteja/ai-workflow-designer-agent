package com.aiworkflow.designer.api.controller;

import com.aiworkflow.designer.knowledge.service.KnowledgeService;
import com.aiworkflow.designer.persistence.entity.BpmnKnowledge;
import com.aiworkflow.designer.persistence.entity.DocumentSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public ResponseEntity<List<BpmnKnowledge>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag) {
        if (category != null) {
            return ResponseEntity.ok(knowledgeService.getKnowledgeByCategory(category));
        }
        if (tag != null) {
            return ResponseEntity.ok(knowledgeService.getKnowledgeByTag(tag));
        }
        return ResponseEntity.ok(knowledgeService.getKnowledgeByCategory("PATTERN"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BpmnKnowledge> add(@RequestBody Map<String, String> body) {
        BpmnKnowledge knowledge = knowledgeService.addKnowledge(
                body.get("title"),
                body.get("content"),
                body.get("category"),
                body.get("source"),
                body.get("tags"));
        return ResponseEntity.ok(knowledge);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Document>> search(@RequestParam String q,
                                                 @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(knowledgeService.searchKnowledge(q, topK));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<DocumentSource>> listDocuments() {
        return ResponseEntity.ok(knowledgeService.listDocumentSources("KNOWLEDGE"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        knowledgeService.deleteKnowledge(id);
        return ResponseEntity.noContent().build();
    }
}
