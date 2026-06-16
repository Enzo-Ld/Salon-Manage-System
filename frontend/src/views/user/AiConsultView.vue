<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { AiConsultMessage, AiConsultSession } from '../../api/types';
import { resolveAssetUrl } from '../../utils/avatar';

type PendingMessage = AiConsultMessage & { pending?: boolean };

const router = useRouter();
const fileInputRef = ref<HTMLInputElement | null>(null);
const chatBoxRef = ref<HTMLElement | null>(null);

const loading = ref(false);
const uploading = ref(false);
const sending = ref(false);
const thinking = ref(false);
const historyLoading = ref(false);
const historyDialogVisible = ref(false);

const currentSession = ref<AiConsultSession | null>(null);
const sessions = ref<AiConsultSession[]>([]);
const favoriteServiceIds = ref<number[]>([]);
const uploadedImageUrl = ref('');
const localPreviewUrl = ref('');
const inputText = ref('');
const pendingMessages = ref<PendingMessage[]>([]);
const historyKeyword = ref('');

const historyPager = reactive({
  pageNum: 1,
  pageSize: 6,
  total: 0,
});

const quickQuestions = [
  '我想放松，先做什么服务',
  '日常护理应该买什么',
  '预算有限服务和商品怎么搭配',
  '我想改善肤色状态',
];

const snapshot = computed(() => currentSession.value?.snapshot);
const messages = computed<PendingMessage[]>(() => [...(currentSession.value?.messages || []), ...pendingMessages.value]);
const previewImage = computed(() => {
  if (localPreviewUrl.value) return localPreviewUrl.value;
  if (uploadedImageUrl.value) return resolveAssetUrl(uploadedImageUrl.value);
  if (currentSession.value?.imageUrl) return resolveAssetUrl(currentSession.value.imageUrl);
  return '';
});
const hasUploadedPhoto = computed(() => Boolean(previewImage.value));
const canSend = computed(() => Boolean(inputText.value.trim() || uploadedImageUrl.value));
const filteredSessions = computed(() => {
  const keyword = historyKeyword.value.trim().toLowerCase();
  if (!keyword) return sessions.value;
  return sessions.value.filter((session) =>
    [session.title, session.previewText, session.snapshot?.summaryText, session.inputText]
      .filter(Boolean)
      .some((item) => String(item).toLowerCase().includes(keyword)),
  );
});
const historyEmptyText = computed(() => (historyKeyword.value.trim() ? '没有匹配的历史会话' : '暂无 AI 顾问历史会话'));
const stageText = computed(() => {
  const stage = snapshot.value?.stage || currentSession.value?.stage;
  if (stage === 'SERVICE_PLANNING') return '项目规划';
  if (stage === 'CARE_PLANNING') return '护理规划';
  return '形象探索';
});
const modeText = computed(() => {
  if (!currentSession.value) return '等待咨询开始';
  if (currentSession.value.analysisMode === 'REMOTE_API') return '智能在线分析';
  if (currentSession.value.analysisMode === 'GENERAL_CHAT') return 'BFSM AI 对话';
  return '辅助分析模式';
});
const composerHint = computed(() => {
  if (!currentSession.value && uploadedImageUrl.value) return '照片已添加，发送后会结合图片和文字一起分析。';
  if (!currentSession.value) return '美容、美发、放松、亮肤、补水等诉求都可以直接说，也可以上传照片补充上下文。';
  return '可以继续补充你的护理目标、预算、到店想法或居家保养需求，服务和商品推荐会随对话实时更新。';
});

function resolveUrl(url?: string | null) {
  return url ? resolveAssetUrl(url) : '';
}

async function scrollToBottom() {
  await nextTick();
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight;
  }
}

function triggerUpload() {
  fileInputRef.value?.click();
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  if (localPreviewUrl.value) URL.revokeObjectURL(localPreviewUrl.value);
  localPreviewUrl.value = URL.createObjectURL(file);
  uploading.value = true;
  try {
    const { data } = await api.aiConsultUpload(file);
    uploadedImageUrl.value = data.data;
    ElMessage.success('照片上传成功，可以发送给 AI 顾问分析。');
  } finally {
    uploading.value = false;
    target.value = '';
  }
}

function appendPending(role: 'USER' | 'ASSISTANT', content: string, imageUrl = '') {
  pendingMessages.value.push({
    id: `pending-${role}-${Date.now()}` as unknown as number,
    recordId: currentSession.value?.id || 0,
    role,
    content,
    imageUrl,
    createdAt: '刚刚',
    pending: true,
  });
  void scrollToBottom();
}

