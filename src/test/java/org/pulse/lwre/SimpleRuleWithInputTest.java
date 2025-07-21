package org.pulse.lwre;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pulse.lwre.core.LWREngine;
import org.pulse.lwre.core.Rule;
import org.pulse.lwre.core.RuleExecutionException;
import org.pulse.lwre.dsl.DSLException;
import org.pulse.lwre.dsl.DSLParser;

import java.util.List;

public class SimpleRuleWithInputTest {
    private LWREngine engine;
    private String  dsl;
    @Before
    public void setup() throws Exception {
        dsl =   "#GLOBAL\n" +
                "input1 : Integer\n" +
                "input2 : Integer\n" +
                "#RULE SIMPLE_RULE\n" +
                "\n" +
                "#USE\n" +
                " input1 : Integer as a FROM Global\n" +
                " input2 : Integer as b FROM Global \n" +
                "#PRODUCE\n" +
                " result : Integer\n" +
                "#ACTION\n" +
                " result = a+b;\n" +
                "#FINAL\n" +
                " return result;\n";
        engine = new LWREngine.Builder().rules(dsl).global("input1",20).global("input2",22).build();
    }
    @Test
    public void testParse() throws DSLException {
        DSLParser.ParseResult parseResult = DSLParser.parseRules(dsl);
        List<Rule> rules = parseResult.getRules();
        Assert.assertNotNull(rules);
        Assert.assertEquals(1,rules.size());
        Assert.assertEquals("SIMPLE_RULE",rules.get(0).getName());
        Assert.assertEquals("MAIN",rules.get(0).getGroup());
        Assert.assertEquals(0,rules.get(0).getPriority());
        Assert.assertNull(rules.get(0).getConditionBlock());
        Assert.assertNotNull(rules.get(0).getActionBlock());
        Assert.assertNotNull(rules.get(0).getFinalBlock());
        Assert.assertNotNull(rules.get(0).getProduces());
        Assert.assertEquals(1,rules.get(0).getProduces().size());
        Assert.assertTrue(rules.get(0).getProduces().containsKey("result"));
        Assert.assertEquals("Integer",rules.get(0).getProduces().get("result"));
        Assert.assertEquals(2,rules.get(0).getUses().size());
        Assert.assertTrue(rules.get(0).getUses().containsKey("a"));
        Assert.assertTrue(rules.get(0).getUses().containsKey("b"));
        Assert.assertEquals("Integer",rules.get(0).getUses().get("a").getClassName());
        Assert.assertEquals("Integer",rules.get(0).getUses().get("b").getClassName());
        Assert.assertEquals("Global",rules.get(0).getUses().get("a").getSource());
        Assert.assertEquals("Global",rules.get(0).getUses().get("b").getSource());
        Assert.assertEquals("input1",rules.get(0).getUses().get("a").getVariableName());
        Assert.assertEquals("input2",rules.get(0).getUses().get("b").getVariableName());
    }
    @Test
    public void testSimpleRule() throws RuleExecutionException {
        Object o = engine.executeRules("MAIN");
        Assert.assertEquals(o,42);
    }
}
