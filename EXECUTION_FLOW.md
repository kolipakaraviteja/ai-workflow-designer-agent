# AI Workflow Designer Agent — Execution Flow & Architecture

> One-page reference for how the application is built, how a request flows through it, and what matters most when running or extending it.

---

## 1. Purpose

A commercial AI assistant that helps business users:

- Design BPMN workflows in natural language.
- Get requirements conversationally before building.
- Visualise workflows in a business-friendly way (Simple/Steps/Advanced BPMN).
- Validate, simulate, and later deploy workflows.

The system separates **LLM reasoning** from **deterministic execution**: the LLM decides and plans; Spring services/tools perform the actual mutations, persistence, and BPMN generation.

---

## 2. Tech Stack

| Layer | Tech |
|-------|------|
| Backend | Java 21, Spring Boot 3.2.0, Maven |
| AI | Spring AI 1.0.0-M4, OpenAI-compatible API (local LLM Studio or OpenAI) |
| BPMN Engine | Flowable 7.0.0 |
| Persistence | PostgreSQL 16 + pgvector, Spring Data JPA |
| Security | Spring Security, JWT resource-server, BCrypt |
| Frontend | React 18, TypeScript, Vite, bpmn-js, mermaid |
| DevOps | Docker, Docker Compose, Render (`render.yaml`) |

---

## 3. High-Level Architecture

```
┌──────────────────┐      REST /api        ┌──────────────────────────┐
│   React Frontend │  <────────────────>    │   Spring Boot Backend    │
│                  │   Bearer JWT           │   (port 8080)            │
│  Simple/Steps/   │                        │                          │
│  Advanced views  │                        │  ┌──────────────────┐    │
│  Chat interface  │                        │  │  REST Controllers  │    │
└──────────────────┘                        │  └────────┬─────────┘    │
                                            │           │              │
                                            │  ┌────────▼──────────┐   │
                                            │  │  AgentService     │   │
                                            │  │  - Orchestrator   │   │
                                            │  │  - Consultant     │   │
                                            │  │  - Designer       │   │
                                            │  │  - Validator      │   │
                                            │  │  - Optimizer      │   │
                                            │  │  - Runtime        │   │
                                            │  └────────┬──────────┘   │
                                            │           │              │
                                            │  ┌────────▼──────────┐   │
                                            │  │  Workflow Tools   │   │
                                            │  │  (create, add,    │   │
                                            │  │   connect, etc.)  │   │
                                            │  └────────┬──────────┘   │
                                            │           │              │
                                            │  ┌────────▼──────────┐   │
                                            │  │  Workflow Model   │   │
                                            │  │  BPMN Generator   │   │
                                            │  │  Flowable Runtime │   │
                                            │  └────────┬──────────┘   │
                                            │           │              │
                                            │  ┌────────▼──────────┐   │
                                            │  │  JPA / pgvector   │   │
                                            │  │  PostgreSQL       │   │
                                            │  └───────────────────┘   │
                                            └──────────────────────────┘
```

---

## 4. Project Structure

