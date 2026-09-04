from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# 项目根目录 = ai-agent-service/
PROJECT_DIR = Path(__file__).resolve().parent.parent
ENV_FILE = PROJECT_DIR / ".env"


class AppSettings(BaseSettings):
    """ai-agent-service 配置（环境变量 / .env 文件加载）。

    - 环境变量 > .env 文件 > 字段默认值
    - 必填字段缺失时启动即抛 ValidationError（避免带着空 key 上线）
    """

    # --- DashScope (阿里云 / OpenAI 兼容网关) ---
    DASHSCOPE_API_KEY: str  # 必填：缺失则启动报错
    DASHSCOPE_BASE_URL: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    DASHSCOPE_MODEL: str = "qwen3.8-flash"

    # --- Redis (与 Java 后端共享) ---
    REDIS_HOST: str = "127.0.0.1"
    REDIS_PORT: int = 6379
    REDIS_PASSWORD: str = ""
    REDIS_DB: int = 0
    # 向量存储模式：auto=探测 RediSearch，有则真 HNSW、无则内存降级；memory=强制内存；redis=强制真实索引(不可用即启动报错)
    REDIS_VECTOR_MODE: str = "auto"

    # --- Java 后端 ---
    JAVA_BACKEND_URL: str = "http://localhost:8080"

    # --- HTTP 代理（访问 Binance 等受限 API）---
    PROXY_ENABLE: bool = False
    PROXY_HOST: str = "127.0.0.1"
    PROXY_PORT: int = 7890

    # --- Langfuse 观测（可选，缺 key 时静默降级）---
    LANGFUSE_PUBLIC_KEY: str = ""
    LANGFUSE_SECRET_KEY: str = ""
    LANGFUSE_HOST: str = "https://cloud.langfuse.com"

    # --- 服务配置 ---
    PORT: int = 8000

    model_config = SettingsConfigDict(
        env_file=str(ENV_FILE),
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


config = AppSettings()
