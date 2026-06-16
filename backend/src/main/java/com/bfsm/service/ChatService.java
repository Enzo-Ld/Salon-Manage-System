package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.ChatSendReq;
import com.bfsm.entity.ChatMessage;
import com.bfsm.entity.ChatSession;
import com.bfsm.entity.SysUser;
import com.bfsm.mapper.ChatMessageMapper;
import com.bfsm.mapper.ChatSessionMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.vo.ChatMessageVo;
import com.bfsm.vo.ChatSessionSummaryVo;
import com.bfsm.vo.PageResultVo;
import com.bfsm.websocket.ChatWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatWebSocketHandler chatWebSocketHandler;

    public ChatService(ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper,
                       SysUserMapper sysUserMapper, ChatWebSocketHandler chatWebSocketHandler) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.sysUserMapper = sysUserMapper;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Transactional
    public void send(ChatSendReq req) {
        Long sender = SecurityUtils.currentUserOrThrow().getUserId();
        persistMessage(sender, req.getReceiverId(), req.getContent());
    }

    @Transactional
    public void sendSystemMessage(Long senderId, Long receiverId, String content) {
        if (senderId == null || receiverId == null || senderId.equals(receiverId)) {
            return;
        }
        if (content == null || content.isBlank()) {
            return;
        }
        if (sysUserMapper.selectById(senderId) == null || sysUserMapper.selectById(receiverId) == null) {
            return;
        }
        persistMessage(senderId, receiverId, content);
    }

    public List<ChatSessionSummaryVo> sessions() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        List<ChatSession> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .and(wrapper -> wrapper.eq(ChatSession::getUserAId, userId).or().eq(ChatSession::getUserBId, userId))
                .orderByDesc(ChatSession::getUpdatedAt));
        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(sessions.stream()
                        .flatMap(session -> List.of(session.getUserAId(), session.getUserBId()).stream())
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, ChatMessage> latestMessages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .in(ChatMessage::getSessionId, sessions.stream().map(ChatSession::getId).toList())
                        .orderByDesc(ChatMessage::getId))
                .stream()
                .collect(Collectors.toMap(ChatMessage::getSessionId, Function.identity(), (first, second) -> first));

        return sessions.stream().map(session -> {
            Long otherUserId = session.getUserAId().equals(userId) ? session.getUserBId() : session.getUserAId();
            SysUser otherUser = users.get(otherUserId);
            ChatMessage latestMessage = latestMessages.get(session.getId());
            ChatSessionSummaryVo vo = new ChatSessionSummaryVo();
            vo.setId(session.getId());
            vo.setOtherUserId(otherUserId);
            vo.setOtherUserName(otherUser == null ? ("User " + otherUserId) : displayName(otherUser));
            vo.setLastMessage(latestMessage == null ? "" : latestMessage.getContent());
            vo.setUpdatedAt(session.getUpdatedAt());
            return vo;
        }).toList();
    }

    public PageResultVo<ChatSessionSummaryVo> pageSessions(Long pageNum, Long pageSize) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        Page<ChatSession> page = new Page<>(
                PageUtils.normalizePageNum(pageNum),
                PageUtils.normalizePageSize(pageSize)
        );
        Page<ChatSession> result = chatSessionMapper.selectPage(page, new LambdaQueryWrapper<ChatSession>()
                .and(wrapper -> wrapper.eq(ChatSession::getUserAId, userId).or().eq(ChatSession::getUserBId, userId))
                .orderByDesc(ChatSession::getUpdatedAt));
        return new PageResultVo<>(
                buildSessionVos(result.getRecords(), userId),
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages()
        );
    }

    public List<ChatMessageVo> messages(Long sessionId) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        ChatSession session = assertSessionMember(sessionId, userId);
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByAsc(ChatMessage::getCreatedAt));
        if (messages.isEmpty()) {
            return List.of();
        }

        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(messages.stream()
                        .map(ChatMessage::getSenderId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return messages.stream().map(message -> {
            ChatMessageVo vo = new ChatMessageVo();
            SysUser sender = users.get(message.getSenderId());
            vo.setId(message.getId());
            vo.setSessionId(message.getSessionId());
            vo.setSenderId(message.getSenderId());
            vo.setSenderName(sender == null ? ("User " + message.getSenderId()) : displayName(sender));
            vo.setReceiverId(message.getReceiverId());
            vo.setContent(message.getContent());
            vo.setCreatedAt(message.getCreatedAt());
            return vo;
        }).toList();
    }

    public PageResultVo<ChatMessageVo> pageMessages(Long sessionId, Long pageNum, Long pageSize) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        ChatSession session = assertSessionMember(sessionId, userId);
        Page<ChatMessage> page = new Page<>(
                PageUtils.normalizePageNum(pageNum),
                PageUtils.normalizePageSize(pageSize)
        );
        Page<ChatMessage> result = chatMessageMapper.selectPage(page, new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByDesc(ChatMessage::getCreatedAt)
                .orderByDesc(ChatMessage::getId));
        List<ChatMessage> records = result.getRecords();
        Map<Long, SysUser> users = records.isEmpty() ? Map.of() : sysUserMapper.selectBatchIds(records.stream()
                        .map(ChatMessage::getSenderId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<ChatMessageVo> messageVos = records.stream()
                .sorted((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
                .map(message -> {
                    ChatMessageVo vo = new ChatMessageVo();
                    SysUser sender = users.get(message.getSenderId());
                    vo.setId(message.getId());
                    vo.setSessionId(message.getSessionId());
                    vo.setSenderId(message.getSenderId());
                    vo.setSenderName(sender == null ? ("User " + message.getSenderId()) : displayName(sender));
                    vo.setReceiverId(message.getReceiverId());
                    vo.setContent(message.getContent());
                    vo.setCreatedAt(message.getCreatedAt());
                    return vo;
                })
                .toList();
        return new PageResultVo<>(
                messageVos,
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages()
        );
    }

    private ChatSession findOrCreateSession(Long senderId, Long receiverId) {
        ChatSession session = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .and(wrapper -> wrapper.eq(ChatSession::getUserAId, senderId)
                        .eq(ChatSession::getUserBId, receiverId)
                        .or()
                        .eq(ChatSession::getUserAId, receiverId)
                        .eq(ChatSession::getUserBId, senderId))
                .last("limit 1"));
        if (session != null) {
            return session;
        }

        ChatSession created = new ChatSession();
        created.setUserAId(senderId);
        created.setUserBId(receiverId);
        chatSessionMapper.insert(created);
        return created;
    }

    private void persistMessage(Long senderId, Long receiverId, String content) {
        ChatSession session = findOrCreateSession(senderId, receiverId);

        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content.trim());
        chatMessageMapper.insert(message);

        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);
        chatWebSocketHandler.pushTo(receiverId, message);
    }

    private ChatSession assertSessionMember(Long sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(404, "Chat session not found");
        }
        if (!userId.equals(session.getUserAId()) && !userId.equals(session.getUserBId())) {
            throw new BizException(403, "No permission to view this chat session");
        }
        return session;
    }

    private String displayName(SysUser user) {
        return user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername();
    }

    private List<ChatSessionSummaryVo> buildSessionVos(List<ChatSession> sessions, Long userId) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(sessions.stream()
                        .flatMap(session -> List.of(session.getUserAId(), session.getUserBId()).stream())
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, ChatMessage> latestMessages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .in(ChatMessage::getSessionId, sessions.stream().map(ChatSession::getId).toList())
                        .orderByDesc(ChatMessage::getId))
                .stream()
                .collect(Collectors.toMap(ChatMessage::getSessionId, Function.identity(), (first, second) -> first));

        return sessions.stream().map(session -> {
            Long otherUserId = session.getUserAId().equals(userId) ? session.getUserBId() : session.getUserAId();
            SysUser otherUser = users.get(otherUserId);
            ChatMessage latestMessage = latestMessages.get(session.getId());
            ChatSessionSummaryVo vo = new ChatSessionSummaryVo();
            vo.setId(session.getId());
            vo.setOtherUserId(otherUserId);
            vo.setOtherUserName(otherUser == null ? ("User " + otherUserId) : displayName(otherUser));
            vo.setLastMessage(latestMessage == null ? "" : latestMessage.getContent());
            vo.setUpdatedAt(session.getUpdatedAt());
            return vo;
        }).toList();
    }
}
