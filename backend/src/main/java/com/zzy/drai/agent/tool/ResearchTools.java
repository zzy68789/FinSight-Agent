package com.zzy.drai.agent.tool;

import com.zzy.drai.config.AgentProperties;
import com.zzy.drai.rag.RagDocument;
import com.zzy.drai.rag.RagService;
import com.zzy.drai.search.SearchResult;
import com.zzy.drai.search.SearchService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 暴露给 ReAct 调研 agent 的工具集。由 LLM 决定调用哪个工具、调用几次、何时已收集到足够证据，
 * 替代原先 ResearcherNode 里"先检索再按需搜索"的硬编码顺序。
 *
 * <p>每次调用都记录进线程级 {@link ToolCallLog}，使节点能重建子任务埋点和原始证据列表，
 * 供 SSE / 持久化使用。
 */
@Component
public class ResearchTools {

    private final RagService ragService;
    private final SearchService searchService;
    private final AgentProperties properties;

    public ResearchTools(RagService ragService, SearchService searchService, AgentProperties properties) {
        this.ragService = ragService;
        this.searchService = searchService;
        this.properties = properties;
    }

    @Tool("检索本地知识库（用户上传的 PDF 文档）。当问题可能在已上传资料中找到答案时优先使用。返回相关文档片段；若无相关内容会明确告知。")
    public String searchLocalKnowledge(String query) {
        List<RagDocument> docs = ragService.retrieve(query, properties.getLocalTopK());
        List<String> evidence = new ArrayList<>();
        for (RagDocument doc : docs) {
            if (doc.score() >= properties.getLocalRelevanceThreshold()) {
                evidence.add(localEvidence(doc));
            }
        }
        record("searchLocalKnowledge", query, evidence);
        if (evidence.isEmpty()) {
            return "本地知识库中未找到与「" + query + "」相关的内容。";
        }
        return String.join("\n\n", evidence);
    }

    @Tool("快速探查本地知识库里与某主题相关的资料概况（只返回来源和简短摘要，不返回完整内容）。用于在规划阶段判断本地有哪些资料，从而决定子任务方向。")
    public String probeKnowledge(String topic) {
        List<RagDocument> docs = ragService.retrieve(topic, properties.getLocalTopK());
        List<String> hints = new ArrayList<>();
        for (RagDocument doc : docs) {
            if (doc.score() >= properties.getLocalRelevanceThreshold()) {
                String snippet = doc.content().length() > 120 ? doc.content().substring(0, 120) : doc.content();
                hints.add("- " + doc.source() + "（score=" + doc.score() + "）：" + snippet.replaceAll("\\s+", " "));
            }
        }
        record("probeKnowledge", topic, hints);
        if (hints.isEmpty()) {
            return "本地知识库中没有与「" + topic + "」明显相关的资料，规划时应更多依赖联网检索方向。";
        }
        return "本地知识库中与「" + topic + "」相关的资料概况：\n" + String.join("\n", hints);
    }

    @Tool("联网搜索获取最新或外部信息。当本地知识库无法回答，或问题涉及实时、外部信息时使用。返回带来源和 URL 的搜索结果。")
    public String searchWeb(String query) {
        List<SearchResult> results = searchService.search(query, properties.getWebTopK());
        List<String> evidence = new ArrayList<>();
        for (SearchResult result : results) {
            if ("fallback".equalsIgnoreCase(result.source())) {
                continue;
            }
            evidence.add(webEvidence(query, result));
        }
        record("searchWeb", query, evidence);
        if (evidence.isEmpty()) {
            return "联网搜索未返回与「" + query + "」相关的有效结果。";
        }
        return String.join("\n\n", evidence);
    }

    private void record(String tool, String query, List<String> evidence) {
        ToolCallLog log = ToolCallLog.current();
        if (log != null) {
            log.record(tool, query, evidence);
        }
    }

    private String localEvidence(RagDocument doc) {
        return "### LOCAL evidence (" + doc.source() + ", score=" + doc.score() + ")\n" + doc.content();
    }

    private String webEvidence(String query, SearchResult result) {
        return "### WEB evidence (" + query + ")\n"
                + "- source: " + result.source() + "\n"
                + "- title: " + result.title() + "\n"
                + "- url: " + result.url() + "\n"
                + result.content();
    }
}
