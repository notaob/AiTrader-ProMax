from typing import TypedDict, Annotated, Sequence, List, Dict, Any
from langgraph.graph import StateGraph, END
from langchain_core.messages import BaseMessage, HumanMessage, AIMessage, ToolMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
import operator
import json

from app.llm import create_llm
from app.tools.market_tools import market_tools
from app.tools.analysis_tools import analysis_tools
from app.tools.rag_tools import rag_tools

# 合并所有工具
all_tools = market_tools + analysis_tools + rag_tools
tool_map = {t.name: t for t in all_tools}

# 定义状态
class TradingState(TypedDict):
    messages: Annotated[Sequence[BaseMessage], operator.add]
    user_id: str
    session_id: str
    intermediate_steps: List[Dict[str, Any]]
    mode: str  # "chat" 或 "strategy"
    context: Dict[str, Any]  # 上下文：state, summaries, memories, knowledge_chunks, system_prompt

# LLM 绑定工具
llm = create_llm().bind_tools(all_tools)

# 系统提示词 - 通用模式
SYSTEM_PROMPT = """你是一个专业的交易助手，可以帮助用户分析市场、查询价格、进行技术分析等。

你可以使用以下工具：
1. get_current_price - 获取当前价格
2. get_market_state - 获取市场状态（价格、成交量等）
3. get_technical_analysis - 获取技术分析指标（MA、RSI、支撑阻力等）
4. get_trading_suggestion - 基于技术分析给出交易建议
5. search_knowledge - 搜索交易知识库（策略方法、概念术语、用户笔记文档）
6. add_to_knowledge_base - 添加新知识到知识库

工具选择原则：
- 知识性问题（交易概念、策略方法、用户保存的笔记文档）→ search_knowledge
- 实时行情数据 → get_current_price / get_market_state
- 技术分析 → get_technical_analysis / get_trading_suggestion

请根据用户的需求，选择合适的工具来获取信息，然后给出专业的分析和建议。"""

# 系统提示词 - 策略报告模式
STRATEGY_PROMPT = """你是一名专业的加密货币交易专家。请根据当前市场数据，制定一份详细的交易策略报告。

【格式要求 - 严格遵守】
1. 整篇返回必须是标准Markdown格式，方便前端React组件解析
2. 仅用 ## 标题和 - 列表，不要用表格（|符号）和emoji
3. 所有内容用文字段落或简单列表呈现

【报告结构】

## 1. 市场趋势分析
- 判断当前趋势（多头/空头/震荡）
- 分析关键技术指标（MA、RSI等）

## 2. 关键价位
- 支撑位：具体价格
- 阻力位：具体价格
- 重要价格节点说明

## 3. 交易建议
- 入场点位建议
- 止损点位设置
- 止盈目标设定
- 仓位管理建议

## 4. 风险提示
- 潜在风险因素
- 需要关注的市场信号

请基于实时市场数据给出专业、客观的分析建议。确保返回的是完整Markdown格式文本。
"""

async def agent_node(state: TradingState):
    """Agent 思考节点"""
    context = state.get("context", {}) or {}

    # 优先使用预构建的 system_prompt（由 build_prompt 生成，包含完整上下文）
    system_prompt = context.get("system_prompt")
    if not system_prompt:
        mode = state.get("mode", "chat")
        if mode == "strategy":
            system_prompt = STRATEGY_PROMPT
        else:
            system_prompt = SYSTEM_PROMPT

        # 追加上下文信息，转义花括号防止 ChatPromptTemplate 误解析
        state_info = context.get("state")
        summaries = context.get("summaries")

        if state_info:
            system_prompt += "\n\n当前会话状态："
            for key, value in state_info.items():
                val_str = str(value).replace("{", "{{").replace("}", "}}")
                system_prompt += f"\n- {key}: {val_str}"

        if summaries and len(summaries) > 0:
            system_prompt += "\n\n历史摘要："
            for summary in summaries:
                system_prompt += "\n" + str(summary).replace("{", "{{").replace("}", "}}")
    
    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        MessagesPlaceholder(variable_name="messages"),
    ])
    
    chain = prompt | llm
    response = await chain.ainvoke({"messages": state["messages"]})
    
    # 记录思考过程
    steps = state.get("intermediate_steps", [])
    steps.append({
        "step": len(steps) + 1,
        "type": "thought",
        "content": response.content if response.content else "调用工具..."
    })
    
    return {
        "messages": [response],
        "intermediate_steps": steps
    }

def should_continue(state: TradingState) -> str:
    """决定下一步：继续工具调用还是结束"""
    last_message = state["messages"][-1]
    if hasattr(last_message, 'tool_calls') and last_message.tool_calls:
        return "tools"
    return "end"

async def tools_node(state: TradingState):
    """工具执行节点"""
    last_message = state["messages"][-1]
    
    if not hasattr(last_message, 'tool_calls') or not last_message.tool_calls:
        return {"messages": []}
    
    tool_results = []
    steps = state.get("intermediate_steps", [])
    
    for tool_call in last_message.tool_calls:
        tool_name = tool_call.get("name", "")
        tool_args = tool_call.get("args", {})
        tool_id = tool_call.get("id", "")
        
        # 自动注入 user_id：如果工具需要 user_id 但 LLM 没传，从 state 补充
        if tool_name == "search_knowledge" and "user_id" not in tool_args:
            user_id_from_state = state.get("user_id", "0")
            try:
                tool_args["user_id"] = int(user_id_from_state)
            except (ValueError, TypeError):
                tool_args["user_id"] = 0
        
        # 记录工具调用
        steps.append({
            "step": len(steps) + 1,
            "type": "action",
            "tool": tool_name,
            "input": tool_args
        })
        
        # 执行工具
        if tool_name in tool_map:
            try:
                result = await tool_map[tool_name].ainvoke(tool_args)
                tool_results.append(ToolMessage(
                    content=str(result),
                    tool_call_id=tool_id
                ))
                
                # 记录观察结果
                steps.append({
                    "step": len(steps) + 1,
                    "type": "observation",
                    "tool": tool_name,
                    "output": str(result)[:200] + "..." if len(str(result)) > 200 else str(result)
                })
            except Exception as e:
                tool_results.append(ToolMessage(
                    content=f"工具执行错误: {str(e)}",
                    tool_call_id=tool_id
                ))
    
    return {
        "messages": tool_results,
        "intermediate_steps": steps
    }

def create_trading_graph():
    """创建交易分析工作流图"""
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
        {
            "tools": "tools",
            "end": END
        }
    )
    
    # 工具执行后返回 Agent
    workflow.add_edge("tools", "agent")
    
    return workflow.compile()

# 创建图实例
trading_graph = create_trading_graph()
