package org.pulse.lwre;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pulse.lwre.core.LWREngine;
import org.pulse.lwre.core.RuleExecutionException;

import java.util.Map;
import java.util.Queue;

public class RulesWithDependanciesTest {

    private LWREngine engine;
    private String dsl;

     @Before
    public void setup() throws Exception {
         dsl = "#RULE RULE1\n" +
                 "#USE\n" +
                 " input1 : Integer as a FROM Global\n" +
                 " input2 : Integer as b FROM Global \n" +
                 "#PRODUCE\n" +
                 " result as Integer\n" +
                 "#ACTION\n" +
                 " result = a+b;\n" +
                 "#RULE RULE2\n" +
                 "#USE\n" +
                 " result : Integer as response FROM RULE RULE1\n" +
                 "#CONDITION\n" +
                 " return response == 42;\n" +
                 "#FINAL\n" +
                 " return response;";
        engine = new LWREngine.Builder().rules(dsl).global("input1",20).global("input2",22).build();
    }
    @Test
    public void testRulesWithDependancies() throws RuleExecutionException {
        Object o = engine.executeRules();
        Assert.assertEquals(42,o);

    }
}
