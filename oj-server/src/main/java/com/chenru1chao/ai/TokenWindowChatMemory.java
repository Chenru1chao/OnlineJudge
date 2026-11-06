package com.chenru1chao.ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 按「token 预算 + 条数上限」裁剪的会话记忆。
 *
 * <p>为什么不直接用 MessageWindowChatMemory：
 * 它只按条数截断，一条消息可能很长（比如之前的整页候选题目），
 * 条数不多但内容早就把上下文撑爆了。这里按估算 token 从新到旧累加，
 * 超出预算就丢弃最旧的消息，同时保留最近一问答，避免把当前话题切断。</p>
 *
 * <p>存储交给 ChatMemoryRepository（本项目是 MySQL），所以重启不丢。</p>
 */
public class TokenWindowChatMemory implements ChatMemory {

    /** 每条消息的 role/分隔符等固定开销，经验值 */
    private static final int MESSAGE_OVERHEAD_TOKENS = 4;
    /** 至少保留最近几条，哪怕单条就超预算 */
    private static final int KEEP_AT_LEAST = 2;

    private final ChatMemoryRepository repository;
    private final int maxTokens;
    private final int maxMessages;

    public TokenWindowChatMemory(ChatMemoryRepository repository, int maxTokens, int maxMessages) {
        this.repository = repository;
        this.maxTokens = maxTokens;
        this.maxMessages = maxMessages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // saveAll 是「先删后插」，所以要把历史和新消息拼起来再存
        List<Message> all = new ArrayList<>(repository.findByConversationId(conversationId));
        all.addAll(messages);
        repository.saveAll(conversationId, trim(all));
    }

    @Override
    public List<Message> get(String conversationId) {
        return trim(repository.findByConversationId(conversationId));
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    /**
     * 从最新一条往前累加 token，超出预算就停；返回值一定是最新的连续后缀。
     */
    private List<Message> trim(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int start = messages.size();
        int tokens = 0;
        int count = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            int cost = estimateTokens(messages.get(i)) + MESSAGE_OVERHEAD_TOKENS;
            boolean mustKeep = count < KEEP_AT_LEAST;
            if (!mustKeep && (tokens + cost > maxTokens || count >= maxMessages)) {
                break;
            }
            tokens += cost;
            count++;
            start = i;
        }
        return List.copyOf(messages.subList(start, messages.size()));
    }

    static int estimateTokens(Message message) {
        return message == null ? 0 : estimateTokens(message.getText());
    }

    /**
     * 粗估 token：汉字约 1 字 1 token，英文/代码约 4 字符 1 token。
     * 宁可估高一点（偏保守），避免真超上下文。
     */
    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cjk++;
            }
        }
        int other = text.length() - cjk;
        return cjk + (other + 3) / 4;
    }
}
