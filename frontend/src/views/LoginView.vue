<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import heroImage from '../assets/login.png';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const store = useAuthStore();
const form = reactive({ username: '', password: '' });
const submitting = ref(false);

async function submit() {
  if (submitting.value) {
    return;
  }

  submitting.value = true;
  try {
    await store.doLogin(form.username, form.password);
    router.push(store.defaultRoute);
  } catch {
    // The request layer already shows the backend message.
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="auth-shell">
    <div class="ambient ambient-one" />
    <div class="ambient ambient-two" />

    <section class="brand-stage" :style="{ backgroundImage: `url(${heroImage})` }">
      <div class="stage-scrim" />
      <div class="stage-content">
        <p class="brand-kicker">Beauty Face & Salon Management</p>
        <h1>BFSM</h1>
        <p class="brand-copy">业务流程完整的美容美发预约管理系统</p>
        <div class="stage-points" aria-label="系统能力概览">
          <span>服务预约</span>
          <span>商城订单</span>
          <span>技师工作台</span>
          <span>经营统计</span>
        </div>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-heading">
        <p class="auth-kicker">Welcome ！！！！</p>
        <h2>BFSM平台</h2>
        <span>根据账号角色自动进入用户端、技师端或后台管理端。</span>
      </div>

      <el-form class="login-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" size="large" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <div class="auth-toolbar">
          <el-button class="login-button" type="warning" native-type="submit" :loading="submitting">进入系统</el-button>
          <el-button class="register-button" text :disabled="submitting" @click="$router.push('/register')">
            注册普通用户
          </el-button>
        </div>
      </el-form>

    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr);
  overflow: hidden;
  background:
    radial-gradient(circle at 82% 10%, rgba(37, 99, 235, 0.16), transparent 28%),
    linear-gradient(135deg, #f8fbff 0%, #edf4ff 48%, #ffffff 100%);
}

.ambient {
  position: absolute;
  z-index: -1;
  border-radius: 999px;
  filter: blur(6px);
  opacity: 0.8;
  animation: drift 10s ease-in-out infinite alternate;
}

.ambient-one {
  width: 280px;
  height: 280px;
  right: 28%;
  top: -120px;
  background: rgba(37, 99, 235, 0.18);
}

.ambient-two {
  width: 220px;
  height: 220px;
  right: -70px;
  bottom: 8%;
  background: rgba(96, 165, 250, 0.2);
  animation-delay: 1.4s;
}

.brand-stage {
  position: relative;
  min-height: 100vh;
  background-size: cover;
  background-position: center;
  animation: stageReveal 0.8s ease both;
}

.stage-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(15, 23, 42, 0.78), rgba(29, 78, 216, 0.34) 56%, rgba(219, 234, 254, 0.06)),
    radial-gradient(circle at 22% 20%, rgba(147, 197, 253, 0.26), transparent 30%);
}

.stage-content {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 22px;
  padding: clamp(32px, 7vw, 86px);
  color: #fff;
}

.brand-kicker,
.auth-kicker {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.brand-kicker {
  color: rgba(239, 246, 255, 0.86);
}

.stage-content h1 {
  margin: 0;
  font-size: clamp(78px, 12vw, 156px);
  line-height: 0.82;
  letter-spacing: 0.08em;
  text-shadow: 0 20px 60px rgba(0, 0, 0, 0.24);
}

.brand-copy {
  max-width: 520px;
  margin: 0;
  font-size: clamp(20px, 2.2vw, 31px);
  line-height: 1.45;
  font-weight: 700;
}

.stage-points {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.stage-points span {
  padding: 8px 12px;
  border-radius: 999px;
  color: rgba(239, 246, 255, 0.96);
  border: 1px solid rgba(219, 234, 254, 0.28);
  background: rgba(219, 234, 254, 0.14);
  backdrop-filter: blur(12px);
}

.login-panel {
  position: relative;
  display: grid;
  align-content: center;
  gap: 30px;
  width: min(520px, calc(100% - 48px));
  margin: 40px auto;
  padding: clamp(30px, 5vw, 52px);
  align-self: center;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 38px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 28px 88px rgba(30, 64, 175, 0.13);
  backdrop-filter: blur(22px);
  animation: panelRise 0.72s 0.1s ease both;
}

.login-panel::before {
  content: '';
  position: absolute;
  inset: 14px;
  z-index: -1;
  border-radius: 30px;
  border: 1px solid rgba(255, 255, 255, 0.55);
}

.auth-kicker {
  color: var(--accent-deep);
}

.login-heading {
  display: grid;
  gap: 10px;
}

.login-heading h2 {
  margin: 0;
  font-size: clamp(36px, 5vw, 56px);
  line-height: 1;
  letter-spacing: 0.02em;
}

.login-heading span,
.login-note span {
  color: var(--muted);
}

.login-form {
  display: grid;
  gap: 4px;
}

.login-form :deep(.el-form-item__label) {
  color: #334155;
  font-weight: 700;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 54px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.92);
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.12);
  transition: box-shadow 0.2s ease, transform 0.2s ease, background 0.2s ease;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.58), 0 14px 34px rgba(37, 99, 235, 0.13);
  transform: translateY(-1px);
}

.auth-toolbar {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.login-button {
  min-height: 52px;
  border: 0;
  border-radius: 18px;
  font-weight: 800;
  letter-spacing: 0.08em;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 18px 38px rgba(37, 99, 235, 0.24);
}

.login-button:hover {
  transform: translateY(-1px);
}

.register-button {
  color: var(--accent-deep);
  font-weight: 700;
}

.login-note {
  display: grid;
  gap: 4px;
  padding-top: 22px;
  border-top: 1px solid rgba(37, 99, 235, 0.12);
}

.login-note strong {
  color: var(--text);
}

@keyframes stageReveal {
  from {
    opacity: 0;
    transform: scale(1.03);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes panelRise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-18px, 22px, 0) scale(1.08);
  }
}

@media (max-width: 960px) {
  .auth-shell {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .brand-stage,
  .stage-content {
    min-height: 44svh;
  }

  .stage-content {
    padding: 28px;
  }

  .login-panel {
    width: calc(100% - 24px);
    margin: -18px auto 24px;
  }

  .auth-toolbar {
    grid-template-columns: 1fr;
  }

  .register-button {
    justify-self: center;
  }
}
</style>