function clearPending() {
  pendingMessages.value = [];
  thinking.value = false;
}

function showFailure() {
  thinking.value = false;
  appendPending('ASSISTANT', 'AI 暂时没有成功返回结果，请稍后重试，或换一张更清晰、光线更自然的照片继续咨询。');
}

async function sendMessage() {
  const content = inputText.value.trim();
  const imageUrl = uploadedImageUrl.value;
  if (!content && !imageUrl) {
    ElMessage.warning('请先输入问题，或者上传照片后再发送。');
    return;
  }

  const userContent = content || '请结合这张照片，推荐适合的服务项目和护理商品。';
  appendPending('USER', imageUrl ? `${userContent}\n（已附加照片）` : userContent, imageUrl);
  thinking.value = true;
  inputText.value = '';
  sending.value = true;

  try {
    const { data } = currentSession.value
      ? await api.sendAiConsultSessionMessage(currentSession.value.id, { content: userContent, imageUrl })
      : await api.createAiConsultSession({ content: userContent, imageUrl });
    currentSession.value = data.data.session;
    clearPending();
    clearAttachment();
    await loadSessions(1);
    await scrollToBottom();
  } catch {
    showFailure();
  } finally {
    sending.value = false;
  }
}

async function sendQuick(question: string) {
  inputText.value = question;
  await sendMessage();
}

async function loadSessions(pageNum = historyPager.pageNum) {
  historyLoading.value = true;
  try {
    const { data } = await api.aiConsultSessions({ pageNum, pageSize: historyPager.pageSize });
    sessions.value = data.data.records;
    historyPager.pageNum = data.data.pageNum;
    historyPager.total = data.data.total;
  } catch {
    sessions.value = [];
    historyPager.total = 0;
  } finally {
    historyLoading.value = false;
  }
  if (!currentSession.value && sessions.value.length > 0) {
    await openSession(sessions.value[0], false);
  }
}

async function openSession(session: AiConsultSession, closeDialog = true) {
  loading.value = true;
  try {
    const { data } = await api.aiConsultSessionDetail(session.id);
    currentSession.value = data.data;
    clearPending();
    clearAttachment();
    if (closeDialog) {
      historyDialogVisible.value = false;
    }
    await scrollToBottom();
  } finally {
    loading.value = false;
  }
}

async function deleteSession(session: AiConsultSession) {
  await ElMessageBox.confirm('确认删除这条 AI 顾问会话吗？删除后无法恢复。', '删除会话', { type: 'warning' });
  await api.deleteAiConsultRecord(session.id);
  if (currentSession.value?.id === session.id) {
    startNew();
  }
  await loadSessions(historyPager.pageNum);
  if (sessions.value.length === 0 && historyPager.pageNum > 1) {
    await loadSessions(historyPager.pageNum - 1);
  }
  ElMessage.success('会话已删除。');
}

async function loadFavoriteIds() {
  try {
    const { data } = await api.favoriteServiceItemIds();
    favoriteServiceIds.value = data.data;
  } catch {
    favoriteServiceIds.value = [];
  }
}

async function toggleFavorite(serviceItemId: number) {
  const { data } = await api.toggleServiceFavorite(serviceItemId);
  favoriteServiceIds.value = data.data.active
    ? [...new Set([...favoriteServiceIds.value, serviceItemId])]
    : favoriteServiceIds.value.filter((id) => id !== serviceItemId);
  ElMessage.success(data.data.active ? '已收藏服务项目。' : '已取消收藏。');
}

function isFavorited(serviceItemId: number) {
  return favoriteServiceIds.value.includes(serviceItemId);
}

function goToService(serviceItemId: number) {
  router.push(`/services?focusServiceId=${serviceItemId}`);
}

function goToProduct(productId: number) {
  router.push(`/shop/${productId}`);
}

function goToShop() {
  router.push('/shop');
}

function clearAttachment() {
  uploadedImageUrl.value = '';
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value);
    localPreviewUrl.value = '';
  }
}

function startNew() {
  currentSession.value = null;
  inputText.value = '';
  historyDialogVisible.value = false;
  clearPending();
  clearAttachment();
}

function historyStageType(stage?: string) {
  if (stage === 'SERVICE_PLANNING') return 'warning';
  if (stage === 'CARE_PLANNING') return 'success';
  return 'info';
}

function historyStageLabel(stage?: string) {
  if (stage === 'SERVICE_PLANNING') return '项目规划';
  if (stage === 'CARE_PLANNING') return '护理规划';
  return '形象探索';
}

onMounted(() => {
  void Promise.all([loadSessions(), loadFavoriteIds()]);
});

