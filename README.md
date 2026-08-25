# AI Reading Agent

一个类似微信读书网页端的 MVP：React 阅读器 + Spring Boot 后端 + MySQL 业务数据 + Elasticsearch 向量检索 + Spring AI RAG 问答。

## 当前已实现

- 上传 UTF-8 文本、Markdown、EPUB、PDF 书籍
- 自动识别中文章节标题和 Markdown 标题
- 书架、目录、章节阅读
- 阅读进度接口
- 划线和笔记
- 基于 Elasticsearch VectorStore 的书籍问答接口
- Docker Compose 部署骨架

## 本地启动

后端默认使用 H2 内存数据库，方便先跑通普通接口。AI 问答需要 Elasticsearch 和 `DASHSCOPE_API_KEY`。

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

前端地址：`http://localhost:5173`

## Docker 部署

在项目根目录创建 `.env`：

```env
DASHSCOPE_API_KEY=你的DashScope API Key
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_CHAT_COMPLETIONS_PATH=/chat/completions
QWEN_EMBEDDINGS_PATH=/embeddings
QWEN_MODEL=qwen-plus
QWEN_EMBEDDING_MODEL=text-embedding-v3
EMBEDDING_DIMENSIONS=1024
AI_ENABLED=true
AI_MODEL_CHAT=openai
AI_MODEL_EMBEDDING=openai
AI_VECTORSTORE_TYPE=elasticsearch
MYSQL_ROOT_PASSWORD=reading_root
MYSQL_DATABASE=reading_agent
MYSQL_USER=reading
MYSQL_PASSWORD=reading_pass
VITE_API_BASE=/api
CORS_ORIGIN=http://你的服务器IP
```

启动：

```bash
docker compose up -d --build
```

浏览器打开：`http://你的服务器IP`

Docker 部署默认启用 RAG：后端会等待 MySQL 和 Elasticsearch 健康后启动。上传新书后会自动在后台创建向量索引；对已经上传过的旧书，可以在右侧“问问这本书”标题旁点击重建索引按钮。

如果只想先启用上传、阅读、划线等基础功能，可以暂时不配置 AI：

```env
AI_ENABLED=false
AI_MODEL_CHAT=none
AI_MODEL_EMBEDDING=none
AI_VECTORSTORE_TYPE=none
SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreAutoConfiguration
```

当服务器内存足够、Elasticsearch 已正常启动并且 `DASHSCOPE_API_KEY` 已配置后，再把这些值改回 AI 配置。

如果 AI 问答返回配置或调用失败，先检查后端容器里的关键环境变量：

```bash
docker compose exec backend printenv | grep -E "DASHSCOPE|QWEN|AI_|ELASTICSEARCH"
```

## 下一步建议

- 增加用户注册登录和权限隔离
- 优化 EPUB/PDF 目录识别和版式还原
- RAG 检索改成 BM25 + 向量混合检索
- AI 回答增加引用跳转到章节位置
- 书籍文件和封面迁移到对象存储
