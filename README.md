# AI Reading Agent

一个类似微信读书网页端的 MVP：React 阅读器 + Spring Boot 后端 + MySQL 业务数据 + Elasticsearch 向量检索 + Spring AI RAG 问答。

## 当前已实现

- 上传 UTF-8 文本、Markdown、EPUB、PDF 书籍
- 自动识别中文章节标题和 Markdown 标题
- 书架、目录、章节阅读
- 阅读进度接口
- 划线和笔记
- 基于 Elasticsearch VectorStore 的书籍问答接口
- Docker Compose 本地部署

## Windows + Docker Desktop 启动

### 1. 准备环境

安装并启动 Docker Desktop，使用 Linux containers（Docker Desktop 默认模式）。建议至少为 Docker Desktop 分配 4 GB 内存；启用 AI/RAG 时建议 6 GB 或更多。

在 PowerShell 中进入项目目录：

```powershell
cd "E:\Autumn Recruitment\Project\ReadingAgent"
```

项目已经提供本机 `.env`，默认关闭 AI，因此不配置 API Key 也可以使用上传、阅读、划线和笔记功能。若需要重新生成配置：

```powershell
Copy-Item .env.example .env
```

### 2. 构建并启动

```powershell
docker compose up -d --build
```

首次构建需要下载 Maven、Node.js 和 MySQL 镜像，耗时取决于网络。默认只启动基础阅读功能，Elasticsearch 属于可选的 `ai` profile。容器启动后访问：

- 应用：`http://localhost:8088`
- 后端接口：`http://localhost:8080/api/books`
- MySQL：`localhost:3307`

这些端口仅绑定到 `127.0.0.1`，不会直接暴露给局域网。端口被占用时，修改 `.env` 中对应的 `APP_PORT`、`BACKEND_PORT`、`MYSQL_PORT` 或 `ELASTICSEARCH_PORT`，然后重新启动。

### 3. 查看状态和日志

```powershell
docker compose ps
docker compose logs -f backend
```

停止服务但保留书籍、笔记和索引数据：

```powershell
docker compose down
```

如需连同 MySQL 和 Elasticsearch 数据一起清空：

```powershell
docker compose down -v
```

`down -v` 会永久删除 Docker 卷中的本项目数据，请只在确定需要重置时使用。

## 启用 AI / RAG

编辑 `.env`，填入 DashScope API Key，并把 AI 配置改为：

```env
DASHSCOPE_API_KEY=你的DashScope_API_Key
AI_ENABLED=true
AI_MODEL_CHAT=openai
AI_MODEL_EMBEDDING=openai
AI_VECTORSTORE_TYPE=elasticsearch
SPRING_AUTOCONFIGURE_EXCLUDE=
```

保存后启用 `ai` profile，并重建容器：

```powershell
docker compose --profile ai up -d --build
docker compose logs -f backend
```

启用 AI 后可通过 `http://localhost:9200` 检查 Elasticsearch。以后启动项目时也要保留 `--profile ai`；省略它不会删除已有索引数据，只是不启动 Elasticsearch 容器。

上传新书后会自动在后台创建向量索引。对已经上传的书，可以在右侧“问问这本书”标题旁点击重建索引按钮。

检查后端容器实际读取到的配置：

```powershell
docker compose exec backend printenv
```

## 不使用 Docker 的开发启动

后端默认使用 H2 内存数据库：

```powershell
cd backend
mvn spring-boot:run
```

另开一个 PowerShell 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

开发前端地址为 `http://localhost:5173`。
