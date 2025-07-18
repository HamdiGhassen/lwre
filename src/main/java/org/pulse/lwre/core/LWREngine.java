package org.pulse.lwre.core;

import org.pulse.lwre.dsl.DSLParser;
import org.pulse.lwre.metric.Context;
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
 /** The {@code LWREngine} class implements a lightweight rule engine for executing business rules
 * defined in a domain-specific language (DSL). It supports rule compilation, dependency graph
 * construction, parallel execution, and metrics collection. The engine manages rule execution
 * contexts, global variables, and circuit breaking to ensure robust performance under load.
 * Rules are organized into groups, and the engine supports rule versioning, rollback, and
 * execution tracing for debugging purposes.
 *
 * <p>The engine uses a directed graph to model rule dependencies and employs a work-stealing
 * thread pool for efficient parallel execution. It also provides a context pool for managing
 * evaluation contexts and a circuit breaker to prevent overload. Metrics are collected to monitor
 * rule execution performance, including condition evaluation, action execution, and error rates.
 *
 * <p>The class is designed to be thread-safe, with concurrent data structures such as
 * {@code ConcurrentHashMap} and {@code CopyOnWriteArrayList} used for managing rules, variables,
 * and execution state. It also supports cloning for creating independent instances of the engine.
 *
 * @author Hamdi Ghassen
 */
public class LWREngine implements Cloneable {
    private final Map<String, List<String>> groupHelpers = new ConcurrentHashMap<>();
    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> ruleOutputs = new ConcurrentHashMap<>();
    private final List<CompiledRule> compiledRules = new ArrayList<>();
    private final RuleCompiler compiler = new RuleCompiler();
    private final Map<String, Rule> ruleVersions = new ConcurrentHashMap<>();


    private final Queue<Map<String, Object>> contextPool = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newWorkStealingPool();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker(5, 30_000);
    private final MetricRegistry metrics = new MetricRegistry();
    private final Map<String, DirectedGraph<CompiledRule>> groupGraphs = new ConcurrentHashMap<>();
    private final Map<String, List<CompiledRule>> compiledRulesByGroup = new ConcurrentHashMap<>();
    private final Map<String, List<CompiledRule>> cachedExecutionPaths = new ConcurrentHashMap<>();
    private boolean traceEnabled = false;
    private int maxExecutionSteps = 1000;
    private Map<String, Map<CompiledRule, Integer>> initialInDegreePerGroup = new ConcurrentHashMap<>();
    private Map<String, List<CompiledRule>> rootRulesPerGroup = new ConcurrentHashMap<>();
    private Map<String, Map<String, CompiledRule>> ruleByNamePerGroup = new ConcurrentHashMap<>();

