package org.pulse.lwre.core;

import org.pulse.lwre.dsl.DSLParser;
import org.pulse.lwre.metric.Meter;
import org.pulse.lwre.metric.MetricRegistry;

import java.util.*;
import java.util.concurrent.*;
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
 * paths using precomputed dependency graphs. This implementation includes enhancements for handling
 * retries with delays and enforcing timeouts using a thread pool, ensuring thread-safe and efficient
 * rule execution.
 *
 * @author Hamdi Ghassen
 */
public class LWREngine implements Cloneable {
    // Thread-local pool for reusing context maps, reducing memory allocation overhead
    private static final ThreadLocal<Queue<Map<String, Object>>> THREAD_LOCAL_CONTEXT_POOL =
            ThreadLocal.withInitial(() -> new ArrayDeque<>(500)); // Pre-sized for performance

    // Thread pool for parallel rule execution with timeout enforcement
    private static final ForkJoinPool EXECUTION_POOL = new ForkJoinPool(
            Runtime.getRuntime().availableProcessors(),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null, true);

    // Thread-safe storage for precomputed execution paths, rule indices, helpers, and variables
    private final Map<String, CompiledRule[]> precomputedExecutionPaths = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> ruleNameToIndexPerGroup = new ConcurrentHashMap<>();
    private final Map<String, String[]> groupHelpers = new ConcurrentHashMap<>();
    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> ruleOutputs = new ConcurrentHashMap<>();

    // List of compiled rules and supporting components
    private final List<CompiledRule> compiledRules = new ArrayList<>();
    private final RuleCompiler compiler = new RuleCompiler();
    private final Map<String, Rule> ruleVersions = new ConcurrentHashMap<>();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker(1000000000, 30_000);
    private final MetricRegistry metrics = new MetricRegistry();
    private final Map<String, CompiledRule[]> compiledRulesByGroup = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CompiledRule>> ruleByNamePerGroup = new ConcurrentHashMap<>();
    private final Map<String, int[][]> dependencyMatrixPerGroup = new ConcurrentHashMap<>();
    private final Map<String, CompiledRule[]> rootRulesPerGroup = new ConcurrentHashMap<>();

    // Configuration flags and variables
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
     * Retrieves the thread-local context pool for managing evaluation contexts.
     *
     * @return the context pool
     */
    public Queue<Map<String, Object>> getContextPool() {
        return THREAD_LOCAL_CONTEXT_POOL.get();
    }

    /**
     * Enables or disables execution tracing for debugging purposes.
     *
     * @param enable true to enable tracing, false otherwise
     */
    public void enableTrace(boolean enable) {
        this.traceEnabled = enable;
    }

    /**
     * Sets the maximum number of execution steps to prevent infinite loops.
     *
     * @param steps the maximum number of steps
     */
    public void setMaxExecutionSteps(int steps) {
        this.maxExecutionSteps = steps;
    }

    /**
     * Sets a global variable accessible to all rules during execution.
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
     * @throws Exception if rule compilation fails or rule already exists
     */
    public void addRule(Rule rule) throws Exception {
        long count = compiledRules.stream().filter(c -> c.getRule().getName().equals(rule.getName()) && c.getRule().getGroup().equals(rule.getGroup())).count();
        if (count != 0) {
            throw new RuleExecutionException("The rule " + rule.getName() + " for group " + rule.getGroup() + " already exists");
        }
        CompiledRule compiledRule = compiler.compileRule(rule,
                Arrays.asList(groupHelpers.getOrDefault(rule.getGroup(), new String[0])));
        compiledRules.add(compiledRule);
        ruleVersions.put(rule.getName() + "_" + rule.getVersion(), rule.clone());
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
        Map<String, String[]> groupHelperMap = new HashMap<>();
        for (Rule rule : parseResult.getRules()) {
            long count = compiledRules.stream().filter(c -> c.getRule().getName().equals(rule.getName()) && c.getRule().getGroup().equals(rule.getGroup())).count();
            if (count != 0) {
                throw new RuleExecutionException("The rule " + rule.getName() + " for group " + rule.getGroup() + " already exists");
            }
            List<String> helpers = parseResult.getHelpers();
            groupHelperMap.put(rule.getGroup(), helpers.toArray(new String[0]));
        }
        groupHelpers.putAll(groupHelperMap);
        for (Rule rule : parseResult.getRules()) {
            String[] helpers =groupHelpers.get(rule.getGroup());
            CompiledRule compiledRule = compiler.compileRule(rule, Arrays.asList(helpers));
            compiledRules.add(compiledRule);
            ruleVersions.put(rule.getName() + "_" + rule.getVersion(), rule.clone());
        }
        rebuildGroupStructures();
    }

