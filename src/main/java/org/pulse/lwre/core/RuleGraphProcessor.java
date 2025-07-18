package org.pulse.lwre.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * The {@code RuleGraphProcessor} class is responsible for processing and organizing rules in the Lightweight Rule Engine (LWRE)
 * into directed graphs based on their groups. It constructs dependency graphs for rules by analyzing their variable usage and
 * control flow (success/failure transitions). The class also provides methods to identify root nodes and print graph structures
 * for debugging purposes. It ensures proper handling of rule dependencies and detects cycles in the graph to prevent execution issues.
 *
 * @author Hamdi Ghassen
 */
public class RuleGraphProcessor {
    /**
     * Processes a list of rules and constructs directed graphs for each rule group based on dependencies.
     *
     * @param rules the list of rules to process
     * @return a map of group names to their corresponding directed graphs
     */
    public static Map<String, DirectedGraph<Rule>> processRules(List<Rule> rules) {
        Map<String, DirectedGraph<Rule>> groupGraphs = new HashMap<>();
        Map<String, List<Rule>> rulesByGroup = new HashMap<>();
        for (Rule rule : rules) {
            String group = rule.getGroup();
            rulesByGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(rule);
        }
        for (Map.Entry<String, List<Rule>> entry : rulesByGroup.entrySet()) {
            String group = entry.getKey();
            List<Rule> groupRules = entry.getValue();
            DirectedGraph<Rule> graph = new DirectedGraph<>();
            for (Rule rule : groupRules) {
                graph.addVertex(rule);
            }
            Map<String, Rule> ruleMap = new HashMap<>();
            for (Rule rule : groupRules) {
                ruleMap.put(rule.getName(), rule);
            }
            Map<String, List<Rule>> globalProducers = new HashMap<>();
            for (Rule rule : groupRules) {
                for (String varName : rule.getProduces().keySet()) {
                    globalProducers.computeIfAbsent(varName, k -> new ArrayList<>()).add(rule);
                }
            }

            for (Rule rule : groupRules) {
                for (Rule.UseVariable use : rule.getUses().values()) {
                    if ("RULE".equals(use.getSource())) {
                        String sourceRuleName = use.getSourceId();
                        Rule parentRule = ruleMap.get(sourceRuleName);
                        if (parentRule != null) {
                            graph.addEdge(parentRule, rule);
                        }
                    } else if ("Global".equals(use.getSource())) {
                        String globalVar = use.getVariableName();
                        List<Rule> producers = globalProducers.get(globalVar);
                        if (producers != null) {
                            for (Rule producer : producers) {
                                // Avoid self-edges
                                if (!producer.equals(rule)) {
                                    graph.addEdge(producer, rule);
                                }
                            }
                        }
                    }
                }

                if (rule.getNextRuleOnSuccess() != null) {
                    Rule nextRule = ruleMap.get(rule.getNextRuleOnSuccess());
                    if (nextRule != null) {
                        graph.addEdge(rule, nextRule);
                    }
                }

                if (rule.getNextRuleOnFailure() != null) {
                    Rule nextRule = ruleMap.get(rule.getNextRuleOnFailure());
                    if (nextRule != null) {
                        graph.addEdge(rule, nextRule);
                    }
                }
            }

            groupGraphs.put(group, graph);
        }

        for (Map.Entry<String, DirectedGraph<Rule>> entry : groupGraphs.entrySet()) {
            try {
                entry.getValue().topologicalSort();
            } catch (IllegalStateException e) {
                System.out.println("Warning: Cycle detected in rule dependencies for group: " + entry.getKey());
            }
        }

        return groupGraphs;
    }
    /**
     * Identifies the root nodes of a rule group, defined as rules with the lowest priority and no rule dependencies.
     *
     * @param graph the directed graph of rules
     * @param groupRules the list of rules in the group
     * @return a list of root nodes
     */
    public static List<Rule> getRootNodes(DirectedGraph<Rule> graph, List<Rule> groupRules) {
        List<Rule> rootNodes = new ArrayList<>();

        int minPriority = Integer.MAX_VALUE;
        for (Rule rule : groupRules) {
            minPriority = Math.min(minPriority, rule.getPriority());
        }

        for (Rule rule : groupRules) {
            if (rule.getPriority() == minPriority) {
                boolean hasNoRuleDependencies = true;
                for (Rule.UseVariable use : rule.getUses().values()) {
                    if ("RULE".equals(use.getSource())) {
                        hasNoRuleDependencies = false;
                        break;
                    }
                }
                if (hasNoRuleDependencies) {
                    rootNodes.add(rule);
                }
            }
        }

        return rootNodes;
    }
    /**
     * Prints the structure of the rule graph for a specific group, including root nodes and dependencies.
     *
     * @param groupName the name of the rule group
     * @param graph the directed graph of rules
     */
    public static void printGraphStructure(String groupName, DirectedGraph<Rule> graph) {
        System.out.println("\n=== Group: " + groupName + " ===");

        List<String> rootNames = graph.getRootNodeNames();
        System.out.println("Root nodes: " + rootNames);

        for (Rule rule : graph.getVertices()) {
            List<String> children = graph.getChildRuleNames(rule.getName());
            StringBuilder dependencies = new StringBuilder();
            for (Rule.UseVariable use : rule.getUses().values()) {
                if ("RULE".equals(use.getSource())) {
                    dependencies.append(" depends on ").append(use.getSourceId());
                }
            }
            String depString = rule.getName() + " (priority: " + rule.getPriority() + ")" + dependencies + " -> " + children;
            System.out.println(depString);
        }
    }
}