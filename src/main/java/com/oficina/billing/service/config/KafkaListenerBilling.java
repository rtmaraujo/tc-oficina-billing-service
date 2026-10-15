package com.oficina.billing.service.config;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaListenerBilling {

    // Consumir os-created do OS Service
    @KafkaListener(topics = "os-created", groupId = "billing-service-group")
    public void handleOsCreated(String message) {
        System.out.println("Billing Service received os-created: " + message);
        // Lógica para gerar orçamento automaticamente ou notificar cliente
        // Poderia publicar budget-generated imediatamente ou após validação
    }
}
