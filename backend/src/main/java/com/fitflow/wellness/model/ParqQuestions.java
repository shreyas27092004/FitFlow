package com.fitflow.wellness.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ParqQuestions {

    private static final Map<String, String> QUESTIONS;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Q1", "A doctor said you have a heart condition and should only do physical activity recommended by a doctor.");
        map.put("Q2", "You feel pain in your chest when you do physical activity.");
        map.put("Q3", "In the past month you had chest pain when not doing physical activity.");
        map.put("Q4", "You lose your balance because of dizziness or you lose consciousness.");
        map.put("Q5", "You have a bone or joint problem that could be made worse by a change in your physical activity.");
        map.put("Q6", "A doctor currently prescribes medication for your blood pressure or a heart condition.");
        map.put("Q7", "You know of any other reason you should not do physical activity.");
        QUESTIONS = Collections.unmodifiableMap(map);
    }

    private ParqQuestions() {}

    public static Map<String, String> getQuestions() {
        return QUESTIONS;
    }

    public static boolean isValidKey(String key) {
        return key != null && QUESTIONS.containsKey(key.toUpperCase());
    }
}
