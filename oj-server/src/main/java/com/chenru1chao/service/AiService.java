package com.chenru1chao.service;

import com.chenru1chao.dto.UserMessageDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.vo.AiDiagnosis;
import reactor.core.publisher.Flux;

public interface AiService {

    /** 诊断一次提交，返回结构化结论 */
    Result<AiDiagnosis> diagnosis(Integer submitId);

    /**
     * 流式推荐：模型每生成一小段就推给前端，用户不用干等十几秒。
     * 同步阶段的校验失败（未登录、限流、参数为空）直接抛 IllegalStateException。
     */
    Flux<String> recommendStream(UserMessageDTO userMessageDTO);

    /** 清空当前用户的会话记忆（前端“新对话”） */
    Result<Void> clearMemory();
}
