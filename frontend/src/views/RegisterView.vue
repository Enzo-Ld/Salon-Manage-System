<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { register } from '../api/auth';
import heroImage from '../assets/login.png';
import { isValidMainlandPhone, normalizePhone } from '../utils/validators';

const router = useRouter();
const submitting = ref(false);
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  roleCode: 'USER' as const,
});

async function submit() {
  if (submitting.value) {
    return;
  }

  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名');
    return;
  }
  if (!form.password.trim()) {
    ElMessage.warning('请输入密码');
    return;
  }
  if (!isValidMainlandPhone(form.phone)) {
    ElMessage.warning('手机号需为 11 位大陆手机号');
    return;
  }

  submitting.value = true;
  try {
    await register({
      username: form.username.trim(),
      password: form.password.trim(),
      nickname: form.nickname.trim() || undefined,
      phone: normalizePhone(form.phone) || undefined,
      roleCode: 'USER',
    });
    ElMessage.success('注册成功，请登录');
    router.push('/login');
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="register-shell">
    <section class="register-panel">
      <button class="brand-link" type="button" @click="$router.push('/login')">BFSM</button>

      <div class="register-heading">
        <h1>创建账号</h1>
        <p>普通用户注册</p>
      </div>

      <el-form class="register-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input
            v-model="form.username"
            size="large"
            maxlength="30"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            maxlength="30"
            placeholder="请输入密码"
            autocomplete="new-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" size="large" maxlength="30" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input
            v-model="form.phone"
            size="large"
            maxlength="11"
            placeholder="11 位大陆手机号"
            autocomplete="tel"
          />
        </el-form-item>
        <p class="register-note">仅支持普通用户注册</p>

        <div class="register-toolbar">
          <el-button class="submit-button" type="primary" native-type="submit" :loading="submitting">注册</el-button>
          <el-button class="login-link" text :disabled="submitting" @click="$router.push('/login')">
            已有账号，去登录
          </el-button>
        </div>
      </el-form>
    </section>

    <section class="register-visual" :style="{ backgroundImage: `url(${heroImage})` }" aria-label="BFSM">
      <div class="visual-overlay" />
      <div class="visual-content">
        <p>Beauty Face &amp; Salon Management</p>
        <h2>BFSM</h2>
      </div>
    </section>
  </div>
</template>

<style scoped>
.register-shell {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(440px, 0.82fr) minmax(0, 1.18fr);
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 12%, rgba(37, 99, 235, 0.13), transparent 30%),
    linear-gradient(135deg, #f8fbff 0%, #edf4ff 52%, #ffffff 100%);
}

.register-panel {
  display: grid;
  align-content: center;
  gap: 30px;
  width: min(520px, calc(100% - 48px));
  margin: 40px auto;
  padding: clamp(30px, 5vw, 52px);
  align-self: center;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 28px 80px rgba(30, 64, 175, 0.13);
  backdrop-filter: blur(20px);
  animation: panelRise 0.62s ease both;
}

.brand-link {
  width: fit-content;
  border: 0;
  padding: 0;
  color: #1d4ed8;
  background: transparent;
  font-size: 15px;
  font-weight: 900;
  letter-spacing: 0.18em;
  cursor: pointer;
}

.register-heading {
  display: grid;
  gap: 8px;
}

.register-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: clamp(38px, 5vw, 56px);
  line-height: 1;
  letter-spacing: 0;
}

.register-heading p {
  margin: 0;
  color: #64748b;
  font-size: 18px;
}

.register-form {
  display: grid;
  gap: 2px;
}

.register-form :deep(.el-form-item__label) {
  color: #334155;
  font-weight: 800;
}

.register-form :deep(.el-input__wrapper) {
  min-height: 54px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.92);
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.12);
  transition: box-shadow 0.2s ease, transform 0.2s ease, background 0.2s ease;
}

.register-form :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.58), 0 14px 34px rgba(37, 99, 235, 0.13);
  transform: translateY(-1px);
}

.register-note {
  margin: 0;
  color: #64748b;
  font-size: 15px;
}

.register-toolbar {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.submit-button {
  min-height: 52px;
  border: 0;
  border-radius: 16px;
  font-weight: 900;
  letter-spacing: 0.08em;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 18px 38px rgba(37, 99, 235, 0.24);
}

.submit-button:hover {
  transform: translateY(-1px);
}

.login-link {
  color: #1d4ed8;
  font-weight: 800;
}

.register-visual {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background-size: cover;
  background-position: center;
  animation: visualReveal 0.8s ease both;
}

.visual-overlay {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(15, 23, 42, 0.08), rgba(29, 78, 216, 0.52)),
    linear-gradient(135deg, rgba(15, 23, 42, 0.72), rgba(37, 99, 235, 0.24) 58%, rgba(191, 219, 254, 0.16));
}

.visual-content {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 16px;
  padding: clamp(34px, 7vw, 90px);
  color: #fff;
}

.visual-content p {
  margin: 0;
  color: rgba(239, 246, 255, 0.88);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.visual-content h2 {
  margin: 0;
  font-size: clamp(82px, 12vw, 168px);
  line-height: 0.82;
  letter-spacing: 0.08em;
  text-shadow: 0 22px 64px rgba(15, 23, 42, 0.28);
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

@keyframes visualReveal {
  from {
    opacity: 0;
    transform: scale(1.03);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 960px) {
  .register-shell {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .register-panel {
    width: calc(100% - 24px);
    margin: 16px auto 0;
    padding: 28px;
    order: 2;
  }

  .register-visual,
  .visual-content {
    min-height: 34svh;
  }

  .visual-content {
    padding: 28px;
  }

  .register-toolbar {
    grid-template-columns: 1fr;
  }

  .login-link {
    justify-self: center;
  }
}
</style>
