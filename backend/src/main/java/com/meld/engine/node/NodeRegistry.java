package com.meld.engine.node;

import com.meld.engine.domain.WorkflowNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of available node types.
 * Allows the engine to look up node implementations by type name.
 */
public class NodeRegistry {

    private final Map<String, WorkflowNode> nodes = new HashMap<>();

    public NodeRegistry() {
        // Register built-in node types
        register(new AgentNode());
        register(new ToolNode());
        register(new OutputNode());
    }

    public void register(WorkflowNode node) {
        nodes.put(node.getNodeType(), node);
    }

    public WorkflowNode get(String nodeType) {
        WorkflowNode node = nodes.get(nodeType);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node type: " + nodeType);
        }
        return node;
    }

    public boolean has(String nodeType) {
        return nodes.containsKey(nodeType);
    }
}