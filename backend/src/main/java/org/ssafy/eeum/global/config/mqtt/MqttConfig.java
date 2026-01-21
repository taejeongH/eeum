package org.ssafy.eeum.global.config.mqtt;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.nio.charset.StandardCharsets;

@Configuration
public class MqttConfig {

    @Value("${spring.mqtt.url}")
    private String url;

    @Value("${spring.mqtt.client-id}")
    private String clientId;

    @Value("${spring.mqtt.default-topic}")
    private String defaultTopic;

    @Value("${spring.mqtt.username}")
    private String username;

    @Value("${spring.mqtt.password}")
    private String password;

    @Bean
    public MqttConnectionOptions mqttConnectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();

        options.setServerURIs(new String[]{url});
        options.setUserName(username);
        options.setPassword(password.getBytes(StandardCharsets.UTF_8));

        options.setCleanStart(true);
        options.setKeepAliveInterval(60);
        options.setAutomaticReconnect(true);

        return options;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        Mqttv5PahoMessageHandler messageHandler = new Mqttv5PahoMessageHandler(url, clientId + "-out");

        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(defaultTopic);

        return messageHandler;
    }
}