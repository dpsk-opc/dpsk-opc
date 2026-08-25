package com.xiaomizhou.dpsk.workflow.langgraph;

import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

/**
 * 轻量 {@link StateSerializer}：状态克隆只做字段透传，不进行 Java 深度序列化。
 *
 * <p>langgraph4j 默认的 {@code ObjectStreamStateSerializer} 会对 state 中的每个值执行
 * Java 序列化（cloneObject），而业务对象（{@link WorkflowContext} / AgentDef / 回调等）未必实现
 * {@code Serializable}，必然抛 {@code java.io.NotSerializableException}。
 *
 * <p>本序列化器针对 Phase 1 单进程内同步执行（无 Checkpointer）的场景：
 * 执行期 state 只包含 {@code context}（{@link WorkflowContext}，执行期共享单例）与
 * {@code routeId}（String）。克隆时 context 直接复用持有的实例引用，routeId 走文本透传。
 */
public class LightStateSerializer extends StateSerializer<LangGraphState> {

    /** 执行期共享的任务上下文（同一执行实例内唯一） */
    private final WorkflowContext context;

    public LightStateSerializer(WorkflowContext context) {
        super(LangGraphState::new);
        this.context = context;
    }

    @Override
    public void writeData(Map<String, Object> data, ObjectOutput out) throws IOException {
        out.writeInt(data.size());
        for (Map.Entry<String, Object> e : data.entrySet()) {
            Serializer.writeUTF(e.getKey(), out);
            Object v = e.getValue();
            if (v == null) {
                out.writeByte(0);
            } else if (v instanceof WorkflowContext) {
                // 不序列化上下文，读端用持有的引用还原
                out.writeByte(1);
            } else if (v instanceof String s) {
                out.writeByte(2);
                Serializer.writeUTF(s, out);
            } else {
                // 其他值（理论上 Phase 1 不会出现）走原生序列化
                out.writeByte(3);
                out.writeObject(v);
            }
        }
    }

    @Override
    public Map<String, Object> readData(ObjectInput in) throws IOException, ClassNotFoundException {
        int size = in.readInt();
        Map<String, Object> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = Serializer.readUTF(in);
            byte type = in.readByte();
            switch (type) {
                case 0 -> map.put(key, null);
                case 1 -> map.put(key, context);
                case 2 -> map.put(key, Serializer.readUTF(in));
                default -> map.put(key, in.readObject());
            }
        }
        return map;
    }
}
