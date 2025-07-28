package org.pulse.lwre;

import org.junit.Test;
import org.pulse.lwre.core.LWREngine;
import org.pulse.lwre.core.RuleExecutionException;

import static org.junit.Assert.*;

public class LWREngineTest {

    @Test
    public void testExecuteSimpleRule() throws Exception {
        String dsl = "#RULE SimpleRule\n" +
                     "#PRODUCE\n" +
                     " result : Integer\n" +
                     "#ACTION\n" +
                     " result = 42;\n" +
                     "#FINAL\n" +
                     " return result;\n";
        LWREngine engine = new LWREngine.Builder().rules(dsl).build();
        Object result = engine.executeRules("MAIN");
        assertEquals(42, result);
    }

    @Test
    public void testExecuteRulesWithDependencies() throws Exception {
        String dsl = "#RULE Rule1\n" +
                     "#PRODUCE\n" +
                     " result1 : Integer\n" +
                     "#ACTION\n" +
                     " result1 = 21;\n" +
                     "#RULE Rule2\n" +
                     "#USE\n" +
                     " result1 : Integer as input FROM RULE Rule1\n" +
                     "#PRODUCE\n" +
                     " result2 : Integer\n" +
                     "#ACTION\n" +
                     " result2 = input * 2;\n" +
                     "#FINAL\n" +
                     " return result2;\n";
        LWREngine engine = new LWREngine.Builder().rules(dsl).build();
        Object result = engine.executeRules("MAIN");
        assertEquals(42, result);
    }



    @Test
    public void testExecuteRuleWithNextOnSuccess() throws Exception {
        String dsl = "#RULE Rule1\n" +
                     "#NEXT_ON_SUCCESS Rule2\n"+
                     "#PRODUCE \nresult : Integer\n" +
                     "#ACTION\n" +
                     " result = 42;\n" +
                     "#RULE Rule2\n\n#PRODUCE \nresult : Integer\n" +
                     "#ACTION\n" +
                     " result = 100;\n" +
                     "#FINAL\n" +
                     " return result;\n";
        LWREngine engine = new LWREngine.Builder().rules(dsl).build();
        Object result = engine.executeRules("MAIN");
        assertEquals(100, result);
    }
}