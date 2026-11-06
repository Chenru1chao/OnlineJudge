package com.chenru1chao.controller;

import com.chenru1chao.dto.UserMessageDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.AiService;
import com.chenru1chao.vo.AiDiagnosis;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    /** SSE 每帧只发一个单行 JSON，避免模型输出里的换行破坏事件格式 */
    private static final JsonMapper JSON = new JsonMapper();

    private final AiService aiService;

    /** 诊断某次提交，返回结构化卡片数据 */
    @PostMapping("/diagnose/{submitId}")
    public Result<AiDiagnosis> diagnose(@PathVariable Integer submitId) {
        return aiService.diagnosis(submitId);
    }

    /** 清空当前用户的会话记忆，前端点“新对话”时调用 */
    @PostMapping("/memory/clear")
    public Result<Void> clearMemory() {
        return aiService.clearMemory();
    }

    /** 流式推荐（SSE），每个 chunk 包成 {"c":"正文片段"} 的单行 JSON 直接透传给前端 */
    @PostMapping(value = "/recommend/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> recommendStream(@RequestBody UserMessageDTO userMessageDTO) {
        return aiService.recommendStream(userMessageDTO)
                .map(chunk -> write("c", chunk));
    }

    private static String write(String key, String value) {
        try {
            return JSON.writeValueAsString(Map.of(key, value));
        } catch (Exception e) {
            return "{\"e\":\"AI 生成中断\"}";
        }
    }
}

