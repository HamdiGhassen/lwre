package org.pulse.lwre;

import org.junit.Test;
import org.pulse.lwre.core.CompiledRule;
import org.pulse.lwre.core.Rule;
import org.pulse.lwre.core.RuleCompiler;
import org.pulse.lwre.core.RuleExecutionException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RuleCompilerTest {

    @Test
    public void testCompileSimpleRule() throws Exception {
        Rule rule = new Rule();
        rule.setName("SimpleRule");
        rule.setActionBlock("int a = 1; int b = 2; result = a + b;");
        rule.getProduces().put("result", "Integer");
        List<String> helpers = new ArrayList<>();
        RuleCompiler compiler = new RuleCompiler();
        CompiledRule compiledRule = compiler.compileRule(rule, helpers);
        assertNotNull(compiledRule);
        assertNotNull(compiledRule.getActionMethod());
    }

    @Test
    public void testCompileRuleWithConditionAndFinal() throws Exception {
        Rule rule = new Rule();
        rule.setName("ComplexRule");
        rule.setConditionBlock("return input > 0;");
        rule.setActionBlock("result = input * 2;");
        rule.setFinalBlock("return result;");
        rule.getUses().put("input", new Rule.UseVariable("value", "Global", "Global", "Integer"));
        rule.getProduces().put("result", "Integer");
        List<String> helpers = new ArrayList<>();
        RuleCompiler compiler = new RuleCompiler();
        CompiledRule compiledRule = compiler.compileRule(rule, helpers);
        assertNotNull(compiledRule.getConditionMethod());
        assertNotNull(compiledRule.getActionMethod());
        assertNotNull(compiledRule.getFinalMethod());
    }

    @Test
    public void testCompileWithHelper() throws Exception {
        Rule rule = new Rule();
        rule.setName("HelperRule");
        rule.setActionBlock("result = isEven(4);");
        rule.getProduces().put("result", "Boolean");
        List<String> helpers = new ArrayList<>();
        helpers.add("static boolean isEven(int num) { return num % 2 == 0; }");
        RuleCompiler compiler = new RuleCompiler();
        CompiledRule compiledRule = compiler.compileRule(rule, helpers);
        assertNotNull(compiledRule.getActionMethod());
    }

    @Test(expected = RuleCompiler.RuleCompilationException.class)
    public void testCompileWithForbiddenClass() throws Exception {
        Rule rule = new Rule();
        rule.setName("DangerousRule");
        rule.setActionBlock("System.exit(0);");
        List<String> helpers = new ArrayList<>();
        RuleCompiler compiler = new RuleCompiler();
        compiler.compileRule(rule, helpers);
    }

    @Test(expected = RuleCompiler.RuleCompilationException.class)
    public void testCompileInvalidSyntax() throws Exception {
        Rule rule = new Rule();
        rule.setName("InvalidRule");
        rule.setActionBlock("int a = 1; result = a + ;"); // Syntax error
        List<String> helpers = new ArrayList<>();
        RuleCompiler compiler = new RuleCompiler();
        compiler.compileRule(rule, helpers);
    }
}