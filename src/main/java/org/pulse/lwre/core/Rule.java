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
 * The {@code Rule} class represents a business rule in the Lightweight Rule Engine (LWRE).
 * It encapsulates the configuration and logic for a single rule, including its name, version,
 * priority, group, retry policies, execution limits, and script blocks for condition, action,
 * final processing, and retry conditions. The class also manages dependencies through imports
 * and variable usage, and supports cloning for rule versioning. The {@code UseVariable} inner
 * class defines variables used by the rule, specifying their source and type.
 *
 * @author Hamdi Ghassen
 */
public class Rule {
    private String name;
    private String version = "1.0.0";
    private int priority;
    private String group = "MAIN";
    private int maxRetries = 0;
    private long retryDelay = 0;
    private int maxExecutions = 1;
    private long timeout = 5000; // Default 5 seconds
    private String nextRuleOnSuccess;
    private String nextRuleOnFailure;
    private List<String> imports;
    private Map<String, String> produces;
    private Map<String, UseVariable> uses;
    private String condition;
    private String action;
    private String finalBlock;
    private String retryCondition;
    private List<String> helpers;

    /**
     * Constructs a new {@code Rule} with default values and initialized collections.
     */
    public Rule() {
        this.imports = new ArrayList<>();
        this.produces = new HashMap<>();
        this.uses = new HashMap<>();
        this.helpers = new ArrayList<>();
    }

    /**
     * Retrieves the name of the rule.
     *
     * @return the rule name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the rule.
     *
     * @param name the rule name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retrieves the version of the rule.
     *
     * @return the rule version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the version of the rule.
     *
     * @param version the rule version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Retrieves the priority of the rule, used for execution ordering.
     *
     * @return the rule priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the priority of the rule.
     *
     * @param priority the rule priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Retrieves the group to which the rule belongs.
     *
     * @return the rule group
     */
    public String getGroup() {
        return group;
    }

    /**
     * Sets the group to which the rule belongs.
     *
     * @param group the rule group
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * Retrieves the maximum number of retries allowed for the rule.
     *
     * @return the maximum retries
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the maximum number of retries for the rule.
     *
     * @param maxRetries the maximum retries
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * Retrieves the retry delay in milliseconds.
     *
     * @return the retry delay
     */
    public long getRetryDelay() {
        return retryDelay;
    }

    /**
     * Sets the retry delay in milliseconds.
     *
     * @param retryDelay the retry delay
     */
    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    /**
     * Retrieves the maximum number of executions allowed for the rule.
     *
     * @return the maximum executions
     */
    public int getMaxExecutions() {
        return maxExecutions;
    }

    /**
     * Sets the maximum number of executions for the rule.
     *
     * @param maxExecutions the maximum executions
     */
    public void setMaxExecutions(int maxExecutions) {
        this.maxExecutions = maxExecutions;
    }

    /**
     * Retrieves the execution timeout in milliseconds.
     *
     * @return the timeout
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the execution timeout in milliseconds.
     *
     * @param timeout the timeout
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Retrieves the name of the next rule to execute on successful execution.
     *
     * @return the next rule name, or null if not set
     */
    public String getNextRuleOnSuccess() {
        return nextRuleOnSuccess;
    }

    /**
     * Sets the name of the next rule to execute on successful execution.
     *
     * @param nextRuleOnSuccess the next rule name
     */
    public void setNextRuleOnSuccess(String nextRuleOnSuccess) {
        this.nextRuleOnSuccess = nextRuleOnSuccess;
    }

    /**
     * Retrieves the name of the next rule to execute on failed execution.
     *
     * @return the next rule name, or null if not set
     */
    public String getNextRuleOnFailure() {
        return nextRuleOnFailure;
    }

    /**
     * Sets the name of the next rule to execute on failed execution.
     *
     * @param nextRuleOnFailure the next rule name
     */
    public void setNextRuleOnFailure(String nextRuleOnFailure) {
        this.nextRuleOnFailure = nextRuleOnFailure;
    }

    /**
     * Retrieves the list of imports used by the rule.
     *
     * @return the list of imports
     */
    public List<String> getImports() {
        return imports;
    }

    /**
     * Sets the list of imports used by the rule.
     *
     * @param imports the list of imports
     */
    public void setImports(List<String> imports) {
        this.imports = imports;
    }

    /**
     * Retrieves the map of variables produced by the rule.
     *
     * @return the map of produced variables
     */
    public Map<String, String> getProduces() {
        return produces;
    }

    /**
     * Sets the map of variables produced by the rule.
     *
     * @param produces the map of produced variables
     */
    public void setProduces(Map<String, String> produces) {
        this.produces = produces;
    }

    /**
     * Retrieves the map of variables used by the rule.
     *
     * @return the map of used variables
     */
    public Map<String, UseVariable> getUses() {
        return uses;
    }

    /**
     * Sets the map of variables used by the rule.
     *
     * @param uses the map of used variables
     */
    public void setUses(Map<String, UseVariable> uses) {
        this.uses = uses;
    }

    /**
     * Retrieves the condition block script of the rule.
     *
     * @return the condition block script, or null if not set
     */
    public String getConditionBlock() {
        return condition;
    }

    /**
     * Sets the condition block script of the rule.
     *
     * @param condition the condition block script
     */
    public void setConditionBlock(String condition) {
        this.condition = condition;
    }

    /**
     * Retrieves the action block script of the rule.
     *
     * @return the action block script, or null if not set
     */
    public String getActionBlock() {
        return action;
    }

