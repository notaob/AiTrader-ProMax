import operator
from collections.abc import Sequence
from typing import Annotated, Any, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, ToolMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, StateGraph

from app.llm import create_llm
from app.prompts import STRATEGY_PROMPT, SYSTEM_PROMPT
from app.tools.analysis_tools import analysis_tools
from app.tools.market_tools import market_tools
from app.tools.rag_tools import rag_tools

# 合并所有工具
all_tools = market_tools + analysis_tools + rag_tools
tool_map = {t.name: t for t in all_tools}


class TradingState(TypedDict, total=False):
    """LangGraph 显式状态。

    - messages:            消息流（HumanMessage / AIMessage / ToolMessage），operator.add 累积
    - current_message:     当前用户输入（主流程预置，节点可直接消费）
    - thought:             最近一次"思考"文本（可观测 / 后续 HITL 展示）
    - intermediate_steps:  规范化轨迹：thought / action / observation 依次编号
    - user_id/session_id:  会话归属
    - mode:                "chat" | "strategy"
    - context:             state / summaries / memories / knowledge_chunks / system_prompt
    """

    messages: Annotated[Sequence[BaseMessage], operator.add]
    user_id: str
    session_id: str
    current_message: str
    thought: str
    intermediate_steps: list[dict[str, Any]]
    mode: str
    context: dict[str, Any]


# LLM 绑定工具
llm = create_llm().bind_tools(all_tools)


def _record_step(steps: list[dict[str, Any]], **payload) -> list[dict[str, Any]]:
    """规范化中间步骤记录：自动递增 step 编号。"""
    steps.append({"step": len(steps) + 1, **payload})
    return steps


async def agent_node(state: TradingState):
    """Agent 思考节点"""
    context = state.get("context") or {}

    # 优先使用预构建的 system_prompt（由 build_prompt 生成，含完整上下文）
    system_prompt = context.get("system_prompt")
    if not system_prompt:
        # 兜底：graph 直连 /agent/execute 等场景下按 mode 选用统一提示词
        system_prompt = STRATEGY_PROMPT if state.get("mode") == "strategy" else SYSTEM_PROMPT
        state_info = context.get("state")
        summaries = context.get("summaries")

        if state_info:
            system_prompt += "\n\n当前会话状态："
            for key, value in state_info.items():
                val_str = str(value).replace("{", "{{").replace("}", "}}")
                system_prompt += f"\n- {key}: {val_str}"

        if summaries:
            system_prompt += "\n\n历史摘要："
            for summary in summaries:
                system_prompt += "\n" + str(summary).replace("{", "{{").replace("}", "}}")

    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        MessagesPlaceholder(variable_name="messages"),
    ])

    chain = prompt | llm
    response = await chain.ainvoke({"messages": state["messages"]})

    content = response.content if response.content else "调用工具..."
    steps = _record_step(list(state.get("intermediate_steps") or []), type="thought", content=content)

    return {
        "messages": [response],
        "intermediate_steps": steps,
        "thought": content,
    }


def should_continue(state: TradingState) -> str:
    """决定下一步：有工具调用则进 tools，否则结束。"""
    messages = state.get("messages") or []
    if not messages:
        return "end"
    last_message = messages[-1]
    if isinstance(last_message, AIMessage) and getattr(last_message, "tool_calls", None):
        return "tools"
    return "end"


async def tools_node(state: TradingState):
    """工具执行节点"""
    last_message = state["messages"][-1]

    if not isinstance(last_message, AIMessage) or not getattr(last_message, "tool_calls", None):
        return {"messages": []}

    tool_results = []
    steps = list(state.get("intermediate_steps") or [])

    for tool_call in last_message.tool_calls:
        tool_name = tool_call.get("name", "")
        tool_args = dict(tool_call.get("args", {}))
        tool_id = tool_call.get("id", "")

        # 自动注入 user_id：search_knowledge / add_to_knowledge_base 需要但 LLM 未必传
        if tool_name in {"search_knowledge", "add_to_knowledge_base"} and "user_id" not in tool_args:
            try:
                tool_args["user_id"] = int(state.get("user_id", "0"))
            except (ValueError, TypeError):
                tool_args["user_id"] = 0

        steps = _record_step(steps, type="action", tool=tool_name, input=tool_args)

        if tool_name in tool_map:
            try:
                result = await tool_map[tool_name].ainvoke(tool_args)
                tool_results.append(ToolMessage(content=str(result), tool_call_id=tool_id))

                summary = str(result)
                if len(summary) > 200:
                    summary = summary[:200] + "..."
                steps = _record_step(steps, type="observation", tool=tool_name, output=summary)
            except Exception as e:
                tool_results.append(ToolMessage(content=f"工具执行错误: {str(e)}", tool_call_id=tool_id))
                steps = _record_step(steps, type="observation", tool=tool_name, output=f"工具执行错误: {str(e)[:200]}")

    return {"messages": tool_results, "intermediate_steps": steps}


def create_trading_graph():
    """创建交易分析工作流图（带 MemorySaver checkpointer，预留 HITL 口）。"""
    workflow = StateGraph(TradingState)

    # 添加节点
    workflow.add_node("agent", agent_node)
    workflow.add_node("tools", tools_node)

    # 设置入口
    workflow.set_entry_point("agent")

    # 添加条件边
    workflow.add_conditional_edges(
        "agent",
        should_continue,
        {"tools": "tools", "end": END},
    )

    # 工具执行后返回 Agent
    workflow.add_edge("tools", "agent")

    # checkpointer：为后续 streaming / interrupt (HITL) 预留；
    # thread_id 由 make_run_config 按 session_id 生成（见 observability/langfuse.py）
    return workflow.compile(checkpointer=MemorySaver())


# 创建图实例
trading_graph = create_trading_graph()