onBeforeUnmount(() => {
  if (localPreviewUrl.value) URL.revokeObjectURL(localPreviewUrl.value);
});
</script>

<template>
  <div class="page-shell ai-page" v-loading="loading">
    <section class="hero-panel">
      <div>
        <span class="eyebrow">BFSM AI 服务与商品顾问</span>
        <h1>先正常聊需求，再落到服务与商品方案</h1>
        <p>你可以先和 BFSM AI 正常对话，也可以直接说放松、亮肤、补水、发质或居家护理诉求；当你问到服务或商品时，右侧推荐会实时更新。</p>
      </div>
      <div class="hero-status">
        <strong>{{ stageText }}</strong>
        <span>{{ modeText }}</span>
      </div>
    </section>

    <section class="consult-grid">
      <article class="panel chat-panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">AI 对话</h2>
            <p class="panel-subtitle">{{ composerHint }}</p>
          </div>
          <div class="header-actions">
            <el-button plain @click="historyDialogVisible = true">历史会话</el-button>
            <el-button plain @click="startNew">新咨询</el-button>
          </div>
        </div>

        <div ref="chatBoxRef" class="message-list">
          <div v-if="messages.length === 0 && !thinking" class="welcome-card">
            <span>开始咨询</span>
            <h3>像和专业顾问聊天一样开始</h3>
            <p>你可以先说“我想放松”“日常护理买什么”“服务和商品怎么搭配”，也可以上传一张清晰照片，让 AI 结合你的诉求继续细化建议。</p>
            <div class="starter-grid">
              <button type="button" @click="inputText = '我想放松，先做什么服务？'">我想放松，先做什么服务？</button>
              <button type="button" @click="inputText = '日常护理应该买什么商品？'">日常护理应该买什么商品？</button>
              <button type="button" @click="inputText = '预算有限，服务和商品怎么搭配？'">预算有限，服务和商品怎么搭配？</button>
            </div>
          </div>

          <div
            v-for="message in messages"
            :key="message.id"
            class="message-row"
            :class="{ mine: message.role === 'USER', pending: message.pending }"
          >
            <div class="speaker">{{ message.role === 'USER' ? '你' : 'AI 顾问' }}</div>
            <div class="bubble">
              <img v-if="message.imageUrl" :src="resolveUrl(message.imageUrl)" alt="咨询图片" />
              <p>{{ message.content }}</p>
              <small>{{ message.createdAt }}</small>
            </div>
          </div>

          <div v-if="thinking" class="message-row">
            <div class="speaker">AI 顾问</div>
            <div class="bubble thinking">
              <span></span><span></span><span></span>
              <em>正在结合你的诉求、照片和门店项目进行分析...</em>
            </div>
          </div>
        </div>

        <div class="quick-row">
          <el-button v-for="question in quickQuestions" :key="question" size="small" plain @click="sendQuick(question)">
            {{ question }}
          </el-button>
        </div>

        <div v-if="hasUploadedPhoto" class="attachment">
          <img :src="previewImage" alt="已上传照片" />
          <div>
            <strong>已附加照片</strong>
            <p>发送后 AI 会把这张照片作为当前咨询的重要上下文。</p>
          </div>
          <el-button text @click="clearAttachment">移除</el-button>
        </div>
        <div v-else class="attachment attachment-empty">
          <div class="attachment-empty-icon">+</div>
          <div>
            <strong>未上传照片</strong>
            <p>可以直接文字咨询，也可以上传照片补充上下文，让建议更贴合你当前的状态和诉求。</p>
          </div>
        </div>

        <div class="composer">
          <input ref="fileInputRef" type="file" accept=".jpg,.jpeg,.png,.webp" @change="handleFileChange" />
          <el-button plain :loading="uploading" @click="triggerUpload">上传照片</el-button>
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="例如：我想放松先做什么服务、日常护理买什么，或者预算有限怎么搭配？"
            @keyup.enter.exact.prevent="sendMessage"
          />
          <el-button type="warning" :loading="sending" :disabled="!canSend" @click="sendMessage">发送</el-button>
        </div>
      </article>

      <aside class="side-board">
        <section class="panel board-card">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">门店项目</h2>
              <p class="panel-subtitle">根据当前对话实时更新，只展示门店服务项目推荐。</p>
            </div>
          </div>

          <div v-if="snapshot?.serviceRecommendations.length" class="card-list">
            <div v-for="item in snapshot.serviceRecommendations" :key="item.serviceItemId" class="advice-card">
              <div class="card-top">
                <strong>{{ item.serviceName }}</strong>
                <span>￥{{ Number(item.price).toFixed(2) }}</span>
              </div>
              <p>{{ item.categoryName }} · {{ item.durationMin }} 分钟</p>
              <p>{{ item.reason }}</p>
              <div class="action-row">
                <el-button size="small" plain @click="toggleFavorite(item.serviceItemId)">
                  {{ isFavorited(item.serviceItemId) ? '取消收藏' : '收藏项目' }}
                </el-button>
                <el-button size="small" type="warning" @click="goToService(item.serviceItemId)">去预约</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无项目推荐" />
        </section>

        <section class="panel board-card">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">商城商品</h2>
              <p class="panel-subtitle">根据当前对话实时更新，只展示商城商品推荐。</p>
            </div>
            <el-button size="small" plain @click="goToShop">进入商城</el-button>
          </div>

          <div v-if="snapshot?.productRecommendations.length" class="card-list">
            <button
              v-for="item in snapshot.productRecommendations"
              :key="item.productId"
              type="button"
              class="product-card"
              @click="goToProduct(item.productId)"
            >
              <div>
                <strong>{{ item.productName }}</strong>
                <p>{{ item.categoryName }} · 库存 {{ item.stock }}</p>
                <small>{{ item.reason }}</small>
              </div>
              <span>￥{{ Number(item.price).toFixed(2) }}</span>
            </button>
          </div>
          <el-empty v-else description="暂无商品推荐" />
        </section>
      </aside>
    </section>

    <el-dialog v-model="historyDialogVisible" title="历史会话" width="720px" top="8vh">
      <div class="history-dialog" v-loading="historyLoading">
        <div class="history-dialog-toolbar">
          <el-input
            v-model="historyKeyword"
            class="history-search"
            clearable
            placeholder="搜索历史主题、摘要或咨询内容"
          />
          <el-button plain @click="startNew">新咨询</el-button>
        </div>

        <div class="history-list">
          <div
            v-for="session in filteredSessions"
            :key="session.id"
            class="history-item history-card"
            :class="{ active: currentSession?.id === session.id }"
          >
            <div class="history-main" @click="openSession(session)">
              <div class="history-topline">
                <strong>{{ session.title }}</strong>
                <small>{{ session.lastMessageAt || session.createdAt }}</small>
              </div>
              <p class="history-preview">{{ session.previewText || session.snapshot?.summaryText || session.inputText }}</p>
              <div class="tag-row history-tags">
                <el-tag size="small" :type="historyStageType(session.stage)" effect="plain">{{ historyStageLabel(session.stage) }}</el-tag>
                <el-tag size="small" effect="plain">{{ session.messageCount || 0 }} 条消息</el-tag>
                <el-tag
                  v-for="tag in (session.snapshot?.intentTags || []).slice(0, 2)"
                  :key="`${session.id}-${tag}`"
                  size="small"
                  effect="plain"
                >
                  {{ tag }}
                </el-tag>
              </div>
            </div>
            <el-button size="small" type="danger" plain @click="deleteSession(session)">删除</el-button>
          </div>
          <el-empty v-if="filteredSessions.length === 0" :description="historyEmptyText" />
        </div>

        <div class="pagination-wrap">
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="historyPager.pageNum"
            :page-size="historyPager.pageSize"
            :total="historyPager.total"
            @current-change="loadSessions"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-page {
  display: grid;
  gap: 22px;
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 34px;
  border-radius: 34px;
  color: #fff8ee;
  background:
    radial-gradient(circle at 80% 12%, rgba(255, 223, 169, 0.28), transparent 28%),
    linear-gradient(135deg, #2b2118 0%, #714d22 58%, #d99a38 100%);
  box-shadow: 0 24px 72px rgba(84, 51, 15, 0.16);
}

.hero-panel h1 {
  margin: 8px 0 10px;
  font-size: clamp(34px, 5vw, 58px);
  line-height: 1.04;
}

.hero-panel p {
  max-width: 760px;
  margin: 0;
  line-height: 1.8;
  color: rgba(255, 248, 238, 0.82);
}

.eyebrow {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.hero-status {
  min-width: 180px;
  display: grid;
  align-content: center;
  gap: 8px;
  padding: 20px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.consult-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(320px, 0.82fr);
  gap: 22px;
  align-items: start;
}

.chat-panel,
.board-card {
  padding: 24px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.message-list {
  min-height: 580px;
  max-height: 70vh;
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 18px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(211, 156, 78, 0.1), transparent 28%),
    rgba(255, 249, 240, 0.82);
}

.welcome-card {
  max-width: 680px;
  justify-self: center;
  align-self: center;
  padding: 30px;
  text-align: center;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(128, 88, 38, 0.1);
}

.welcome-card h3 {
  margin: 10px 0;
  font-size: 28px;
}

.starter-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 18px;
}

.starter-grid button {
  min-height: 70px;
  border: 1px solid rgba(128, 88, 38, 0.14);
  border-radius: 18px;
  background: rgba(255, 247, 235, 0.94);
  cursor: pointer;
  line-height: 1.55;
}

.message-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
}