    /**
     * Sets the action block script of the rule.
     *
     * @param action the action block script
     */
    public void setActionBlock(String action) {
        this.action = action;
    }

    /**
     * Retrieves the final block script of the rule.
     *
     * @return the final block script, or null if not set
     */
    public String getFinalBlock() {
        return finalBlock;
    }

    /**
     * Sets the final block script of the rule.
     *
     * @param finalBlock the final block script
     */
    public void setFinalBlock(String finalBlock) {
        this.finalBlock = finalBlock;
    }

    /**
     * Retrieves the retry condition script of the rule.
     *
     * @return the retry condition script, or null if not set
     */
    public String getRetryCondition() {
        return retryCondition;
    }

    /**
     * Sets the retry condition script of the rule.
     *
     * @param retryCondition the retry condition script
     */
    public void setRetryCondition(String retryCondition) {
        this.retryCondition = retryCondition;
    }

    /**
     * Retrieves the list of helper functions used by the rule.
     *
     * @return the list of helper functions
     */
    public List<String> getHelpers() {
        return helpers;
    }

    /**
     * Sets the list of helper functions used by the rule.
     *
     * @param helpers the list of helper functions
     */
    public void setHelpers(List<String> helpers) {
        this.helpers = helpers;
    }

    /**
     * Creates a deep copy of the rule, including all its properties and collections.
     *
     * @return a cloned rule instance
     */
    public Rule clone() {
        Rule cloned;
        try {
            cloned = (Rule) super.clone();
        } catch (CloneNotSupportedException e) {
            cloned = new Rule();
        }


        cloned.setName(this.name);
        cloned.setVersion(this.version);
        cloned.setPriority(this.priority);
        cloned.setGroup(this.group);
        cloned.setMaxRetries(this.maxRetries);
        cloned.setRetryDelay(this.retryDelay);
        cloned.setMaxExecutions(this.maxExecutions);
        cloned.setTimeout(this.timeout);
        cloned.setNextRuleOnSuccess(this.nextRuleOnSuccess);
        cloned.setNextRuleOnFailure(this.nextRuleOnFailure);
        cloned.setImports(new ArrayList<>(this.imports));
        cloned.setProduces(new HashMap<>(this.produces));
        cloned.setHelpers(new ArrayList<>(this.helpers));

        Map<String, UseVariable> clonedUses = new HashMap<>();
        for (Map.Entry<String, UseVariable> entry : this.uses.entrySet()) {
            UseVariable uv = entry.getValue();
            clonedUses.put(entry.getKey(),
                    new UseVariable(uv.getVariableName(), uv.getSource(), uv.getSourceId(), uv.getClassName()));
        }
        cloned.setUses(clonedUses);

        cloned.setConditionBlock(this.condition);
        cloned.setActionBlock(this.action);
        cloned.setFinalBlock(this.finalBlock);
        cloned.setRetryCondition(this.retryCondition);
        return cloned;
    }

    /**
     * Returns a string representation of the rule, including all its properties.
     *
     * @return a string representation of the rule
     */
    @Override
    public String toString() {
        return "Rule{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", priority=" + priority +
                ", group='" + group + '\'' +
                ", maxRetries=" + maxRetries +
                ", retryDelay=" + retryDelay +
                ", maxExecutions=" + maxExecutions +
                ", timeout=" + timeout +
                ", nextRuleOnSuccess='" + nextRuleOnSuccess + '\'' +
                ", nextRuleOnFailure='" + nextRuleOnFailure + '\'' +
                ", imports=" + imports +
                ", produces=" + produces +
                ", uses=" + uses +
                ", condition='" + condition + '\'' +
                ", action='" + action + '\'' +
                ", finalBlock='" + finalBlock + '\'' +
                ", retryCondition='" + retryCondition + '\'' +
                '}';
    }

    /**
     * Inner class representing a variable used by the rule, specifying its name, source, and type.
     */
    public static class UseVariable {
        private final String variableName;
        private final String source;
        private final String sourceId;
        private final String className;

        /**
         * Constructs a new {@code UseVariable} with the specified properties.
         *
         * @param variableName the name of the variable
         * @param source       the source of the variable (e.g., "Global" or "RULE")
         * @param sourceId     the identifier of the source
         * @param className    the class name of the variable type
         */
        public UseVariable(String variableName, String source, String sourceId, String className) {
            this.variableName = variableName;
            this.source = source;
            this.sourceId = sourceId;
            this.className = className;
        }

        /**
         * Retrieves the name of the variable.
         *
         * @return the variable name
         */
        public String getVariableName() {
            return variableName;
        }

        /**
         * Retrieves the source of the variable.
         *
         * @return the variable source
         */
        public String getSource() {
            return source;
        }

        /**
         * Retrieves the identifier of the variable source.
         *
         * @return the source identifier
         */
        public String getSourceId() {
            return sourceId;
        }

        /**
         * Retrieves the class name of the variable type.
         *
         * @return the class name
         */
        public String getClassName() {
            return className;
        }

        /**
         * Returns a string representation of the variable.
         *
         * @return a string representation of the variable
         */
        @Override
        public String toString() {
            return "UseVariable{" +
                    "variableName='" + variableName + '\'' +
                    ", source='" + source + '\'' +
                    ", sourceId='" + sourceId + '\'' +
                    ", className='" + className + '\'' +
                    '}';
        }
    }
}