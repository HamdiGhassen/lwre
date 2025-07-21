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

public class SimpleRuleTest {
    private LWREngine engine;
    private String  dsl;
    @Before
    public void setup() throws Exception {
        dsl = "#RULE SIMPLE_RULE\n" +
                "#PRODUCE\n" +
                " result : Integer\n" +
                "#ACTION\n" +
                " int a = 1;\n" +
                " int b = 41;\n" +
                " result = a+b;\n" +
                "#FINAL\n" +
                " return result;\n";
        engine = new LWREngine.Builder().rules(dsl).build();
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
        Assert.assertTrue(rules.get(0).getProduces().keySet().contains("result"));
        Assert.assertEquals("Integer",rules.get(0).getProduces().get("result"));
    }
    @Test
    public void testSimpleRule() throws RuleExecutionException {
        Object o = engine.executeRules("MAIN");
        Assert.assertEquals(o,42);
    }


}
