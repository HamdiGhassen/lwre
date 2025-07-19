package org.pulse.lwre.core;

import org.codehaus.janino.*;
import org.codehaus.commons.compiler.CompileException;
import org.pulse.lwre.utils.CodeFormatter;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
 * The {@code RuleCompiler} class is responsible for compiling rules in the Lightweight Rule Engine (LWRE).
 * It transforms {@code Rule} objects into {@code CompiledRule} objects by generating and validating script
 * evaluators for condition, action, final, and retry logic. The compiler ensures script safety by restricting
 * access to dangerous classes and methods, and it caches compiled rules to improve performance. The class
 * also handles helper blocks, variable declarations, and imports, ensuring proper context setup for rule
 * execution. The implementation is thread-safe, utilizing a {@code ConcurrentHashMap} for caching.
 *
 * @author Hamdi Ghassen
 */
public class RuleCompiler {
    private static final Set<String> FORBIDDEN_CLASSES = Collections.unmodifiableSet(
            //TODO MAKE IT CONFIGURABLE
            new HashSet<>(Arrays.asList(
                    "java.lang.Runtime", "java.lang.Process", "java.lang.System",
                    "java.io.File", "java.net.Socket", "java.lang.Thread"
            ))
    );
    private static final Pattern FORBIDDEN_PATTERNS = Pattern.compile(
            "\\b(exec|exit|gc|loadLibrary|setSecurityManager|runFinalizersOnExit)\\b"
    );
    private final Map<String, CompiledRule> compiledRulesCache = new ConcurrentHashMap<>();
    /**
     * Compiles a rule into a {@code CompiledRule} object, caching the result for efficiency.
     *
     * @param rule the rule to compile
     * @param helperBlocks the list of helper function blocks
     * @return the compiled rule
     * @throws RuleCompilationException if compilation fails
     */
    public CompiledRule compileRule(Rule rule, List<String> helperBlocks) throws RuleCompilationException {
        String cacheKey = generateCacheKey(rule, helperBlocks);
        return compiledRulesCache.computeIfAbsent(cacheKey, k -> {
            try {
                validateScriptSafety(rule);
                validateHelperSafety(helperBlocks);
                return doCompileRule(rule, helperBlocks);
            } catch (RuleCompilationException e) {
                throw e; // Re-throw our custom exceptions
            } catch (Exception e) {
                throw new RuleCompilationException("Failed to compile rule: " + rule.getName(), e);
            }
        });
    }
    /**
     * Validates the safety of helper blocks by checking for forbidden classes and methods.
     *
     * @param helperBlocks the list of helper function blocks
     */
    private void validateHelperSafety(List<String> helperBlocks) {
        if (helperBlocks == null) return;
        for (String helper : helperBlocks) {
            validateScriptBlock(helper);
        }
    }
    /**
     * Validates the safety of a rule's script blocks (condition, action, final, and retry).
     *
     * @param rule the rule to validate
     */
    private void validateScriptSafety(Rule rule) {
        validateScriptBlock(rule.getConditionBlock());
        validateScriptBlock(rule.getActionBlock());
        validateScriptBlock(rule.getFinalBlock());
        validateScriptBlock(rule.getRetryCondition());
    }
    /**
     * Validates a single script block for forbidden classes and dangerous method calls.
     *
     * @param code the script block to validate
     */
    private void validateScriptBlock(String code) {
        if (code == null || code.isEmpty()) return;

        for (String forbidden : FORBIDDEN_CLASSES) {
            if (code.contains(forbidden)) {
                throw new SecurityException("Forbidden class reference: " + forbidden);
            }
        }

        if (FORBIDDEN_PATTERNS.matcher(code).find()) {
            throw new SecurityException("Dangerous method call detected");
        }
    }
    /**
     * Generates a unique cache key for a rule based on its properties and helper blocks.
     *
     * @param rule the rule
     * @param helperBlocks the list of helper function blocks
     * @return the cache key
     */
    private String generateCacheKey(Rule rule, List<String> helperBlocks) {
        return rule.getName() + "_" + rule.getVersion() + "_" +
                Objects.hash(rule.getConditionBlock(), rule.getActionBlock(), rule.getFinalBlock(),
                        rule.getImports(), rule.getProduces(), rule.getUses(), helperBlocks);
    }
    /**
     * Combines helper blocks that are referenced in the rule's script blocks.
     *
     * @param helperBlocks the list of helper function blocks
     * @param rule the rule
     * @return the combined helper code
     */
    private String combineHelperBlocks(List<String> helperBlocks, Rule rule) {
        if (helperBlocks == null || helperBlocks.isEmpty()) return "";
        Set<String> usedMethods = new HashSet<>();
        Stream.of(rule.getConditionBlock(), rule.getActionBlock(), rule.getFinalBlock(), rule.getRetryCondition())
                .filter(Objects::nonNull)
                .forEach(block -> {
                    Pattern methodPattern = Pattern.compile("\\b(\\w+)\\s*\\(");
                    Matcher matcher = methodPattern.matcher(block);
                    while (matcher.find()) {
                        usedMethods.add(matcher.group(1));
                    }
                });

        helperBlocks = helperBlocks.stream().distinct().collect(Collectors.toList());
        StringBuilder combined = new StringBuilder();
        for (String helper : helperBlocks) {
            String trimmed = helper.trim();
            if (!trimmed.isEmpty()) {
                boolean include = usedMethods.stream().anyMatch(method -> helper.contains(method + "("));
                if (include) {
                    String adjustedHelper = CodeFormatter.adjustHelperMethodAccess(trimmed);
                    combined.append(adjustedHelper).append("\n");
                }
            }
        }
        return combined.toString();
    }
    /**
     * Performs the actual compilation of a rule into a {@code CompiledRule} object.
     *
     * @param rule the rule to compile
     * @param helperBlocks the list of helper function blocks
     * @return the compiled rule
     * @throws RuleCompilationException if compilation fails
     */
    private CompiledRule doCompileRule(Rule rule, List<String> helperBlocks) throws RuleCompilationException {

        validateHelperSafety(helperBlocks);
        validateScriptSafety(rule);

        ScriptEvaluator conditionEvaluator = null;
        if (rule.getConditionBlock() != null && !rule.getConditionBlock().trim().isEmpty()) {
            String conditionCode = rule.getConditionBlock();
            try {
                conditionEvaluator = compileEvaluator(rule, helperBlocks, conditionCode, boolean.class, "condition");
            } catch (RuleCompilationException e) {
                throw new RuleCompilationException("Error in condition block of rule '" + rule.getName() + "': " + e.getMessage(), e);
            }
        }

        ScriptEvaluator actionEvaluator = null;
        if (rule.getActionBlock() != null && !rule.getActionBlock().trim().isEmpty()) {
            String actionCode = buildActionCode(rule);
            try {
                actionEvaluator = compileEvaluator(rule, helperBlocks, actionCode, void.class, "action");
            } catch (RuleCompilationException e) {
                throw new RuleCompilationException("Error in action block of rule '" + rule.getName() + "': " + e.getMessage(), e);
            }
        }

        ScriptEvaluator finalEvaluator = null;
        if (rule.getFinalBlock() != null && !rule.getFinalBlock().trim().isEmpty()) {
            try {
                finalEvaluator = compileEvaluator(rule, helperBlocks, rule.getFinalBlock(), Object.class, "final");
            } catch (RuleCompilationException e) {
                throw new RuleCompilationException("Error in final block of rule '" + rule.getName() + "': " + e.getMessage(), e);
            }
        }

        ScriptEvaluator retryEvaluator = null;
        if (rule.getRetryCondition() != null && !rule.getRetryCondition().trim().isEmpty()) {
            try {
                retryEvaluator = compileEvaluator(rule, helperBlocks, rule.getRetryCondition(), boolean.class, "retry condition");
            } catch (RuleCompilationException e) {
                throw new RuleCompilationException("Error in retry condition block of rule '" + rule.getName() + "': " + e.getMessage(), e);
            }
        }

        return new CompiledRule(rule, conditionEvaluator, actionEvaluator, finalEvaluator, retryEvaluator,
                rule.getUses().keySet().toArray(new String[0]));
    }
    /**
     * Compiles a script evaluator for a specific block of the rule.
     *
     * @param rule the rule
     * @param helperBlocks the list of helper function blocks
     * @param block the script block to compile
     * @param returnType the return type of the evaluator
     * @param blockType the type of block being compiled (for error messages)
     * @return the compiled script evaluator
     * @throws RuleCompilationException if compilation fails
     */
    private ScriptEvaluator compileEvaluator(Rule rule, List<String> helperBlocks, String block, Class<?> returnType, String blockType) throws RuleCompilationException {
        StringBuilder code = buildCodeHeader(rule, helperBlocks, block);

        code.append(block).append("\n").append("\n");

        ScriptEvaluator evaluator = new ScriptEvaluator();
        evaluator.setParameters(new String[]{"context", "error"}, new Class[]{Map.class, Throwable.class});
        evaluator.setReturnType(returnType);
        try {
            evaluator.cook(new StringReader(code.toString()));
        } catch (CompileException e) {
            // Enhance Janino errors with exact line numbers
            int lineNumber = e.getLocation() != null ? e.getLocation().getLineNumber()  : -1;
            int columnNumber = e.getLocation() != null ? e.getLocation().getColumnNumber() : -1;
            String s = CodeFormatter.formatJavaCode(code.toString());
            String message = "Compilation error in " + blockType + " block";

            message+=s;
            message += ": " + e.getMessage();

            throw new RuleCompilationException(message, e);
        } catch (Exception e) {
            throw new RuleCompilationException("Error compiling " + blockType + " block: " + e.getMessage(), e);
        }
        return evaluator;
    }
    /**
     * Builds the action code by appending context updates for produced variables.
     *
     * @param rule the rule
     * @return the action code
     */
    private String buildActionCode(Rule rule) {
        StringBuilder code = new StringBuilder();
        code.append(rule.getActionBlock()).append("\n");
        for (String produceVar : rule.getProduces().keySet()) {
            code.append("context.put(\"").append(produceVar).append("\", ").append(produceVar).append(");\n");
        }
        return code.toString();
    }
    /**
     * Builds the code header for a script evaluator, including imports, helpers, and variable declarations.
     *
     * @param rule the rule
     * @param helperBlocks the list of helper function blocks
     * @param block the script block
     * @return the code header
     */
    private StringBuilder buildCodeHeader(Rule rule, List<String> helperBlocks, String block) {
        StringBuilder code = new StringBuilder();
        for (String imp : rule.getImports()) {
            code.append("import ").append(imp).append(";\n");
        }
        String helperCode = combineHelperBlocks(helperBlocks, rule);
        code.append(helperCode).append("\n");
        Set<String> declaredVars = new HashSet<>();
        for (Map.Entry<String, Rule.UseVariable> entry : rule.getUses().entrySet()) {
            String varName = entry.getKey();
            if (declaredVars.add(varName)) {
                code.append(entry.getValue().getClassName()).append(" ").append(varName)
                        .append(" = (").append(entry.getValue().getClassName()).append(") context.get(\"").append(varName).append("\");\n");
            }
        }
        for (String produceVar : getReferencedVariables(block, rule.getProduces().keySet())) {
            if (declaredVars.add(produceVar)) {
                String typeName = resolveType(rule, produceVar);
                code.append(typeName).append(" ").append(produceVar)
                        .append(" = (").append(typeName).append(") context.get(\"").append(produceVar).append("\");\n");
            }
        }
        return code;
    }
    /**
     * Resolves the type of a variable from the rule's produces map.
     *
     * @param rule the rule
     * @param varName the variable name
     * @return the variable type
     */
    private String resolveType(Rule rule, String varName) {
        return rule.getProduces().getOrDefault(varName, "Object");
    }
    /**
     * Identifies variables referenced in a script block that match the provided candidates.
     *
     * @param codeBlock the script block
     * @param candidates the candidate variable names
     * @return the set of referenced variables
     */
    private Set<String> getReferencedVariables(String codeBlock, Collection<String> candidates) {
        if (codeBlock == null) return Collections.emptySet();

        Set<String> referenced = new HashSet<>();
        for (String var : candidates) {
            // Use word boundaries to avoid partial matches
            Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(var) + "\\b");
            if (varPattern.matcher(codeBlock).find()) {
                referenced.add(var);
            }
        }
        return referenced;
    }
    /**
     * Exception thrown when rule compilation fails.
     */
    public static class RuleCompilationException extends RuntimeException {
        /**
         * Constructs a new {@code RuleCompilationException} with the specified message and cause.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public RuleCompilationException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new {@code RuleCompilationException} with the specified message.
         *
         * @param message the error message
         */
        public RuleCompilationException(String message) {
            super(message);
        }
    }
}