    /**
     * Rebuilds group structures and precomputes execution paths for all rule groups to optimize runtime performance.
     */
    private void rebuildGroupStructures() {
        precomputedExecutionPaths.clear();
        ruleNameToIndexPerGroup.clear();
        compiledRulesByGroup.clear();
        ruleByNamePerGroup.clear();
        dependencyMatrixPerGroup.clear();
        rootRulesPerGroup.clear();
        Map<String, List<CompiledRule>> tempGroupMap = new HashMap<>();
        for (CompiledRule cr : compiledRules) {
            tempGroupMap.computeIfAbsent(cr.getRule().getGroup(), k -> new ArrayList<>()).add(cr);
        }
        for (Map.Entry<String, List<CompiledRule>> entry : tempGroupMap.entrySet()) {
            String group = entry.getKey();
            List<CompiledRule> groupRules = entry.getValue();
            CompiledRule[] rulesArray = groupRules.toArray(new CompiledRule[0]);
            compiledRulesByGroup.put(group, rulesArray);
            Map<String, CompiledRule> nameMap = new HashMap<>();
            Map<String, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < rulesArray.length; i++) {
                CompiledRule rule = rulesArray[i];
                nameMap.put(rule.getRule().getName(), rule);
                indexMap.put(rule.getRule().getName(), i);
            }
            ruleByNamePerGroup.put(group, nameMap);
            ruleNameToIndexPerGroup.put(group, indexMap);
            precomputeExecutionPath(group, rulesArray, nameMap);
        }
    }

    /**
     * Precomputes the optimal execution path for a rule group using dependency graphs to avoid runtime traversal.
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
     * Computes a topological order of rules using Kahn's algorithm for efficient execution sequencing.
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
     * Asynchronously executes all rules in a specified group, handling retries and timeouts.
     *
     * @param group the rule group to execute
     * @return a CompletableFuture containing the final result of the execution
     */
    public CompletableFuture<Object> executeRulesAsync(String group) {
        return CompletableFuture.supplyAsync(() -> {
            if (precomputedExecutionPaths.isEmpty()) {
                try {
                    throw new RuleExecutionException("Nothing to run");
                } catch (RuleExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            String executionId = "EXEC" + System.nanoTime();
            RuleExecutionContext context = new RuleExecutionContext(executionId);
            Object result = null;
            try {
                result = executeRules(context, group, executionId);
            } catch (RuleExecutionException e) {
                throw new RuntimeException(e);
            }
            context.reset();
            return result;
        }, EXECUTION_POOL);
    }

    /**
     * Asynchronously executes all rules across all groups, using parallel execution for multiple groups.
     *
     * @return a CompletableFuture containing the final result of the execution
     */
    public CompletableFuture<Object> executeRulesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (precomputedExecutionPaths.isEmpty()) {
                try {
                    throw new RuleExecutionException("Nothing to run");
                } catch (RuleExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            Set<String> groups = precomputedExecutionPaths.keySet();
            if (groups.size() == 1) {
                String executionId = "EXEC" + System.nanoTime();
                RuleExecutionContext context = new RuleExecutionContext(executionId);
                Object result = null;
                try {
                    result = executeRules(context, groups.iterator().next(), executionId);
                } catch (RuleExecutionException e) {
                    throw new RuntimeException(e);
                }
                context.reset();
                return result;
            } else {
                CompletableFuture<Object>[] futures = groups.stream()
                        .map(group -> CompletableFuture.supplyAsync(() -> {
                            try {
                                String s = "EXEC" + System.nanoTime();
                                RuleExecutionContext context = new RuleExecutionContext(s);
                                Object o = executeRules(context, group, s);
                                context.reset();
                                return o;
                            } catch (RuleExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }, EXECUTION_POOL))
                        .toArray(CompletableFuture[]::new);
                CompletableFuture.allOf(futures).join();
                return futures[futures.length - 1].join();
            }
        }, EXECUTION_POOL);
    }

    /**
     * Executes all rules in a specified group, handling retries and timeouts.
     *
     * @param group the rule group to execute
     * @return the final result of the execution
     * @throws RuleExecutionException if execution fails
     */
    public Object executeRules(String group) throws RuleExecutionException {
        if (precomputedExecutionPaths.isEmpty()) {
            throw new RuleExecutionException("Nothing to run");
        }
        String executionId = "EXEC" + System.nanoTime();
        RuleExecutionContext context = new RuleExecutionContext(executionId);
        Object o = executeRules(context, group, executionId);
        context.reset();
        return o;
    }

    /**
     * Executes all rules across all groups, using parallel execution for multiple groups.
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
           String executionId = "EXEC" + System.nanoTime();
            RuleExecutionContext context = new RuleExecutionContext(executionId);
            localResult = executeRules(context,groups.iterator().next(),executionId );
            context.reset();
        } else {
            CompletableFuture<Object>[] futures = groups.stream()
                    .map(group -> CompletableFuture.supplyAsync(() -> {
                        try {
                            String s = "EXEC" + System.nanoTime();
                            RuleExecutionContext context = new RuleExecutionContext(s);
                            Object o = executeRules(context, group, s);
                            context.reset();
                            return o;
                        } catch (RuleExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }, EXECUTION_POOL))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
            localResult = futures[futures.length - 1].join();
        }
        return localResult;
    }

    /**
     * Executes rules in a specific group with a given execution ID, managing retries and timeouts.
     * This method processes rules in a loop, respecting dependencies and scheduling retries as needed.
     *
     * @param group       the rule group
     * @param executionId the unique execution ID
     * @return the final result of the execution
     * @throws RuleExecutionException if execution fails
     */
    private Object executeRules(RuleExecutionContext context ,String group, String executionId) throws RuleExecutionException {
        CompiledRule[] rules = precomputedExecutionPaths.get(group);
        if (rules == null || rules.length == 0) {
            throw new RuleExecutionException("No rules found for group: " + group);
        }

        Map<String, Integer> pendingParents = new HashMap<>(); // Tracks dependencies for each rule
        Map<String, Integer> ruleNameToIndex = ruleNameToIndexPerGroup.get(group);
        int[][] depMatrix = dependencyMatrixPerGroup.get(group);

        // Initialize dependency counts for each rule
        for (CompiledRule rule : rules) {
            String ruleName = rule.getRule().getName();
            int i = ruleNameToIndex.get(ruleName);
            int count = 0;
            for (int j = 0; j < rules.length; j++) {
                if (depMatrix[j][i] == 1) count++;
            }
            pendingParents.put(ruleName, count);
        }

        Set<String> completedRules = new HashSet<>();
        Object finalResult = null;
        long nextTime = Long.MAX_VALUE;
        // Main execution loop: process rules until all are completed or failed
        while (true) {
            long currentTime = System.currentTimeMillis();
            List<CompiledRule> readyRules = new ArrayList<>();

            // Identify rules ready to execute (no pending dependencies and scheduled time reached)
            for (CompiledRule rule : rules) {
                String ruleName = rule.getRule().getName();
                RuleExecutionContext.RuleExecutionState state = context.getState(ruleName);
                if (!completedRules.contains(ruleName) && !state.isFailed()) {
                    if (pendingParents.get(ruleName) == 0 && state.getNextExecutionTime() <= currentTime) {
                        readyRules.add(rule);
                    }
                }
            }

            if (readyRules.isEmpty()) {
                // Determine the next scheduled execution time

                for (CompiledRule rule : rules) {
                    String ruleName = rule.getRule().getName();
                    RuleExecutionContext.RuleExecutionState state = context.getState(ruleName);
                    if (!completedRules.contains(ruleName) && !state.isFailed()) {
                        long nextExec = state.getNextExecutionTime();
                        if (nextExec < nextTime) nextTime = nextExec;
                    }
                }
                if (nextTime == Long.MAX_VALUE) {
                    break; // No more rules to execute
                }
                long delay = nextTime - currentTime;
                if (delay > 0) {
                    try {
                        Thread.sleep(delay); // Wait until the next scheduled execution
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                  nextTime = Long.MAX_VALUE;
                }
            } else {
                // Execute the first ready rule
                CompiledRule rule = readyRules.get(0);
                RuleOutcome outcome = executeSingleRule(rule, context);
                String ruleName = rule.getRule().getName();
                if (outcome.success) {
                    completedRules.add(ruleName);
                    if (outcome.finalResult != null) {
                        finalResult = outcome.finalResult;
                    }
                    int i = ruleNameToIndex.get(ruleName);
                    // Update dependencies for child rules
                    for (int j = 0; j < rules.length; j++) {
                        if (depMatrix[i][j] == 1) {
                            String childName = rules[j].getRule().getName();
                            pendingParents.put(childName, pendingParents.get(childName) - 1);
                        }
                    }
                } else if (!canRetry(rule, context.getState(ruleName))) {
                    context.getState(ruleName).setFailed(true); // Mark as failed if no retries remain
                }
            }
        }

       // clearCache();

        return finalResult;
    }

    /**
     * Checks if a rule can be retried based on its retry count and maximum retries.
     *
     * @param rule the compiled rule
     * @param state the rule's execution state
     * @return true if the rule can be retried, false otherwise
     */
    private boolean canRetry(CompiledRule rule, RuleExecutionContext.RuleExecutionState state) {
        return state.getRetryCount() < rule.getRule().getMaxRetries();
    }

    /**
     * Represents the outcome of a rule execution, encapsulating the final result and success status.
     */
    private static class RuleOutcome {
        final Object finalResult;
        final boolean success;

        RuleOutcome(Object finalResult, boolean success) {
            this.finalResult = finalResult;
            this.success = success;
        }
    }

    /**
     * Executes a single rule within the given execution context, handling retries and timeouts.
     * If execution fails, it evaluates the retry condition and schedules a retry if applicable.
     *
     * @param compiledRule the compiled rule to execute
     * @param context the execution context
     * @return the outcome of the rule execution
     */
    private RuleOutcome executeSingleRule(CompiledRule compiledRule, RuleExecutionContext context) {
        Rule rule = compiledRule.getRule();
        RuleExecutionContext.RuleExecutionState state = context.getState(rule.getName());
        Object finalResult = null;
        boolean success = false;

        // Check if maximum executions have been reached
        if (state.getExecutionCount() >= rule.getMaxExecutions()) {
            if (traceEnabled) {
                System.out.println("Skipping rule " + rule.getName() + " (max executions reached)");
            }
            return new RuleOutcome(finalResult, success);
        }

        try {
            state.setLastExecutionTime(System.currentTimeMillis());
            if (!circuitBreaker.allowExecution()) {
                throw new EngineOverloadException("Circuit breaker tripped");
            }
            boolean conditionResult = evaluateCondition(compiledRule, context);
            if (conditionResult) {
                state.incrementExecutionCount();
                executeAction(compiledRule, context);
                if (compiledRule.getFinalEvaluator() != null) {
                    finalResult = executeFinalBlock(compiledRule, context);
                }
                state.setCompleted(true);
                success = true;
                if (traceEnabled) {
                    System.out.println("Rule executed successfully: " + rule.getName());
                }
            } else {
                state.setCompleted(true);
                success = true; // Condition false is not a failure
                if (traceEnabled) {
                    System.out.println("Rule condition false: " + rule.getName());
                }
            }
        } catch (Exception e) {
            if (metric) {
                metrics.meter(rule.getName() + ".errors").mark();
            }
            state.setLastError(e);
            if (state.getRetryCount() < rule.getMaxRetries()) {
                boolean shouldRetry = true;
                if (rule.getRetryCondition() != null && compiledRule.getRetryEvaluator() != null) {
                    Map<String, Object> evalContext = getContext();
                    try {
                        populateContext(evalContext, rule, context);
                        shouldRetry = (Boolean) compiledRule.getRetryEvaluator().evaluate(new Object[]{evalContext, e});
                    } catch (Exception ex) {
                        shouldRetry = false;
                    } finally {
                        returnContext(evalContext);
                    }
                }
                if (shouldRetry) {
                    state.incrementRetryCount();
                    state.setNextExecutionTime(System.currentTimeMillis() + rule.getRetryDelay());
                    if (traceEnabled) {
                        System.out.println("Rule execution failed, will retry: " + rule.getName());
                    }
                } else {
                    state.setFailed(true);
                    if (traceEnabled) {
                        System.out.println("Rule failed (retry condition false): " + rule.getName());
                    }
                }
            } else {
                state.setFailed(true);
                if (traceEnabled) {
                    System.out.println("Rule failed permanently: " + rule.getName());
                }
            }
            circuitBreaker.recordFailure();
        }
        return new RuleOutcome(finalResult, success);
    }

    /**
     * Evaluates the condition block of a compiled rule with timeout enforcement using the thread pool.
     *
     * @param compiledRule the compiled rule
     * @param context the execution context
     * @return true if the condition evaluates to true, false otherwise
     * @throws Exception if evaluation fails or times out
     */
    private boolean evaluateCondition(CompiledRule compiledRule, RuleExecutionContext context) throws Exception {
        if (compiledRule.getConditionEvaluator() == null) return true;
        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            Future<Boolean> future = EXECUTION_POOL.submit(() ->
                    (Boolean) compiledRule.getConditionEvaluator().evaluate(new Object[]{evalContext, null}));
            return future.get(compiledRule.getRule().getTimeout(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuleTimeoutException("Condition evaluation timed out for rule: " + compiledRule.getRule().getName());
        } finally {
            returnContext(evalContext);
        }
    }

    /**
     * Executes the action block of a compiled rule with timeout enforcement using the thread pool.
     *
     * @param compiledRule the compiled rule
     * @param context the execution context
     * @throws Exception if execution fails or times out
     */
    private void executeAction(CompiledRule compiledRule, RuleExecutionContext context) throws Exception {
        if (compiledRule.getActionEvaluator() == null) return;
        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            Future<?> future = EXECUTION_POOL.submit(() -> {
                compiledRule.getActionEvaluator().evaluate(new Object[]{evalContext, null});
                return null;
            });
            future.get(compiledRule.getRule().getTimeout(), TimeUnit.MILLISECONDS);
            Rule rule = compiledRule.getRule();
            if (!rule.getProduces().isEmpty()) {
                Map<String, Object> outputs = new HashMap<>();
                for (String produceVar : rule.getProduces().keySet()) {
                    outputs.put(produceVar, evalContext.get(produceVar));
                }
                ruleOutputs.put(rule.getName(), outputs);
            }
        } catch (TimeoutException e) {
            throw new RuleTimeoutException("Action execution timed out for rule: " + compiledRule.getRule().getName());
        } finally {
            returnContext(evalContext);
        }
    }

    /**
     * Executes the final block of a compiled rule with timeout enforcement using the thread pool.
     *
     * @param compiledRule the compiled rule
     * @param context the execution context
     * @return the result of the final block
     * @throws Exception if execution fails or times out
     */
    private Object executeFinalBlock(CompiledRule compiledRule, RuleExecutionContext context) throws Exception {
        if (compiledRule.getFinalEvaluator() == null) return null;
        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            Future<Object> future = EXECUTION_POOL.submit(() ->
                    compiledRule.getFinalEvaluator().evaluate(new Object[]{evalContext, null}));
            return future.get(compiledRule.getRule().getTimeout(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuleTimeoutException("Final block execution timed out for rule: " + compiledRule.getRule().getName());
        } finally {
            returnContext(evalContext);
        }
    }

    /**
     * Populates the evaluation context with global variables, rule outputs, and used variables for rule execution.
     *
     * @param context the evaluation context
     * @param rule the rule being executed
     * @param executionContext the rule execution context
     */
    private void populateContext(Map<String, Object> context, Rule rule, RuleExecutionContext executionContext) {
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
     * Retrieves a context map from the thread-local pool or creates a new one if the pool is empty.
     *
     * @return a context map for rule evaluation
     */
    public Map<String, Object> getContext() {
        Queue<Map<String, Object>> pool = THREAD_LOCAL_CONTEXT_POOL.get();
        Map<String, Object> context = pool.poll();
        return (context != null) ? context : new HashMap<>(500);
    }

    /**
     * Returns a context map to the thread-local pool after clearing it, limiting pool size per thread.
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
     * Clears the rule output cache after execution to free up memory.
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
     * Rolls back to the latest version of a rule by updating it to the most recent stored version.
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
     * Retrieves a snapshot of the current metrics for monitoring rule execution performance.
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
     * Creates a deep copy of the engine for safe concurrent modifications.
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
     * Builder class for constructing an {@code LWREngine} instance with configurable options.
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
     * Exception thrown when the engine is overloaded and the circuit breaker trips.
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

    /**
     * Exception thrown when a rule execution exceeds its configured timeout.
     */
    public static class RuleTimeoutException extends RuleExecutionException {
        /**
         * Constructs a new {@code RuleTimeoutException} with the specified message.
         *
         * @param message the error message
         */
        public RuleTimeoutException(String message) {
            super(message);
        }
    }
}