package org.pulse.lwre.core;

import org.pulse.lwre.dsl.DSLParser;
import org.pulse.lwre.metric.Meter;
import org.pulse.lwre.metric.MetricRegistry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
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
 * The {@code LWREngine} class is the core execution engine for the Lightweight Rule Engine (LWRE).
 * It manages the execution of compiled rules, organizes them by groups, and optimizes their execution
 * paths using precomputed dependency graphs. The engine supports thread-safe operations, context pooling,
 * circuit breaking, and metrics collection. It provides methods to add, update, and rollback rules, as well
 * as execute them in a controlled manner. The implementation is designed for high performance and scalability,
 * utilizing a {@code ForkJoinPool} for parallel execution and a {@code ConcurrentHashMap} for thread-safe data
 * structures.
 *
 * @author Hamdi Ghassen
 */
public class LWREngine implements Cloneable {
    private static final ThreadLocal<Queue<Map<String, Object>>> THREAD_LOCAL_CONTEXT_POOL =
            ThreadLocal.withInitial(() -> new ArrayDeque<>(500)); // Pre-size for better performance
    private static final ForkJoinPool EXECUTION_POOL = new ForkJoinPool(
            Runtime.getRuntime().availableProcessors(),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null, true);
    private final Map<String, CompiledRule[]> precomputedExecutionPaths = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> ruleNameToIndexPerGroup = new ConcurrentHashMap<>();
    private final Map<String, String[]> groupHelpers = new ConcurrentHashMap<>();
    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> ruleOutputs = new ConcurrentHashMap<>();
    private final List<CompiledRule> compiledRules = new ArrayList<>();
    private final RuleCompiler compiler = new RuleCompiler();
    private final Map<String, Rule> ruleVersions = new ConcurrentHashMap<>();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker(5, 30_000);
    private final MetricRegistry metrics = new MetricRegistry();
    private final Map<String, CompiledRule[]> compiledRulesByGroup = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CompiledRule>> ruleByNamePerGroup = new ConcurrentHashMap<>();
    private final Map<String, int[][]> dependencyMatrixPerGroup = new ConcurrentHashMap<>();
    private final Map<String, CompiledRule[]> rootRulesPerGroup = new ConcurrentHashMap<>();
    private volatile boolean traceEnabled = false;
    private volatile int maxExecutionSteps = 1000;
    private volatile boolean metric = false;
    private volatile Object localResult = null;

    /**
     * Private constructor to enforce use of Builder pattern.
     */
    private LWREngine() {
    }

    /**
     * Retrieves the thread-local context pool.
     *
     * @return the context pool
     */
    public Queue<Map<String, Object>> getContextPool() {
        return THREAD_LOCAL_CONTEXT_POOL.get();
    }

    /**
     * Enables or disables execution tracing.
     *
     * @param enable true to enable tracing, false otherwise
     */
    public void enableTrace(boolean enable) {
        this.traceEnabled = enable;
    }

    /**
     * Sets the maximum number of execution steps allowed.
     *
     * @param steps the maximum number of steps
     */
    public void setMaxExecutionSteps(int steps) {
        this.maxExecutionSteps = steps;
    }

    /**
     * Sets a global variable accessible to all rules.
     *
     * @param name  the variable name
     * @param value the variable value
     */
    public void setGlobalVariable(String name, Object value) {
        if (value != null) {
            globalVariables.put(name, value);
        }
    }

    /**
     * Retrieves a global variable by name.
     *
     * @param name the variable name
     * @return the variable value, or null if not found
     */
    public Object getGlobalVariable(String name) {
        return globalVariables.get(name);
    }

