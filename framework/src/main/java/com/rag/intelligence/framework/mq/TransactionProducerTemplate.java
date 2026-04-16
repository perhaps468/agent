/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rag.intelligence.framework.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class TransactionProducerTemplate {

    private final RocketMQTemplate rocketMQTemplate;

    public TransactionSendResult sendInTransaction(String topic, String tag, Object payload) {
        return sendInTransaction(topic, tag, payload, null, null);
    }

    public TransactionSendResult sendInTransaction(String topic, String tag, Object payload,
                                                    String keys, Map<String, String> headers) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        try {
            org.apache.rocketmq.spring.core.RocketMQTemplate template = rocketMQTemplate;

            Message<Object> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader("messageId", messageId)
                    .setHeader("timestamp", System.currentTimeMillis())
                    .build();

            if (headers != null) {
                headers.forEach(message::setHeader);
            }

            String destination = buildDestination(topic, tag);
            org.springframework.messaging.SendResult sendResult = template.sendMessageInTransaction(
                    destination,
                    message,
                    keys
            );

            if (sendResult != null && sendResult.getMessageID() != null) {
                log.info("Transaction message sent successfully, messageId: {}, topic: {}", 
                        messageId, topic);
                return TransactionSendResult.builder()
                        .messageId(sendResult.getMessageID())
                        .topic(topic)
                        .transactionId(messageId)
                        .success(true)
                        .build();
            }

            return TransactionSendResult.builder()
                    .messageId(messageId)
                    .topic(topic)
                    .success(false)
                    .errorMessage("Send result is null")
                    .build();

        } catch (Exception e) {
            log.error("Failed to send transaction message, messageId: {}, topic: {}", 
                    messageId, topic, e);
            return TransactionSendResult.builder()
                    .messageId(messageId)
                    .topic(topic)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public TransactionSendResult sendInTransaction(RocketMQMessage<?> rocketMQMessage) {
        return sendInTransaction(
                rocketMQMessage.getTopic(),
                rocketMQMessage.getTag(),
                rocketMQMessage.getPayload(),
                rocketMQMessage.getKey(),
                rocketMQMessage.getHeaders()
        );
    }

    private String buildDestination(String topic, String tag) {
        if (tag == null || tag.isEmpty()) {
            return topic;
        }
        return topic + ":" + tag;
    }
}