    private LWREngine() {
    }
    /**
     * Retrieves the context pool used for managing evaluation contexts.
     *
     * @return the queue of context maps
     */
    public Queue<Map<String, Object>> getContextPool() {
        return contextPool;
    }
    /**
     * Enables or disables tracing for rule execution debugging.
     *
     * @param enable true to enable tracing, false to disable
     */
    public void enableTrace(boolean enable) {
        this.traceEnabled = enable;
    }
    /**
     * Sets the maximum number of execution steps allowed for rule processing.
     *
     * @param steps the maximum number of steps
     */
    public void setMaxExecutionSteps(int steps) {
        this.maxExecutionSteps = steps;
    }
    /**
     * Sets a global variable that can be used across rule executions.
     *
     * @param name the name of the variable
     * @param value the value of the variable
     */
    public void setGlobalVariable(String name, Object value) {
        if (value != null) {
            globalVariables.put(name, value);
        }
    }
    /**
     * Retrieves the value of a global variable.
     *
     * @param name the name of the variable
     * @return the value of the variable, or null if not found
     */
    public Object getGlobalVariable(String name) {
        return globalVariables.get(name);
    }
    /**
     * Adds a single rule to the engine and updates the dependency graph.
     *
     * @param rule the rule to add
     * @throws Exception if rule compilation fails
     */
    public void addRule(Rule rule) throws Exception {

        CompiledRule compiledRule = compiler.compileRule(rule, rule.getHelpers());
        compiledRules.add(compiledRule);
        ruleVersions.put(rule.getName() + "_" + rule.getVersion(), rule.clone());

        compiledRulesByGroup.computeIfAbsent(rule.getGroup(), k -> new CopyOnWriteArrayList<>())
                .add(compiledRule);
        buildDependencyGraphs();
    }
    /**
     * Adds multiple rules from a DSL content string and updates the dependency graph.
     *
     * @param dslContent the DSL content containing rules and helpers
     * @throws Exception if parsing or compilation fails
     */
    public void addRules(String dslContent) throws Exception {
        DSLParser.ParseResult parseResult = DSLParser.parseRules(dslContent);

        Map<String, List<String>> groupHelperMap = new HashMap<>();
        for (Rule rule : parseResult.getRules()) {
            groupHelperMap.computeIfAbsent(rule.getGroup(), k -> new ArrayList<>())
                    .addAll(parseResult.getHelpers());
        }

        for (Map.Entry<String, List<String>> entry : groupHelperMap.entrySet()) {
            groupHelpers.put(entry.getKey(), entry.getValue());
        }

        for (Rule rule : parseResult.getRules()) {
            List<String> groupHelpers = this.groupHelpers.getOrDefault(rule.getGroup(), Collections.emptyList());
            CompiledRule compiledRule = compiler.compileRule(rule, groupHelpers);
            compiledRules.add(compiledRule);
            ruleVersions.put(rule.getName() + "_" + rule.getVersion(), rule.clone());


            compiledRulesByGroup.computeIfAbsent(rule.getGroup(), k -> new CopyOnWriteArrayList<>())
                    .add(compiledRule);
        }
        buildDependencyGraphs();
    }
    /**
     * Builds dependency graphs for all rule groups to determine execution order.
     */
    private void buildDependencyGraphs() {
        initialInDegreePerGroup.clear();
        rootRulesPerGroup.clear();
        ruleByNamePerGroup.clear();
        groupGraphs.clear();

        List<Rule> allRules = compiledRules.stream()
                .map(CompiledRule::getRule)
                .collect(Collectors.toList());

        Map<String, DirectedGraph<Rule>> ruleGraphsByGroup = RuleGraphProcessor.processRules(allRules);

        for (Map.Entry<String, List<CompiledRule>> entry : compiledRulesByGroup.entrySet()) {
            String group = entry.getKey();
            List<CompiledRule> groupRules = entry.getValue();
            DirectedGraph<Rule> ruleGraph = ruleGraphsByGroup.get(group);

            if (ruleGraph == null) {
                groupGraphs.put(group, new DirectedGraph<>());
                continue;
            }

            Map<String, CompiledRule> ruleNameToCompiled = new HashMap<>();
            for (CompiledRule cr : groupRules) {
                ruleNameToCompiled.put(cr.getRule().getName(), cr);
            }
            ruleByNamePerGroup.put(group, ruleNameToCompiled);

            DirectedGraph<CompiledRule> compiledRuleGraph = new DirectedGraph<>();
            for (Rule rule : ruleGraph.getVertices()) {
                CompiledRule compiledRule = ruleNameToCompiled.get(rule.getName());
                if (compiledRule != null) {
                    compiledRuleGraph.addVertex(compiledRule);
                }
            }

            for (Rule rule : ruleGraph.getVertices()) {
                CompiledRule source = ruleNameToCompiled.get(rule.getName());
                if (source != null) {
                    for (Rule neighbor : ruleGraph.getNeighbors(rule)) {
                        CompiledRule target = ruleNameToCompiled.get(neighbor.getName());
                        if (target != null) {
                            compiledRuleGraph.addEdge(source, target);
                        }
                    }
                }
            }

            groupGraphs.put(group, compiledRuleGraph);

            Map<CompiledRule, Integer> inDegreeMap = new HashMap<>();
            for (CompiledRule vertex : compiledRuleGraph.getVertices()) {
                inDegreeMap.put(vertex, 0);
            }
            for (CompiledRule u : compiledRuleGraph.getVertices()) {
                for (CompiledRule v : compiledRuleGraph.getNeighbors(u)) {
                    inDegreeMap.put(v, inDegreeMap.get(v) + 1);
                }
            }
            initialInDegreePerGroup.put(group, inDegreeMap);

            List<CompiledRule> rootRules = new ArrayList<>();
            for (CompiledRule vertex : compiledRuleGraph.getVertices()) {
                if (inDegreeMap.get(vertex) == 0) {
                    rootRules.add(vertex);
                }
            }
            rootRules.sort(Comparator.comparingInt(cr -> cr.getRule().getPriority()));
            rootRulesPerGroup.put(group, rootRules);
        }
    }
    /**
     * Executes rules for a specific group.
     *
     * @param group the group name
     * @return the final result of the rule execution
     * @throws RuleExecutionException if no rules are found or execution fails
     */
    public Object executeRules(String group) throws RuleExecutionException {
        if (groupGraphs.size() == 0) {
            throw new RuleExecutionException("Nothing to run");
        }
        return executeRules(group, UUID.randomUUID().toString());
    }

