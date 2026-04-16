# rag-intelligence

智能RAG问答系统 - 基于Spring Boot的智能文档处理与检索系统

## 项目结构

```
rag-intelligence/
├── bootstrap/       # 核心业务模块
├── framework/       # 通用框架层
├── infra-ai/        # AI基础设施层
└── mcp-server/     # MCP协议服务器
```

## 技术栈

- Java 17
- Spring Boot 3.5.7
- MyBatis Plus
- Milvus / PgVector 向量数据库
- Redis + Redisson
- RocketMQ

## 模块说明

### Framework
通用框架层，提供异常处理、结果封装、链路追踪、幂等控制等基础设施。

### Infra-AI
AI基础设施层，封装LLM、Embedding、重排序等AI能力，支持多模型路由。

### MCP-Server
MCP协议服务器，实现Model Control Protocol工具调用协议。

### Bootstrap
核心业务模块，包含RAG检索、意图识别、对话记忆、知识库管理等核心功能。

## 构建

```bash
mvn clean install
```
