package org.pulse.lwre.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
 * The {@code RuleExecutionContext} class manages the execution context for rules in the Lightweight Rule Engine (LWRE).
 * It maintains global variables and rule-specific execution states in a thread-safe manner using concurrent data structures.
 * The context is identified by a unique execution ID and provides methods to manage rule states and global variables.
 * The inner {@code RuleExecutionState} class tracks execution metrics such as execution count, retry count, and last error
 * for individual rules, enabling retry logic and execution tracking.
 *
 * @author Hamdi Ghassen
 */
public class RuleExecutionContext {
    public final Map<String, Object> executionGlobals = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RuleExecutionState> ruleStates = new ConcurrentHashMap<>();
    private final String executionId;

    /**
     * Constructs a new {@code RuleExecutionContext} with the specified execution ID.
     *
     * @param executionId the unique identifier for this execution context
     */
    public RuleExecutionContext(String executionId) {
        this.executionId = executionId;
    }

    /**
     * Retrieves or creates the execution state for a specific rule.
     *
     * @param ruleName the name of the rule
     * @return the rule's execution state
     */
    public RuleExecutionState getState(String ruleName) {
        if (ruleStates.get(ruleName) == null) {
            ruleStates.putIfAbsent(ruleName, new RuleExecutionState());
        }
        return ruleStates.get(ruleName);
    }

    /**
     * Resets the execution state for a specific rule.
     *
     * @param ruleName the name of the rule
     */
    public void resetState(String ruleName) {
        ruleStates.remove(ruleName);
    }
    public void reset() {
        for (RuleExecutionState state : ruleStates.values()) {
            state.retryCount = 0;
            state.executionCount = 0;
            state.setFailed(false);
            state.setCompleted(false);
            state.setLastError(null);
            state.setNextExecutionTime(0); // Reset to 0 or Long.MAX_VALUE
        }
        executionGlobals.clear();
    }
    /**
     * Sets a global variable in the execution context.
     *
     * @param key   the variable key
     * @param value the variable value
     */
    public void setGlobal(String key, Object value) {
        executionGlobals.put(key, value);
    }

    /**
     * Retrieves a global variable from the execution context.
     *
     * @param key the variable key
     * @return the variable value, or null if not found
     */
    public Object getGlobal(String key) {
        return executionGlobals.get(key);
    }

    /**
     * Retrieves the unique execution ID for this context.
     *
     * @return the execution ID
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * Inner class representing the execution state of a rule, tracking execution count, retries, and errors.
     */
    public static class RuleExecutionState {
        private int executionCount = 0;
        private int retryCount = 0;
        private long lastExecutionTime = 0;
        private Throwable lastError;
        private long nextExecutionTime = 0;

        private boolean completed = false;
        private boolean failed = false;

        /**
         * Retrieves the number of times the rule has been executed.
         *
         * @return the execution count
         */
        public int getExecutionCount() {
            return executionCount;
        }

        /**
         * Increments the execution count for the rule.
         */
        public void incrementExecutionCount() {
            executionCount++;
        }

        /**
         * Retrieves the number of retry attempts for the rule.
         *
         * @return the retry count
         */
        public int getRetryCount() {
            return retryCount;
        }

        /**
         * Increments the retry count for the rule.
         */
        public void incrementRetryCount() {
            retryCount++;
        }

        /**
         * Resets the retry count for the rule to zero.
         */
        public void resetRetryCount() {
            retryCount = 0;
        }

        /**
         * Retrieves the timestamp of the last execution attempt.
         *
         * @return the last execution time in milliseconds
         */
        public long getLastExecutionTime() {
            return lastExecutionTime;
        }

        /**
         * Sets the timestamp of the last execution attempt.
         *
         * @param time the execution time in milliseconds
         */
        public void setLastExecutionTime(long time) {
            lastExecutionTime = time;
        }

        /**
         * Retrieves the last error encountered during rule execution.
         *
         * @return the last error, or null if none
         */
        public Throwable getLastError() {
            return lastError;
        }

        /**
         * Sets the last error encountered during rule execution.
         *
         * @param error the error to set
         */
        public void setLastError(Throwable error) {
            lastError = error;
        }

        /**
         * Gets the next execution time of the processed rule
         * @return the next execution time accounting the delay of the next execution
         */
        public long getNextExecutionTime() { return nextExecutionTime; }

        /**
         * Sets the next execution time for the processed rule
         * @param time the next time when the rule will be fired
         */
        public void setNextExecutionTime(long time) { nextExecutionTime = time; }

        /**
         * Checks the rule execution completion
         * @return true if the rule execution completed , false otherwise
         */
        public boolean isCompleted() { return completed; }

        /**
         * Sets the rule execution completion
         * @param completed flag to set rule execution completion
         */
        public void setCompleted(boolean completed) { this.completed = completed; }
        /**
         * Checks the rule execution failure
         * @return true if the rule execution failed , false otherwise
         */
        public boolean isFailed() { return failed; }
        /**
         * Sets the rule execution failure
         * @param failed flag to set rule execution completion
         */
        public void setFailed(boolean failed) { this.failed = failed; }
        /**
         * Determines if the rule should be retried based on its retry count and delay.
         *
         * @param rule the rule to check
         * @return true if a retry is allowed, false otherwise
         */
        public boolean shouldRetry(Rule rule) {
            if (retryCount >= rule.getMaxRetries()) return false;
            long timeSinceLast = System.currentTimeMillis() - lastExecutionTime;
            return timeSinceLast >= rule.getRetryDelay();
        }

    }
}