package com.meld.workflow;

import com.meld.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sequential MVP graph rules per ROADMAP.md M6 and TEST.md TC-012/TC-013.
 *
 * <p>The MVP executes one node at a time, so the graph must be a single
 * unbranched chain: exactly one start node, at most one outgoing edge per node,
 * every node reachable, and no cycles. Conditional branching is a P1 feature.
 */
final class WorkflowGraphValidator {

    private WorkflowGraphValidator() {}

    static List<String> validate(List<WorkflowDtos.NodeInput> nodes, List<WorkflowDtos.EdgeInput> edges) {
        if (nodes == null || nodes.isEmpty()) {
            throw invalid("A workflow version needs at least one node");
        }

        Set<String> keys = new LinkedHashSet<>();
        for (WorkflowDtos.NodeInput node : nodes) {
            if (!keys.add(node.key())) {
                throw invalid("Duplicate node key: " + node.key());
            }
        }

        Map<String, String> next = new HashMap<>();
        Set<String> targets = new HashSet<>();
        Set<String> seenEdges = new HashSet<>();
        for (WorkflowDtos.EdgeInput edge : edges == null ? List.<WorkflowDtos.EdgeInput>of() : edges) {
            if (!keys.contains(edge.source()) || !keys.contains(edge.target())) {
                throw invalid("Edge references a missing node: " + edge.source() + " -> " + edge.target());
            }
            if (edge.source().equals(edge.target())) {
                throw invalid("A node cannot connect to itself: " + edge.source());
            }
            if (!seenEdges.add(edge.source() + "\u0000" + edge.target())) {
                throw invalid("Duplicate edge: " + edge.source() + " -> " + edge.target());
            }
            if (next.putIfAbsent(edge.source(), edge.target()) != null) {
                throw invalid("Sequential workflows allow one outgoing edge per node; "
                        + "node '" + edge.source() + "' has more than one");
            }
            targets.add(edge.target());
        }

        String start = null;
        for (String key : keys) {
            if (!targets.contains(key)) {
                if (start != null) {
                    throw invalid("A sequential workflow needs exactly one start node; found '"
                            + start + "' and '" + key + "'");
                }
                start = key;
            }
        }
        if (start == null) {
            throw invalid("The workflow has a cycle and no start node");
        }

        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String current = start;
        while (current != null) {
            if (!visited.add(current)) {
                throw invalid("The workflow contains a cycle at node: " + current);
            }
            order.add(current);
            current = next.get(current);
        }

        if (order.size() != keys.size()) {
            Set<String> unreachable = new LinkedHashSet<>(keys);
            unreachable.removeAll(visited);
            throw invalid("Every node must be reachable from the start node; unreachable: " + unreachable);
        }
        return order;
    }

    static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_INVALID", message);
    }
}
