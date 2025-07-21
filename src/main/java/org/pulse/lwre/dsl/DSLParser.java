package org.pulse.lwre.dsl;

import org.pulse.lwre.core.Rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * The {@code DSLParser} class is responsible for parsing a domain-specific language (DSL) string into a set of {@code Rule}
 * objects and helper blocks for the Lightweight Rule Engine (LWRE). It processes DSL content to extract rule configurations,
 * including rule names, priorities, groups, imports, variables, script blocks, retry policies, and control flow directives.
 * The parser uses regular expressions to validate and extract directives, ensuring accurate rule creation and helper block
 * collection. The {@code ParseResult} inner class encapsulates the parsed rules and helpers for further processing.
 *
 * @author Hamdi Ghassen
 */
public class DSLParser {

    private static final Pattern USE_PATTERN =
            Pattern.compile("(\\w+)\\s*:\\s*([\\w.<>,\\s]+)\\s+as\\s+(\\w+)\\s+FROM\\s+(RULE\\s+\\w+|Global)");
    private static final Pattern RETRY_PATTERN =
            Pattern.compile("#RETRY\\s+(\\d+)(?:\\s+DELAY\\s+(\\d+))?(?:\\s+IF\\s+\\{(.+?)\\})?");
    private static final Pattern NEXT_RULE_PATTERN =
            Pattern.compile("#NEXT_ON_(SUCCESS|FAILURE)\\s+(\\w+)");
    private static final Pattern TIMEOUT_PATTERN =
            Pattern.compile("#TIMEOUT\\s+(\\d+)\\s*(ms|s)");
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("#VERSION\\s+(\\d+\\.\\d+\\.\\d+)");
    private static final Pattern EXECUTION_LIMIT_PATTERN =
            Pattern.compile("#MAX_EXECUTIONS\\s+(\\d+)");
    /**
     * Parses a DSL content string into a set of rules and helper blocks.
     *
     * @param dslContent the DSL content to parse
     * @return a {@code ParseResult} containing the parsed rules and helpers
     * @throws DSLException if the DSL content is null or empty
     */
    public static ParseResult parseRules(String dslContent) throws DSLException {
        if (dslContent == null) {
            throw new DSLException("DSL is null");
        }
        if (dslContent.trim().isEmpty()) {
            throw new DSLException("DSL is empty");
        }
        List<Rule> rules = new ArrayList<>();
        List<String> helpers = new ArrayList<>();
        Map<String,String> globals = new HashMap<>();
        String[] sections = dslContent.split("(?=#(RULE|HELPER|GLOBAL))");
        StringBuilder currentHelper = new StringBuilder();
        StringBuilder currentGlobal = new StringBuilder();
        boolean inHelperBlock = false;
        boolean inGlobalBlock = false;
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty() || section.startsWith("//")) continue;
            if (section.startsWith("#GLOBAL")) {
                inGlobalBlock = true;
                String globalContent = section.substring(7).trim();
                currentGlobal.append(globalContent).append("\n");
            }
            if (section.startsWith("#HELPER")) {
                inHelperBlock = true;
                // Remove #HELPER directive and keep the content
                String helperContent = section.substring(7).trim();
                currentHelper.append(helperContent).append("\n");
            } else if (section.startsWith("#RULE")) {
                // If we were in a helper block, finish it before processing rule
                if (inHelperBlock) {
                    helpers.add(currentHelper.toString());
                    currentHelper = new StringBuilder();
                    inHelperBlock = false;
                }
                if (inGlobalBlock) {
                    fillGlobals(currentGlobal.toString(),globals);
                    currentGlobal = new StringBuilder();
                    inGlobalBlock = false;
                }
                Rule rule = parseRule(section);
                rules.add(rule);
            } else if (inHelperBlock) {
                currentHelper.append(section).append("\n");
            } else if (inGlobalBlock) {
            }
        }

        if (inHelperBlock && !helpers.contains(currentHelper.toString())) {

            helpers.add(currentHelper.toString());
        }
        ParseResult parseResult = new ParseResult(rules, globals, helpers);
        validateParseResults(parseResult);
        return parseResult;
    }

    private static void validateParseResults(ParseResult parseResult) throws DSLException {
        List<Rule> rules = parseResult.getRules();

        for (Rule rule : rules) {
            if (rules.stream().filter(r-> r.getName().equals(rule.getName()) && r.getGroup().equals(rule.getGroup())).count() != 1) {
                throw new DSLException("Multiple rules with same name "+ rule.getName()+" on the same group "+ rule.getGroup());
            }
            for (Rule.UseVariable variable : rule.getUses().values()) {
                if ("Global".equals(variable.getSource())) {
                   if (! parseResult.global.containsKey(variable.getVariableName())) {
                       throw new DSLException("RULE "+ rule.getName()+" uses an undefined Global variable : "+variable.getVariableName());

                   }
                }
                if ("RULE".equals(variable.getSource())) {
                  for (Rule rule1 : rules) {
                      long count = rules.stream().filter(r ->
                          rule.getGroup().equals(r.getGroup()) && r.getName().equals(variable.getSourceId())
                      ).count();
                      if (count == 0) {
                          throw new DSLException("RULE "+ rule.getName()+" uses an undefined variable : \""+variable.getVariableName()+"\" , rule \""+variable.getSourceId()+"\" does not exist in the group \""+rule.getGroup()+"\"");

                      }
                      if (rule1.getName().equals(variable.getSourceId()) && rule1.getGroup().equals(rule.getGroup())) {
                          if (!rule1.getProduces().containsKey(variable.getVariableName())) {
                             throw new DSLException("rule \""+ rule.getName()+"\" uses an undefined variable : \""+variable.getVariableName()+"\" from \"" + rule1.getName()+"\"");
                         }
                      }
                  }
                }

             }
        }
    }
    /**
     * Parse global variables
     * @param globalContent the content of the section #GLOBAL
     * @param globals parsed global variables , including its name and Type
     * @throws DSLException throws an exception when the #GLOBAL block is declared in the dsl but it's empty or the format expected for the global variable isn't <name> : <type>
     */
    private static void fillGlobals(String globalContent,Map<String,String> globals) throws DSLException {
        for (String global : globalContent.split("\n")) {
            String trimmed = global.trim();
            if (trimmed.isEmpty()) {
                throw new DSLException("GLOBAL block cannot be empty");
            }
            String[] parts = trimmed.split("\\s*:\\s*");
            if (parts.length != 2) {
                throw new DSLException("Invalid GLOBAL format. Expected: '<name> : <Type>'");
            }
           globals.put(parts[0].trim(), parts[1].trim());
        }
    }
    /**
     * Parses a single rule from a DSL section.
     *
     * @param ruleContent the DSL section containing the rule
     * @return the parsed {@code Rule} object
     */
    public static Rule parseRule(String ruleContent) throws DSLException {
        Rule rule = new Rule();
        String[] lines = ruleContent.split("\\n");
        StringBuilder currentBlock = new StringBuilder();
        String currentBlockType = null;
        boolean hasRuleDirective = false;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("//")) continue;

            if (line.startsWith("#RULE")) {
                flushBlock(rule, currentBlockType, currentBlock);
                String ruleName = line.substring(5).trim();
                if (ruleName.isEmpty()) {
                    throw new DSLException("Rule name cannot be empty");
                }
                rule.setName(ruleName);
                hasRuleDirective = true;
            } else if (line.startsWith("#PRIORITY")) {
                flushBlock(rule, currentBlockType, currentBlock);
                try {
                    rule.setPriority(Integer.parseInt(line.substring(9).trim()));
                } catch (NumberFormatException e) {
                    throw new DSLException("Invalid priority value: " + line.substring(9).trim());
                }
            } else if (line.startsWith("#GROUP")) {
                flushBlock(rule, currentBlockType, currentBlock);
                rule.setGroup(line.substring(6).trim());
            } else if (line.startsWith("#IMPORT")) {
                flushBlock(rule, currentBlockType, currentBlock);
                currentBlockType = "IMPORT";
                currentBlock = new StringBuilder();
            } else if (line.startsWith("#PRODUCE")) {
                flushBlock(rule, currentBlockType, currentBlock);
                currentBlockType = "PRODUCE";
                currentBlock = new StringBuilder();
            } else if (line.startsWith("#USE")) {
                flushBlock(rule, currentBlockType, currentBlock);
                currentBlockType = "USE";
                currentBlock = new StringBuilder();
            } else if (line.startsWith("#CONDITION")) {
                flushBlock(rule, currentBlockType, currentBlock);
                currentBlockType = "CONDITION";
                currentBlock = new StringBuilder();
            } else if (line.startsWith("#ACTION")) {
                flushBlock(rule, currentBlockType, currentBlock);
                currentBlockType = "ACTION";
                currentBlock = new StringBuilder();
            } else if (line.startsWith("#FINAL")) {
                flushBlock(rule, currentBlockType, currentBlock);
                currentBlockType = "FINAL";
                currentBlock = new StringBuilder();
            } else if (line.startsWith("#RETRY")) {
                flushBlock(rule, currentBlockType, currentBlock);
                parseRetryDirective(rule, line);
            } else if (line.startsWith("#NEXT_ON")) {
                flushBlock(rule, currentBlockType, currentBlock);
                parseNextDirective(rule, line);
            } else if (line.startsWith("#TIMEOUT")) {
                flushBlock(rule, currentBlockType, currentBlock);
                parseTimeoutDirective(rule, line);
            } else if (line.startsWith("#VERSION")) {
                flushBlock(rule, currentBlockType, currentBlock);
                parseVersionDirective(rule, line);
            } else if (line.startsWith("#MAX_EXECUTIONS")) {
                flushBlock(rule, currentBlockType, currentBlock);
                parseExecutionLimit(rule, line);
            } else if (!line.startsWith("#")) {
                if (currentBlock.length() > 0) currentBlock.append("\n");
                currentBlock.append(line);
            } else if (line.startsWith("#HELPER")) {
                flushBlock(rule, currentBlockType, currentBlock);
                currentBlockType = "HELPER";
                currentBlock = new StringBuilder();
            }
        }
        flushBlock(rule, currentBlockType, currentBlock);

        // Validation checks
        if (!hasRuleDirective) {
            throw new DSLException("Rule must have a #RULE directive");
        }
        if (rule.getName() == null || rule.getName().isEmpty()) {
            throw new DSLException("Rule name cannot be empty");
        }
        if (rule.getConditionBlock() == null && rule.getActionBlock() == null) {
            throw new DSLException("Rule must have at least one of #CONDITION or #ACTION blocks");
        }

        return rule;
    }
    /**
     * Flushes the current block content into the appropriate rule field based on the block type.
     *
     * @param rule the rule to update
     * @param blockType the type of the current block (e.g., IMPORT, CONDITION)
     * @param block the content of the current block
     */
    private static void flushBlock(Rule rule, String blockType, StringBuilder block) throws DSLException {
        if (blockType == null || block == null) return;

        String content = block.toString().trim();
        if (content.isEmpty()) {
            if ("IMPORT".equals(blockType)) {
                throw new DSLException("IMPORT block cannot be empty");
            }
            if ("PRODUCE".equals(blockType)) {
                throw new DSLException("PRODUCE block cannot be empty");
            }
            if ("USE".equals(blockType)) {
                throw new DSLException("USE block cannot be empty");
            }
            if ("CONDITION".equals(blockType)) {
                throw new DSLException("CONDITION block cannot be empty");
            }
            if ("ACTION".equals(blockType)) {
                throw new DSLException("ACTION block cannot be empty");
            }
            if ("FINAL".equals(blockType)) {
                throw new DSLException("FINAL block cannot be empty");
            }
            return;
        }

        switch (blockType) {
            case "IMPORT":
                for (String imp : content.split("\n")) {
                    String trimmed = imp.trim();
                    if (!trimmed.isEmpty()) {
                        rule.getImports().add(trimmed);
                    }
                }
                break;

            case "PRODUCE":
                for (String produce : content.split("\n")) {
                    String trimmed = produce.trim();
                    if (trimmed.isEmpty()) {
                        throw new DSLException("PRODUCE statement cannot be empty");
                    }
                    String[] parts = trimmed.split("\\s*:\\s*");
                    if (parts.length != 2) {
                        throw new DSLException("Invalid PRODUCE format. Expected: '<type> as <alias>'");
                    }
                    rule.getProduces().put(parts[0].trim(), parts[1].trim());
                }
                break;

            case "USE":
                for (String use : content.split("\n")) {
                    if (!use.trim().isEmpty()) {
                        parseUseVariable(rule, use.trim());
                    }
                }
                break;

            case "CONDITION":
                if (rule.getConditionBlock() != null) {
                    throw  new DSLException("Rule does not allow multiple condition blocks");
                }
                rule.setConditionBlock(content);
                break;

            case "ACTION":
                if (rule.getActionBlock() != null) {
                    throw  new DSLException("Rule does not allow multiple action blocks");
                }
                rule.setActionBlock(content);
                break;

            case "FINAL":
                if (rule.getFinalBlock() != null) {
                    throw  new DSLException("Rule does not allow multiple final blocks");
                }
                rule.setFinalBlock(content);
                break;
        }
    }
    /**
     * Parses a USE directive to extract variable usage information and add it to the rule.
     *
     * @param rule the rule to update
     * @param stmt the USE directive statement
     */
    private static void parseUseVariable(Rule rule, String stmt) throws DSLException {
        Matcher m = USE_PATTERN.matcher(stmt);
        if (m.matches()) {
            String local = m.group(3).trim();
            String type = m.group(2).trim();
            String var = m.group(1).trim();
            String src = m.group(4).trim();

            if ("Global".equals(src)) {
                rule.getUses().put(local,
                        new Rule.UseVariable(var, "Global", "Global", type));
            } else if (src.startsWith("RULE")) {
                String ruleName = src.substring(4).trim();
                rule.getUses().put(local,
                        new Rule.UseVariable(var, "RULE", ruleName, type));
            } else {
                throw new DSLException("Invalid source in USE directive: " + src);
            }
        } else {
            throw new DSLException("Invalid USE format. Expected: '<variable> : <type> as <alias> FROM <RULE <name>|Global>'");
        }
    }
    /**
     * Parses a RETRY directive to set retry policies for the rule.
     *
     * @param rule the rule to update
     * @param line the RETRY directive line
     */
    private static void parseRetryDirective(Rule rule, String line) throws DSLException {
        Matcher m = RETRY_PATTERN.matcher(line);
        if (m.find()) {
            try {
                rule.setMaxRetries(Integer.parseInt(m.group(1)));
                if (m.group(2) != null) {
                    rule.setRetryDelay(Long.parseLong(m.group(2)));
                }
                if (m.group(3) != null) {
                    rule.setRetryCondition(m.group(3).trim());
                }
            } catch (NumberFormatException e) {
                throw new DSLException("Invalid number in RETRY directive");
            }
        } else {
            throw new DSLException("Invalid RETRY format. Expected: '#RETRY <maxAttempts> [DELAY <delay>] [IF {condition}]'");
        }
    }
    /**
     * Parses a NEXT_ON_SUCCESS or NEXT_ON_FAILURE directive to set control flow for the rule.
     *
     * @param rule the rule to update
     * @param line the NEXT directive line
     */
    private static void parseNextDirective(Rule rule, String line) throws DSLException {
        Matcher m = NEXT_RULE_PATTERN.matcher(line);
        if (m.find()) {
            String condition = m.group(1);
            String targetRule = m.group(2);

            if ("SUCCESS".equals(condition)) {
                rule.setNextRuleOnSuccess(targetRule);
            } else if ("FAILURE".equals(condition)) {
                rule.setNextRuleOnFailure(targetRule);
            }
        } else {
            throw new DSLException("Invalid NEXT_ON format. Expected: '#NEXT_ON_SUCCESS <ruleName>' or '#NEXT_ON_FAILURE <ruleName>'");
        }
    }
    /**
     * Parses a TIMEOUT directive to set the execution timeout for the rule.
     *
     * @param rule the rule to update
     * @param line the TIMEOUT directive line
     */
    private static void parseTimeoutDirective(Rule rule, String line) throws DSLException {
        Matcher m = TIMEOUT_PATTERN.matcher(line);
        if (m.find()) {
            try {
                long value = Long.parseLong(m.group(1));
                String unit = m.group(2);
                if ("s".equals(unit)) {
                    rule.setTimeout(value * 1000);
                } else if ("ms".equals(unit)) {
                    rule.setTimeout(value);
                } else {
                    throw new DSLException("Invalid time unit in TIMEOUT. Use 'ms' or 's'");
                }
            } catch (NumberFormatException e) {
                throw new DSLException("Invalid number in TIMEOUT directive");
            }
        } else {
            throw new DSLException("Invalid TIMEOUT format. Expected: '#TIMEOUT <value> <ms|s>'");
        }
    }
    /**
     * Parses a VERSION directive to set the version of the rule.
     *
     * @param rule the rule to update
     * @param line the VERSION directive line
     */
    private static void parseVersionDirective(Rule rule, String line) throws DSLException {
        Matcher m = VERSION_PATTERN.matcher(line);
        if (m.find()) {
            rule.setVersion(m.group(1));
        } else {
            throw new DSLException("Invalid VERSION format. Expected: '#VERSION <major.minor.patch>'");
        }
    }
    /**
     * Parses a MAX_EXECUTIONS directive to set the maximum execution limit for the rule.
     *
     * @param rule the rule to update
     * @param line the MAX_EXECUTIONS directive line
     */
    private static void parseExecutionLimit(Rule rule, String line) throws DSLException {
        Matcher m = EXECUTION_LIMIT_PATTERN.matcher(line);
        if (m.find()) {
            try {
                rule.setMaxExecutions(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException e) {
                throw new DSLException("Invalid number in MAX_EXECUTIONS directive");
            }
        } else {
            throw new DSLException("Invalid MAX_EXECUTIONS format. Expected: '#MAX_EXECUTIONS <value>'");
        }
    }
    /**
     * Inner class representing the result of parsing DSL content, containing rules and helper blocks.
     */
    public static class ParseResult {
        private final Map<String,String> global;
        private final List<Rule> rules;
        private final List<String> helpers;
        /**
         * Constructs a new {@code ParseResult} with the specified rules and helpers.
         *
         * @param rules the list of parsed rules
         * @param globals the list of global parameters required by the DSL
         * @param helpers the list of helper blocks
         *
         */
        public ParseResult(List<Rule> rules,Map<String,String> globals, List<String> helpers) {
            this.rules = rules;
            this.helpers = helpers;
            this.global = globals;
        }
        /**
         * Retrieves the list of parsed rules.
         *
         * @return the list of rules
         */
        public List<Rule> getRules() {
            return rules;
        }
        /**
         * Retrieves the list of helper blocks.
         *
         * @return the list of helper blocks
         */
        public List<String> getHelpers() {
            return helpers;
        }
        /**
         * Retrieves the global variables from Global blocks.
         *
         * @return the map of global variables
         */
        public Map<String,String> getGlobals() {
            return global;
        }
    }
}