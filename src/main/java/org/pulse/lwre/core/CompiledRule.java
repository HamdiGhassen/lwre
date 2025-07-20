package org.pulse.lwre.core;

import org.codehaus.janino.ScriptEvaluator;
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
 * The {@code CompiledRule} class represents a compiled rule in the Lightweight Rule Engine (LWRE).
 * It encapsulates a {@code Rule} object along with its associated script evaluators for condition,
 * action, final, and retry logic, as well as the context keys used during evaluation. This class
 * serves as a container for the compiled components of a rule, enabling efficient execution within
 * the rule engine.
 *
 * @author Hamdi Ghassen
 */
public class CompiledRule {
    private final Rule rule;
    private final ScriptEvaluator conditionEvaluator;
    private final ScriptEvaluator actionEvaluator;
    private final ScriptEvaluator finalEvaluator;
    private final ScriptEvaluator retryEvaluator;
    private final String[] contextKeys;

    /**
     * Constructs a new {@code CompiledRule} with the specified rule and evaluators.
     *
     * @param rule               the rule object
     * @param conditionEvaluator the evaluator for the rule's condition
     * @param actionEvaluator    the evaluator for the rule's action
     * @param finalEvaluator     the evaluator for the rule's final block
     * @param retryEvaluator     the evaluator for the rule's retry logic
     * @param contextKeys        the context keys used in evaluation
     */
    public CompiledRule(Rule rule, ScriptEvaluator conditionEvaluator,
                        ScriptEvaluator actionEvaluator, ScriptEvaluator finalEvaluator,
                        ScriptEvaluator retryEvaluator, String[] contextKeys) {
        this.rule = rule;
        this.conditionEvaluator = conditionEvaluator;
        this.actionEvaluator = actionEvaluator;
        this.finalEvaluator = finalEvaluator;
        this.retryEvaluator = retryEvaluator;
        this.contextKeys = contextKeys;
    }

    /**
     * Retrieves the rule associated with this compiled rule.
     *
     * @return the rule object
     */
    public Rule getRule() {
        return rule;
    }

    /**
     * Retrieves the script evaluator for the rule's condition.
     *
     * @return the condition evaluator, or null if not set
     */
    public ScriptEvaluator getConditionEvaluator() {
        return conditionEvaluator;
    }

    /**
     * Retrieves the script evaluator for the rule's action.
     *
     * @return the action evaluator, or null if not set
     */
    public ScriptEvaluator getActionEvaluator() {
        return actionEvaluator;
    }

    /**
     * Retrieves the script evaluator for the rule's final block.
     *
     * @return the final evaluator, or null if not set
     */
    public ScriptEvaluator getFinalEvaluator() {
        return finalEvaluator;
    }

    /**
     * Retrieves the script evaluator for the rule's retry logic.
     *
     * @return the retry evaluator, or null if not set
     */
    public ScriptEvaluator getRetryEvaluator() {
        return retryEvaluator;
    }

    /**
     * Retrieves the context keys used during rule evaluation.
     *
     * @return an array of context keys
     */
    public String[] getContextKeys() {
        return contextKeys;
    }
}