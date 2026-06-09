package com.xiaomizhou.dpsk.task.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务消费者执行结果。
 *
 * @author eason - vipzhsh@163.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskConsumeResult {

    /** 是否成功 */
    private boolean success;

    /** 结果摘要 */
    private String result;

    /** 错误信息（失败时填写） */
    private String errorMessage;

    public static TaskConsumeResult ok(String result) {
        return TaskConsumeResult.builder()
                .success(true)
                .result(result)
                .build();
    }

    public static TaskConsumeResult fail(String errorMessage) {
        return TaskConsumeResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
