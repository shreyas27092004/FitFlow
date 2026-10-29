package com.fitflow.membership.proration;

public interface ProrationPolicy {

    String name();

    ProrationResult calculate(ProrationInput input);
}
