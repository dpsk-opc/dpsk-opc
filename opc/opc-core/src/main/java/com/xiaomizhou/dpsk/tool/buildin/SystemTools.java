package com.xiaomizhou.dpsk.tool.buildin;

import com.xiaomizhou.dpsk.tool.ToolMeta;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 系统信息工具，提供 JVM、操作系统、内存等运行环境信息。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/17
 */
@ToolMeta(value = "获取系统信息", level = "normal", category = "meta", tags = {"系统信息", "环境"})
public class SystemTools {

    @dev.langchain4j.agent.tool.Tool(name = "system_info", value = "获取当前系统运行环境信息")
    public String systemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        // ---- OS ----
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> osInfo = new LinkedHashMap<>();
        osInfo.put("name", os.getName());
        osInfo.put("arch", os.getArch());
        osInfo.put("version", os.getVersion());
        osInfo.put("available_processors", os.getAvailableProcessors());
        osInfo.put("system_load_average", String.format(Locale.US, "%.2f", os.getSystemLoadAverage()));
        info.put("os", osInfo);
//
//        // ---- JVM ----
//        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
//        Map<String, Object> jvmInfo = new LinkedHashMap<>();
//        jvmInfo.put("vm_name", runtime.getVmName());
//        jvmInfo.put("vm_vendor", runtime.getVmVendor());
//        jvmInfo.put("vm_version", runtime.getVmVersion());
//        jvmInfo.put("spec_version", runtime.getSpecVersion());
//        jvmInfo.put("start_time", runtime.getStartTime());
//        jvmInfo.put("uptime_ms", runtime.getUptime());
//        jvmInfo.put("uptime", formatDuration(runtime.getUptime()));
//        jvmInfo.put("input_arguments", runtime.getInputArguments());
//        info.put("jvm", jvmInfo);
//
//        // ---- Memory ----
//        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
//        Map<String, Object> memInfo = new LinkedHashMap<>();
//        memInfo.put("heap_init_mb", toMB(memory.getHeapMemoryUsage().getInit()));
//        memInfo.put("heap_used_mb", toMB(memory.getHeapMemoryUsage().getUsed()));
//        memInfo.put("heap_committed_mb", toMB(memory.getHeapMemoryUsage().getCommitted()));
//        memInfo.put("heap_max_mb", toMB(memory.getHeapMemoryUsage().getMax()));
//        memInfo.put("non_heap_init_mb", toMB(memory.getNonHeapMemoryUsage().getInit()));
//        memInfo.put("non_heap_used_mb", toMB(memory.getNonHeapMemoryUsage().getUsed()));
//        memInfo.put("non_heap_committed_mb", toMB(memory.getNonHeapMemoryUsage().getCommitted()));
//        memInfo.put("non_heap_max_mb", toMB(memory.getNonHeapMemoryUsage().getMax()));
//        info.put("memory", memInfo);
//
//        // ---- Threads ----
//        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
//        Map<String, Object> threadInfo = new LinkedHashMap<>();
//        threadInfo.put("thread_count", thread.getThreadCount());
//        threadInfo.put("peak_thread_count", thread.getPeakThreadCount());
//        threadInfo.put("daemon_thread_count", thread.getDaemonThreadCount());
//        threadInfo.put("total_started_thread_count", thread.getTotalStartedThreadCount());
//        info.put("threads", threadInfo);
//
//        // ---- 其他 ----
//        info.put("available_processors", Runtime.getRuntime().availableProcessors());
//        info.put("free_memory_mb", toMB(Runtime.getRuntime().freeMemory()));
//        info.put("total_memory_mb", toMB(Runtime.getRuntime().totalMemory()));
//        info.put("max_memory_mb", toMB(Runtime.getRuntime().maxMemory()));

        return toJson(info);
    }

    private static long toMB(long bytes) {
        if (bytes < 0) return bytes;
        return bytes / (1024 * 1024);
    }

    private static String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.toHours() % 24;
        long minutes = d.toMinutes() % 60;
        long seconds = d.getSeconds() % 60;
        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        }
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }

    @SuppressWarnings("unchecked")
    private String toJson(Object obj) {
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            int count = 0;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) obj).entrySet()) {
                if (count > 0) sb.append(",\n");
                sb.append("  \"").append(entry.getKey()).append("\": ");
                appendValue(sb, entry.getValue());
                count++;
            }
            sb.append("\n}");
            return sb.toString();
        }
        return String.valueOf(obj);
    }

    private void appendValue(StringBuilder sb, Object val) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof String) {
            sb.append("\"").append(val).append("\"");
        } else if (val instanceof Number || val instanceof Boolean) {
            sb.append(val);
        } else if (val instanceof Map) {
            sb.append(toJson(val));
        } else if (val instanceof Iterable) {
            sb.append("[");
            int i = 0;
            for (Object item : (Iterable<?>) val) {
                if (i > 0) sb.append(", ");
                appendValue(sb, item);
                i++;
            }
            sb.append("]");
        } else {
            sb.append("\"").append(val).append("\"");
        }
    }
}
