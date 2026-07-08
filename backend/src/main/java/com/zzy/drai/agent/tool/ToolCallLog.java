package com.zzy.drai.agent.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次调用级别记录：某个调研 agent 选择调用了哪些工具，以及它们返回的原始证据。
 * AiServices 在调用线程上同步跑完整个 ReAct 循环，因此即便 {@link ResearchTools} 是共享单例，
 * 用 ThreadLocal 也能安全地隔离一次 agent 运行的工具活动。
 *
 * <p>用法：调用 agent 前先 {@code ToolCallLog.begin()}，之后用 {@link #current()} 读取，
 * 并始终在 finally 块中 {@code ToolCallLog.clear()}。
 */
public final class ToolCallLog {

    public record ToolCall(String tool, String query, int resultCount) {
    }

    private static final ThreadLocal<ToolCallLog> HOLDER = new ThreadLocal<>();

    private final List<ToolCall> calls = new ArrayList<>();
    private final List<String> evidence = new ArrayList<>();

    private ToolCallLog() {
    }

    public static void begin() {
        HOLDER.set(new ToolCallLog());
    }

    public static ToolCallLog current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    void record(String tool, String query, List<String> results) {
        calls.add(new ToolCall(tool, query, results.size()));
        evidence.addAll(results);
    }

    public List<ToolCall> calls() {
        return List.copyOf(calls);
    }

    public List<String> evidence() {
        return List.copyOf(evidence);
    }

    public boolean isEmpty() {
        return evidence.isEmpty();
    }
}
