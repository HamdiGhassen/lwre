package org.pulse.lwre;

import org.junit.Test;
import org.pulse.lwre.core.LWREngine;
import org.pulse.lwre.core.RuleExecutionException;
import org.pulse.lwre.dsl.DSLException;

public class EmptyOrNullDslTest {
    @Test(expected = DSLException.class)
    public void testEngineBuildWithEmptyDSL() throws Exception {
        LWREngine e = new LWREngine.Builder().rules("").build();
        e.executeRules("MAIN");
    }
    @Test(expected = DSLException.class)
    public void testEngineBuildWithNullDSL() throws Exception {
        LWREngine e = new LWREngine.Builder().rules(null).build();
        e.executeRules("MAIN");
    }
    @Test(expected = RuleExecutionException.class)
    public void testEngineBuildWithDSLNotSet() throws RuleExecutionException {
        LWREngine e = new LWREngine.Builder().build();
        e.executeRules();
    }

}
