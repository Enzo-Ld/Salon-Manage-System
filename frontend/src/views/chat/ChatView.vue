<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '../../api/modules';
import type { ChatMessage, ChatSessionSummary } from '../../api/types';
import { useAuthStore } from '../../store/auth';
import { normalizePagePayload } from '../../utils/page';

const route = useRoute();
const auth = useAuthStore();

const wsState = ref('未连接');
const sessions = ref<ChatSessionSummary[]>([]);
const messages = ref<ChatMessage[]>([]);
const activeSessionId = ref<number | null>(null);
const text = ref('');
const sessionKeyword = ref('');
const messageKeyword = ref('');
const boxRef = ref<HTMLElement | null>(null);
const sessionLoading = ref(false);
const messageLoading = ref(false);
let socket: WebSocket | null = null;

const sessionPager = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
});

const messagePager = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0,
});

const routeReceiverId = computed(() => {
  const value = route.query.receiverId;
  if (typeof value !== 'string') return null;
  const parsed = Number(value);
  return Number.isNaN(parsed) ? null : parsed;
});

const routeDraft = computed(() => {
  const value = route.query.draft;
  return typeof value === 'string' ? value : '';
});

const routeProductId = computed(() => {
  const value = route.query.productId;
  return typeof value === 'string' ? value : '';
});

const routeAppointmentId = computed(() => {
  const value = route.query.appointmentId;
  return typeof value === 'string' ? value : '';
});

const routeServiceName = computed(() => {
  const value = route.query.serviceName;
  return typeof value === 'string' ? value : '';
});

const routeAppointmentTime = computed(() => {
  const value = route.query.appointmentTime;
  return typeof value === 'string' ? value : '';
});

const routeTechnicianName = computed(() => {
  const value = route.query.technicianName;
  return typeof value === 'string' ? value : '';
});

const activeSession = computed(() => sessions.value.find((item) => item.id === activeSessionId.value) ?? null);
const currentReceiverId = computed(() => activeSession.value?.otherUserId ?? routeReceiverId.value);
const filteredSessions = computed(() => {
  const keyword = sessionKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return sessions.value;
  }
  return sessions.value.filter((session) =>
    [session.otherUserName, session.lastMessage, session.updatedAt].some((value) =>
      String(value ?? '').toLowerCase().includes(keyword),
    ),
  );
});
const pagedSessions = computed(
  () => normalizePagePayload(filteredSessions.value, sessionPager.pageNum, sessionPager.pageSize).records,
);
const filteredMessages = computed(() => {
  const keyword = messageKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return messages.value;
  }
  return messages.value.filter((message) =>
    [message.senderName, message.content, message.createdAt].some((value) =>
      String(value ?? '').toLowerCase().includes(keyword),
    ),
  );
});
const pagedMessages = computed(
  () => normalizePagePayload(filteredMessages.value, messagePager.pageNum, messagePager.pageSize).records,
);

async function scrollToBottom() {
  await nextTick();
  if (boxRef.value) {
    boxRef.value.scrollTop = boxRef.value.scrollHeight;
  }
}

async function loadMessages(sessionId: number, pageNum = messagePager.pageNum) {
  messageLoading.value = true;
  try {
    const { data } = await api.chatMessages(sessionId);
    messages.value = Array.isArray(data.data) ? data.data : normalizePagePayload<ChatMessage>(data.data, 1, 100000).records;
    messagePager.total = filteredMessages.value.length;
    messagePager.pageNum = pageNum;
    await scrollToBottom();
  } catch {
    messages.value = [];
    messagePager.total = 0;
  } finally {
    messageLoading.value = false;
  }
}

async function pickSession(sessionId: number | null) {
  if (!sessionId) {
    activeSessionId.value = null;
    messages.value = [];
    messageKeyword.value = '';
    messagePager.total = 0;
    return;
  }
  activeSessionId.value = sessionId;
  messageKeyword.value = '';
  messagePager.pageNum = 1;
  await loadMessages(sessionId, 1);
}

