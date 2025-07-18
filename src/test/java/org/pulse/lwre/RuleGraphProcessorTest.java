package org.pulse.lwre;

import org.junit.Test;
import org.pulse.lwre.core.DirectedGraph;
import org.pulse.lwre.core.Rule;
import org.pulse.lwre.core.RuleGraphProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RuleGraphProcessorTest {

    @Test
    public void testBuildGraphNoDependencies() {
        List<Rule> rules = new ArrayList<>();
        Rule rule1 = new Rule();
        rule1.setName("Rule1");
        rule1.setGroup("Group1");
        rules.add(rule1);
        Map<String, DirectedGraph<Rule>> groupGraphs = RuleGraphProcessor.processRules(rules);
        assertEquals(1, groupGraphs.size());
        DirectedGraph<Rule> graph = groupGraphs.get("Group1");
        assertNotNull(graph);
        List<Rule> sorted = graph.topologicalSort();
        assertEquals(1, sorted.size());
        assertEquals("Rule1", sorted.get(0).getName());
    }

    @Test
    public void testBuildGraphWithDependencies() {
        List<Rule> rules = new ArrayList<>();
        Rule rule1 = new Rule();
        rule1.setName("Rule1");
        rule1.setGroup("Group1");
        Rule rule2 = new Rule();
        rule2.setName("Rule2");
        rule2.setGroup("Group1");
        rule2.getUses().put("input", new Rule.UseVariable("output", "RULE", "Rule1", "Integer"));
        rules.add(rule1);
        rules.add(rule2);
        Map<String, DirectedGraph<Rule>> groupGraphs = RuleGraphProcessor.processRules(rules);
        DirectedGraph<Rule> graph = groupGraphs.get("Group1");
        List<Rule> sorted = graph.topologicalSort();
        assertEquals(2, sorted.size());
        assertEquals("Rule1", sorted.get(0).getName());
        assertEquals("Rule2", sorted.get(1).getName());
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildGraphWithCycle() {
        List<Rule> rules = new ArrayList<>();
        Rule rule1 = new Rule();
        rule1.setName("Rule1");
        rule1.setGroup("Group1");
        rule1.getUses().put("input", new Rule.UseVariable("output", "RULE", "Rule2", "Integer"));
        Rule rule2 = new Rule();
        rule2.setName("Rule2");
        rule2.setGroup("Group1");
        rule2.getUses().put("input", new Rule.UseVariable("output", "RULE", "Rule1", "Integer"));
        rules.add(rule1);
        rules.add(rule2);
        Map<String, DirectedGraph<Rule>> groupGraphs = RuleGraphProcessor.processRules(rules);
        DirectedGraph<Rule> graph = groupGraphs.get("Group1");
        graph.topologicalSort();
    }

    @Test
    public void testPrioritySorting() {
        List<Rule> rules = new ArrayList<>();
        Rule rule1 = new Rule();
        rule1.setName("Rule1");
        rule1.setGroup("Group1");
        rule1.setPriority(2);
        Rule rule2 = new Rule();
        rule2.setName("Rule2");
        rule2.setGroup("Group1");
        rule2.setPriority(5);
        rules.add(rule2);
        rules.add(rule1);
        Map<String, DirectedGraph<Rule>> groupGraphs = RuleGraphProcessor.processRules(rules);
        DirectedGraph<Rule> graph = groupGraphs.get("Group1");
        List<Rule> sorted = graph.topologicalSort();
        assertEquals(2, sorted.size());
        assertEquals("Rule1", sorted.get(0).getName()); // Higher priority first
        assertEquals("Rule2", sorted.get(1).getName());
    }

    @Test
    public void testMissingDependency() {
        List<Rule> rules = new ArrayList<>();
        Rule rule = new Rule();
        rule.setName("Rule1");
        rule.setGroup("Group1");
        rule.getUses().put("input", new Rule.UseVariable("output", "RULE", "NonExistent", "Integer"));
        rules.add(rule);
        Map<String, DirectedGraph<Rule>> groupGraphs = RuleGraphProcessor.processRules(rules);
        DirectedGraph<Rule> graph = groupGraphs.get("Group1");
        List<Rule> sorted = graph.topologicalSort();
        assertEquals(1, sorted.size()); // Should not fail, but dependency is ignored
    }
}