    /**
     * Adds a single rule to the engine and recompiles group structures.
     *
     * @param rule the rule to add
     * @throws Exception if rule compilation fails
     */
    public void addRule(Rule rule) throws Exception {
        long count = compiledRules.stream().filter(c -> c.getRule().getName().equals(rule.getName()) && c.getRule().getGroup().equals(rule.getGroup())).count();
        if (count != 0) {
            throw new RuleExecutionException("The rule "+rule.getName()+" for group "+rule.getGroup()+" already exist");
        }
        CompiledRule compiledRule = compiler.compileRule(rule,
                Arrays.asList(groupHelpers.getOrDefault(rule.getGroup(), new String[0])));
        compiledRules.add(compiledRule);
        ruleVersions.put(rule.getName() + "_" + rule.getVersion(), rule.clone());

        // Rebuild group structures
        rebuildGroupStructures();
    }

    /**
     * Adds multiple rules from a DSL content string and recompiles group structures.
     *
     * @param dslContent the DSL content containing rules and helpers
     * @throws Exception if rule parsing or compilation fails
     */
    public void addRules(String dslContent) throws Exception {
        DSLParser.ParseResult parseResult = DSLParser.parseRules(dslContent);

        // Convert helpers to arrays for better performance
        Map<String, String[]> groupHelperMap = new HashMap<>();
        for (Rule rule : parseResult.getRules()) {
            long count = compiledRules.stream().filter(c -> c.getRule().getName().equals(rule.getName()) && c.getRule().getGroup().equals(rule.getGroup())).count();
            if (count != 0) {
                throw new RuleExecutionException("The rule "+rule.getName()+" for group "+rule.getGroup()+" already exist");
            }
            List<String> helpers = parseResult.getHelpers();
            groupHelperMap.put(rule.getGroup(), helpers.toArray(new String[0]));
        }
        groupHelpers.putAll(groupHelperMap);

        // Compile all rules
        for (Rule rule : parseResult.getRules()) {
            String[] helpers = groupHelpers.getOrDefault(rule.getGroup(), new String[0]);
            CompiledRule compiledRule = compiler.compileRule(rule, Arrays.asList(helpers));
            compiledRules.add(compiledRule);
            ruleVersions.put(rule.getName() + "_" + rule.getVersion(), rule.clone());
        }

        rebuildGroupStructures();
    }

