package com.zzy.finsight.agent.event;

/**
 * 接收 Research Agent 动态事件并转发给 SSE 或后台恢复任务。
 */
public interface AgentEventListener {
    /** 接收一条运行事件。 */
    void onEvent(String eventType, Object data);

    /** 接收任务正常终止通知。 */
    void onDone();

    /** 接收任务异常终止通知。 */
    void onError(Throwable throwable);

    /** 返回不执行外部通知的监听器。 */
    static AgentEventListener noop() {
        return new AgentEventListener() {
            @Override
            public void onEvent(String eventType, Object data) {
            }

            @Override
            public void onDone() {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        };
    }
}
