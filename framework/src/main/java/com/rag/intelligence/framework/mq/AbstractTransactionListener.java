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

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractTransactionListener implements RocketMQLocalTransactionListener {

    private final Map<String, TransactionExecuteUnit> transactionCache = new ConcurrentHashMap<>();

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String transactionId = (String) msg.getHeaders().get("transactionId");
        if (transactionId == null) {
            transactionId = (String) arg;
        }

        try {
            Object payload = msg.getPayload();
            RocketMQLocalTransactionState state = doExecuteLocalTransaction(payload, arg);
            log.info("Execute local transaction, transactionId: {}, state: {}", 
                    transactionId, state);
            return state;
        } catch (Exception e) {
            log.error("Execute local transaction failed, transactionId: {}", 
                    transactionId, e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String transactionId = (String) msg.getHeaders().get("transactionId");
        try {
            Object payload = msg.getPayload();
            RocketMQLocalTransactionState state = doCheckLocalTransaction(payload);
            log.info("Check local transaction, transactionId: {}, state: {}", 
                    transactionId, state);
            return state;
        } catch (Exception e) {
            log.error("Check local transaction failed, transactionId: {}", 
                    transactionId, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    protected abstract RocketMQLocalTransactionState doExecuteLocalTransaction(
            Object payload, Object arg);

    protected abstract RocketMQLocalTransactionState doCheckLocalTransaction(Object payload);

    public void registerTransaction(String transactionId, TransactionExecuteUnit unit) {
        transactionCache.put(transactionId, unit);
        log.debug("Register transaction, transactionId: {}", transactionId);
    }

    public TransactionExecuteUnit getTransaction(String transactionId) {
        return transactionCache.get(transactionId);
    }

    public void removeTransaction(String transactionId) {
        transactionCache.remove(transactionId);
        log.debug("Remove transaction, transactionId: {}", transactionId);
    }

    @FunctionalInterface
    public interface TransactionExecuteUnit {
        RocketMQLocalTransactionState execute();
    }
}
