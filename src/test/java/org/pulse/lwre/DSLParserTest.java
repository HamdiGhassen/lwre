package org.pulse.lwre;

import org.junit.Test;
import org.pulse.lwre.core.Rule;
import org.pulse.lwre.core.Rule.UseVariable;
import org.pulse.lwre.dsl.DSLException;
import org.pulse.lwre.dsl.DSLParser;
import static org.pulse.lwre.dsl.DSLParser.*;
import java.util.List;

import static org.junit.Assert.*;

public class DSLParserTest {

    @Test
    public void testParseSimpleRule() throws DSLException {
        String dsl = "#RULE SimpleRule\n" +
                     "#GROUP TestGroup\n" +
                     "#PRIORITY 5\n" +
                     "#PRODUCE\n" +
                     " result : Integer\n" +
                     "#ACTION\n" +
                     " result = 42;\n" +
                     "#FINAL\n" +
                     " return result;\n";
        ParseResult parseResult = DSLParser.parseRules(dsl);
        List<Rule> rules = parseResult.getRules();
        assertEquals(1, rules.size());
        Rule rule = rules.get(0);
        assertEquals("SimpleRule", rule.getName());
        assertEquals("TestGroup", rule.getGroup());
        assertEquals(5, rule.getPriority());
        assertNull(rule.getConditionBlock());
        assertEquals("result = 42;", rule.getActionBlock().trim());
        assertEquals("return result;", rule.getFinalBlock().trim());
        assertEquals(1, rule.getProduces().size());
        assertEquals("Integer", rule.getProduces().get("result"));
    }

    @Test
    public void testParseRuleWithDependency() throws DSLException {
        String dsl = "#RULE Rule1\n" +
                     "#PRODUCE\n" +
                     " result1 : Integer\n" +
                     "#ACTION\n" +
                     " result1 = 21;\n" +
                     "#RULE Rule2\n" +
                     "#USE\n" +
                     " result1 : Integer as input FROM RULE Rule1\n" +
                     "#ACTION\n" +
                     " int output = input * 2;\n";
        ParseResult parseResult = DSLParser.parseRules(dsl);
        List<Rule> rules = parseResult.getRules();
        assertEquals(2, rules.size());
        Rule rule2 = rules.get(1);
        assertEquals("Rule2", rule2.getName());
        assertEquals(1, rule2.getUses().size());
        UseVariable useVar = rule2.getUses().get("input");
        assertEquals("result1", useVar.getVariableName());
        assertEquals("RULE", useVar.getSource());
        assertEquals("Rule1", useVar.getSourceId());
        assertEquals("Integer", useVar.getClassName());
    }

    @Test
    public void testParseRuleWithRetry() throws DSLException {
        String dsl = "#RULE RetryRule\n" +
                     "#RETRY 3 DELAY 100 IF { error != null }\n" +
                     "#ACTION\n" +
                     " throw new Exception(\"Test\");\n";
        ParseResult parseResult = DSLParser.parseRules(dsl);
        List<Rule> rules = parseResult.getRules();
        assertEquals(1, rules.size());
        Rule rule = rules.get(0);
        assertEquals(3, rule.getMaxRetries());
        assertEquals(100, rule.getRetryDelay());
        assertEquals("error != null", rule.getRetryCondition());
    }

    @Test
    public void testParseRuleWithTimeout() throws DSLException {
        String dsl = "#RULE TimeoutRule\n" +
                     "#TIMEOUT 500ms\n" +
                     "#ACTION\n" +
                     " Thread.sleep(1000);\n";
        ParseResult parseResult = DSLParser.parseRules(dsl);
        List<Rule> rules = parseResult.getRules();
        assertEquals(1, rules.size());
        Rule rule = rules.get(0);
        assertEquals(500, rule.getTimeout());
    }

    @Test
    public void testParseRuleWithNextOnSuccessAndFailure() throws DSLException {
        String dsl = "#RULE ControlRule\n" +
                     "#NEXT_ON_SUCCESS SuccessRule\n" +
                     "#NEXT_ON_FAILURE FailRule\n" +
                     "#ACTION\n" +
                     " System.out.println(\"Control\");\n";
        ParseResult parseResult = DSLParser.parseRules(dsl);
        List<Rule> rules = parseResult.getRules();
        assertEquals(1, rules.size());
        Rule rule = rules.get(0);
        assertEquals("SuccessRule", rule.getNextRuleOnSuccess());
        assertEquals("FailRule", rule.getNextRuleOnFailure());
    }

    @Test
    public void testParseHelperBlock() throws DSLException {
        String dsl = "#HELPER\n" +
                     "boolean isEven(int num) {\n" +
                     "    return num % 2 == 0;\n" +
                     "}\n";
        ParseResult parseResult = DSLParser.parseRules(dsl);
        List<String> helpers = parseResult.getHelpers();
        assertEquals(1, helpers.size());
        assertTrue(helpers.get(0).contains("boolean isEven(int num)"));
    }

    @Test(expected = DSLException.class)
    public void testParseNullDSL() throws DSLException {
        DSLParser.parseRules(null);
    }

    @Test(expected = DSLException.class)
    public void testParseEmptyDSL() throws DSLException {
        DSLParser.parseRules("");
    }

    @Test(expected = DSLException.class)
    public void testParseMalformedUseDirective() throws DSLException {
        String dsl = "#RULE MalformedRule\n" +
                     "#USE  invalidSyntax\n";
        DSLParser.parseRules(dsl);
    }

    @Test
    public void testParseRuleWithVersionAndMaxExecutions() throws DSLException {
        String dsl = "#RULE VersionedRule\n" +
                     "#VERSION 1.0.0\n" +
                     "#MAX_EXECUTIONS 10\n" +
                     "#ACTION\n" +
                     " System.out.println(\"Versioned\");\n";
        ParseResult parseResult = DSLParser.parseRules(dsl);
        List<Rule> rules = parseResult.getRules();
        assertEquals(1, rules.size());
        Rule rule = rules.get(0);
        assertEquals("1.0.0", rule.getVersion());
        assertEquals(10, rule.getMaxExecutions());
    }

    @Test
    public void testParseMultipleGroups() throws DSLException {
        String dsl ="#GLOBAL\n" +
                     "\n"+
                     "result :Integer\n" +
                     "result1 : Integer\n" +
                     "\n"+
                     "#RULE Rule1\n" +
                     "#GROUP GroupA\n" +
                     "#ACTION\n" +
                     " System.out.println(\"A\");\n" +
                     "#RULE Rule2\n" +
                     "#GROUP GroupB\n" +
                     "#ACTION\n" +
                     " System.out.println(\"B\");\n";
        ParseResult parseResult = DSLParser.parseRules(dsl);
        List<Rule> rules = parseResult.getRules();
        assertEquals(2, rules.size());
        assertEquals("GroupA", rules.get(0).getGroup());
        assertEquals("GroupB", rules.get(1).getGroup());
    }
}