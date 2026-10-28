package com.fitflow.messaging;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class MessagingConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "fitflow.messaging.mode", havingValue = "inmemory", matchIfMissing = true)
    public InMemoryEventDispatcher inMemoryEventDispatcher(@Autowired(required = false) List<EventHandler> handlers) {
        return new InMemoryEventDispatcher(handlers);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "fitflow.messaging.mode", havingValue = "rabbit")
    public RabbitMqEventPublisher rabbitMqEventPublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitMqEventPublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "fitflow.messaging.mode", havingValue = "rabbit")
    public RabbitTemplate configuredRabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    @ConditionalOnProperty(name = "fitflow.messaging.mode", havingValue = "rabbit")
    public org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new org.springframework.amqp.rabbit.core.RabbitAdmin(connectionFactory);
    }
}
