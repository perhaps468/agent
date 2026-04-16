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

package com.rag.intelligence.infra.rerank;

import com.rag.intelligence.framework.convention.RetrievedChunk;
import com.rag.intelligence.infra.model.ModelTarget;

import java.util.List;

/**
 * Rerank客户端接�?
 * 用于对检索到的文档片段进行重新排序，以提高检索结果的相关�?
 */
public interface RerankClient {

    /**
     * 获取Rerank服务提供商名�?
     *
     * @return 提供商标识，�?"bailian"�?jina" �?
     */
    String provider();

    /**
     * 对检索到的文档片段进行重新排�?
     *
     * @param query      用户查询文本
     * @param candidates 待排序的候选文档片段列�?
     * @param topN       返回前N个最相关的结�?
     * @param target     目标模型配置信息
     * @return 重新排序后的文档片段列表，按相关性从高到低排�?
     */
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target);
}
