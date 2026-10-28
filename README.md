# AI Workflow Designer Agent

An AI-powered BPMN workflow designer platform that transforms natural language business process descriptions into executable BPMN workflows with validation, simulation, and deployment capabilities.

## Features

- **Natural Language Workflow Design**: Describe business processes in plain English and let AI create BPMN workflows
- **Multi-Agent Architecture**: Specialized agents for design, validation, optimization, and runtime analysis
- **BPMN 2.0 Support**: Full BPMN 2.0 generation, parsing, and validation
- **Workflow Simulation**: Test workflows with what-if analysis and execution path simulation
- **RAG Knowledge Base**: Built-in BPMN patterns, best practices, and specifications
- **Flowable Integration**: Deploy workflows to Flowable BPMN engine
- **Interactive UI**: React frontend with bpmn-js visualization
- **Dual LLM Support**: OpenAI for production, local LLM Studio for development

## Architecture

### Backend (Spring Boot + Java 21)
- **Agent Layer**: Orchestrator, Designer, Validator, Optimizer, Runtime agents
- **Workflow Model**: Engine-independent workflow domain model
- **BPMN Processing**: Generator, parser, and validator
- **Simulation Engine**: Workflow execution and what-if analysis
- **RAG Knowledge**: pgvector-based knowledge base
- **Runtime Integration**: Flowable BPMN engine
- **REST API**: Comprehensive API for all operations

### Frontend (React + TypeScript)
Lives in a separate repo: `ai-workflow-designer-ui`
- **BPMN Visualization**: bpmn-js for interactive diagrams
- **Chat Interface**: Natural language interaction with AI agents
- **Workflow Management**: Create, edit, and manage workflows
- **Validation & Simulation**: Real-time workflow testing

## Technology Stack

- **Backend**: Java 21, Spring Boot 3.x, Spring AI
- **AI**: OpenAI GPT-4 (production), Local LLM Studio (development)
- **Database**: PostgreSQL with pgvector extension
- **BPMN Engine**: Flowable 7.0
- **Frontend**: React 18, TypeScript, bpmn-js
- **Containerization**: Docker, Docker Compose
- **Deployment**: Render (production), Docker Compose (local)

## Getting Started

### Prerequisites
- Java 21
- Maven 3.9+
- Node.js 20+
- Docker and Docker Compose
- PostgreSQL with pgvector (or use Docker Compose)

### Local Development

1. **Clone the repository**
```bash
git clone <repository-url>
cd ai-workflow-designer-agent
```

2. **Set up environment variables**
```bash
# For local development with LLM Studio
export LLM_STUDIO_API_KEY=sk-local
export LLM_STUDIO_BASE_URL=http://localhost:11434/v1
export LLM_STUDIO_MODEL=llama2
```

3. **Start PostgreSQL with pgvector**
```bash
docker-compose up postgres
```

4. **Run the backend**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

5. **Run the frontend** (separate repo)
```bash
# In a separate terminal, from this repo's parent directory:
cd ../ai-workflow-designer-ui
npm install
npm run dev
# Vite dev server on :3000 proxies /api to the backend on :8080
```

The application will be available at:
- Backend: http://localhost:8080
- Frontend: http://localhost:3000

### Docker Compose (Backend Services)

```bash
docker-compose up
```

This will start:
- PostgreSQL with pgvector
- Spring Boot backend

The frontend is not part of this compose stack — run `npm run dev` in the
separate `ai-workflow-designer-ui` repo.

## Configuration

### LLM Profiles

The application supports two LLM profiles:

**Local Profile (development)**:
- Uses local LLM Studio
- Configured in `application-local.properties`
- Environment variables: `LLM_STUDIO_API_KEY`, `LLM_STUDIO_BASE_URL`, `LLM_STUDIO_MODEL`

**Render Profile (production)**:
- Uses OpenAI
- Configured in `application-render.properties`
- Environment variable: `OPENAI_API_KEY`

### Database Configuration

**Local Development**:
```
spring.datasource.url=jdbc:postgresql://localhost:5432/workflow_designer
spring.datasource.username=workflow_user
spring.datasource.password=workflow_password
```

**Production (Render)**:
- `render.yaml` wires `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME`,
  `DATABASE_USERNAME`, `DATABASE_PASSWORD` from the managed database via
  `fromDatabase`; `application-render.properties` assembles the JDBC URL

