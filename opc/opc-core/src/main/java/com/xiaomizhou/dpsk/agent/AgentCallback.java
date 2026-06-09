package com.xiaomizhou.dpsk.agent;

import com.xiaomizhou.dpsk.agent.event.AgentEvent;

/**
 * Agent 执行过程中的回调接口。
 * opc-core 调用此接口发射语义事件，opc-im 实现此接口进行协议翻译。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
public interface AgentCallback {

    /** 收到一个语义事件 */
    void onEvent(AgentEvent event);

    /** 执行正常结束 */
    default void onComplete() {
    }

    /** 执行异常终止 */
    default void onError(Throwable error) {
    }
}
