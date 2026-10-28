package com.aiworkflow.designer.api.controller;

import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.agent.service.AgentService;
import com.aiworkflow.designer.api.dto.DocumentUploadResponse;
import com.aiworkflow.designer.conversation.memory.ConversationContext;
import com.aiworkflow.designer.conversation.service.ConversationService;
import com.aiworkflow.designer.document.service.DocumentParserService;
import com.aiworkflow.designer.knowledge.service.KnowledgeService;
import com.aiworkflow.designer.persistence.entity.DocumentSource;
import com.aiworkflow.designer.persistence.repository.AppUserRepository;
import com.aiworkflow.designer.persistence.repository.DocumentSourceRepository;
import com.aiworkflow.designer.security.AccessControlService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentParserService documentParserService;
    private final KnowledgeService knowledgeService;
    private final AgentService agentService;
    private final ConversationService conversationService;
    private final DocumentSourceRepository documentSourceRepository;
    private final AppUserRepository appUserRepository;
    private final AccessControlService accessControl;
    private final com.aiworkflow.designer.document.service.UploadRateLimiter uploadRateLimiter;

    /**
     * Upload a document. purpose=KNOWLEDGE ingests it into the shared RAG
     * knowledge base and is restricted to admins; purpose=WORKFLOW extracts the
     * text and asks the designer agent to build a workflow from it.
     */
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "KNOWLEDGE") String purpose,
            @AuthenticationPrincipal Jwt jwt) {

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String username = accessControl.requireUsername(jwt);

        if ("KNOWLEDGE".equalsIgnoreCase(purpose) && !accessControl.isAdmin(jwt)) {
            throw new AccessDeniedException("Knowledge-base ingestion is restricted to administrators");
        }
        uploadRateLimiter.checkUpload(username);

        log.info("Document upload: {} (purpose={}, user={})", filename, purpose, username);

        String text = documentParserService.extractText(file);
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(DocumentUploadResponse.builder()
                    .filename(filename)
                    .purpose(purpose)
                    .message("No text could be extracted from the document")
                    .build());
        }

        if ("WORKFLOW".equalsIgnoreCase(purpose)) {
            return ResponseEntity.ok(handleWorkflowPurpose(file, filename, text, jwt));
        }
        return ResponseEntity.ok(handleKnowledgePurpose(file, filename, text, username));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String purpose) {
        return ResponseEntity.ok(
                documentSourceRepository.findByPurposeOrderByCreatedAtDesc(
                        purpose != null ? purpose : "KNOWLEDGE"));
    }

    private DocumentUploadResponse handleKnowledgePurpose(MultipartFile file, String filename,
                                                          String text, String username) {
        DocumentSource source = knowledgeService.ingestDocument(
                filename, file.getContentType(), text, "DOCUMENTATION", username);

        return DocumentUploadResponse.builder()
                .filename(filename)
                .purpose("KNOWLEDGE")
                .extractedCharacters(text.length())
                .chunkCount(source.getChunkCount())
                .documentSourceId(source.getId())
                .message("Document ingested into knowledge base as " + source.getChunkCount() + " chunks")
                .build();
    }

    private DocumentUploadResponse handleWorkflowPurpose(MultipartFile file, String filename,
                                                         String text, Jwt jwt) {
        DocumentSource source = DocumentSource.builder()
                .filename(filename)
                .contentType(file.getContentType())
                .purpose("WORKFLOW")
                .uploadedBy(jwt != null ? jwt.getSubject() : null)
                .chunkCount(0)
                .build();
        documentSourceRepository.save(source);

        String prompt = """
                The following text was extracted from a document named "%s".
                Design a BPMN workflow that implements the business process it describes.
                Extract the process steps, decision points, actors and conditions, then
                create the workflow using your tools.

                Document text:
                %s
                """.formatted(filename, truncate(text, 8000));

        UUID conversationId = conversationService.createConversationId();
        UUID userId = jwt != null
                ? appUserRepository.findByUsername(jwt.getSubject()).map(u -> u.getId()).orElse(null)
                : null;
        ConversationContext.set(null, userId);
        try {
            AgentState state = agentService.processWithDesigner(prompt, null, conversationId.toString());
            WorkflowDefinition workflow = (WorkflowDefinition) state.getContext("modified_workflow");

            return DocumentUploadResponse.builder()
                    .filename(filename)
                    .purpose("WORKFLOW")
                    .extractedCharacters(text.length())
                    .documentSourceId(source.getId())
                    .workflow(workflow)
                    .message(state.getLastAgentResponse())
                    .build();
        } finally {
            ConversationContext.clear();
        }
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