    Object localResult = null;
    /**
     * Executes rules for all groups in parallel.
     *
     * @return the result of the last executed group
     * @throws RuleExecutionException if no rules are found or execution fails
     */
    public Object executeRules() throws RuleExecutionException {

        if (groupGraphs.size() == 0) {
            throw new RuleExecutionException("Nothing to run");
        }
        groupGraphs.keySet().parallelStream().forEach(group -> {
            try {
                localResult = executeRules(group, UUID.randomUUID().toString());
            } catch (RuleExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        return localResult;
    }
    /**
     * Caches the execution path for a specific group based on rule dependencies.
     *
     * @param group the group name
     * @throws RuleExecutionException if no rules are found for the group
     */
    private void cacheExecutionPath(String group) throws RuleExecutionException {
        DirectedGraph<CompiledRule> graph = groupGraphs.get(group);
        if (graph == null) {
            throw new RuleExecutionException("No rules found for group: " + group);
        }

        Map<CompiledRule, Integer> inDegreeMap = new HashMap<>();
        for (CompiledRule vertex : graph.getVertices()) {
            inDegreeMap.put(vertex, 0);
        }
        for (CompiledRule u : graph.getVertices()) {
            for (CompiledRule v : graph.getNeighbors(u)) {
                inDegreeMap.put(v, inDegreeMap.get(v) + 1);
            }
        }

        List<CompiledRule> rootRules = new ArrayList<>();
        for (CompiledRule vertex : graph.getVertices()) {
            if (inDegreeMap.get(vertex) == 0) {
                rootRules.add(vertex);
            }
        }
        rootRules.sort(Comparator.comparingInt(cr -> cr.getRule().getPriority()));
        cachedExecutionPaths.put(group, rootRules);
    }
    /**
     * Executes rules for a specific group with a given execution ID.
     *
     * @param group the group name
     * @param executionId the unique execution ID
     * @return the final result of the rule execution
     * @throws RuleExecutionException if no rules are found or execution fails
     */
    public Object executeRules(String group, String executionId) throws RuleExecutionException {
        RuleExecutionContext executionContext = new RuleExecutionContext(executionId);
        Object finalResult = null;

        DirectedGraph<CompiledRule> graph = groupGraphs.get(group);
        if (graph == null) {
            throw new RuleExecutionException("No rules found for group: " + group);
        }

        Map<CompiledRule, Integer> inDegreeMap = new HashMap<>(initialInDegreePerGroup.get(group));
        List<CompiledRule> rootRules = new ArrayList<>(rootRulesPerGroup.get(group));
        Map<String, CompiledRule> ruleMap = ruleByNamePerGroup.get(group);

        Queue<CompiledRule> queue = new LinkedList<>(rootRules);
        Set<CompiledRule> executed = ConcurrentHashMap.newKeySet();


        while (!queue.isEmpty()) {
            CompiledRule current = queue.poll();
            if (executed.contains(current)) continue;

            RuleOutcome outcome = executeSingleRule(current, executionContext);
            executed.add(current);

            if (outcome.finalResult != null) {
                finalResult = outcome.finalResult;
            }

            for (CompiledRule child : graph.getNeighbors(current)) {
                int newDegree = inDegreeMap.get(child) - 1;
                inDegreeMap.put(child, newDegree);
                if (newDegree == 0) {
                    queue.add(child);
                }
            }

            String nextRuleName = outcome.success ?
                    current.getRule().getNextRuleOnSuccess() :
                    current.getRule().getNextRuleOnFailure();
            if (nextRuleName != null) {
                CompiledRule nextRule = ruleMap.get(nextRuleName);
                if (nextRule != null && !executed.contains(nextRule)) {
                    int newDegree = inDegreeMap.get(nextRule) - 1;
                    inDegreeMap.put(nextRule, newDegree);
                    if (newDegree == 0) {
                        queue.add(nextRule);
                    }
                }
            }
        }

        clearCache();
        return finalResult;
    }
    /**
     * Executes a rule and its dependencies, updating the execution context.
     *
     * @param rule the rule to execute
     * @param graph the dependency graph
     * @param context the execution context
     * @param executed the set of executed rules
     * @param inDegreeMap the map of in-degrees for dependency tracking
     * @return the outcome of the rule execution
     */
    private RuleOutcome executeRuleWithDependencies(CompiledRule rule,
                                                    DirectedGraph<CompiledRule> graph,
                                                    RuleExecutionContext context,
                                                    Set<CompiledRule> executed,
                                                    Map<CompiledRule, Integer> inDegreeMap) {
        final Object[] finalResult = {null};
        Queue<CompiledRule> queue = new LinkedList<>();
        queue.add(rule);

        while (!queue.isEmpty()) {
            CompiledRule current = queue.poll();
            if (executed.contains(current)) continue;
            if (inDegreeMap.get(current) > 0) {
                queue.add(current);
                continue;
            }

            RuleOutcome outcome = executeSingleRule(current, context);
            executed.add(current);

            if (outcome.finalResult != null) {
                finalResult[0] = outcome.finalResult;
            }

            for (CompiledRule child : graph.getNeighbors(current)) {
                int newDegree = inDegreeMap.get(child) - 1;
                inDegreeMap.put(child, newDegree);

                if (newDegree == 0 && !executed.contains(child)) {
                    queue.add(child);
                }
            }

            String nextRuleName = outcome.success ?
                    current.getRule().getNextRuleOnSuccess() :
                    current.getRule().getNextRuleOnFailure();

            if (nextRuleName != null) {
                compiledRulesByGroup.get(current.getRule().getGroup()).stream()
                        .filter(cr -> cr.getRule().getName().equals(nextRuleName))
                        .findFirst()
                        .ifPresent(nextRule -> {
                            if (!executed.contains(nextRule)) {
                                int newDegree = inDegreeMap.get(nextRule) - 1;
                                inDegreeMap.put(nextRule, newDegree);

                                if (newDegree == 0) {
                                    queue.add(nextRule);
                                }
                            }
                        });
            }
        }

        return new RuleOutcome(finalResult[0], true);
    }
    /**
     * Adds the next rules to the execution queue based on the success or failure of the current rule.
     *
     * @param current the current rule
     * @param success whether the rule executed successfully
     * @param queue the queue of rules to execute
     * @param executed the set of executed rules
     */
    private void addNextRules(
            CompiledRule current,
            boolean success,
            Queue<CompiledRule> queue,
            Set<CompiledRule> executed
    ) {
        String nextRuleName = success ?
                current.getRule().getNextRuleOnSuccess() :
                current.getRule().getNextRuleOnFailure();

        if (nextRuleName != null) {
            compiledRulesByGroup.get(current.getRule().getGroup()).stream()
                    .filter(cr -> cr.getRule().getName().equals(nextRuleName))
                    .findFirst()
                    .ifPresent(nextRule -> {
                        if (!executed.contains(nextRule)) {
                            queue.add(nextRule);
                        }
                    });
        }
    }
    /**
     * Executes a single rule, evaluating its condition and action, and handling retries and errors.
     *
     * @param compiledRule the compiled rule to execute
     * @param context the execution context
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
                System.out.println("Skipping rule " + rule.getName() +
                        " (max executions reached)");
            }
            return new RuleOutcome(finalResult, success);
        }

        if (state.getRetryCount() > 0 && !state.shouldRetry(rule)) {
            if (traceEnabled) {
                System.out.println("Delaying retry for rule " + rule.getName() +
                        " (retry " + state.getRetryCount() + "/" +
                        rule.getMaxRetries() + ")");
            }
            return new RuleOutcome(finalResult, false);
        }

        try {
            state.setLastExecutionTime(System.currentTimeMillis());

            if (!circuitBreaker.allowExecution()) {
                throw new EngineOverloadException("Circuit breaker tripped");
            }

            Context conditionTimer = metrics.timer(rule.getName() + ".condition").time();
            boolean conditionResult = evaluateCondition(compiledRule, context);
            conditionTimer.close();

            if (conditionResult) {

                state.incrementExecutionCount();
                Context actionTimer = metrics.timer(rule.getName() + ".action").time();
                executeAction(compiledRule, context);
                actionTimer.close();

                if (compiledRule.getFinalEvaluator() != null) {
                    Context finalTimer = metrics.timer(rule.getName() + ".final").time();
                    finalResult = executeFinalBlock(compiledRule, context);
                    finalTimer.close();
                }

                state.resetRetryCount();
                success = true;

                if (traceEnabled) {
                    System.out.println("Rule executed successfully: " + rule.getName());
                }
            } else {

                if (traceEnabled) {
                    System.out.println("Rule condition false: " + rule.getName());
                }
            }
        } catch (Exception e) {
            metrics.meter(rule.getName() + ".errors").mark();
            state.setLastError(e);
            state.incrementRetryCount();

            if (state.getRetryCount() <= rule.getMaxRetries()) {

                if (traceEnabled) {
                    System.out.println("Rule execution failed, will retry: " + rule.getName());
                    e.printStackTrace();
                }
            } else {
                if (traceEnabled) {
                    System.out.println("Rule failed permanently: " + rule.getName());
                    e.printStackTrace();
                }
            }
            success = false;
        }

        return new RuleOutcome(finalResult, success);
    }
    /**
     * Evaluates the condition of a compiled rule.
     *
     * @param compiledRule the compiled rule
     * @param context the execution context
     * @return true if the condition is satisfied, false otherwise
     * @throws Exception if evaluation fails
     */
    private boolean evaluateCondition(CompiledRule compiledRule,
                                      RuleExecutionContext context) throws Exception {
        if (compiledRule.getConditionEvaluator() == null) return true;

        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            Future<Boolean> future = executor.submit(() ->
                    (Boolean) compiledRule.getConditionEvaluator().evaluate(
                            new Object[]{evalContext, null}));

            return future.get(compiledRule.getRule().getTimeout(), TimeUnit.MILLISECONDS);
        } finally {
            returnContext(evalContext);
        }
    }
    /**
     * Executes the action of a compiled rule.
     *
     * @param compiledRule the compiled rule
     * @param context the execution context
     * @throws Exception if action execution fails
     */
    private void executeAction(CompiledRule compiledRule,
                               RuleExecutionContext context) throws Exception {
        if (compiledRule.getActionEvaluator() == null) return;

        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            Future<?> future = executor.submit(() ->
                    compiledRule.getActionEvaluator().evaluate(
                            new Object[]{evalContext, null}));

            future.get(compiledRule.getRule().getTimeout(), TimeUnit.MILLISECONDS);

            Rule rule = compiledRule.getRule();
            Map<String, Object> outputs = new HashMap<>();
            for (String produceVar : rule.getProduces().keySet()) {
                outputs.put(produceVar, evalContext.get(produceVar));
            }
            ruleOutputs.put(rule.getName(), outputs);
        } finally {
            returnContext(evalContext);
        }
    }
    /**
     * Executes the final block of a compiled rule, if present.
     *
     * @param compiledRule the compiled rule
     * @param context the execution context
     * @return the result of the final block, or null if none
     * @throws Exception if final block execution fails
     */
    private Object executeFinalBlock(CompiledRule compiledRule,
                                     RuleExecutionContext context) throws Exception {
        if (compiledRule.getFinalEvaluator() == null) return null;

        Map<String, Object> evalContext = getContext();
        try {
            populateContext(evalContext, compiledRule.getRule(), context);
            Future<Object> future = executor.submit(() ->
                    compiledRule.getFinalEvaluator().evaluate(
                            new Object[]{evalContext, null}));

            return future.get(compiledRule.getRule().getTimeout(), TimeUnit.MILLISECONDS);
        } finally {
            returnContext(evalContext);
        }
    }
    /**
     * Populates the evaluation context with global variables and rule-specific variables.
     *
     * @param context the evaluation context
     * @param rule the rule being executed
     * @param executionContext the rule execution context
     */
    private void populateContext(Map<String, Object> context, Rule rule,
                                 RuleExecutionContext executionContext) {
        context.clear();

        executionContext.executionGlobals.forEach(context::put);

        globalVariables.forEach(context::put);

        for (Map.Entry<String, Rule.UseVariable> entry : rule.getUses().entrySet()) {
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

            context.put(localName, value);
        }

        Map<String, Object> existingOutputs = ruleOutputs.get(rule.getName());
        if (existingOutputs != null) {
            context.putAll(existingOutputs);
        }
    }
    /**
     * Retrieves a context map from the pool or creates a new one.
     *
     * @return a context map
     */
    public Map<String, Object> getContext() {
        Map<String, Object> context = contextPool.poll();
        return (context != null) ? context : new HashMap<>();
    }
    /**
     * Returns a context map to the pool after clearing it, if the pool is not full.
     *
     * @param context the context map to return
     */
    private void returnContext(Map<String, Object> context) {
        int MAX_POOL_SIZE = 1000;
        if (contextPool.size() < MAX_POOL_SIZE) {
            context.clear();
            contextPool.offer(context);
        }
    }
    /**
     * Clears the rule output and context pool caches.
     */
    public void clearCache() {
        ruleOutputs.clear();
        contextPool.clear();
    }
    /**
     * Updates an existing rule with a new version and rebuilds the dependency graph.
     *
     * @param newRule the new rule version
     * @throws Exception if rule compilation fails
     */
    public void updateRule(Rule newRule) throws Exception {
        String versionKey = newRule.getName() + "_" + newRule.getVersion();
        if (ruleVersions.containsKey(versionKey)) return;

        List<String> groupHelpers = this.groupHelpers.getOrDefault(newRule.getGroup(), Collections.emptyList());
        CompiledRule compiled = compiler.compileRule(newRule, groupHelpers);
        compiledRules.removeIf(cr -> cr.getRule().getName().equals(newRule.getName()));
        compiledRules.add(compiled);

        // Update group mapping
        compiledRulesByGroup.computeIfAbsent(newRule.getGroup(), k -> new CopyOnWriteArrayList<>())
                .removeIf(cr -> cr.getRule().getName().equals(newRule.getName()));
        compiledRulesByGroup.get(newRule.getGroup()).add(compiled);

        buildDependencyGraphs();
        ruleVersions.put(versionKey, newRule);
    }
    /**
     * Rolls back a rule to its latest version.
     *
     * @param ruleName the name of the rule to roll back
     * @return true if rollback is successful, false otherwise
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
     * Retrieves a snapshot of the current metrics for rule execution.
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
     * Creates a deep copy of the rule engine.
     *
     * @return a cloned instance of the rule engine
     */
    @Override
    public LWREngine clone() {
        LWREngine cloned = new LWREngine();
        cloned.traceEnabled = this.traceEnabled;
        cloned.compiledRules.addAll(this.compiledRules);
        cloned.globalVariables.putAll(this.globalVariables);
        cloned.ruleVersions.putAll(this.ruleVersions);
        cloned.clearCache();
        return cloned;
    }
    /**
     * Builder class for constructing an {@code LWREngine} instance with custom configuration.
     */
    public static class Builder {
        private LWREngine e = new LWREngine();
        /**
         * Enables or disables debug tracing.
         *
         * @param flag true to enable tracing, false to disable
         * @return this builder
         */
        public Builder debug(boolean flag) {
            e.traceEnabled = flag;
            return this;
        }
        /**
         * Sets the maximum number of execution steps.
         *
         * @param steps the maximum number of steps
         * @return this builder
         */
        public Builder maxSteps(int steps) {
            e.maxExecutionSteps = steps;
            return this;
        }
        /**
         * Adds rules from a DSL string.
         *
         * @param dsl the DSL content
         * @return this builder
         * @throws Exception if parsing or compilation fails
         */
        public Builder rules(String dsl) throws Exception {
            e.addRules(dsl);
            return this;
        }
        /**
         * Sets a global variable.
         *
         * @param name the variable name
         * @param value the variable value
         * @return this builder
         */
        public Builder global(String name, Object value) {
            e.setGlobalVariable(name, value);
            return this;
        }
        /**
         * Builds the rule engine instance.
         *
         * @return the configured rule engine
         */
        public LWREngine build() {
            return e;
        }
    }

    /**
     * Exception thrown when the engine is overloaded and the circuit breaker is tripped.
     */
    public static class EngineOverloadException extends RuleExecutionException {
        public EngineOverloadException(String message) {
            super(message);
        }
    }
}