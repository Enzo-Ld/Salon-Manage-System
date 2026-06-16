<script setup lang="ts">
import { computed } from 'vue';
import type { AdminUser, Technician } from '../api/types';
import { buildTechnicianOptions, selectableTechnicianOptions, technicianOptionLabel } from '../composables/useTechnicianOptions';

const model = defineModel<number | number[] | null | undefined>();

const props = withDefaults(
  defineProps<{
    technicians: Technician[];
    users?: AdminUser[];
    multiple?: boolean;
    activeOnly?: boolean;
    placeholder?: string;
    disabled?: boolean;
    collapseTags?: boolean;
  }>(),
  {
    users: () => [],
    multiple: false,
    activeOnly: true,
    placeholder: '请选择技师',
    disabled: false,
    collapseTags: true,
  },
);

const options = computed(() => {
  const allOptions = buildTechnicianOptions(props.technicians, props.users);
  return props.activeOnly ? selectableTechnicianOptions(allOptions) : allOptions;
});
</script>

<template>
  <el-select
    v-model="model"
    filterable
    clearable
    :multiple="multiple"
    :collapse-tags="collapseTags"
    collapse-tags-tooltip
    :placeholder="placeholder"
    :disabled="disabled"
  >
    <el-option
      v-for="option in options"
      :key="option.technicianId"
      :label="technicianOptionLabel(option)"
      :value="option.technicianId"
    >
      <div class="technician-option">
        <strong>{{ option.technicianName }}</strong>
        <span>
          {{ option.nickname || option.username || `账号 #${option.userId}` }}
          · {{ option.phone || '未填手机号' }}
          · {{ option.levelName || '未设置等级' }}
          · {{ option.status === 'ACTIVE' ? '在职' : '停用' }}
        </span>
      </div>
    </el-option>
  </el-select>
</template>

<style scoped>
.technician-option {
  display: grid;
  line-height: 1.35;
}

.technician-option span {
  color: var(--muted);
  font-size: 12px;
}
</style>
