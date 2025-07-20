package org.pulse.lwre.core;

import java.util.*;
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
 * The {@code DirectedGraph} class represents a directed graph data structure that supports generic vertices.
 * It is used to model rule dependencies in the Lightweight Rule Engine (LWRE), with vertices typically
 * representing rules and edges indicating dependencies. The class provides methods for adding vertices and
 * edges, retrieving neighbors, performing topological sorting, and querying rule relationships such as root
 * nodes, child nodes, parent nodes, and paths between rules. The implementation is thread-safe for read
 * operations and supports cycle detection during topological sorting.
 *
 * @author Hamdi Ghassen
 */
public class DirectedGraph<T> {
    private final Map<T, List<T>> adjList;

    /**
     * Constructs a new empty directed graph with a hash map-based adjacency list.
     */
    public DirectedGraph() {
        adjList = new HashMap<>();
    }

    /**
     * Adds a vertex to the graph if it does not already exist.
     *
     * @param vertex the vertex to add
     */
    public void addVertex(T vertex) {
        adjList.putIfAbsent(vertex, new ArrayList<>());
    }

    /**
     * Adds a directed edge from a source vertex to a destination vertex, ensuring no duplicate edges.
     *
     * @param source      the source vertex
     * @param destination the destination vertex
     */
    public void addEdge(T source, T destination) {
        addVertex(source);
        addVertex(destination);
        List<T> neighbors = adjList.get(source);
        // Prevent adding duplicate edges
        if (!neighbors.contains(destination)) {
            neighbors.add(destination);
        }
    }

    /**
     * Retrieves the set of all vertices in the graph.
     *
     * @return a set of vertices
     */
    public Set<T> getVertices() {
        return adjList.keySet();
    }

    /**
     * Retrieves the list of neighbors (outgoing edges) for a given vertex.
     *
     * @param vertex the vertex whose neighbors are requested
     * @return a list of neighboring vertices
     */
    public List<T> getNeighbors(T vertex) {
        return adjList.getOrDefault(vertex, new ArrayList<>());
    }

