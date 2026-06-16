package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.AiConsultMessageReq;
import com.bfsm.dto.AiConsultSessionReq;
import com.bfsm.service.AiConsultService;
import com.bfsm.vo.AiConsultSessionVo;
import com.bfsm.vo.AiConsultTurnVo;
import com.bfsm.vo.PageResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai-consult")
@Tag(name = "AI Consult")
public class AiConsultController {
    private final AiConsultService aiConsultService;

    public AiConsultController(AiConsultService aiConsultService) {
        this.aiConsultService = aiConsultService;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload AI consult face image")
    public ApiResponse<String> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(aiConsultService.uploadImage(file));
    }

    @PostMapping("/sessions")
    @Operation(summary = "Create an AI consult session")
    public ApiResponse<AiConsultTurnVo> createSession(@RequestBody AiConsultSessionReq req) {
        return ApiResponse.ok(aiConsultService.createSession(req));
    }

    @GetMapping("/sessions/page")
    @Operation(summary = "Paginated current user AI consult sessions")
    public ApiResponse<PageResultVo<AiConsultSessionVo>> sessions(@RequestParam(required = false) Long pageNum,
                                                                  @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(aiConsultService.pageSessions(pageNum, pageSize));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get AI consult session detail")
    public ApiResponse<AiConsultSessionVo> sessionDetail(@PathVariable Long id) {
        return ApiResponse.ok(aiConsultService.sessionDetail(id));
    }

    @PostMapping("/sessions/{id}/messages")
    @Operation(summary = "Send message to AI consult session")
    public ApiResponse<AiConsultTurnVo> sendSessionMessage(@PathVariable Long id,
                                                           @Valid @RequestBody AiConsultMessageReq req) {
        return ApiResponse.ok(aiConsultService.sendSessionMessage(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one current user AI consult history record")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aiConsultService.deleteCurrentUserRecord(id);
        return ApiResponse.ok();
    }
}
