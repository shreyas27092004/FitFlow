package com.fitflow.payment.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class ConfirmPaymentRequest {
    private Map<String, Object> clientPayload;
    private final Map<String, Object> extraFields = new HashMap<>();

    public ConfirmPaymentRequest() {
    }

    @JsonCreator
    public ConfirmPaymentRequest(@JsonProperty("clientPayload") Map<String, Object> clientPayload) {
        this.clientPayload = clientPayload;
    }

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    @JsonProperty("clientPayload")
    public Map<String, Object> clientPayload() {
        if (clientPayload != null && !clientPayload.isEmpty()) {
            return clientPayload;
        }
        return extraFields;
    }

    public Map<String, Object> getClientPayload() {
        return clientPayload();
    }
}
