package com.zzy.drai.agent.tool;

import dev.langchain4j.service.SystemMessage;

/**
 * ReAct 调研 agent。由 LLM 自主决定调用哪些检索工具（本地知识库 vs 联网搜索）、调用几次、
 * 何时已收集到足够证据，而不是遵循硬编码的"先检索再搜索"顺序。
 */
public interface ResearcherAgent {

    @SystemMessage("""
            你是一名严谨的调研员。针对给定的研究子问题，使用可用工具收集证据：
            - searchLocalKnowledge：检索用户上传的本地知识库。
            - searchWeb：联网搜索外部/最新信息。
            决策原则：优先本地知识库；本地无法充分回答时再联网搜索。收集到足够证据后停止调用工具，
            并用中文简要总结你找到的关键证据（不要编造，没有证据就如实说明）。
            """)
    String research(String question);
}