```
ai-workflow-designer-agent/
├── pom.xml
├── docker-compose.yml
├── render.yaml
├── Dockerfile
├── README.md
├── IMPLEMENTATION_STATUS.md
├── src/main/java/com/aiworkflow/designer/
│   ├── AiWorkflowDesignerApplication.java
│   ├── agent/
│   │   ├── core/BaseAgent.java
│   │   ├── impl/
│   │   │   ├── OrchestratorAgent.java
│   │   │   ├── ConsultantAgent.java
│   │   │   ├── DesignerAgent.java
│   │   │   ├── ValidatorAgent.java
│   │   │   ├── OptimizerAgent.java
│   │   │   └── RuntimeAgent.java
│   │   ├── model/AgentState.java
│   │   └── service/AgentService.java
│   ├── api/
│   │   ├── controller/
│   │   │   ├── AgentController.java
│   │   │   ├── AuthController.java
│   │   │   ├── WorkflowController.java
│   │   │   ├── SimulationController.java
│   │   │   ├── DeploymentController.java
│   │   │   ├── KnowledgeController.java
│   │   │   └── DocumentController.java
│   │   └── dto/           # AgentRequest, AgentResponse, WorkflowResponse, etc.
│   ├── bpmn/
│   │   ├── BpmnGenerator.java
│   │   ├── BpmnParser.java
│   │   └── BpmnValidator.java
│   ├── config/
│   │   ├── AiConfiguration.java
│   │   ├── SecurityConfiguration.java
│   │   ├── JwtConfiguration.java
│   │   └── WebConfiguration.java
│   ├── conversation/
│   │   ├── memory/JpaChatMemory.java
│   │   └── service/ConversationService.java
│   ├── document/
│   │   └── service/DocumentParserService.java
│   ├── knowledge/
│   │   ├── service/KnowledgeService.java
│   │   └── config/KnowledgeInitializationConfig.java
│   ├── persistence/
│   │   ├── entity/
│   │   │   ├── Workflow.java
│   │   │   ├── WorkflowSpec.java
│   │   │   ├── Conversation.java
│   │   │   ├── AppUser.java
│   │   │   ├── BpmnKnowledge.java
│   │   │   ├── Deployment.java
│   │   │   ├── ProcessInstance.java
│   │   │   ├── ValidationResult.java
│   │   │   ├── SimulationResult.java
│   │   │   ├── AgentExecution.java
│   │   │   └── DocumentSource.java
│   │   └── repository/
│   ├── requirements/
│   │   └── service/RequirementSpecService.java
│   ├── runtime/
│   │   ├── flowable/FlowableProcessService.java
│   │   ├── flowable/FlowableDeploymentService.java
│   │   └── simulation/SimulationEngine.java
│   ├── security/
│   │   ├── JwtService.java
│   │   ├── AppUserDetailsService.java
│   │   └── AdminUserInitializer.java
│   ├── tools/
│   │   ├── WorkflowToolCallbacks.java
│   │   ├── ToolRegistry.java
│   │   └── <WorkflowTool impls>
│   └── workflow/
│       ├── model/
│       │   ├── WorkflowDefinition.java
│       │   ├── WorkflowNode.java
│       │   └── WorkflowEdge.java
│       └── service/WorkflowService.java
└── src/main/resources/
│   ├── application-local.properties
│   ├── application-render.properties
│   ├── schema.sql
│   └── schema-local.sql
```

The React application is maintained separately in the `ai-workflow-designer-ui` repo.

---

## 5. End-to-End Execution Flow

### 5.1 User logs in

1. Frontend calls `POST /api/auth/login`.
2. `AuthController` verifies `AppUser` password via `PasswordEncoder`.
3. `JwtService` generates a JWT signed with HS256.
4. Frontend stores `token` and `user` in `localStorage`.
5. Axios attaches the token to all `/api/**` requests.

### 5.2 User asks for a new workflow

Example message: *“Design an employee leave approval process.”*

```
Frontend: POST /api/agent/chat
          { message, workflowId?, conversationId? }
                 │
                 ▼
AgentController.chat()
  - Resolves user from JWT
  - Creates or reuses a conversationId
  - Loads existing Workflow / definition if workflowId provided
  - Calls AgentService.processUserRequest(message, definition, conversationId)
                 │
                 ▼
AgentService.processUserRequest()
  - Delegates to OrchestratorAgent.process()
                 │
                 ▼
OrchestratorAgent
  - beginRequest() → fresh AgentState
  - generateOrchestrationPlan() (internal, no conversationId, not persisted)
  - parsePlannedAgent() to decide: CONSULTANT / DESIGNER / etc.
  - executeOrchestrationPlan()
                 │
                 ▼
ConsultantAgent (default for new/vague requests)
  - Loads/gets WorkflowSpec for the conversation
  - Treats confirmation words ("yes", "ok", "proceed", etc.) as build signal
  - Calls updateSpecFromConversation() → extracts name, steps, actors,
    decisions, conditions, missingInfo from the chat using the LLM
  - isComplete() = name + steps + actors are present
  - If complete but not confirmed → summary + ask for "yes"
  - If complete and confirmed → set ready_to_build = true
                 │
                 ▼
OrchestratorAgent sees ready_to_build == true
  - Calls handOffToDesigner(spec, workflow, conversationId)
                 │
                 ▼
DesignerAgent
  - beginRequest() → fresh state
  - Builds Spring AI function callbacks from WorkflowToolCallbacks
  - Calls the LLM with system prompt + current workflow JSON + RAG context
  - LLM emits tool calls: create_workflow, add_task, add_gateway,
    add_event, connect_nodes, validate_workflow, generate_bpmn_xml
  - Tools mutate an AtomicReference<WorkflowDefinition>
  - After tool loop, mutated workflow is placed in AgentState.context
                 │
                 ▼
Back to AgentController
  - Pulls modifiedWorkflow from AgentState context
  - If existing workflow → updateWorkflow() + updateBpmnXml()
  - If new workflow → createWorkflow() + updateBpmnXml()
  - specService.markBuilt() if spec confirmed
  - Returns AgentResponse to frontend
                 │
                 ▼
Frontend
  - Appends assistant message to chat
  - If workflowId returned → refetches workflow and selects it
  - Renders Simple/Steps/Advanced tabs from workflow.definition and bpmnXml
```

