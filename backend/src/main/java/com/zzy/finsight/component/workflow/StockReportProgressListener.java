package com.zzy.finsight.component.workflow;


/**
 * 股票报告执行进度监听器。监听器异常不得影响后台工作流。
 */
public interface StockReportProgressListener {
    void onStep(String step, Object data);

    void onDone();

    void onError(Throwable throwable);

    static StockReportProgressListener noop() {
        return new StockReportProgressListener() {
            @Override
            public void onStep(String step, Object data) {
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
