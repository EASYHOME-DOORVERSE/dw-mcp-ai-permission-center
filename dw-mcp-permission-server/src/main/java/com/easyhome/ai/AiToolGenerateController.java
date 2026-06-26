package com.easyhome.ai;

import com.easyhome.api.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 一键生成工具配置接口
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AiToolGenerateController {

    private final AiToolGenerateService aiToolGenerateService;

    /**
     * 根据 SQL 模板 AI 生成工具配置（同步，向后兼容）
     */
    @PostMapping("/tool-generate")
    public Result<AiToolGenerateResponse> generate(@RequestBody @Validated AiToolGenerateRequest request) {
        AiToolGenerateResponse resp = aiToolGenerateService.generate(request);
        return Result.ok(resp);
    }

    /**
     * 根据 SQL 模板 AI 生成工具配置（SSE 流式，逐字输出思考过程）
     *
     * SSE 事件类型：
     *   thinking      - 模型原始输出文本片段（逐字）
     *   schema_fetching - 正在查询表结构进度提示
     *   result        - 生成完成，携带完整 AiToolGenerateResponse JSON
     *   error         - 出错信息
     */
    @PostMapping(value = "/tool-generate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@RequestBody @Validated AiToolGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        aiToolGenerateService.generateStream(request, emitter);
        return emitter;
    }
}