    /**
     * Rebuilds group structures and precomputes execution paths for all rule groups.
     */
    private void rebuildGroupStructures() {
        precomputedExecutionPaths.clear();
        ruleNameToIndexPerGroup.clear();
        compiledRulesByGroup.clear();
        ruleByNamePerGroup.clear();
        dependencyMatrixPerGroup.clear();
        rootRulesPerGroup.clear();

        // Group rules by their group names
        Map<String, List<CompiledRule>> tempGroupMap = new HashMap<>();
        for (CompiledRule cr : compiledRules) {
            tempGroupMap.computeIfAbsent(cr.getRule().getGroup(), k -> new ArrayList<>()).add(cr);
        }

        // Process each group
        for (Map.Entry<String, List<CompiledRule>> entry : tempGroupMap.entrySet()) {
            String group = entry.getKey();
            List<CompiledRule> groupRules = entry.getValue();

            // Convert to arrays for better performance
            CompiledRule[] rulesArray = groupRules.toArray(new CompiledRule[0]);
            compiledRulesByGroup.put(group, rulesArray);

            // Build name-to-rule mapping
            Map<String, CompiledRule> nameMap = new HashMap<>();
            Map<String, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < rulesArray.length; i++) {
                CompiledRule rule = rulesArray[i];
                nameMap.put(rule.getRule().getName(), rule);
                indexMap.put(rule.getRule().getName(), i);
            }
            ruleByNamePerGroup.put(group, nameMap);
            ruleNameToIndexPerGroup.put(group, indexMap);

            // Pre-compute execution path
            precomputeExecutionPath(group, rulesArray, nameMap);
        }
    }

    /**
     * Precomputes the optimal execution path for a rule group to avoid runtime graph traversal.
     *
     * @param group   the rule group
     * @param rules   the compiled rules in the group
     * @param nameMap mapping of rule names to compiled rules
     */
    private void precomputeExecutionPath(String group, CompiledRule[] rules, Map<String, CompiledRule> nameMap) {
        List<Rule> ruleList = Arrays.stream(rules).map(CompiledRule::getRule).collect(Collectors.toList());
        Map<String, DirectedGraph<Rule>> ruleGraphsByGroup = RuleGraphProcessor.processRules(ruleList);
        DirectedGraph<Rule> ruleGraph = ruleGraphsByGroup.get(group);

        if (ruleGraph == null) {
            precomputedExecutionPaths.put(group, new CompiledRule[0]);
            rootRulesPerGroup.put(group, new CompiledRule[0]);
            return;
        }

        int n = rules.length;
        int[][] depMatrix = new int[n][n];
        Map<String, Integer> nameToIndex = ruleNameToIndexPerGroup.get(group);

        for (Rule rule : ruleGraph.getVertices()) {
            Integer sourceIdx = nameToIndex.get(rule.getName());
            if (sourceIdx != null) {
                for (Rule neighbor : ruleGraph.getNeighbors(rule)) {
                    Integer targetIdx = nameToIndex.get(neighbor.getName());
                    if (targetIdx != null) {
                        depMatrix[sourceIdx][targetIdx] = 1;
                    }
                }
            }
        }
        dependencyMatrixPerGroup.put(group, depMatrix);
        List<CompiledRule> rootRules = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean hasIncoming = false;
            for (int j = 0; j < n; j++) {
                if (depMatrix[j][i] == 1) {
                    hasIncoming = true;
                    break;
                }
            }
            if (!hasIncoming) {
                rootRules.add(rules[i]);
            }
        }

        rootRules.sort(Comparator.comparingInt(cr -> cr.getRule().getPriority()));
        rootRulesPerGroup.put(group, rootRules.toArray(new CompiledRule[0]));
        CompiledRule[] executionPath = computeTopologicalOrder(rules, depMatrix, nameToIndex);
        precomputedExecutionPaths.put(group, executionPath);
    }

    /**
     * Computes a topological order of rules using Kahn's algorithm for efficient execution.
     *
     * @param rules       the compiled rules
     * @param depMatrix   the dependency matrix
     * @param nameToIndex mapping of rule names to indices
     * @return the ordered array of compiled rules
     */
    private CompiledRule[] computeTopologicalOrder(CompiledRule[] rules, int[][] depMatrix,
                                                   Map<String, Integer> nameToIndex) {
        int n = rules.length;
        int[] inDegree = new int[n];

        // Calculate in-degrees
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (depMatrix[i][j] == 1) {
                    inDegree[j]++;
                }
            }
        }

        Queue<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (inDegree[i] == 0) {
                queue.add(i);
            }
        }

        List<CompiledRule> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            int current = queue.poll();
            result.add(rules[current]);

            for (int i = 0; i < n; i++) {
                if (depMatrix[current][i] == 1) {
                    inDegree[i]--;
                    if (inDegree[i] == 0) {
                        queue.add(i);
                    }
                }
            }
        }

        return result.toArray(new CompiledRule[0]);
    }

    /**
     * Executes all rules in a specified group.
     *
     * @param group the rule group to execute
     * @return the final result of the execution
     * @throws RuleExecutionException if execution fails
     */
    public Object executeRules(String group) throws RuleExecutionException {
        if (precomputedExecutionPaths.isEmpty()) {
            throw new RuleExecutionException("Nothing to run");
        }
        return executeRules(group, "EXEC" + System.nanoTime());
    }

    /**
     * Executes all rules across all groups, using parallel execution if multiple groups exist.
     *
     * @return the final result of the execution
     * @throws RuleExecutionException if execution fails
     */
    public Object executeRules() throws RuleExecutionException {
        if (precomputedExecutionPaths.isEmpty()) {
            throw new RuleExecutionException("Nothing to run");
        }

        Set<String> groups = precomputedExecutionPaths.keySet();
        if (groups.size() == 1) {
            localResult = executeRules(groups.iterator().next(), "EXEC" + System.nanoTime());
        } else {
            // Use ForkJoinPool for better parallel performance
            CompletableFuture<Object>[] futures = groups.stream()
                    .map(group -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return executeRules(group, UUID.randomUUID().toString());
                        } catch (RuleExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }, EXECUTION_POOL))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();
            // Return the last completed result
            localResult = futures[futures.length - 1].join();
        }
        return localResult;
    }

    /**
     * Executes rules in a specific group with a given execution ID.
     *
     * @param group       the rule group
     * @param executionId the unique execution ID
     * @return the final result of the execution
     * @throws RuleExecutionException if execution fails
     */
    private Object executeRules(String group, String executionId) throws RuleExecutionException {
        CompiledRule[] executionPath = precomputedExecutionPaths.get(group);
        if (executionPath == null || executionPath.length == 0) {
            throw new RuleExecutionException("No rules found for group: " + group);
        }

        RuleExecutionContext executionContext = new RuleExecutionContext(executionId);
        Object finalResult = null;

        for (CompiledRule rule : executionPath) {
            RuleOutcome outcome = executeSingleRule(rule, executionContext);
            if (outcome.finalResult != null) {
                finalResult = outcome.finalResult;
            }
        }

        clearCache();
        return finalResult;
    }

    /**
     * Executes a single rule within the given execution context.
     *
     * @param compiledRule the compiled rule to execute
     * @param context      the execution context
     * @return the outcome of the rule execution
     */
    private RuleOutcome executeSingleRule(CompiledRule compiledRule,
                                          RuleExecutionContext context) {
        Rule rule = compiledRule.getRule();
        RuleExecutionContext.RuleExecutionState state = context.getState(rule.getName());
        Object finalResult = null;
        boolean success = false;

        if (state.getExecutionCount() >= rule.getMaxExecutions()) {
            if (traceEnabled) {
                System.out.println("Skipping rule " + rule.getName() + " (max executions reached)");
            }
            return new RuleOutcome(finalResult, success);
        }

        if (state.getRetryCount() > 0 && !state.shouldRetry(rule)) {
            if (traceEnabled) {
                System.out.println("Delaying retry for rule " + rule.getName());
            }
            return new RuleOutcome(finalResult, false);
        }

        try {
            state.setLastExecutionTime(System.currentTimeMillis());

            if (!circuitBreaker.allowExecution()) {
                throw new EngineOverloadException("Circuit breaker tripped");
            }

            // Optimized condition evaluation
            boolean conditionResult = evaluateCondition(compiledRule, context);

            if (conditionResult) {
                state.incrementExecutionCount();
                executeAction(compiledRule, context);

                if (compiledRule.getFinalEvaluator() != null) {
                    finalResult = executeFinalBlock(compiledRule, context);
                }

                state.resetRetryCount();
                success = true;

                if (traceEnabled) {
                    System.out.println("Rule executed successfully: " + rule.getName());
                }
            } else if (traceEnabled) {
                System.out.println("Rule condition false: " + rule.getName());
            }
        } catch (Exception e) {
            if (metric) {
                metrics.meter(rule.getName() + ".errors").mark();
            }

            state.setLastError(e);
            state.incrementRetryCount();
            success = false;

            if (traceEnabled) {
                if (state.getRetryCount() <= rule.getMaxRetries()) {
                    System.out.println("Rule execution failed, will retry: " + rule.getName());
                } else {
                    System.out.println("Rule failed permanently: " + rule.getName());
                }
                e.printStackTrace();
            }
        }

        return new RuleOutcome(finalResult, success);
    }

    /**
     * Evaluates the condition block of a compiled rule.
     *
     * @param compiledRule the compiled rule
     * @param context      the execution context
     * @return true if the condition evaluates to true, false otherwise
     * @throws Exception if evaluation fails
     */
    private boolean evaluateCondition(CompiledRule compiledRule,
                                      RuleExecutionContext context) throws Exception {
        if (compiledRule.getConditionEvaluator() == null) return true;

        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            return (Boolean) compiledRule.getConditionEvaluator().evaluate(new Object[]{evalContext, null});
        } finally {
            returnContext(evalContext);
        }
    }

    /**
     * Executes the action block of a compiled rule.
     *
     * @param compiledRule the compiled rule
     * @param context      the execution context
     * @throws Exception if execution fails
     */
    private void executeAction(CompiledRule compiledRule,
                               RuleExecutionContext context) throws Exception {
        if (compiledRule.getActionEvaluator() == null) return;

        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            compiledRule.getActionEvaluator().evaluate(new Object[]{evalContext, null});

            Rule rule = compiledRule.getRule();
            if (!rule.getProduces().isEmpty()) {
                Map<String, Object> outputs = new HashMap<>(rule.getProduces().size());
                for (String produceVar : rule.getProduces().keySet()) {
                    outputs.put(produceVar, evalContext.get(produceVar));
                }
                ruleOutputs.put(rule.getName(), outputs);
            }
        } finally {
            returnContext(evalContext);
        }
    }

    /**
     * Executes the final block of a compiled rule.
     *
     * @param compiledRule the compiled rule
     * @param context      the execution context
     * @return the result of the final block
     * @throws Exception if execution fails
     */
    private Object executeFinalBlock(CompiledRule compiledRule,
                                     RuleExecutionContext context) throws Exception {
        if (compiledRule.getFinalEvaluator() == null) return null;

        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            return compiledRule.getFinalEvaluator().evaluate(new Object[]{evalContext, null});
        } finally {
            returnContext(evalContext);
        }
    }

    /**
     * Populates the evaluation context with global variables, rule outputs, and used variables.
     *
     * @param context          the evaluation context
     * @param rule             the rule being executed
     * @param executionContext the rule execution context
     */
    private void populateContext(Map<String, Object> context, Rule rule,
                                 RuleExecutionContext executionContext) {
        context.clear();

        if (!executionContext.executionGlobals.isEmpty()) {
            context.putAll(executionContext.executionGlobals);
        }

        context.putAll(globalVariables);

        Map<String, Rule.UseVariable> uses = rule.getUses();
        if (!uses.isEmpty()) {
            for (Map.Entry<String, Rule.UseVariable> entry : uses.entrySet()) {
                String localName = entry.getKey();
                Rule.UseVariable useVar = entry.getValue();
                Object value = null;

                if ("Global".equals(useVar.getSource())) {
                    value = globalVariables.get(useVar.getVariableName());
                } else if ("RULE".equals(useVar.getSource())) {
                    Map<String, Object> ruleOutput = ruleOutputs.get(useVar.getSourceId());
                    if (ruleOutput != null) {
                        value = ruleOutput.get(useVar.getVariableName());
                    }
                }

                if (value != null) {
                    context.put(localName, value);
                }
            }
        }

        Map<String, Object> existingOutputs = ruleOutputs.get(rule.getName());
        if (existingOutputs != null) {
            context.putAll(existingOutputs);
        }
    }

    /**
     * Retrieves a context map from the thread-local pool or creates a new one.
     *
     * @return a context map
     */

    public Map<String, Object> getContext() {
        Queue<Map<String, Object>> pool = THREAD_LOCAL_CONTEXT_POOL.get();
        Map<String, Object> context = pool.poll();
        return (context != null) ? context : new HashMap<>(500);
    }

    /**
     * Returns a context map to the thread-local pool after clearing it.
     *
     * @param context the context map to return
     */
    private void returnContext(Map<String, Object> context) {
        Queue<Map<String, Object>> pool = THREAD_LOCAL_CONTEXT_POOL.get();
        if (pool.size() < 500) { // Limit pool size per thread
            context.clear();
            pool.offer(context);
        }
    }

    /**
     * Clears the rule output cache.
     */
    public void clearCache() {
        ruleOutputs.clear();
    }

    /**
     * Updates an existing rule with a new version and recompiles group structures.
     *
     * @param newRule the new rule version
     * @throws Exception if rule compilation fails
     */
    public void updateRule(Rule newRule) throws Exception {
        String versionKey = newRule.getName() + "_" + newRule.getVersion();
        if (ruleVersions.containsKey(versionKey)) return;

        String[] helpers = groupHelpers.getOrDefault(newRule.getGroup(), new String[0]);
        CompiledRule compiled = compiler.compileRule(newRule, Arrays.asList(helpers));

        compiledRules.removeIf(cr -> cr.getRule().getName().equals(newRule.getName()));
        compiledRules.add(compiled);

        rebuildGroupStructures();
        ruleVersions.put(versionKey, newRule);
    }

    /**
     * Rolls back to the latest version of a rule.
     *
     * @param ruleName the name of the rule to rollback
     * @return true if rollback was successful, false otherwise
     */
    public boolean rollbackRule(String ruleName) {
        return ruleVersions.entrySet().stream()
                .filter(e -> e.getKey().startsWith(ruleName + "_"))
                .max(Map.Entry.comparingByKey())
                .map(entry -> {
                    try {
                        updateRule(entry.getValue());
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .orElse(false);
    }

    /**
     * Retrieves a snapshot of the current metrics.
     *
     * @return a map of metric names to their values
     */
    public Map<String, Object> getMetricsSnapshot() {
        return metrics.getMetrics().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            if (e.getValue() instanceof org.pulse.lwre.metric.Timer) {
                                org.pulse.lwre.metric.Timer t = (org.pulse.lwre.metric.Timer) e.getValue();
                                Map<String, Object> metrics = new HashMap<>();
                                metrics.put("count", t.getCount());
                                metrics.put("mean", t.getMeanRate());
                                metrics.put("p99", t.get99thPercentile());
                                return Collections.unmodifiableMap(metrics);
                            } else if (e.getValue() instanceof Meter) {
                                Meter m = (Meter) e.getValue();
                                Map<String, Object> metrics = new HashMap<>();
                                metrics.put("count", m.getCount());
                                metrics.put("mean", m.getMeanRate());
                                return Collections.unmodifiableMap(metrics);
                            }
                            return Collections.emptyMap();
                        }
                ));
    }

    /**
     * Creates a deep copy of the engine.
     *
     * @return a cloned instance of the engine
     */
    @Override
    public LWREngine clone() {
        LWREngine cloned = new LWREngine();
        cloned.traceEnabled = this.traceEnabled;
        cloned.compiledRules.addAll(this.compiledRules);
        cloned.globalVariables.putAll(this.globalVariables);
        cloned.ruleVersions.putAll(this.ruleVersions);
        cloned.rebuildGroupStructures();
        return cloned;
    }

    /**
     * Builder class for constructing an {@code LWREngine} instance.
     */
    public static class Builder {
        private final LWREngine e = new LWREngine();

        /**
         * Enables or disables debug tracing.
         *
         * @param flag true to enable, false otherwise
         * @return this builder
         */
        public Builder debug(boolean flag) {
            e.traceEnabled = flag;
            return this;
        }

        /**
         * Enables or disables metrics collection.
         *
         * @param flag true to enable, false otherwise
         * @return this builder
         */
        public Builder metric(boolean flag) {
            e.metric = flag;
            return this;
        }

        /**
         * Sets the maximum number of execution steps.
         *
         * @param steps the maximum steps
         * @return this builder
         */
        public Builder maxSteps(int steps) {
            e.maxExecutionSteps = steps;
            return this;
        }

        /**
         * Adds rules from a DSL content string.
         *
         * @param dsl the DSL content
         * @return this builder
         * @throws Exception if rule parsing or compilation fails
         */
        public Builder rules(String dsl) throws Exception {
            e.addRules(dsl);
            return this;
        }

        /**
         * Sets a global variable.
         *
         * @param name  the variable name
         * @param value the variable value
         * @return this builder
         */
        public Builder global(String name, Object value) {
            e.setGlobalVariable(name, value);
            return this;
        }

        /**
         * Builds the {@code LWREngine} instance.
         *
         * @return the constructed engine
         */
        public LWREngine build() {
            return e;
        }
    }

    /**
     * Exception thrown when the engine is overloaded.
     */
    public static class EngineOverloadException extends RuleExecutionException {
        /**
         * Constructs a new {@code EngineOverloadException} with the specified message.
         *
         * @param message the error message
         */
        public EngineOverloadException(String message) {
            super(message);
        }
    }
}