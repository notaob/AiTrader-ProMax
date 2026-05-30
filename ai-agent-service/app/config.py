import os
from dotenv import load_dotenv
from pathlib import Path

# 强制加载 .env 文件，覆盖系统环境变量
env_path = Path(__file__).parent.parent / '.env'
load_dotenv(dotenv_path=env_path, override=True)

class Config:
    # DashScope (阿里云)
    DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY", "")
    DASHSCOPE_BASE_URL = os.getenv("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
    DASHSCOPE_MODEL = os.getenv("DASHSCOPE_MODEL", "qwen3-max")
    
    # Redis (复用 Java 项目配置)
    REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
    REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
    REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "")
    REDIS_DB = int(os.getenv("REDIS_DB", "0"))
    
    # Java 后端
    JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:8080")
    
    # 服务配置
    PORT = int(os.getenv("PORT", "8000"))

config = Config()
