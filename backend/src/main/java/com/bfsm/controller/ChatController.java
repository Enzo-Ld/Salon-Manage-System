package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.ChatSendReq;
import com.bfsm.service.ChatService;
import com.bfsm.vo.ChatMessageVo;
import com.bfsm.vo.ChatSessionSummaryVo;
import com.bfsm.vo.PageResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/send")
    @Operation(summary = "Send chat message")
    public ApiResponse<Void> send(@Valid @RequestBody ChatSendReq req) {
        chatService.send(req);
        return ApiResponse.ok();
    }

    @GetMapping("/sessions")
    @Operation(summary = "List current user chat sessions")
    public ApiResponse<List<ChatSessionSummaryVo>> sessions() {
        return ApiResponse.ok(chatService.sessions());
    }

    @GetMapping("/sessions/page")
    @Operation(summary = "Paginated chat sessions")
    public ApiResponse<PageResultVo<ChatSessionSummaryVo>> sessionsPage(@RequestParam(required = false) Long pageNum,
                                                                        @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(chatService.pageSessions(pageNum, pageSize));
    }

    @GetMapping("/session/{sessionId}/messages")
    @Operation(summary = "List chat messages")
    public ApiResponse<List<ChatMessageVo>> messages(@PathVariable Long sessionId) {
        return ApiResponse.ok(chatService.messages(sessionId));
    }

    @GetMapping("/session/{sessionId}/messages/page")
    @Operation(summary = "Paginated chat messages")
    public ApiResponse<PageResultVo<ChatMessageVo>> messagesPage(@PathVariable Long sessionId,
                                                                 @RequestParam(required = false) Long pageNum,
                                                                 @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(chatService.pageMessages(sessionId, pageNum, pageSize));
    }
}