.message-row.mine {
  grid-template-columns: minmax(0, 1fr) 72px;
}

.message-row.mine .speaker {
  order: 2;
  text-align: right;
}

.message-row.mine .bubble {
  order: 1;
  justify-self: end;
  background: rgba(255, 239, 207, 0.98);
}

.speaker {
  padding-top: 10px;
  color: var(--muted);
  font-weight: 700;
  font-size: 13px;
}

.bubble {
  max-width: 86%;
  padding: 14px 16px;
  border-radius: 20px 20px 20px 8px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(128, 88, 38, 0.08);
  box-shadow: 0 12px 32px rgba(72, 43, 12, 0.06);
}

.bubble img {
  width: 120px;
  height: 120px;
  display: block;
  margin-bottom: 10px;
  border-radius: 18px;
  object-fit: cover;
}

.bubble p,
.bubble small {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.75;
}

.bubble small {
  display: block;
  margin-top: 8px;
  color: var(--muted);
}

.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
}

.thinking span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-deep);
  animation: pulse 1s ease-in-out infinite;
}

.thinking span:nth-child(2) {
  animation-delay: 0.15s;
}

.thinking span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes pulse {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

.quick-row,
.action-row,
.tag-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.attachment {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  margin-top: 12px;
  border-radius: 22px;
  background: rgba(255, 248, 236, 0.8);
}

.attachment img {
  width: 86px;
  height: 86px;
  object-fit: cover;
  border-radius: 18px;
}

.attachment-empty {
  grid-template-columns: 56px minmax(0, 1fr);
}

.attachment-empty-icon {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px dashed rgba(128, 88, 38, 0.24);
  color: var(--accent-deep);
  font-size: 28px;
  font-weight: 700;
}

.attachment strong,
.attachment p {
  margin: 0;
}

.attachment p {
  margin-top: 6px;
  color: var(--muted);
  line-height: 1.65;
}

.composer {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
  padding: 12px;
  margin-top: 12px;
  border-radius: 24px;
  background: rgba(255, 253, 248, 0.9);
  border: 1px solid rgba(128, 88, 38, 0.1);
}

.composer input[type='file'] {
  display: none;
}

.side-board {
  position: sticky;
  top: 18px;
  display: grid;
  gap: 18px;
}

.card-list,
.history-list {
  display: grid;
  gap: 12px;
}

.advice-card,
.history-item,
.product-card {
  padding: 15px;
  border-radius: 20px;
  background: rgba(255, 249, 240, 0.82);
  border: 1px solid rgba(128, 88, 38, 0.1);
}

.advice-card p,
.product-card p,
.product-card small,
.history-item small,
.history-preview {
  margin: 0;
  color: var(--muted);
  line-height: 1.65;
}

.card-top,
.product-card,
.history-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.card-top span,
.product-card span {
  color: var(--accent-deep);
  font-weight: 800;
}

.product-card {
  width: 100%;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.history-dialog {
  display: grid;
  gap: 12px;
}

.history-dialog-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
}

.history-item.active {
  border-color: rgba(211, 156, 78, 0.5);
  background: rgba(255, 241, 217, 0.92);
}

.history-search {
  margin-bottom: 0;
}

.history-card {
  align-items: center;
}

.history-main {
  cursor: pointer;
  flex: 1;
  display: grid;
  gap: 8px;
}

.history-topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.history-preview {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.history-tags {
  margin-top: 0;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 1180px) {
  .hero-panel,
  .consult-grid {
    grid-template-columns: 1fr;
    display: grid;
  }

  .side-board {
    position: static;
  }
}

@media (max-width: 760px) {
  .hero-panel,
  .chat-panel,
  .board-card {
    padding: 18px;
  }

  .starter-grid,
  .composer,
  .attachment,
  .history-dialog-toolbar {
    grid-template-columns: 1fr;
  }

  .panel-header {
    flex-direction: column;
  }

  .message-row,
  .message-row.mine {
    grid-template-columns: 1fr;
  }

  .message-row.mine .speaker,
  .message-row.mine .bubble {
    order: initial;
    text-align: left;
    justify-self: stretch;
  }

  .bubble {
    max-width: 100%;
  }
}
</style>

