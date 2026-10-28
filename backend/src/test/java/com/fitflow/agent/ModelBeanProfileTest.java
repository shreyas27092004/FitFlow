package com.fitflow.agent;

import com.fitflow.agent.port.ChatModelPort;
import com.fitflow.agent.port.ScriptedChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ModelBeanProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Profile Gate: ScriptedChatModel is the only ChatModelPort bean in test profile")
    void testScriptedChatModelIsOnlyChatModelPortInTestProfile() {
        Map<String, ChatModelPort> beans = applicationContext.getBeansOfType(ChatModelPort.class);

        assertNotNull(beans, "Expected ChatModelPort beans map to not be null");
        assertEquals(1, beans.size(), "Expected exactly one ChatModelPort bean in test profile");

        ChatModelPort modelBean = beans.values().iterator().next();
        assertTrue(modelBean instanceof ScriptedChatModel,
                "Expected ChatModelPort bean to be ScriptedChatModel, but was: " + modelBean.getClass().getName());

        // Assert live GeminiLlmClient bean is not present in test profile
        Map<String, GeminiLlmClient> geminiBeans = applicationContext.getBeansOfType(GeminiLlmClient.class);
        assertEquals(0, geminiBeans.size(), "GeminiLlmClient must NOT be present in test profile");
    }
}
