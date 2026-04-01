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
  gap: 4px;
  padding: 6px 16px;
  background: #fff;
  border-bottom: 1px solid rgba(31, 35, 41, 0.15);
  overflow-x: auto;
  flex-shrink: 0;

  &::-webkit-scrollbar {
    height: 4px;
  }

  .operation-item {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 4px 10px;
    border: 1px solid #dee0e3;
    border-radius: 4px;
    cursor: grab;
    white-space: nowrap;
    font-size: 12px;
    color: #1f2329;
    background: #f5f6f7;
    user-select: none;
    transition: all 0.2s;

    &:hover {
      border-color: var(--ed-color-primary);
      color: var(--ed-color-primary);
      background: rgba(51, 112, 255, 0.06);
    }

    &:active {
      cursor: grabbing;
    }

    .op-label {
      line-height: 20px;
    }
  }
}
</style>
