package com.aiworkflow.designer.agent.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultantAgentTest {

    @Test
    void recognizesAffirmativeConfirmations() {
        assertThat(ConsultantAgent.isConfirmation("yes")).isTrue();
        assertThat(ConsultantAgent.isConfirmation("Yes, looks good")).isTrue();
        assertThat(ConsultantAgent.isConfirmation("go ahead")).isTrue();
        assertThat(ConsultantAgent.isConfirmation("proceed.")).isTrue();
        assertThat(ConsultantAgent.isConfirmation("CONFIRM")).isTrue();
    }

    @Test
    void rejectsNonConfirmations() {
        assertThat(ConsultantAgent.isConfirmation("hi")).isFalse();
        assertThat(ConsultantAgent.isConfirmation(null)).isFalse();
        assertThat(ConsultantAgent.isConfirmation("")).isFalse();
        assertThat(ConsultantAgent.isConfirmation(
                "yes but actually change the approval step to require two managers")).isFalse();
    }

    @Test
    void rejectsLongMessages() {
        assertThat(ConsultantAgent.isConfirmation(
                "yes that is correct but I also want to add a notification step and an escalation path")).isFalse();
    }
}
