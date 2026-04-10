<script lang="ts" setup>
import { operationList } from './util'
import type { OperationDef } from './util'
import { Icon } from '@/components/icon-custom'

const emits = defineEmits(['dragStart', 'dragEnd'])

const handleDragStart = (e: DragEvent, op: OperationDef) => {
  const data = {
    tableName: op.label,
    type: 'operation',
    operationType: op.key,
    datasourceId: '',
    name: op.label
  }
  e.dataTransfer?.setData('text/plain', JSON.stringify(data))
  emits('dragStart', e, op)
}

const handleDragEnd = () => {
  emits('dragEnd')
}
</script>

<template>
  <div class="operation-toolbar">
    <div
      v-for="op in operationList"
      :key="op.key"
      class="operation-item"
      :draggable="true"
      @dragstart="(e) => handleDragStart(e, op)"
      @dragend="handleDragEnd"
      :title="op.label"
    >
      <el-icon :size="14">
        <Icon :name="op.icon" />
      </el-icon>
      <span class="op-label">{{ op.label }}</span>
    </div>
  </div>
</template>

<style lang="less" scoped>
.operation-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  background: #fff;
  border-bottom: 1px solid rgba(31, 35, 41, 0.08);
  overflow-x: auto;
  flex-shrink: 0;

  &::-webkit-scrollbar {
    height: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(31, 35, 41, 0.12);
    border-radius: 3px;
  }

  .operation-item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 5px 14px;
    border: 1px solid #e4e7ed;
    border-radius: 16px;
    cursor: grab;
    white-space: nowrap;
    font-size: 13px;
    color: #1f2329;
    background: #f9fafb;
    user-select: none;
    transition: all 0.2s ease;

    &:hover {
      border-color: var(--ed-color-primary, #3370ff);
      color: var(--ed-color-primary, #3370ff);
      background: rgba(51, 112, 255, 0.05);
      box-shadow: 0 1px 4px rgba(51, 112, 255, 0.12);
    }

    &:active {
      cursor: grabbing;
      transform: scale(0.97);
    }

    .op-label {
      line-height: 20px;
    }
  }
}
</style>