### 5.3 BPMN generation and visualisation

- `BpmnGenerator` walks `WorkflowDefinition`.
- Nodes are assigned to graph layers via BFS and positioned left-to-right.
- Edges are routed between actual node bounds, not a fixed grid.
- Generated XML is stored as `bpmnXml` on the `Workflow` row.
- Frontend `BpmnViewer` renders the BPMN 2.0 XML with bpmn-js.
- `SimpleFlowViewer` converts the same `definition` into a Mermaid flowchart.
- `ProcessStepsView` renders a numbered, actor-centric narrative.

---

## 6. Key Data Models

### 6.1 `WorkflowDefinition` (engine-independent)

```java
WorkflowDefinition
├─ id, name, description, version
├─ nodes: List<WorkflowNode>
│   ├─ id, type, name, description
│   ├─ properties (assignee, etc.)
│   └─ metadata
├─ edges: List<WorkflowEdge>
│   ├─ id, source, target, condition, type
│   ├─ properties
│   └─ metadata
├─ variables
└─ metadata
```

### 6.2 `WorkflowSpec` (intake buffer)

Persists the in-progress business process specification:

- `processName`, `description`, `steps`, `actors`
- `decisions`, `conditions`, `missingInfo`
- Status lifecycle: `COLLECTING` → `PENDING_CONFIRMATION` → `CONFIRMED` → `BUILT`

### 6.3 `Workflow` (persistent version)

- `id`, `name`, `description`, `version`, `status`
- `definitionJson` — `WorkflowDefinition` as JSON
- `bpmnXml`
- `createdBy`, `updatedBy`, timestamps

### 6.4 RAG knowledge

- `BpmnKnowledge` entity with `embedding vector(1024)`.
- Seeded at startup by `KnowledgeInitializationConfig`.
- `KnowledgeService.retrieveContext(query, 4)` fetches relevant BPMN patterns.
- Knowledge is injected into system prompts in `BaseAgent`.

---

## 7. Configuration Critical Values

### Local profile (`application-local.properties`)

```properties
# Chat + embedding must point to your LM Studio server
spring.ai.openai.base-url=${LLM_STUDIO_BASE_URL:http://localhost:1234}
spring.ai.openai.chat.options.model=${LLM_STUDIO_MODEL:nvidia/nemotron-3-nano-omni}
spring.ai.openai.embedding.options.model=${LLM_STUDIO_EMBEDDING_MODEL:text-embedding-qwen3-embedding-0.6b}
spring.ai.openai.embedding.options.dimensions=1024
spring.ai.vectorstore.pgvector.dimension=1024

spring.datasource.url=jdbc:postgresql://localhost:5432/workflow_designer
spring.datasource.username=workspace
spring.datasource.password=workflow_password
```

