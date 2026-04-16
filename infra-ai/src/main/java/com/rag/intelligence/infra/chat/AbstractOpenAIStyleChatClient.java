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

package com.rag.intelligence.infra.chat;

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rag.intelligence.framework.convention.ChatMessage;
import com.rag.intelligence.framework.convention.ChatRequest;
import com.rag.intelligence.infra.config.AIModelProperties;
import com.rag.intelligence.infra.enums.ModelCapability;
import com.rag.intelligence.infra.http.HttpMediaTypes;
import com.rag.intelligence.infra.http.HttpResponseHelper;
import com.rag.intelligence.infra.http.ModelClientErrorType;
import com.rag.intelligence.infra.http.ModelClientException;
import com.rag.intelligence.infra.http.ModelUrlResolver;
import com.rag.intelligence.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 兼容协议 ChatClient 抽象基类
 */
@Slf4j
public abstract class AbstractOpenAIStyleChatClient implements ChatClient {

    protected final OkHttpClient httpClient;
    protected final Executor modelStreamExecutor;
    protected final Gson gson = new Gson();

    protected AbstractOpenAIStyleChatClient(OkHttpClient httpClient, Executor modelStreamExecutor) {
        this.httpClient = httpClient;
        this.modelStreamExecutor = modelStreamExecutor;
    }

    // ==================== 子类钩子方法 ====================

    /**
     * 流式调用时是否启�?reasoning_content 解析，默认根据请求中�?thinking 标志决定
     */
    protected boolean isReasoningEnabledForStream(ChatRequest request) {
        return Boolean.TRUE.equals(request.getThinking());
    }

    /**
     * 子类可覆写此方法添加提供商特有的请求体字�?
     * 默认实现：当请求开�?thinking 时添�?enable_thinking 字段
     */
    protected void customizeRequestBody(JsonObject body, ChatRequest request) {
        if (Boolean.TRUE.equals(request.getThinking())) {
            body.addProperty("enable_thinking", true);
        }
    }

    /**
     * 是否要求提供商配�?API Key
     */
    protected boolean requiresApiKey() {
        return true;
    }

    // ==================== 模板方法：同步调�?====================

    protected String doChat(ChatRequest request, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = HttpResponseHelper.requireProvider(target, provider());
        if (requiresApiKey()) {
            HttpResponseHelper.requireApiKey(provider, provider());
        }

        JsonObject reqBody = buildRequestBody(request, target, false);
        Request requestHttp = newAuthorizedRequest(provider, target)
                .post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
                .build();

        JsonObject respJson;
        try (Response response = httpClient.newCall(requestHttp).execute()) {
            if (!response.isSuccessful()) {
                String body = HttpResponseHelper.readBody(response.body());
                log.warn("{} 同步请求失败: status={}, body={}", provider(), response.code(), body);
                throw new ModelClientException(
                        provider() + " 同步请求失败: HTTP " + response.code(),
                        ModelClientErrorType.fromHttpStatus(response.code()),
                        response.code()
                );
            }
            respJson = HttpResponseHelper.parseJson(response.body(), provider());
        } catch (IOException e) {
            throw new ModelClientException(
                    provider() + " 同步请求失败: " + e.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR, null, e);
        }

        return extractChatContent(respJson);
    }

    // ==================== 模板方法：流式调�?====================

    protected StreamCancellationHandle doStreamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = HttpResponseHelper.requireProvider(target, provider());
        if (requiresApiKey()) {
            HttpResponseHelper.requireApiKey(provider, provider());
        }

        JsonObject reqBody = buildRequestBody(request, target, true);
        Request streamRequest = newAuthorizedRequest(provider, target)
                .post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
                .addHeader("Accept", "text/event-stream")
                .build();

        Call call = httpClient.newCall(streamRequest);
        boolean reasoningEnabled = isReasoningEnabledForStream(request);
        return StreamAsyncExecutor.submit(
                modelStreamExecutor,
                call,
                callback,
                cancelled -> doStream(call, callback, cancelled, reasoningEnabled)
        );
    }

    private void doStream(Call call, StreamCallback callback, AtomicBoolean cancelled, boolean reasoningEnabled) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                String body = HttpResponseHelper.readBody(response.body());
                throw new ModelClientException(
                        provider() + " 流式请求失败: HTTP " + response.code() + " - " + body,
                        ModelClientErrorType.fromHttpStatus(response.code()),
                        response.code()
                );
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ModelClientException(provider() + " 流式响应为空", ModelClientErrorType.INVALID_RESPONSE, null);
            }
            BufferedSource source = body.source();
            boolean completed = false;
            while (!cancelled.get()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                try {
                    OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(line, gson, reasoningEnabled);
                    if (event.hasReasoning()) {
                        callback.onThinking(event.reasoning());
                    }
                    if (event.hasContent()) {
                        callback.onContent(event.content());
                    }
                    if (event.completed()) {
                        callback.onComplete();
                        completed = true;
                        break;
                    }
                } catch (Exception parseEx) {
                    log.warn("{} 流式响应解析失败: line={}", provider(), line, parseEx);
                }
            }
            if (!cancelled.get() && !completed) {
                throw new ModelClientException(provider() + " 流式响应异常结束", ModelClientErrorType.INVALID_RESPONSE, null);
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    // ==================== 公共构建方法 ====================

    protected JsonObject buildRequestBody(ChatRequest request, ModelTarget target, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", HttpResponseHelper.requireModel(target, provider()));
        if (stream) {
            body.addProperty("stream", true);
        }

        body.add("messages", buildMessages(request));

        if (request.getTemperature() != null) {
            body.addProperty("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.addProperty("top_p", request.getTopP());
        }
        if (request.getTopK() != null) {
            body.addProperty("top_k", request.getTopK());
        }
        if (request.getMaxTokens() != null) {
            body.addProperty("max_tokens", request.getMaxTokens());
        }

        customizeRequestBody(body, request);
        return body;
    }

    private JsonArray buildMessages(ChatRequest request) {
        JsonArray arr = new JsonArray();
        List<ChatMessage> messages = request.getMessages();
        if (CollUtil.isNotEmpty(messages)) {
            for (ChatMessage m : messages) {
                JsonObject msg = new JsonObject();
                msg.addProperty("role", toOpenAiRole(m.getRole()));
                msg.addProperty("content", m.getContent());
                arr.add(msg);
            }
        }
        return arr;
    }

    private String toOpenAiRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private Request.Builder newAuthorizedRequest(AIModelProperties.ProviderConfig provider, ModelTarget target) {
        Request.Builder builder = new Request.Builder()
                .url(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.CHAT));
        if (requiresApiKey()) {
            builder.addHeader("Authorization", "Bearer " + provider.getApiKey());
        }
        return builder;
    }

    private String extractChatContent(JsonObject respJson) {
        if (respJson == null || !respJson.has("choices")) {
            throw new ModelClientException(provider() + " 响应缺少 choices", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonArray choices = respJson.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ModelClientException(provider() + " 响应 choices 为空", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject choice0 = choices.get(0).getAsJsonObject();
        if (choice0 == null || !choice0.has("message")) {
            throw new ModelClientException(provider() + " 响应缺少 message", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        JsonObject message = choice0.getAsJsonObject("message");
        if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
            throw new ModelClientException(provider() + " 响应缺少 content", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return message.get("content").getAsString();
    }
}