async function loadSessions(preferredSessionId: number | null = activeSessionId.value, keepMessage = true) {
  sessionLoading.value = true;
  try {
    const { data } = await api.chatSessions();
    sessions.value = Array.isArray(data.data)
      ? data.data
      : normalizePagePayload<ChatSessionSummary>(data.data, 1, 100000).records;
    sessionPager.total = filteredSessions.value.length;

    let nextSessionId =
      preferredSessionId && sessions.value.some((item) => item.id === preferredSessionId) ? preferredSessionId : null;

    if (!nextSessionId && routeReceiverId.value) {
      nextSessionId = sessions.value.find((item) => item.otherUserId === routeReceiverId.value)?.id ?? null;
    }

    if (!nextSessionId && sessions.value.length > 0) {
      nextSessionId = sessions.value[0].id;
    }

    if (!keepMessage || activeSessionId.value !== nextSessionId) {
      await pickSession(nextSessionId);
    } else if (nextSessionId) {
      activeSessionId.value = nextSessionId;
    }

    if (!activeSession.value && routeDraft.value && !text.value) {
      text.value = routeDraft.value;
    }
  } catch {
    sessions.value = [];
    sessionPager.total = 0;
    activeSessionId.value = null;
  } finally {
    sessionLoading.value = false;
  }
}

async function send() {
  if (!currentReceiverId.value || !text.value.trim()) {
    return;
  }
  const targetReceiverId = currentReceiverId.value;
  await api.sendChat(targetReceiverId, text.value.trim());
  text.value = '';
  sessionPager.pageNum = 1;
  await loadSessions(activeSessionId.value, false);

  if (!activeSession.value) {
    const createdSession = sessions.value.find((item) => item.otherUserId === targetReceiverId);
    if (createdSession) {
      await pickSession(createdSession.id);
    }
  }
}

function handleSessionPageChange(pageNum: number) {
  sessionPager.pageNum = pageNum;
}

function handleMessagePageChange(pageNum: number) {
  if (!activeSessionId.value) return;
  messagePager.pageNum = pageNum;
  void scrollToBottom();
}

function handleMessageSizeChange(pageSize: number) {
  if (!activeSessionId.value) return;
  messagePager.pageSize = pageSize;
  messagePager.pageNum = 1;
  void scrollToBottom();
}

function handleSessionSearch() {
  sessionPager.pageNum = 1;
  sessionPager.total = filteredSessions.value.length;
}

function resetSessionSearch() {
  sessionKeyword.value = '';
  handleSessionSearch();
}

function handleMessageSearch() {
  messagePager.pageNum = 1;
  messagePager.total = filteredMessages.value.length;
  void scrollToBottom();
}

function resetMessageSearch() {
  messageKeyword.value = '';
  handleMessageSearch();
}

function connectSocket() {
  if (!auth.token) {
    return;
  }
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  socket = new WebSocket(`${protocol}://localhost:8080/ws/chat?token=${auth.token}`);
  socket.onopen = async () => {
    wsState.value = '已连接';
    try {
      await loadSessions();
    } catch {
      // noop
    }
  };
  socket.onclose = () => {
    wsState.value = '已断开';
  };
  socket.onmessage = async () => {
    const currentId = activeSessionId.value;
    try {
      await loadSessions(currentId, false);
    } catch {
      // noop
    }
  };
}

onMounted(() => {
  if (routeDraft.value) {
    text.value = routeDraft.value;
  }
  connectSocket();
});

onBeforeUnmount(() => {
  socket?.close();
  socket = null;
});

watch(
  () => route.fullPath,
  async () => {
    if (routeDraft.value && !activeSession.value) {
      text.value = routeDraft.value;
    }
    sessionPager.pageNum = 1;
    try {
      await loadSessions(activeSessionId.value, false);
    } catch {
      // noop
    }
  },
);

watch(sessionKeyword, () => {
  handleSessionSearch();
});

watch(messageKeyword, () => {
  handleMessageSearch();
});
</script>

