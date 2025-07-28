package org.pulse.lwre.core;

import java.lang.reflect.Method;

/**
 * The {@code CompiledRule} class represents a compiled rule in the Lightweight Rule Engine (LWRE).
 * It encapsulates a {@code Rule} object along with its associated method references for condition,
 * action, final, and retry logic, as well as the context keys used during evaluation. This class
 * serves as a container for the compiled components of a rule, enabling efficient execution within
 * the rule engine.
 *
 * @author Hamdi Ghassen
 */
public class CompiledRule {
    private final Rule rule;
    private final Method conditionMethod;
    private final Method actionMethod;
    private final Method finalMethod;
    private final Method retryMethod;
    private final String[] contextKeys;

    /**
     * Constructs a new {@code CompiledRule} with the specified rule and method references.
     *
     * @param rule            the rule object
     * @param conditionMethod the method for the rule's condition
     * @param actionMethod    the method for the rule's action
     * @param finalMethod     the method for the rule's final block
     * @param retryMethod     the method for the rule's retry logic
     * @param contextKeys     the context keys used in evaluation
     */
    public CompiledRule(Rule rule, Method conditionMethod, Method actionMethod, Method finalMethod,
                       Method retryMethod, String[] contextKeys) {
        this.rule = rule;
        this.conditionMethod = conditionMethod;
        this.actionMethod = actionMethod;
        this.finalMethod = finalMethod;
        this.retryMethod = retryMethod;
        this.contextKeys = contextKeys;
    }

    public Rule getRule() {
        return rule;
    }

    public Method getConditionMethod() {
        return conditionMethod;
    }

    public Method getActionMethod() {
        return actionMethod;
    }

    public Method getFinalMethod() {
        return finalMethod;
    }

    public Method getRetryMethod() {
        return retryMethod;
    }

    public String[] getContextKeys() {
        return contextKeys;
    }
}