### Render profile (`application-render.properties`)

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4
spring.ai.openai.embedding.options.model=text-embedding-3-small
spring.ai.openai.embedding.options.dimensions=1024
spring.datasource.url=jdbc:postgresql://${DATABASE_HOST}:${DATABASE_PORT:5432}/${DATABASE_NAME}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
server.port=${PORT:8080}
```

### Docker Compose (local)

```bash
docker-compose up
# starts postgres (5432), backend (8080) — frontend runs separately from ai-workflow-designer-ui
```

### Security

- `SecurityConfiguration` permits `/api/auth/**`, `/actuator/health`, Swagger, and OPTIONS.
- All other `/api/**` require a valid JWT.
- CORS origins come from `CORS_ALLOWED_ORIGINS` (defaults: `http://localhost:3000`, `http://localhost:5173`).
- Admin user seeded on startup from `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`.

---

## 8. Agent Layer Details

| Agent | Responsibility | When It Runs |
|-------|----------------|--------------|
| **Orchestrator** | Routes request + coordinates hand-off | Every chat request |
| **Consultant** | Conversational intake, spec building, confirmation | New/vague requests |
| **Designer** | Creates/modifies `WorkflowDefinition` via tools | After spec confirmed or on direct modifications |
| **Validator** | Structural + logical validation | On `/api/workflows/{id}/validate` |
| **Optimizer** | Suggests improvements | On explicit optimization requests |
| **Runtime** | Explains running process instances | On runtime/debugging requests |

### Tool calling safety

`WorkflowToolCallbacks` protects against weak/malformed local models:

- Sanitises stray backticks/quotes from parameters.
- Validates required parameters and returns error text to the LLM.
- Duplicate-call circuit breaker: max 2 identical calls.
- Total-call circuit breaker: max 40 calls per request.
- Max budget exhaustion message tells the model to produce a final answer.

### Memory / RAG

- `JpaChatMemory` persists conversation messages but ignores the `default` conversation ID used for internal orchestration calls.
- `BaseAgent.withRag()` appends retrieved knowledge to each system prompt.
- `QuestionAnswerAdvisor` was removed because StringTemplate braces in user/prompt text broke parsing; RAG is injected manually.

---

## 9. Frontend Architecture

### Routing

- `/login` → `LoginPage`
- `/` → `DesignerHome` (workflow list + chat + diagram)
- `/knowledge` → `KnowledgeBase`

### State

- `workflows`, `selectedWorkflow`, `chatMessages`, `conversationId`, `viewMode` held in React state.
- Authentication persisted in `localStorage`.

### Diagram views

| View | Component | Source |
|------|-----------|--------|
| **Simple** | `SimpleFlowViewer` (Mermaid) | `workflow.definition` |
| **Steps** | `ProcessStepsView` (numbered list) | `workflow.definition` |
| **Advanced** | `BpmnViewer` (bpmn-js) | `workflow.bpmnXml` |

### API service (`ai-workflow-designer-ui/src/services/api.ts`)

- `authApi` — login / register / me
- `workflowApi` — CRUD, validate, generate BPMN, import BPMN
- `agentApi` — chat
- `simulationApi`, `deploymentApi`, `conversationApi`

---

## 10. Known Gaps & Next Priorities

1. **Refresh/session restoration** — `conversationId` and `selectedWorkflow` are only in React state, lost on reload. Planned: server endpoint to look up an open `WorkflowSpec` / conversation by workflow.
2. **Simulation / Deployment** — scaffolding exists under `runtime/` but these are explicitly deferred after generation + visualisation are stable.
3. **LLM quality gate** — local models sometimes emit malformed tool calls; the circuit breakers help, but a native tool-calling model (or OpenAI) gives cleaner results.
4. **Re-layout existing workflows** — BPMN XML stores positions at generation time; old workflows keep their old layout unless BPMN is regenerated or they are rebuilt.

---

## 11. Verification Commands

```bash
# Backend
mvn -q test

# Frontend (in the ai-workflow-designer-ui repo)
npm run build

# Full local backend stack
docker-compose up
```

---

## 12. Quick Reference: File Map

| Concern | File |
|---------|------|
| Entry point | `AiWorkflowDesignerApplication.java` |
| Auth | `security/SecurityConfiguration.java`, `security/JwtService.java` |
| Chat entry | `api/controller/AgentController.java` |
| Routing agents | `agent/impl/OrchestratorAgent.java` |
| Intake | `agent/impl/ConsultantAgent.java`, `requirements/RequirementSpecService.java` |
| Build | `agent/impl/DesignerAgent.java`, `tools/WorkflowToolCallbacks.java` |
| BPMN | `bpmn/BpmnGenerator.java` |
| Persistence | `persistence/entity/Workflow.java`, `workflow/service/WorkflowService.java` |
| Frontend entry | `ai-workflow-designer-ui/src/App.tsx` |
| API client | `ai-workflow-designer-ui/src/services/api.ts` |
| Simple view | `ai-workflow-designer-ui/src/components/SimpleFlowViewer.tsx` |
| Steps view | `ai-workflow-designer-ui/src/components/ProcessStepsView.tsx` |
| BPMN view | `ai-workflow-designer-ui/src/components/BpmnViewer.tsx` |
