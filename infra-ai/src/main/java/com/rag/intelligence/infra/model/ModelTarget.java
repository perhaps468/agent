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

package com.rag.intelligence.infra.model;

import com.rag.intelligence.infra.config.AIModelProperties;

/**
 * 模型目标配置记录
 * <p>
 * 用于封装 AI 模型的配置信息，包括模型标识、候选模型配置和提供商配�?
 *
 * @param id        模型唯一标识�?
 * @param candidate 模型候选配置，包含模型的具体参数和设置
 * @param provider  提供商配置，包含模型提供商的相关信息
 */
public record ModelTarget(
        String id,
        AIModelProperties.ModelCandidate candidate,
        AIModelProperties.ProviderConfig provider
) {
}
