package com.xiaomizhou.dpsk.memory.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.xiaomizhou.dpsk.db.FileService;
import com.xiaomizhou.dpsk.db.dto.FileRecordDto;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.db.dao.ChatMessageDao;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.memory.config.MemoryKey;
import com.xiaomizhou.dpsk.memory.dto.AiThinkingMsgDto;
import com.xiaomizhou.dpsk.memory.dto.ToolMsgDto;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.xiaomizhou.dpsk.utils.SequenceUtils.UUIDSequenceGenerator.CHAT_MESSAGE_PREFIX;

/**
 * MessageRepository 实现，基于 opc-im 现有的 t_chat_message 表。
 * <p>
 * 通过 ConversationDao 将会话 code 转换为 sender/receiver 对来查询消息。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageRepositoryImpl implements MessageRepository {

    private final ConversationDao conversationDao;

    private final ChatMessageDao chatMessageDao;


    private final FileService fileService;

    private static final Set<String> ALLOW_SUFFIX = Sets.newHashSet(".txt", ".md", ".log",".java",".py");

    @Override
    public synchronized List<ChatMessage> findTopByConversationAndAgent(String conversationCode, String ownerCode, int limit) {
        Conversation conv = conversationDao.getOneByCode(conversationCode);
        if (conv == null) {
            log.debug("Conversation not found: {}", conversationCode);
            return List.of();
        }

        String userCode = conv.getOwnerCode();
        String agentCode = conv.getTargetCode();

        List<com.xiaomizhou.dpsk.db.model.ChatMessage> messages = chatMessageDao.list(
                Wrappers.<com.xiaomizhou.dpsk.db.model.ChatMessage>lambdaQuery()
                        .ne(com.xiaomizhou.dpsk.db.model.ChatMessage::getStatus, "IGNORE")
                        .and(w -> w.and(w1 -> {
                            w1.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getSenderCode, userCode);
                            w1.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, agentCode);
                        }).or(w2 -> {
                            w2.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getSenderCode, agentCode);
                            w2.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, userCode);
                        }))
                        .orderByDesc(com.xiaomizhou.dpsk.db.model.ChatMessage::getId)
                        .last("LIMIT " + limit));

        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }

        // 时间正序
        Collections.reverse(messages);
        justMsg(messages);

        return messages.stream()
                .map(this::toChatMessage)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<ChatMessage> findTopGroupMessages(String groupCode, String agentCode, int limit) {
        if (StringUtils.isBlank(groupCode)) {
            return List.of();
        }

        List<com.xiaomizhou.dpsk.db.model.ChatMessage> messages = chatMessageDao.list(
                Wrappers.<com.xiaomizhou.dpsk.db.model.ChatMessage>lambdaQuery()
                        .eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, groupCode)
                        .eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getConversationType, "GROUP")
                        .ne(com.xiaomizhou.dpsk.db.model.ChatMessage::getStatus, "IGNORE")
                        .orderByDesc(com.xiaomizhou.dpsk.db.model.ChatMessage::getId)
                        .last("LIMIT " + limit));

        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }

        Collections.reverse(messages);

        justMsg(messages);
        return messages.stream()
                .map(this::toChatMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public ChatMessage findByCode(String messageCode) {
        if (StringUtils.isBlank(messageCode)) {
            return null;
        }
        com.xiaomizhou.dpsk.db.model.ChatMessage msg = chatMessageDao.getOne(
                Wrappers.<com.xiaomizhou.dpsk.db.model.ChatMessage>lambdaQuery()
                        .eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getCode, messageCode));
        return msg != null ? toChatMessage(msg) : null;
    }

    @Override
    public List<ChatMessage> findContext(String messageCode, int contextSize) {
        if (StringUtils.isBlank(messageCode)) {
            return List.of();
        }

        com.xiaomizhou.dpsk.db.model.ChatMessage target = chatMessageDao.getOne(
                Wrappers.<com.xiaomizhou.dpsk.db.model.ChatMessage>lambdaQuery()
                        .eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getCode, messageCode));
        if (target == null) {
            return List.of();
        }

        // 查找前后各 contextSize 条消息（同一对话中）
        // 通过 sender/receiver 对确定会话范围
        String senderCode = target.getSenderCode();
        String receiverCode = target.getReceiverCode();
        String conversationType = target.getConversationType();

        List<com.xiaomizhou.dpsk.db.model.ChatMessage> allContext = chatMessageDao.list(
                Wrappers.<com.xiaomizhou.dpsk.db.model.ChatMessage>lambdaQuery()
                        .ne(com.xiaomizhou.dpsk.db.model.ChatMessage::getStatus, "IGNORE")
                        .and(w -> w.and(w1 -> {
                            w1.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getSenderCode, senderCode);
                            w1.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, receiverCode);
                        }).or(w2 -> {
                            w2.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getSenderCode, receiverCode);
                            w2.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, senderCode);
                        }))
                        .eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getConversationType, conversationType)
                        .orderByAsc(com.xiaomizhou.dpsk.db.model.ChatMessage::getId));

        // 找到目标消息的索引
        int targetIdx = -1;
        for (int i = 0; i < allContext.size(); i++) {
            if (messageCode.equals(allContext.get(i).getCode())) {
                targetIdx = i;
                break;
            }
        }

        if (targetIdx < 0) {
            return List.of(toChatMessage(target));
        }

        int from = Math.max(0, targetIdx - contextSize);
        int to = Math.min(allContext.size(), targetIdx + contextSize + 1);

        return allContext.subList(from, to).stream()
                .map(this::toChatMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void saveMessages(MemoryKey memoryKey, List<ChatMessage> messages) {
        // 消息已在 ChatService 中保存，此处仅记录日志
        log.debug("saveMessages called with {} messages (skipped, already saved by ChatService)", messages.size());

        UserMessage lastUserMsg = null;
        for (int i = messages.size() - 1; i > 0; i--) {
            ChatMessage last = messages.get(i);
            if (last instanceof UserMessage) {
                lastUserMsg = (UserMessage) last;
                break;
            }
        }

        if (Objects.isNull(lastUserMsg)) {
            log.warn("can not find any last user message from message.");
        }

        String userMsgCode = Objects.isNull(lastUserMsg) || Objects.isNull(lastUserMsg.attributes()) ? "" : MapUtils.getString(lastUserMsg.attributes(),"code");


        // 用户消息在ChatService已经保存过了
        messages = messages.stream()
                .filter(Objects::nonNull)
                .filter(message -> message instanceof ToolExecutionResultMessage || message instanceof AiMessage)
                .distinct()
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(messages) || Objects.isNull(memoryKey)) {
            return;
        }


        Conversation conv = conversationDao.getOneByCode(memoryKey.getConversationCode());

        messages.forEach(msg -> {


            com.xiaomizhou.dpsk.db.model.ChatMessage model = new com.xiaomizhou.dpsk.db.model.ChatMessage();

            ToolMsgDto tool = new ToolMsgDto();

            String code = SequenceUtils.generator().next(CHAT_MESSAGE_PREFIX);
            model.setCode(code);

            if (msg instanceof ToolExecutionResultMessage) {

                ToolExecutionResultMessage d = (ToolExecutionResultMessage) msg;

                Map<String, Object> attributes = d.attributes();
                if (StringUtils.isNotBlank(MapUtils.getString(attributes, "code"))) {
                    return;
                }

                if (Objects.isNull(attributes)) {
                    tool.setAttributes(Map.of("code", code));
                }

                tool.setId(d.id());
                tool.setToolName(d.toolName());

                tool.setIsError(d.isError());
                tool.setContents(d.contents().stream().filter(c -> c.type().equals(ContentType.TEXT)).map(t -> {
                    ToolMsgDto.Content c = new ToolMsgDto.Content();
                    c.setText(((TextContent) t).text());
                    c.setType(t.type().name());
                    return c;
                }).collect(Collectors.toList()));

                model.setMessageType("TOOL");
                model.setContent(JsonUtils.toJson(tool));
            } else {
                AiMessage am = (AiMessage) msg;
                Map<String, Object> attributes = am.attributes();
                if (StringUtils.isNotBlank(MapUtils.getString(attributes, "code"))) {
                    return;
                }

                if (Objects.isNull(attributes)) {
                    tool.setAttributes(Map.of("code", code));
                }

                model.setMessageType("THINKING");

                AiThinkingMsgDto thinking = new AiThinkingMsgDto();

                thinking.setThinking(am.thinking());

                ToolMsgDto.Content c = new ToolMsgDto.Content();
                c.setType("text");
                c.setText(am.text());
                thinking.setContent(c);
                thinking.setAttributes(am.attributes());
                List<ToolExecutionRequest> requests = am.toolExecutionRequests();
                thinking.setRequests(CollectionUtils.isEmpty(requests) ? List.of() : requests.stream().map(r -> {
                    AiThinkingMsgDto.ToolExecutionRequest req = new AiThinkingMsgDto.ToolExecutionRequest();
                    req.setId(r.id());
                    req.setName(r.name());
                    req.setArguments(r.arguments());
                    return req;
                }).collect(Collectors.toList()));

                model.setContent(JsonUtils.toJson(thinking));
            }

            model.setContentType(0);
            if (memoryKey.isGroupChat()) {
                model.setSenderCode(conv.getOwnerCode());
                model.setReceiverCode(conv.getTargetCode());
            } else {
                model.setSenderCode(conv.getTargetCode());
                model.setReceiverCode(conv.getOwnerCode());
            }
            model.setConversationType(memoryKey.isGroupChat() ? "GROUP" : "SINGLE");
            model.setCreateTime(new Date());
            model.setUpdateTime(new Date());
            model.setConversationCode(conv.getCode());
            model.setRelateUserMessageCode(userMsgCode);

            chatMessageDao.save(model);
        });
    }

    @Override
    public List<String> findMessageCodesByConversationAndAgent(String conversationCode, String ownerCode, int limit) {
        Conversation conv = conversationDao.getOneByCode(conversationCode);
        if (conv == null) {
            return List.of();
        }

        String userCode = conv.getOwnerCode();
        String agentCode = conv.getTargetCode();

        List<com.xiaomizhou.dpsk.db.model.ChatMessage> messages = chatMessageDao.list(
                Wrappers.<com.xiaomizhou.dpsk.db.model.ChatMessage>lambdaQuery()
                        .ne(com.xiaomizhou.dpsk.db.model.ChatMessage::getStatus, "IGNORE")
                        .and(w -> w.and(w1 -> {
                            w1.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getSenderCode, userCode);
                            w1.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, agentCode);
                        }).or(w2 -> {
                            w2.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getSenderCode, agentCode);
                            w2.eq(com.xiaomizhou.dpsk.db.model.ChatMessage::getReceiverCode, userCode);
                        }))
                        .orderByDesc(com.xiaomizhou.dpsk.db.model.ChatMessage::getId)
                        .last("LIMIT " + limit));

        Collections.reverse(messages);
        return messages.stream()
                .map(com.xiaomizhou.dpsk.db.model.ChatMessage::getCode)
                .collect(Collectors.toList());
    }

    private void justMsg(List<com.xiaomizhou.dpsk.db.model.ChatMessage> messages) {

        if (CollectionUtils.isEmpty(messages)) {
            return;
        }


        if(1 == messages.size() && messages.get(0).getMessageType().equals(MessageType.USER.getValue())){
            messages = List.of();
        }

        // 获取最后一条消息的 code
        int size = messages.size();
        com.xiaomizhou.dpsk.db.model.ChatMessage last = messages.get(size - 1);
        if (!MessageType.USER.getValue().equalsIgnoreCase(last.getMessageType())) {
            return;
        }

        // 去掉最后一个 UserMessage（如果存在），原因是Langchain4j在构建UserMessage()会append一个消息，同一条数据也会从db查出来，导致有两条一模一样的消息发给LLM
        messages.remove(size - 1);

        String msgCode = last.getCode();
        List<File> files = fileService.getDiskFilesByMsgCode(msgCode, (file) -> {
            String suffix = file.getFileExtension();
            return ALLOW_SUFFIX.contains(suffix.toLowerCase());
        });

        if (CollectionUtils.isEmpty(files)) {
            return;
        }

        var tmp = messages;

        files.forEach(file -> {
            try {
                String str = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                String c = """
                        the file is uploaded by user. 
                        fileName:%s,
                        fileContent:%s
                        """.formatted(file.getName(), str);
                com.xiaomizhou.dpsk.db.model.ChatMessage cm = new com.xiaomizhou.dpsk.db.model.ChatMessage();
                BeanUtils.copyProperties(last, cm);
                cm.setContent(c);
                tmp.add(cm);
            } catch (Exception e) {
                log.warn("load file content failed.", e);
            }
        });

    }

    /**
     * 将 DB ChatMessage 转为 LangChain4j ChatMessage。
     * 约定：sender_code 为 user 的是 UserMessage，否则为 AiMessage。
     */
    private ChatMessage toChatMessage(com.xiaomizhou.dpsk.db.model.ChatMessage msg) {
        if (msg == null || msg.getContent() == null) {
            return null;
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("code", msg.getCode());
        List<Content> contents = Lists.newArrayList(TextContent.from(msg.getContent()));

        // default 或 user 类型的是用户消息
        if (Strings.CS.equals("USER", msg.getMessageType())) {

            return UserMessage.builder()
                    .contents(contents)
                    .attributes(attributes)
                    .build();
        }

        if (Strings.CS.equals("TOOL", msg.getMessageType())) {
            String content = msg.getContent();
            ToolMsgDto tool = JsonUtils.toObj(content, ToolMsgDto.class);

            if (Objects.isNull(tool)) {
                log.warn("tool is null, content: {}", content);
                return null;
            }

            Map<String, Object> attr = tool.getAttributes();
            if (MapUtils.isEmpty(attr)) {
                attr = new HashMap<>();
            }
            attr.putAll(attributes);
            return ToolExecutionResultMessage.builder()
                    .id(tool.getId())
                    .toolName(tool.getToolName())
                    .isError(tool.getIsError())
                    .attributes(attributes)
                    .contents(CollectionUtils.isEmpty(tool.getContents()) ? List.of() : tool.getContents().stream().map(c -> TextContent.from(c.getText())).collect(Collectors.toList()))
                    .build();
        }

        if (Strings.CI.equals("THINKING", msg.getMessageType())) {

            String content = msg.getContent();
            AiThinkingMsgDto thinking = JsonUtils.toObj(content, AiThinkingMsgDto.class);

            if (Objects.isNull(thinking) || CollectionUtils.isEmpty(thinking.getRequests())) {
                log.debug("tool is null or tool requests is empty, content: {}", content);
                return null;
            }

            Map<String, Object> attr = thinking.getAttributes();
            if (MapUtils.isEmpty(attr)) {
                attr = new HashMap<>();
            }
            attr.putAll(attributes);


            return AiMessage.builder()
                    .text(thinking.getContent().getText())
                    .thinking(thinking.getThinking())
                    .toolExecutionRequests(CollectionUtils.isEmpty(thinking.getRequests()) ? List.of() : thinking.getRequests().stream().map(r -> ToolExecutionRequest.builder()
                            .id(r.getId())
                            .name(r.getName())
                            .arguments(r.getArguments())
                            .build()).collect(Collectors.toList()))
                    .attributes(attributes)
                    .build();
        }


        return AiMessage.builder()
                .text(msg.getContent())
                .attributes(attributes)
                .build();
    }
}