<template>
  <div class="chat-shell">
    <aside class="panel session-panel" v-loading="sessionLoading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">会话列表</h2>
          <p class="panel-subtitle">连接状态：{{ wsState }}</p>
        </div>
      </div>

      <div v-if="routeProductId" class="consult-banner">
        当前正在咨询商品
        <span>#{{ routeProductId }}</span>
      </div>

      <div v-if="routeAppointmentId" class="consult-banner appointment">
        <strong>当前正在沟通预约事项</strong>
        <p>预约编号：{{ routeAppointmentId }}</p>
        <p v-if="routeServiceName">服务项目：{{ routeServiceName }}</p>
        <p v-if="routeAppointmentTime">预约时间：{{ routeAppointmentTime }}</p>
        <p v-if="routeTechnicianName">预约技师：{{ routeTechnicianName }}</p>
      </div>

      <div class="toolbar search-bar">
        <el-input
          v-model="sessionKeyword"
          placeholder="搜索联系人 / 最近消息"
          clearable
          @keyup.enter="handleSessionSearch"
        />
        <el-button text @click="resetSessionSearch">重置</el-button>
      </div>

      <el-empty v-if="filteredSessions.length === 0" description="没有匹配的聊天会话" />

      <div v-else class="session-list">
        <button
          v-for="session in pagedSessions"
          :key="session.id"
          class="session-card"
          :class="{ active: session.id === activeSessionId }"
          @click="pickSession(session.id)"
        >
          <strong>{{ session.otherUserName }}</strong>
          <p>{{ session.lastMessage || '暂无消息' }}</p>
          <small>{{ session.updatedAt }}</small>
        </button>
      </div>

      <div class="pagination-wrap">
        <el-pagination
          background
          size="small"
          layout="total, prev, pager, next"
          :current-page="sessionPager.pageNum"
          :page-size="sessionPager.pageSize"
          :total="filteredSessions.length"
          @current-change="handleSessionPageChange"
        />
      </div>
    </aside>

    <section class="panel message-panel" v-loading="messageLoading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">{{ activeSession?.otherUserName || '在线沟通' }}</h2>
          <p class="panel-subtitle">
            {{
              currentReceiverId
                ? `当前沟通对象用户 ID：${currentReceiverId}`
                : '请选择一个会话，或从预约 / 商品页面发起新的沟通'
            }}
          </p>
        </div>
      </div>

      <div class="toolbar search-bar message-search" v-if="activeSessionId">
        <el-input
          v-model="messageKeyword"
          placeholder="搜索当前会话消息"
          clearable
          @keyup.enter="handleMessageSearch"
        />
        <el-button text @click="resetMessageSearch">重置</el-button>
      </div>

      <div ref="boxRef" class="message-list">
        <el-empty
          v-if="filteredMessages.length === 0"
          :description="
            currentReceiverId
              ? messageKeyword
                ? '没有匹配的消息'
                : '发送第一条消息开始沟通'
              : '请先选择一个会话'
          "
        />
        <div
          v-for="message in pagedMessages"
          :key="message.id"
          class="message-row"
          :class="{ mine: message.senderId === auth.user?.userId }"
        >
          <div class="message-bubble">
            <strong>{{ message.senderName }}</strong>
            <p>{{ message.content }}</p>
            <small>{{ message.createdAt }}</small>
          </div>
        </div>
      </div>

      <div class="pagination-wrap message-pagination" v-if="activeSessionId">
        <el-pagination
          background
          size="small"
          layout="total, sizes, prev, pager, next"
          :current-page="messagePager.pageNum"
          :page-size="messagePager.pageSize"
          :page-sizes="[20, 50, 100]"
          :total="filteredMessages.length"
          @current-change="handleMessagePageChange"
          @size-change="handleMessageSizeChange"
        />
      </div>

      <div class="composer">
        <el-input
          v-model="text"
          type="textarea"
          :rows="3"
          placeholder="输入想沟通的内容，例如到店时间、服务准备事项、预约调整或售后问题"
          @keyup.enter.exact.prevent="send"
        />
        <div class="toolbar">
          <el-button type="primary" @click="send">发送消息</el-button>
          <el-button v-if="routeDraft" @click="text = routeDraft">恢复建议话术</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat-shell {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
}

.session-panel,
.message-panel {
  padding: 24px;
}

.consult-banner {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 244, 223, 0.76);
  margin-bottom: 16px;
}

.consult-banner span {
  font-weight: 700;
}

.consult-banner.appointment p {
  margin: 6px 0 0;
  color: var(--muted);
}

.search-bar {
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search-bar .el-input {
  width: min(280px, 100%);
}

.message-search .el-input {
  width: min(360px, 100%);
}

.session-list {
  display: grid;
  gap: 12px;
}

.session-card {
  border: 1px solid rgba(122, 86, 37, 0.14);
  background: rgba(255, 250, 243, 0.82);
  border-radius: 18px;
  padding: 14px;
  text-align: left;
  cursor: pointer;
}

.session-card.active {
  border-color: rgba(186, 140, 66, 0.4);
  background: rgba(255, 244, 223, 0.9);
}

.session-card p,
.session-card small {
  margin: 8px 0 0;
  color: var(--muted);
}

.message-list {
  min-height: 420px;
  max-height: 520px;
  overflow: auto;
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.message-row {
  display: flex;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-bubble {
  max-width: 70%;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 250, 243, 0.82);
}

.message-row.mine .message-bubble {
  background: rgba(255, 244, 223, 0.9);
}

.message-bubble p,
.message-bubble small {
  margin: 8px 0 0;
  color: var(--muted);
}

.composer {
  display: grid;
  gap: 12px;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.message-pagination {
  margin-top: 0;
  margin-bottom: 16px;
}

@media (max-width: 960px) {
  .chat-shell {
    grid-template-columns: 1fr;
  }

  .message-bubble {
    max-width: 100%;
  }
}
</style>