## API Endpoints

### Workflows
- `POST /api/workflows` - Create workflow
- `GET /api/workflows/{id}` - Get workflow
- `GET /api/workflows` - List workflows
- `PUT /api/workflows/{id}` - Update workflow
- `DELETE /api/workflows/{id}` - Delete workflow
- `POST /api/workflows/{id}/validate` - Validate workflow
- `POST /api/workflows/{id}/generate-bpmn` - Generate BPMN XML
- `GET /api/workflows/{id}/bpmn` - Get BPMN XML
- `POST /api/workflows/import-bpmn` - Import BPMN XML

### Agent
- `POST /api/agent/process` - Process request with specific agent
- `POST /api/agent/chat` - Chat with AI assistant
- `GET /api/agent/state/{agentType}` - Get agent state

### Simulation
- `POST /api/simulation/simulate` - Simulate workflow
- `POST /api/simulation/what-if` - What-if analysis

## Usage Examples

### Create a Workflow via Natural Language

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Create an employee onboarding workflow. HR creates the employee record, IT provisions the laptop, manager approves access, and the employee completes mandatory training."
  }'
```

### Validate a Workflow

```bash
curl -X POST http://localhost:8080/api/workflows/{id}/validate
```

### Simulate a Workflow

```bash
curl -X POST http://localhost:8080/api/simulation/simulate \
  -H "Content-Type: application/json" \
  -d '{
    "workflowId": "{id}",
    "inputVariables": {
      "employeeType": "CONTRACTOR",
      "department": "IT"
    }
  }'
```

## Deployment

### Render Deployment

1. **Push code to GitHub** (both this repo and `ai-workflow-designer-ui`)
2. **Create Render account**
3. **Deploy this repo's blueprint first** — `render.yaml` creates:
   - `workflow-backend` Docker web service (`SPRING_PROFILES_ACTIVE=render`)
   - `workflow-db` managed PostgreSQL (pgvector enabled by `schema.sql` on first boot)
4. **Set secrets prompted by the blueprint**:
   - `OPENAI_API_KEY`
   - `APP_ADMIN_PASSWORD` (min 12 chars — `JWT_SECRET` auto-generates)
5. **Deploy the frontend blueprint** in `ai-workflow-designer-ui` — a static
   site that calls `https://workflow-backend.onrender.com/api` (allowed via
   `CORS_ALLOWED_ORIGINS` in this repo's `render.yaml`)

Deploy the backend first: the frontend's `VITE_API_BASE_URL` and the backend's
`CORS_ALLOWED_ORIGINS` assume the `workflow-backend`/`workflow-frontend`
service names exist.

## Development

### Project Structure

```
ai-workflow-designer-agent/
├── agent/              # AI agent implementation
├── api/                # REST controllers and DTOs
├── bpmn/               # BPMN processing
├── config/             # Spring configuration
├── knowledge/          # RAG knowledge base
├── persistence/        # JPA entities and repositories
├── runtime/            # Flowable integration and simulation
├── tools/             # Agent tools
└── workflow/           # Domain model and services
```

### Adding New Agent Tools

1. Create a new class implementing `WorkflowTool`
2. Add it to the `ToolRegistry`
3. Use it in agent implementations

### Adding BPMN Patterns to Knowledge Base

Add new knowledge entries in `KnowledgeService.initializeDefaultKnowledge()` method.

## Testing

### Run Backend Tests
```bash
mvn test
```

### Run Frontend Tests
```bash
# In the ai-workflow-designer-ui repo
npm test
```

### Integration Tests
```bash
mvn verify -Pintegration-test
```

## Troubleshooting

### PostgreSQL Connection Issues
- Ensure PostgreSQL is running
- Check connection string in application properties
- Verify pgvector extension is installed

### LLM Connection Issues
- Verify API keys are set correctly
- Check LLM Studio is running (for local profile)
- Test OpenAI API connectivity (for render profile)

### BPMN Generation Issues
- Check workflow model is valid
- Verify all nodes have proper connections
- Review BPMN validator output

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License.

## Acknowledgments

- Spring AI team for the excellent AI framework
- Flowable project for the BPMN engine
- bpmn-js for the visualization library
- OpenAI for the GPT models

## Support

For issues and questions:
- GitHub Issues: [repository-url]/issues
- Documentation: [repository-url]/wiki
