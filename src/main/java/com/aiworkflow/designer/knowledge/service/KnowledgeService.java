package com.aiworkflow.designer.knowledge.service;

import com.aiworkflow.designer.persistence.entity.BpmnKnowledge;
import com.aiworkflow.designer.persistence.entity.DocumentSource;
import com.aiworkflow.designer.persistence.repository.BpmnKnowledgeRepository;
import com.aiworkflow.designer.persistence.repository.DocumentSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeService {

    private final BpmnKnowledgeRepository knowledgeRepository;
    private final DocumentSourceRepository documentSourceRepository;
    private final VectorStore vectorStore;

    @Transactional
    public BpmnKnowledge addKnowledge(String title, String content, String category, String source, String tags) {
        log.info("Adding knowledge: {}", title);

        BpmnKnowledge knowledge = new BpmnKnowledge();
        knowledge.setContent(content);
        knowledge.setKnowledgeType(category);
        knowledge.setSource(source);
        knowledge.setMetadata(tags != null ? Map.of("title", title, "tags", tags) : Map.of("title", title));
        knowledge.setCreatedAt(java.time.LocalDateTime.now());
        knowledge.setUpdatedAt(java.time.LocalDateTime.now());

        knowledge = knowledgeRepository.saveAndFlush(knowledge);

        // Add to vector store
        Document document = knowledge.toDocument();
        vectorStore.add(List.of(document));

        log.info("Knowledge added and indexed: {}", title);
        return knowledge;
    }

    @Transactional
    public void addBulkKnowledge(List<BpmnKnowledge> knowledgeList) {
        log.info("Adding {} knowledge documents", knowledgeList.size());

        List<BpmnKnowledge> savedKnowledge = knowledgeRepository.saveAll(knowledgeList);
        knowledgeRepository.flush();

        // Add to vector store
        List<Document> documents = savedKnowledge.stream()
                .map(BpmnKnowledge::toDocument)
                .collect(Collectors.toList());

        vectorStore.add(documents);

        log.info("Bulk knowledge added and indexed");
    }

    public List<Document> searchKnowledge(String query, int topK) {
        log.info("Searching knowledge for query: {}", query);

        org.springframework.ai.vectorstore.SearchRequest searchRequest = org.springframework.ai.vectorstore.SearchRequest.query(query).withTopK(topK);
        List<Document> results = vectorStore.similaritySearch(searchRequest);

        log.info("Found {} relevant documents", results.size());
        return results;
    }

    /**
     * Retrieves relevant knowledge and formats it for injection into a system prompt.
     * Returns an empty string when nothing is found or retrieval fails, so callers
     * never break on a RAG outage.
     */
    public String retrieveContext(String query, int topK) {
        try {
            List<Document> docs = searchKnowledge(query, topK);
            if (docs == null || docs.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("<knowledge_base>\n");
            for (Document doc : docs) {
                sb.append("---\n").append(doc.getContent()).append('\n');
            }
            sb.append("</knowledge_base>");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Knowledge retrieval failed for query '{}': {}", query, e.getMessage());
            return "";
        }
    }

    public List<BpmnKnowledge> getKnowledgeByCategory(String category) {
        return knowledgeRepository.findByKnowledgeType(category);
    }

    public List<BpmnKnowledge> searchKnowledgeByKeyword(String keyword) {
        return knowledgeRepository.searchByKeyword(keyword);
    }

    public List<BpmnKnowledge> getKnowledgeByTag(String tag) {
        return knowledgeRepository.findByMetadataContaining(tag);
    }

    /**
     * Ingests a raw text document: splits it into chunks, stores each chunk as
     * knowledge (linked by document_id metadata) and indexes it in the vector store.
     * Returns the created DocumentSource record.
     */
    @Transactional
    public DocumentSource ingestDocument(String filename, String contentType, String text,
                                         String category, String uploadedBy) {
        log.info("Ingesting document {} ({} chars) into knowledge base", filename, text.length());

        DocumentSource source = DocumentSource.builder()
                .filename(filename)
                .contentType(contentType)
                .purpose("KNOWLEDGE")
                .uploadedBy(uploadedBy)
                .build();
        source = documentSourceRepository.saveAndFlush(source);

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.split(new Document(text));

        List<BpmnKnowledge> entries = new ArrayList<>();
        List<Document> documents = new ArrayList<>();
        int index = 0;
        for (Document chunk : chunks) {
            BpmnKnowledge knowledge = new BpmnKnowledge();
            knowledge.setContent(chunk.getContent());
            knowledge.setKnowledgeType(category != null ? category : "DOCUMENTATION");
            knowledge.setSource(filename);
            knowledge.setMetadata(Map.of(
                    "title", filename,
                    "document_id", source.getId().toString(),
                    "chunk_index", index));
            knowledge.setCreatedAt(java.time.LocalDateTime.now());
            knowledge.setUpdatedAt(java.time.LocalDateTime.now());
            knowledge = knowledgeRepository.saveAndFlush(knowledge);

            Map<String, Object> docMetadata = new java.util.HashMap<>();
            docMetadata.put("id", knowledge.getId().toString());
            docMetadata.put("knowledgeType", knowledge.getKnowledgeType());
            docMetadata.put("source", filename);
            docMetadata.put("document_id", source.getId().toString());
            docMetadata.put("chunk_index", index);
            documents.add(new Document(knowledge.getId().toString(), chunk.getContent(), docMetadata));
            entries.add(knowledge);
            index++;
        }

        vectorStore.add(documents);

        source.setChunkCount(chunks.size());
        documentSourceRepository.save(source);

        log.info("Ingested document {} into {} knowledge chunks", filename, chunks.size());
        return source;
    }

    public List<DocumentSource> listDocumentSources(String purpose) {
        return documentSourceRepository.findByPurposeOrderByCreatedAtDesc(purpose);
    }

    @Transactional
    public void deleteKnowledge(UUID knowledgeId) {
        log.info("Deleting knowledge: {}", knowledgeId);

        BpmnKnowledge knowledge = knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + knowledgeId));

        // Remove from vector store
        Document document = knowledge.toDocument();
        vectorStore.delete(List.of(document.getId()));

        knowledgeRepository.deleteById(knowledgeId);

        log.info("Knowledge deleted: {}", knowledgeId);
    }

    @Transactional
    public void initializeDefaultKnowledge() {
        log.info("Initializing default BPMN knowledge base");

        if (knowledgeRepository.count() > 0) {
            log.info("Knowledge base already initialized, skipping");
            return;
        }

        // Add BPMN specification
        addKnowledge(
                "BPMN 2.0 Specification Overview",
                """
                BPMN 2.0 (Business Process Model and Notation) is a standard for business process modeling.
                It provides a graphical notation for specifying business processes in a Business Process Diagram (BPD).

                Key BPMN Elements:
                - Flow Objects: Events, Activities, Gateways
                - Connecting Objects: Sequence Flows, Message Flows, Associations
                - Swimlanes: Pools and Lanes
                - Artifacts: Data Objects, Groups, Annotations

                BPMN is designed to be readable by both business analysts and technical developers.
                """,
                "SPECIFICATION",
                "OMG BPMN 2.0 Specification",
                "bpmn, specification, standard"
        );

        // Add gateway patterns
        addKnowledge(
                "Exclusive Gateway Pattern",
                """
                An Exclusive Gateway (XOR Gateway) is used to create alternative paths in a process flow.
                Only one of the outgoing paths can be taken based on condition evaluation.

                Best Practices:
                - Always define conditions for sequence flows
                - Consider adding a default flow for unmatched conditions
                - Ensure conditions are mutually exclusive
                - Use descriptive names for gateways

                Example: "Amount > 10000" -> Manager Approval, else -> Direct Approval
                """,
                "PATTERN",
                "BPMN Best Practices",
                "gateway, exclusive, pattern, decision"
        );

        // Add parallel gateway pattern
        addKnowledge(
                "Parallel Gateway Pattern",
                """
                A Parallel Gateway (AND Gateway) is used to split the process flow into multiple parallel paths.
                All outgoing paths are executed simultaneously.

                Best Practices:
                - Use for independent tasks that can run concurrently
                - Always include a converging parallel gateway
                - Ensure parallel paths can complete independently
                - Consider resource implications of parallel execution

                Example: HR Setup, IT Setup, and Finance Setup can run in parallel
                """,
                "PATTERN",
                "BPMN Best Practices",
                "gateway, parallel, pattern, concurrency"
        );

        // Add event patterns
        addKnowledge(
                "Timer Event Pattern",
                """
                Timer Events are used to represent time-based triggers in a process.
                They can be used as start events, intermediate events, or boundary events.

                Common Use Cases:
                - Process timeouts and SLA monitoring
                - Periodic task execution
                - Reminder notifications
                - Escalation after deadline

                Timer Expressions:
                - Duration: PT30M (30 minutes), PT2H (2 hours)
                - Date: 2024-12-31T23:59:59
                - Cycle: R3/PT1H (every hour, 3 times)
                """,
                "PATTERN",
                "BPMN Best Practices",
                "event, timer, pattern, sla"
        );

        // Add error handling pattern
        addKnowledge(
                "Error Handling Pattern",
                """
                Error handling in BPMN is typically implemented using Boundary Events.
                Boundary events can be attached to tasks to catch errors and define alternative flows.

                Types of Error Handling:
                - Error Boundary Events: Catch specific error types
                - Timer Boundary Events: Handle timeouts
                - Signal Boundary Events: Handle external signals
                - Compensation Events: Undo completed work

                Best Practices:
                - Define clear error types
                - Provide meaningful error messages
                - Include retry logic where appropriate
                - Ensure compensation for critical operations
                """,
                "PATTERN",
                "BPMN Best Practices",
                "error, handling, pattern, boundary"
        );

        // Add subprocess pattern
        addKnowledge(
                "Subprocess Pattern",
                """
                Subprocesses allow you to group related activities and create hierarchical process models.
                They improve readability and enable reuse of process fragments.

                Types of Subprocesses:
                - Embedded Subprocess: Part of the same process level
                - Call Activity: Reusable subprocess definition
                - Event Subprocess: Scoped to specific events

                Best Practices:
                - Use subprocesses for logical grouping
                - Keep subprocesses focused on single responsibility
                - Use call activities for reusable processes
                - Consider event subprocesses for error handling
                """,
                "PATTERN",
                "BPMN Best Practices",
                "subprocess, pattern, reuse, hierarchy"
        );

        // Add user task best practices
        addKnowledge(
                "User Task Best Practices",
                """
                User Tasks represent work that needs to be performed by a human actor.
                Proper configuration ensures tasks are routed to the right people.

                Key Configuration:
                - Assignee: Specific user who must complete the task
                - Candidate Users: List of potential assignees
                - Candidate Groups: List of groups that can claim the task
                - Due Date: When the task should be completed
                - Priority: Task importance level

                Best Practices:
                - Use candidate groups for flexibility
                - Set appropriate due dates and priorities
                - Provide clear task descriptions
                - Consider form key for user interface
                """,
                "BEST_PRACTICE",
                "BPMN Best Practices",
                "user task, assignment, best practice"
        );

        // Add service task patterns
        addKnowledge(
                "Service Task Implementation Patterns",
                """
                Service Tasks represent automated work performed by a system or service.
                They can be implemented using various approaches.

                Implementation Options:
                - Java Class: Direct class invocation
                - Delegate Expression: Spring bean reference
                - Expression: SpEL expression evaluation
                - External Service: REST/SOAP call

                Best Practices:
                - Use async execution for long-running services
                - Implement proper error handling
                - Consider idempotency for retry scenarios
                - Monitor service performance
                - Use appropriate timeouts
                """,
                "BEST_PRACTICE",
                "BPMN Best Practices",
                "service task, implementation, pattern"
        );

        log.info("Default BPMN knowledge base initialized with {} documents", knowledgeRepository.count());
    }
}
