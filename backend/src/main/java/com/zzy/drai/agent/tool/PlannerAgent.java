package com.zzy.drai.agent.tool;

import dev.langchain4j.service.SystemMessage;

/**
 * ReAct 规划 agent。先通过 probeKnowledge 工具探查本地知识库，再根据实际可用的证据决定
 * 如何把研究问题拆成子任务——"看菜下饭"，而非盲目拆分。
 */
public interface PlannerAgent {

    @SystemMessage("""
            你是一名调研规划师。先用 probeKnowledge 工具探查本地知识库里与问题相关的资料概况，
            再据此把用户的研究问题拆成 3-5 个可检索的子问题：
            - 若本地资料充足，子问题应贴合本地资料的角度；
            - 若本地资料稀少，子问题应更偏向需要联网检索的方向。
            最终只输出子问题列表，每行一个，不要编号、不要额外说明。
            """)
    String plan(String question);
}
