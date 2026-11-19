package com.meld.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meld.engine.domain.ExecutionContext;
import com.meld.engine.domain.ExecutionStatus;
import com.meld.engine.domain.NodeResult;
import com.meld.engine.domain.NodeStatus;
import com.meld.engine.domain.WorkflowDefinition;
import com.meld.engine.node.AgentNode;
import com.meld.engine.node.NodeRegistry;
import com.meld.engine.node.OutputNode;
import com.meld.engine.node.ToolNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineTest {

    private WorkflowEngine engine;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        NodeRegistry registry = new NodeRegistry();
        engine = new WorkflowEngine(registry, mapper);
    }

    @Test
    void executeSimpleAgentWorkflow() {
        // Arrange: single agent node
        WorkflowDefinition workflow = createWorkflow(
                "agent-only",
                List.of(
                        nodeDef("agent1", "agent", "Test Agent",
                                agentConfig("agent-1", "Test Agent", "openrouter", "meta-llama/llama-3.1-8b-instruct:free",
                                        "You are a helpful assistant.", Map.of(), Map.of("result", "response")))
                ),
                List.of()
        );

        JsonNode input = mapper.createObjectNode().put("question", "What is 2+2?");

        // Act
        WorkflowEngine.ExecutionResult result = engine.execute(workflow, input);

        // Assert
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.nodeResults()).hasSize(1);
        assertThat(result.nodeResults().get("agent1").status()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(result.nodeResults().get("agent1").getOutput()).isPresent();
        assertThat(result.output()).isNotNull();
    }

    @Test
    void executeAgentThenToolWorkflow() {
        // Arrange: agent -> tool chain
        WorkflowDefinition workflow = createWorkflow(
                "agent-tool",
                List.of(
                        nodeDef("agent1", "agent", "Processor",
                                agentConfig("agent-1", "Processor", "openrouter", "meta-llama/llama-3.1-8b-instruct:free",
                                        "Process the input.", Map.of("input", "workflowInput.question"), Map.of("processed", "response"))),
                        nodeDef("tool1", "tool", "HTTP GET",
                                toolConfig("tool-1", "Fetcher", "http_get",
                                        Map.of("url", "https://api.example.com/data"),
                                        Map.of("query", "agent1.processed"), Map.of("data", "data")))
                ),
                List.of(edgeDef("e1", "agent1", "tool1"))
        );

        JsonNode input = mapper.createObjectNode().put("question", "fetch data");

        // Act
        WorkflowEngine.ExecutionResult result = engine.execute(workflow, input);

        // Assert
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.nodeResults()).hasSize(2);
        assertThat(result.nodeResults().get("agent1").status()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(result.nodeResults().get("tool1").status()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(result.output()).isNotNull();
    }

    @Test
    void executeAgentToolOutputWorkflow() {
        // Arrange: agent -> tool -> output
        WorkflowDefinition workflow = createWorkflow(
                "full-chain",
                List.of(
                        nodeDef("agent1", "agent", "Analyzer",
                                agentConfig("agent-1", "Analyzer", "openrouter", "meta-llama/llama-3.1-8b-instruct:free",
                                        "Analyze the input.", Map.of("text", "workflowInput.text"), Map.of("analysis", "response"))),
                        nodeDef("tool1", "tool", "Transform",
                                toolConfig("tool-1", "Transformer", "json_transform",
                                        Map.of("transformSpec", Map.of("uppercase", true)),
                                        Map.of("input", "agent1.analysis"), Map.of("transformed", "output"))),
                        nodeDef("output1", "output", "Result",
                                outputConfig(Map.of("result", "{{tool1.transformed.output}}")))
                ),
                List.of(
                        edgeDef("e1", "agent1", "tool1"),
                        edgeDef("e2", "tool1", "output1")
                )
        );

        JsonNode input = mapper.createObjectNode().put("text", "hello world");

        // Act
        WorkflowEngine.ExecutionResult result = engine.execute(workflow, input);

        // Assert
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.nodeResults()).hasSize(3);
        assertThat(result.nodeResults().get("agent1").status()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(result.nodeResults().get("tool1").status()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(result.nodeResults().get("output1").status()).isEqualTo(NodeStatus.COMPLETED);
    }

    @Test
    void failFastOnNodeFailure() {
        // Arrange: agent that will fail validation (missing required config)
        WorkflowDefinition workflow = createWorkflow(
                "fail-fast",
                List.of(
                        nodeDef("agent1", "agent", "Bad Agent",
                                mapper.createObjectNode() // missing required fields
                        ),
                        nodeDef("tool1", "tool", "Should Not Run",
                                toolConfig("tool-1", "Tool", "http_get", Map.of(), Map.of(), Map.of()))
                ),
                List.of(edgeDef("e1", "agent1", "tool1"))
        );

        // Act & Assert
        assertThatThrownBy(() -> engine.execute(workflow, mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AgentNode requires");
    }

    @Test
    void validateNodeConfigurationsBeforeExecution() {
        // Arrange: valid agent config but invalid tool config
        WorkflowDefinition workflow = createWorkflow(
                "bad-tool",
                List.of(
                        nodeDef("agent1", "agent", "Agent",
                                agentConfig("agent-1", "Agent", "openrouter", "meta-llama/llama-3.1-8b-instruct:free",
                                        "Prompt", Map.of(), Map.of())),
                        nodeDef("tool1", "tool", "Bad Tool",
                                mapper.createObjectNode()
                                        .put("toolId", "bad-tool")
                                        .put("toolType", "unknown_type")) // invalid type
                ),
                List.of(edgeDef("e1", "agent1", "tool1"))
        );

        // Act & Assert
        assertThatThrownBy(() -> engine.execute(workflow, mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported tool type");
    }

    @Test
    void workflowWithNoStartNodeThrows() {
        // Arrange: cycle or disconnected nodes - all nodes have incoming edges
        WorkflowDefinition workflow = createWorkflow(
                "no-start",
                List.of(
                        nodeDef("agent1", "agent", "Agent 1",
                                agentConfig("a1", "Agent 1", "openrouter", "model", "Prompt", Map.of(), Map.of())),
                        nodeDef("agent2", "agent", "Agent 2",
                                agentConfig("a2", "Agent 2", "openrouter", "model", "Prompt", Map.of(), Map.of()))
                ),
                List.of(
                        edgeDef("e1", "agent1", "agent2"),
                        edgeDef("e2", "agent2", "agent1") // cycle
                )
        );

        // Act & Assert
        assertThatThrownBy(() -> engine.execute(workflow, mapper.createObjectNode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no start node");
    }

    @Test
    void nodeOutputsAvailableToDownstreamNodes() {
        // Arrange: agent produces output, tool consumes it via inputMapping
        WorkflowDefinition workflow = createWorkflow(
                "data-flow",
                List.of(
                        nodeDef("agent1", "agent", "Producer",
                                agentConfig("a1", "Producer", "openrouter", "model", "Prompt",
                                        Map.of("value", "workflowInput.x"), Map.of("produced", "response"))),
                        nodeDef("tool1", "tool", "Consumer",
                                toolConfig("t1", "Consumer", "json_transform",
                                        Map.of(), Map.of("input", "agent1.produced"), Map.of("echoed", "output")))
                ),
                List.of(edgeDef("e1", "agent1", "tool1"))
        );

        JsonNode input = mapper.createObjectNode().put("x", "test-value");

        // Act
        WorkflowEngine.ExecutionResult result = engine.execute(workflow, input);

        // Assert
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        NodeResult agentResult = result.nodeResults().get("agent1");
        NodeResult toolResult = result.nodeResults().get("tool1");
        assertThat(agentResult.getOutput()).isPresent();
        assertThat(toolResult.getOutput()).isPresent();
    }

    // Helper methods for building test workflows
    private WorkflowDefinition createWorkflow(String workflowId, List<WorkflowDefinition.WorkflowNodeDef> nodes,
                                              List<WorkflowDefinition.WorkflowEdgeDef> edges) {
        return new WorkflowDefinition(
                UUID.randomUUID().toString(), // workflowId must be valid UUID for engine
                UUID.randomUUID().toString(),
                1,
                nodes,
                edges,
                mapper.createObjectNode(),
                mapper.createObjectNode()
        );
    }

    private WorkflowDefinition.WorkflowNodeDef nodeDef(String nodeKey, String nodeType, String label, JsonNode config) {
        return new WorkflowDefinition.WorkflowNodeDef(nodeKey, nodeType, label, 0, 0, config);
    }

    private WorkflowDefinition.WorkflowEdgeDef edgeDef(String edgeId, String source, String target) {
        return new WorkflowDefinition.WorkflowEdgeDef(edgeId, source, target, null);
    }

    private JsonNode agentConfig(String agentId, String agentName, String provider, String model,
                                  String systemPrompt, Map<String, String> inputMapping,
                                  Map<String, String> outputMapping) {
        ObjectNode config = mapper.createObjectNode();
        config.put("agentId", agentId);
        config.put("agentName", agentName);
        config.put("provider", provider);
        config.put("model", model);
        config.put("systemPrompt", systemPrompt);
        config.set("configuration", mapper.createObjectNode());
        config.set("inputMapping", mapper.valueToTree(inputMapping));
        config.set("outputMapping", mapper.valueToTree(outputMapping));
        return config;
    }

    private JsonNode toolConfig(String toolId, String toolName, String toolType,
                                 Map<String, Object> configuration,
                                 Map<String, String> inputMapping,
                                 Map<String, String> outputMapping) {
        ObjectNode config = mapper.createObjectNode();
        config.put("toolId", toolId);
        config.put("toolName", toolName);
        config.put("toolType", toolType);
        config.set("configuration", mapper.valueToTree(configuration));
        config.set("inputMapping", mapper.valueToTree(inputMapping));
        config.set("outputMapping", mapper.valueToTree(outputMapping));
        return config;
    }

    private JsonNode outputConfig(Map<String, String> inputMapping) {
        ObjectNode config = mapper.createObjectNode();
        config.set("outputTemplate", mapper.createObjectNode().put("result", "{{finalOutput}}"));
        config.set("inputMapping", mapper.valueToTree(inputMapping));
        return config;
    }
}