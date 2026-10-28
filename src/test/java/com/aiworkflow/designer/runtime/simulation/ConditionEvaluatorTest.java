package com.aiworkflow.designer.runtime.simulation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.aiworkflow.designer.runtime.simulation.ConditionEvaluator.Outcome;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionEvaluatorTest {

    @Test
    void evaluatesNumericComparisons() {
        Map<String, Object> vars = Map.of("days", 5, "balance", 10);
        assertThat(ConditionEvaluator.evaluate("${balance >= days}", vars)).isEqualTo(Outcome.TRUE);
        assertThat(ConditionEvaluator.evaluate("${balance < days}", vars)).isEqualTo(Outcome.FALSE);
        assertThat(ConditionEvaluator.evaluate("balance == 10", vars)).isEqualTo(Outcome.TRUE);
        assertThat(ConditionEvaluator.evaluate("balance != 10", vars)).isEqualTo(Outcome.FALSE);
    }

    @Test
    void evaluatesBooleanAndTruthiness() {
        Map<String, Object> vars = Map.of("approved", true, "count", 0);
        assertThat(ConditionEvaluator.evaluate("${approved == true}", vars)).isEqualTo(Outcome.TRUE);
        assertThat(ConditionEvaluator.evaluate("approved", vars)).isEqualTo(Outcome.TRUE);
        assertThat(ConditionEvaluator.evaluate("!approved", vars)).isEqualTo(Outcome.FALSE);
        assertThat(ConditionEvaluator.evaluate("count", vars)).isEqualTo(Outcome.FALSE);
        assertThat(ConditionEvaluator.evaluate("${missing}", vars)).isEqualTo(Outcome.FALSE);
    }

    @Test
    void evaluatesCompoundExpressions() {
        Map<String, Object> vars = Map.of("a", 1, "b", 2);
        assertThat(ConditionEvaluator.evaluate("${a == 1 && b == 2}", vars)).isEqualTo(Outcome.TRUE);
        assertThat(ConditionEvaluator.evaluate("${a == 1 and b == 9}", vars)).isEqualTo(Outcome.FALSE);
        assertThat(ConditionEvaluator.evaluate("${a == 9 || b == 2}", vars)).isEqualTo(Outcome.TRUE);
        assertThat(ConditionEvaluator.evaluate("${a == 9 or b == 9}", vars)).isEqualTo(Outcome.FALSE);
    }

    @Test
    void unevaluatesComplexExpressionsInsteadOfGuessing() {
        assertThat(ConditionEvaluator.evaluate("${notifier.check(request)}", Map.of()))
                .isEqualTo(Outcome.UNEVALUATED);
        assertThat(ConditionEvaluator.evaluate("${days > 3 ? 'a' : 'b'}", Map.of()))
                .isEqualTo(Outcome.UNEVALUATED);
        assertThat(ConditionEvaluator.evaluate("${bean.doSomething()}", Map.of()))
                .isEqualTo(Outcome.UNEVALUATED);
    }

    @Test
    void handlesLiteralsAndNulls() {
        assertThat(ConditionEvaluator.evaluate("true", Map.of())).isEqualTo(Outcome.TRUE);
        assertThat(ConditionEvaluator.evaluate("false", Map.of())).isEqualTo(Outcome.FALSE);
        assertThat(ConditionEvaluator.evaluate("${name == 'alice'}", Map.of("name", "alice")))
                .isEqualTo(Outcome.TRUE);
        assertThat(ConditionEvaluator.evaluate(null, Map.of())).isEqualTo(Outcome.UNEVALUATED);
    }
}
