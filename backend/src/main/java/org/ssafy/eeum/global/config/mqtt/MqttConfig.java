package org.ssafy.eeum.global.config.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.UUID;

@Slf4j
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

        options.setServerURIs(new String[] { url });
        options.setUserName(username);
        options.setPassword(password.getBytes(StandardCharsets.UTF_8));

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            }, new java.security.SecureRandom());
            options.setSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            log.error("MQTT SSL 설정 오류: {}", e.getMessage());
        }

        options.setCleanStart(true);
        options.setKeepAliveInterval(60);
        options.setAutomaticReconnect(true);

        return options;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public Mqttv5PahoMessageDrivenChannelAdapter inbound(MqttConnectionOptions mqttConnectionOptions) {
        String uniqueInboundId = clientId + "-in-" + UUID.randomUUID().toString().substring(0, 5);

        Mqttv5PahoMessageDrivenChannelAdapter adapter = new Mqttv5PahoMessageDrivenChannelAdapter(
                mqttConnectionOptions, uniqueInboundId,
                "eeum/sensor/data",
                "eeum/ai/sentiment",
                "eeum/family/code",
                "eeum/init/device/+/req",
                "eeum/fall/response",
                "eeum/init/device/pair/req",
                "eeum/response",
                "eeum/event",
                "eeum/update",
                "eeum/status",
                "eeum/responsenull");

        adapter.setCompletionTimeout(5000);
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound(MqttConnectionOptions mqttConnectionOptions) {
        String uniqueOutboundId = clientId + "-out-" + UUID.randomUUID().toString().substring(0, 5);

        Mqttv5PahoMessageHandler messageHandler = new Mqttv5PahoMessageHandler(mqttConnectionOptions, uniqueOutboundId);

        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(defaultTopic);

        return messageHandler;
    }
}