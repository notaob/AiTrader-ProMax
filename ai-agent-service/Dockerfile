# ai-agent-service 镜像
# requirements.txt 由 `uv export --no-dev -o requirements.txt` 生成（与 uv.lock 一致）
# 本地开发/测试请使用 uv: `uv sync` + `uv run pytest`
FROM python:3.12-slim

WORKDIR /app

# 安装依赖（锁定版本）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制代码
COPY app/ ./app/

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
