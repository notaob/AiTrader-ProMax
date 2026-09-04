"""提示词统一出口：graph / context_builder 都从这里取，禁止就地再写一份。"""

from app.prompts.strategy import STRATEGY_PROMPT
from app.prompts.system import SYSTEM_BASE, SYSTEM_PROMPT
from app.prompts.user_profile import USER_PROFILE_GUIDANCE

__all__ = ["SYSTEM_BASE", "SYSTEM_PROMPT", "USER_PROFILE_GUIDANCE", "STRATEGY_PROMPT"]
