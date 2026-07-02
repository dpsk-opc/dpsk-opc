package com.xiaomizhou.dpsk.workflow;

import lombok.Data;

import java.util.concurrent.*;

public class WorkflowConfirmManager {

    // 存储每个请求ID对应的专用队列，容量为1就够了
    private final ConcurrentMap<String, BlockingQueue<WorkflowConfirmDto>> pendingRequests = new ConcurrentHashMap<>();


    public void requestConfirm(String taskCode, String nodeId) throws InterruptedException {
        String key = toKey(taskCode, nodeId);
        BlockingQueue<WorkflowConfirmDto> queue = new ArrayBlockingQueue<>(1);
        pendingRequests.put(key, queue);
    }

    public WorkflowConfirmDto onConform(String taskCode, String nodeId, TimeUnit timeUnit, long timeout) throws InterruptedException {

        String key = toKey(taskCode, nodeId);
        BlockingQueue<WorkflowConfirmDto> queue = pendingRequests.get(key);
        if (queue != null) {
            return queue.poll(timeout, timeUnit);
        }

        return null;
    }


    // 处理前端返回的响应（由WebSocket消息处理器调用）
    public void confirm(String taskCode, String nodeId, WorkflowConfirmDto dto) throws InterruptedException {
        String key = toKey(taskCode, nodeId);
        BlockingQueue<WorkflowConfirmDto> queue = pendingRequests.get(key);
        if (queue != null) {
            queue.put(dto);
        }
    }

    private String toKey(String taskCode, String nodeId) {
        return taskCode + "_" + nodeId;
    }


    @Data
    public static class WorkflowConfirmDto {


        private String taskCode;

        private String nodeId;

        private String userId;

        /**
         * 确认结果：
         * 1-确认
         * 2-拒绝
         */
        private Integer confirmResult;


        /**
         * 确认原因
         */
        private String confirmReason;


        public static Integer CONFIRM_RESULT_CONFIRM = 1;
        public static Integer CONFIRM_RESULT_REJECT = 2;
    }

}
