package org.pulse.lwre.core;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.SimpleCompiler;
import org.pulse.lwre.utils.CodeFormatter;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code RuleCompiler} class is responsible for compiling rules in the Lightweight Rule Engine (LWRE).
 * It transforms {@code Rule} objects into {@code CompiledRule} objects by generating and compiling Java classes
 * with methods for condition, action, final, and retry logic. The compiler ensures script safety by restricting
 * access to dangerous classes and methods, and it caches compiled classes to improve performance. The class
 * also handles helper blocks, variable declarations, and imports, ensuring proper context setup for rule
 * execution. The implementation is thread-safe, utilizing a {@code ConcurrentHashMap} for caching.
 *
 * @author Hamdi Ghassen
 */
public class RuleCompiler {
    private static final Set<String> FORBIDDEN_CLASSES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "java.lang.Runtime", "java.lang.Process", "java.lang.System",
                    "java.io.File", "java.net.Socket", "java.lang.Thread"
            ))
    );
    private static final Pattern FORBIDDEN_PATTERNS = Pattern.compile(
            "\\b(exec|exit|gc|loadLibrary|setSecurityManager|runFinalizersOnExit)\\b"
    );
    private final Map<String, CompiledRule> compiledRulesCache = new ConcurrentHashMap<>();

    public CompiledRule compileRule(Rule rule, List<String> helperBlocks) throws RuleCompilationException {
        String cacheKey = generateCacheKey(rule, helperBlocks);
        return compiledRulesCache.computeIfAbsent(cacheKey, k -> {
            try {
                validateScriptSafety(rule);
                validateHelperSafety(helperBlocks);
                return doCompileRule(rule, helperBlocks);
            } catch (RuleCompilationException e) {
                throw e;
            } catch (Exception e) {
                throw new RuleCompilationException("Failed to compile rule: " + rule.getName(), e);
            }
        });
    }

    private void validateHelperSafety(List<String> helperBlocks) {
        if (helperBlocks == null) return;
        for (String helper : helperBlocks) {
            validateScriptBlock(helper);
        }
    }

    private void validateScriptSafety(Rule rule) {
        validateScriptBlock(rule.getConditionBlock());
        validateScriptBlock(rule.getActionBlock());
        validateScriptBlock(rule.getFinalBlock());
        validateScriptBlock(rule.getRetryCondition());
    }

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

    private String generateCacheKey(Rule rule, List<String> helperBlocks) {
        return rule.getName() + "_" + rule.getVersion() + "_" +
                Objects.hash(rule.getConditionBlock(), rule.getActionBlock(), rule.getFinalBlock(),
                        rule.getImports(), rule.getProduces(), rule.getUses(), helperBlocks);
    }

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

    private CompiledRule doCompileRule(Rule rule, List<String> helperBlocks) throws RuleCompilationException {
        validateHelperSafety(helperBlocks);
        validateScriptSafety(rule);

        Method conditionMethod = null;
        if (rule.getConditionBlock() != null && !rule.getConditionBlock().trim().isEmpty()) {
            conditionMethod = compileMethod(rule, helperBlocks, rule.getConditionBlock(), boolean.class, "condition", "executeCondition");
        }

        Method actionMethod = null;
        if (rule.getActionBlock() != null && !rule.getActionBlock().trim().isEmpty()) {
            String actionCode = buildActionCode(rule);
            actionMethod = compileMethod(rule, helperBlocks, actionCode, void.class, "action", "executeAction");
        }

        Method finalMethod = null;
        if (rule.getFinalBlock() != null && !rule.getFinalBlock().trim().isEmpty()) {
            finalMethod = compileMethod(rule, helperBlocks, rule.getFinalBlock(), Object.class, "final", "executeFinal");
        }

        Method retryMethod = null;
        if (rule.getRetryCondition() != null && !rule.getRetryCondition().trim().isEmpty()) {
            retryMethod = compileMethod(rule, helperBlocks, rule.getRetryCondition(), boolean.class, "retry condition", "executeRetry");
        }

        return new CompiledRule(rule, conditionMethod, actionMethod, finalMethod, retryMethod,
                rule.getUses().keySet().toArray(new String[0]));
    }

    private Method compileMethod(Rule rule, List<String> helperBlocks, String block, Class<?> returnType, String blockType, String methodName) throws RuleCompilationException {
        String className = "Rule_" + rule.getName() + "_" + blockType.replace(" ", "") + "_" + System.nanoTime();
        StringBuilder code = new StringBuilder();
        code.append("package org.pulse.lwre.core.generated;\n\n");
        for (String imp : rule.getImports()) {
            code.append("import ").append(imp).append(";\n");
        }
        code.append("import java.util.Map;\n\n");
        code.append("public class ").append(className).append(" {\n");
        code.append(combineHelperBlocks(helperBlocks, rule)).append("\n");

        // Declare variables
        Set<String> declaredVars = new HashSet<>();
        for (Map.Entry<String, Rule.UseVariable> entry : rule.getUses().entrySet()) {
            String varName = entry.getKey();
            if (declaredVars.add(varName)) {
                code.append("    private ").append(entry.getValue().getClassName()).append(" ").append(varName)
                        .append(";\n");
            }
        }
        for (String produceVar : getReferencedVariables(block, rule.getProduces().keySet())) {
            if (declaredVars.add(produceVar)) {
                String typeName = resolveType(rule, produceVar);
                code.append("    private ").append(typeName).append(" ").append(produceVar).append(";\n");
            }
        }

        // Method definition
        code.append("    public ").append(returnType.getName()).append(" ").append(methodName)
                .append("(Map context, Throwable error) {\n");
        for (Map.Entry<String, Rule.UseVariable> entry : rule.getUses().entrySet()) {
            String varName = entry.getKey();
            code.append("        ").append(varName).append(" = (").append(entry.getValue().getClassName())
                    .append(") context.get(\"").append(varName).append("\");\n");
        }
        for (String produceVar : getReferencedVariables(block, rule.getProduces().keySet())) {
            String typeName = resolveType(rule, produceVar);
            code.append("        ").append(produceVar).append(" = (").append(typeName)
                    .append(") context.get(\"").append(produceVar).append("\");\n");
        }
        code.append("        ").append(block).append("\n");
        if (returnType != void.class) {
          //  code.append("        return ").append(block.startsWith("return ") ? "" : "null").append(";\n");
        }
        code.append("    }\n");
        code.append("}\n");

        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.setSourceVersion(17);
            compiler.setTargetVersion(17);
            compiler.setDebuggingInformation(false,false,false);
            compiler.setParentClassLoader(Thread.currentThread().getContextClassLoader());
            compiler.cook(new StringReader(code.toString()));
            Class<?> compiledClass = compiler.getClassLoader().loadClass("org.pulse.lwre.core.generated." + className);
            return compiledClass.getMethod(methodName, Map.class, Throwable.class);
        } catch (CompileException e) {
            int lineNumber = e.getLocation() != null ? e.getLocation().getLineNumber() : -1;
            int columnNumber = e.getLocation() != null ? e.getLocation().getColumnNumber() : -1;
            String formattedCode = CodeFormatter.formatJavaCode(code.toString());
            String message = "Compilation error in " + blockType + " block\n" + formattedCode +
                    "\nLine " + lineNumber + ", Column " + columnNumber + ": " + e.getMessage();
            throw new RuleCompilationException(message, e);
        } catch (Exception e) {
            throw new RuleCompilationException("Error compiling " + blockType + " block: " + e.getMessage(), e);
        }
    }

    private String buildActionCode(Rule rule) {
        StringBuilder code = new StringBuilder();
        code.append(rule.getActionBlock()).append("\n");
        for (String produceVar : rule.getProduces().keySet()) {
            code.append("context.put(\"").append(produceVar).append("\", ").append(produceVar).append(");\n");
        }
        return code.toString();
    }

    private String resolveType(Rule rule, String varName) {
        return rule.getProduces().getOrDefault(varName, "Object");
    }

    private Set<String> getReferencedVariables(String codeBlock, Collection<String> candidates) {
        if (codeBlock == null) return Collections.emptySet();

        Set<String> referenced = new HashSet<>();
        for (String var : candidates) {
            Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(var) + "\\b");
            if (varPattern.matcher(codeBlock).find()) {
                referenced.add(var);
            }
        }
        return referenced;
    }

    public static class RuleCompilationException extends RuntimeException {
        public RuleCompilationException(String message, Throwable cause) {
            super(message, cause);
        }

        public RuleCompilationException(String message) {
            super(message);
        }
    }
}