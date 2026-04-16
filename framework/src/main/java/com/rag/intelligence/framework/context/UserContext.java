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

package com.rag.intelligence.framework.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.rag.intelligence.framework.exception.ClientException;

/**
 * 用户上下文容器（基于 TTL 传递当前线程的登录用户）
 */
public final class UserContext {

    private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static LoginUser requireUser() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new ClientException("未获取到当前登录用户");
        }
        return user;
    }

    public static String getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    public static String getRole() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getRole();
    }

    public static String getAvatar() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getAvatar();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static boolean hasUser() {
        return CONTEXT.get() != null;
    }
}