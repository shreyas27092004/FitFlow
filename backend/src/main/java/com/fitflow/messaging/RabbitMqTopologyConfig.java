package com.fitflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RabbitMqTopologyConfig {

    public static final String EXCHANGE_EVENTS = "fitflow.events";
    public static final String EXCHANGE_DLX = "fitflow.events.dlx";

    public static final String QUEUE_NOTIFICATIONS_EMAIL = "q.notifications.email";
    public static final String QUEUE_NOTIFICATIONS_EMAIL_DLQ = "q.notifications.email.dlq";

    public static final String QUEUE_AGENT_EVENTS = "q.agent.events";
    public static final String QUEUE_AGENT_EVENTS_DLQ = "q.agent.events.dlq";

    public static final List<String> NOTIFICATION_EMAIL_ROUTING_KEYS = List.of(
            "user.created",
            "password.reset_requested",
            "user.suspended",
            "booking.created",
            "booking.cancelled",
            "waitlist.promoted",
            "session.cancelled",
            "session.rescheduled",
            "session.reminder",
            "membership.started",
            "membership.changed",
            "membership.expiring",
            "membership.expired",
            "payment.succeeded",
            "payment.failed",
            "refund.pending_approval",
            "refund.issued",
            "payout.approved",
            "feedback.pain_reported"
    );

    public static final List<String> AGENT_EVENT_ROUTING_KEYS = List.of(
            "member.at_risk",
            "weekly.summary",
            "feedback.pain_reported",
            "profile.completed",
            "booking.created",
            "attendance.marked"
    );

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public Declarables rabbitTopologyDeclarables() {
        TopicExchange eventsExchange = new TopicExchange(EXCHANGE_EVENTS, true, false);
        TopicExchange dlxExchange = new TopicExchange(EXCHANGE_DLX, true, false);

        Queue emailQueue = QueueBuilder.durable(QUEUE_NOTIFICATIONS_EMAIL)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_NOTIFICATIONS_EMAIL_DLQ)
                .build();

        Queue emailDlq = QueueBuilder.durable(QUEUE_NOTIFICATIONS_EMAIL_DLQ).build();

        Queue agentQueue = QueueBuilder.durable(QUEUE_AGENT_EVENTS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_AGENT_EVENTS_DLQ)
                .build();

        Queue agentDlq = QueueBuilder.durable(QUEUE_AGENT_EVENTS_DLQ).build();

        List<Declarable> declarations = new ArrayList<>();
        declarations.add(eventsExchange);
        declarations.add(dlxExchange);
        declarations.add(emailQueue);
        declarations.add(emailDlq);
        declarations.add(agentQueue);
        declarations.add(agentDlq);

        // Bind DLQs
        declarations.add(BindingBuilder.bind(emailDlq).to(dlxExchange).with(QUEUE_NOTIFICATIONS_EMAIL_DLQ));
        declarations.add(BindingBuilder.bind(emailDlq).to(dlxExchange).with(QUEUE_NOTIFICATIONS_EMAIL + ".#"));
        declarations.add(BindingBuilder.bind(agentDlq).to(dlxExchange).with(QUEUE_AGENT_EVENTS_DLQ));
        declarations.add(BindingBuilder.bind(agentDlq).to(dlxExchange).with(QUEUE_AGENT_EVENTS + ".#"));

        // Bind Notification Email routing keys
        for (String rk : NOTIFICATION_EMAIL_ROUTING_KEYS) {
            declarations.add(BindingBuilder.bind(emailQueue).to(eventsExchange).with(rk));
        }

        // Bind Agent Event routing keys
        for (String rk : AGENT_EVENT_ROUTING_KEYS) {
            declarations.add(BindingBuilder.bind(agentQueue).to(eventsExchange).with(rk));
        }

        return new Declarables(declarations);
    }
}
