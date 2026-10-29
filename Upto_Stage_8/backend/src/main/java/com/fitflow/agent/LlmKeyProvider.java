package com.fitflow.agent;

import java.util.Optional;

public interface LlmKeyProvider {
    Optional<String> currentKey();
}
