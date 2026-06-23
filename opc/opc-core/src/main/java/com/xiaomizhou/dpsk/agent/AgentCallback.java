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

    /**
     * 检查当前会话是否已被取消。
     * Pipeline 在执行循环中应定期轮询此方法，若返回 true 则终止执行。
     *
     * @return true 表示已取消，应终止当前会话
     */
    default boolean isCancelled() {
        return false;
    }
}
