-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Workflow table
CREATE TABLE IF NOT EXISTS workflow (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    definition_json JSONB NOT NULL,
    bpmn_xml TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL
);

-- Workflow version table
CREATE TABLE IF NOT EXISTS workflow_version (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflow(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    definition_json JSONB NOT NULL,
    bpmn_xml TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    change_description TEXT,
    UNIQUE(workflow_id, version)
);

-- Application user table
CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'DESIGNER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Conversation table
CREATE TABLE IF NOT EXISTS conversation (
    id UUID PRIMARY KEY,
    conversation_id UUID,
    workflow_id UUID REFERENCES workflow(id) ON DELETE CASCADE,
    user_id UUID,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Migration for existing databases: add new conversation columns
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS conversation_id UUID;
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS role VARCHAR(50);
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS metadata JSONB;
UPDATE conversation SET role = 'USER' WHERE role IS NULL;
UPDATE conversation SET content = '' WHERE content IS NULL;
ALTER TABLE conversation ALTER COLUMN role SET NOT NULL;
ALTER TABLE conversation ALTER COLUMN content SET NOT NULL;

-- Relax legacy columns from older local schema versions (kept for compatibility)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'conversation' AND column_name = 'user_message') THEN
        ALTER TABLE conversation ALTER COLUMN user_message DROP NOT NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'conversation' AND column_name = 'agent_response') THEN
        ALTER TABLE conversation ALTER COLUMN agent_response DROP NOT NULL;
    END IF;
END $$;

-- Workflow spec table (requirements gathered by the consultant agent)
CREATE TABLE IF NOT EXISTS workflow_spec (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL UNIQUE,
    user_id UUID,
    workflow_id UUID,
    process_name VARCHAR(255),
    description TEXT,
    steps JSONB,
    actors JSONB,
    decisions JSONB,
    conditions JSONB,
    missing_info JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'COLLECTING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Document source table (uploaded PDF/text documents)
CREATE TABLE IF NOT EXISTS document_source (
    id UUID PRIMARY KEY,
    filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    purpose VARCHAR(50) NOT NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    uploaded_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Agent execution table
CREATE TABLE IF NOT EXISTS agent_execution (
    id UUID PRIMARY KEY,
    workflow_id UUID REFERENCES workflow(id) ON DELETE CASCADE,
    agent_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_step VARCHAR(255),
    context JSONB,
    tool_results JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

-- Validation result table
CREATE TABLE IF NOT EXISTS validation_result (
    id UUID PRIMARY KEY,
    workflow_id UUID REFERENCES workflow(id) ON DELETE CASCADE,
    is_valid BOOLEAN NOT NULL,
    errors JSONB,
    warnings JSONB,
    validated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Simulation result table
CREATE TABLE IF NOT EXISTS simulation_result (
    id UUID PRIMARY KEY,
    workflow_id UUID REFERENCES workflow(id) ON DELETE CASCADE,
    input_variables JSONB,
    output_variables JSONB,
    execution_path JSONB,
    execution_details JSONB,
    gateway_decisions JSONB,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    start_time BIGINT,
    end_time BIGINT,
    duration BIGINT,
    simulated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Deployment table
CREATE TABLE IF NOT EXISTS deployment (
    id UUID PRIMARY KEY,
    workflow_id UUID REFERENCES workflow(id) ON DELETE CASCADE,
    workflow_version INTEGER NOT NULL,
    deployment_id VARCHAR(255),
    process_definition_key VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    deployed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deployed_by VARCHAR(255) NOT NULL,
    environment VARCHAR(50) NOT NULL
);

-- Process instance table
CREATE TABLE IF NOT EXISTS process_instance (
    id UUID PRIMARY KEY,
    workflow_id UUID REFERENCES workflow(id) ON DELETE CASCADE,
    deployment_id UUID REFERENCES deployment(id) ON DELETE CASCADE,
    process_instance_id VARCHAR(255),
    process_definition_key VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    current_activity_id VARCHAR(255),
    variables JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

-- BPMN knowledge table for RAG with pgvector (1024 dimensions for local LLM Studio)
CREATE TABLE IF NOT EXISTS bpmn_knowledge (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1024),
    knowledge_type VARCHAR(100) NOT NULL,
    source VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Widen an existing embedding column if it was created with a different dimension
ALTER TABLE bpmn_knowledge ALTER COLUMN embedding TYPE vector(1024) USING embedding::vector(1024);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_workflow_status ON workflow(status);
CREATE INDEX IF NOT EXISTS idx_workflow_created_by ON workflow(created_by);
CREATE INDEX IF NOT EXISTS idx_workflow_version_workflow_id ON workflow_version(workflow_id);
CREATE INDEX IF NOT EXISTS idx_conversation_workflow_id ON conversation(workflow_id);
CREATE INDEX IF NOT EXISTS idx_conversation_conversation_id ON conversation(conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversation_user_id ON conversation(user_id);
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_document_source_purpose ON document_source(purpose);
CREATE INDEX IF NOT EXISTS idx_document_source_uploaded_by ON document_source(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_workflow_spec_conversation_id ON workflow_spec(conversation_id);
CREATE INDEX IF NOT EXISTS idx_workflow_spec_user_id ON workflow_spec(user_id);
CREATE INDEX IF NOT EXISTS idx_agent_execution_workflow_id ON agent_execution(workflow_id);
CREATE INDEX IF NOT EXISTS idx_validation_result_workflow_id ON validation_result(workflow_id);
CREATE INDEX IF NOT EXISTS idx_simulation_result_workflow_id ON simulation_result(workflow_id);
CREATE INDEX IF NOT EXISTS idx_deployment_workflow_id ON deployment(workflow_id);
CREATE INDEX IF NOT EXISTS idx_process_instance_workflow_id ON process_instance(workflow_id);
CREATE INDEX IF NOT EXISTS idx_process_instance_deployment_id ON process_instance(deployment_id);

-- Create vector index for similarity search (1024 dimensions)
CREATE INDEX IF NOT EXISTS idx_bpmn_knowledge_embedding ON bpmn_knowledge USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Create index on knowledge_type for filtering
CREATE INDEX IF NOT EXISTS idx_bpmn_knowledge_type ON bpmn_knowledge(knowledge_type);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers to automatically update updated_at
CREATE TRIGGER update_workflow_updated_at BEFORE UPDATE ON workflow
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bpmn_knowledge_updated_at BEFORE UPDATE ON bpmn_knowledge
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Telemetry: correlation id + agent routing path on agent executions
ALTER TABLE agent_execution ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);
ALTER TABLE agent_execution ADD COLUMN IF NOT EXISTS agent_path VARCHAR(500);

-- LLM token usage per user / workflow / conversation
CREATE TABLE IF NOT EXISTS llm_usage (
    id UUID PRIMARY KEY,
    user_id UUID,
    username VARCHAR(255),
    workflow_id UUID,
    conversation_id UUID,
    correlation_id VARCHAR(64),
    agent_type VARCHAR(100),
    model VARCHAR(255),
    prompt_tokens BIGINT,
    completion_tokens BIGINT,
    total_tokens BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_llm_usage_user_id ON llm_usage(user_id);
CREATE INDEX IF NOT EXISTS idx_llm_usage_username ON llm_usage(username);
CREATE INDEX IF NOT EXISTS idx_llm_usage_workflow_id ON llm_usage(workflow_id);
CREATE INDEX IF NOT EXISTS idx_agent_execution_correlation_id ON agent_execution(correlation_id);

-- Link deployments to the explicit workflow version they were built from
ALTER TABLE deployment ADD COLUMN IF NOT EXISTS workflow_version INTEGER;