    /**
     * Performs a topological sort of the graph, ordering vertices by priority for rules.
     * Throws an exception if a cycle is detected.
     *
     * @return a list of vertices in topological order
     * @throws IllegalStateException if the graph contains a cycle
     */
    public List<T> topologicalSort() throws IllegalStateException {

        Map<T, Integer> inDegree = new HashMap<>();
        for (T vertex : adjList.keySet()) {
            inDegree.put(vertex, 0);
        }

        for (T vertex : adjList.keySet()) {
            for (T neighbor : adjList.get(vertex)) {
                inDegree.put(neighbor, inDegree.getOrDefault(neighbor, 0) + 1);
            }
        }

        PriorityQueue<T> queue = new PriorityQueue<>((a, b) -> {
            if (a instanceof Rule && b instanceof Rule) {
                return Integer.compare(
                        ((Rule) a).getPriority(),
                        ((Rule) b).getPriority()
                );
            }
            return 0;
        });

        for (T vertex : adjList.keySet()) {
            if (inDegree.get(vertex) == 0) {
                queue.add(vertex);
            }
        }

        List<T> sorted = new ArrayList<>();
        int count = 0;
        while (!queue.isEmpty()) {
            T vertex = queue.poll();
            sorted.add(vertex);
            count++;

            for (T neighbor : adjList.getOrDefault(vertex, new ArrayList<>())) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (count != adjList.size()) {
            throw new IllegalStateException("Graph contains a cycle");
        }
        return sorted;
    }

    /**
     * Retrieves the names of root nodes (rules with no rule dependencies and lowest priority).
     *
     * @return a list of root node names
     */
    public List<String> getRootNodeNames() {
        List<String> rootNames = new ArrayList<>();

        if (adjList.isEmpty()) {
            return rootNames;
        }

        int minPriority = Integer.MAX_VALUE;
        for (T vertex : adjList.keySet()) {
            if (vertex instanceof Rule) {
                Rule rule = (Rule) vertex;
                minPriority = Math.min(minPriority, rule.getPriority());
            }
        }

        for (T vertex : adjList.keySet()) {
            if (vertex instanceof Rule) {
                Rule rule = (Rule) vertex;
                if (rule.getPriority() == minPriority) {
                    // Check if this rule has no rule dependencies (only Global or no dependencies)
                    boolean hasNoRuleDependencies = true;
                    for (Rule.UseVariable use : rule.getUses().values()) {
                        if ("RULE".equals(use.getSource())) {
                            hasNoRuleDependencies = false;
                            break;
                        }
                    }
                    if (hasNoRuleDependencies) {
                        rootNames.add(rule.getName());
                    }
                }
            }
        }

        return rootNames;
    }

    /**
     * Retrieves the names of child rules (direct dependents) for a given rule.
     *
     * @param ruleName the name of the rule
     * @return a list of child rule names
     */
    public List<String> getChildRuleNames(String ruleName) {
        Set<String> childNames = new LinkedHashSet<>();

        T targetVertex = null;
        for (T vertex : adjList.keySet()) {
            if (vertex instanceof Rule && ((Rule) vertex).getName().equals(ruleName)) {
                targetVertex = vertex;
                break;
            }
        }

        if (targetVertex != null) {
            for (T neighbor : adjList.getOrDefault(targetVertex, new ArrayList<>())) {
                if (neighbor instanceof Rule) {
                    childNames.add(((Rule) neighbor).getName());
                }
            }
        }

        return new ArrayList<>(childNames);
    }

    /**
     * Retrieves the names of parent rules (rules that depend on the given rule).
     *
     * @param ruleName the name of the rule
     * @return a list of parent rule names
     */
    public List<String> getParentRuleNames(String ruleName) {
        List<String> parentNames = new ArrayList<>();

        T targetVertex = null;
        for (T vertex : adjList.keySet()) {
            if (vertex instanceof Rule && ((Rule) vertex).getName().equals(ruleName)) {
                targetVertex = vertex;
                break;
            }
        }

        if (targetVertex != null) {
            for (Map.Entry<T, List<T>> entry : adjList.entrySet()) {
                if (entry.getValue().contains(targetVertex) && entry.getKey() instanceof Rule) {
                    parentNames.add(((Rule) entry.getKey()).getName());
                }
            }
        }

        return parentNames;
    }

    /**
     * Checks if there is a path from a source rule to a destination rule using depth-first search.
     *
     * @param sourceRuleName the name of the source rule
     * @param destRuleName   the name of the destination rule
     * @return true if a path exists, false otherwise
     */
    public boolean hasPath(String sourceRuleName, String destRuleName) {
        T sourceVertex = null;
        T destVertex = null;

        for (T vertex : adjList.keySet()) {
            if (vertex instanceof Rule) {
                Rule rule = (Rule) vertex;
                if (rule.getName().equals(sourceRuleName)) {
                    sourceVertex = vertex;
                }
                if (rule.getName().equals(destRuleName)) {
                    destVertex = vertex;
                }
            }
        }

        if (sourceVertex == null || destVertex == null) {
            return false;
        }

        return hasPathDFS(sourceVertex, destVertex, new HashSet<>());
    }

    /**
     * Performs a depth-first search to check for a path between two vertices.
     *
     * @param current the current vertex
     * @param target  the target vertex
     * @param visited the set of visited vertices
     * @return true if a path exists, false otherwise
     */
    private boolean hasPathDFS(T current, T target, Set<T> visited) {
        if (current.equals(target)) {
            return true;
        }

        visited.add(current);

        for (T neighbor : adjList.getOrDefault(current, new ArrayList<>())) {
            if (!visited.contains(neighbor)) {
                if (hasPathDFS(neighbor, target, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Prints the graph structure, showing each rule, its priority, and its neighbors.
     */
    public void printGraph() {
        System.out.println("Graph structure:");
        for (Map.Entry<T, List<T>> entry : adjList.entrySet()) {
            T vertex = entry.getKey();
            List<T> neighbors = entry.getValue();

            if (vertex instanceof Rule) {
                Rule rule = (Rule) vertex;
                System.out.print(rule.getName() + " (priority: " + rule.getPriority() + ") -> [");
                for (int i = 0; i < neighbors.size(); i++) {
                    if (neighbors.get(i) instanceof Rule) {
                        System.out.print(((Rule) neighbors.get(i)).getName());
                        if (i < neighbors.size() - 1) {
                            System.out.print(", ");
                        }
                    }
                }
                System.out.println("]");
            }
        }
    }
}