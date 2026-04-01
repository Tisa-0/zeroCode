<script lang="ts" setup>
import { ref, computed, inject, type Ref } from 'vue'
import { Icon } from '@/components/icon-custom'
import { fieldType } from '@/utils/attr'
import { operationList } from './util'
import type { Node } from './util'
import { Plus, Close, Delete, Search, ArrowRight, FolderAdd, Collection, DataLine, Rank } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  node: { type: Object as () => Node | null, default: null }
})

const emits = defineEmits(['update:visible', 'confirm'])

const localConfig = ref<Record<string, any>>({})

// 数据集所有字段（由父组件 provide），用于在排序 / 分组 / 汇总配置里提供可拖拽字段列表
const allfields = inject<Ref<any[]> | null>('allfields', null)

const operationLabel = computed(() => {
  if (!props.node?.operationType) return ''
  return operationList.find(op => op.key === props.node?.operationType)?.label || ''
})

const drawerTitle = computed(() => {
  if (!props.node) return '编辑节点'
  if (props.node.type === 'operation') return `配置${operationLabel.value}`
  return '编辑节点'
})

const sortFieldOptions = computed(() => {
  return allfields?.value || []
})

// 数据列搜索
const dataColumnSearch = ref('')
const filteredDataColumns = computed(() => {
  const list = sortFieldOptions.value
  const q = (dataColumnSearch.value || '').trim().toLowerCase()
  if (!q) return list
  return list.filter(f => {
    const name = getFieldLabel(f)
    const key = getFieldKey(f)
    return (
      String(name).toLowerCase().includes(q) ||
      String(key).toLowerCase().includes(q)
    )
  })
})

// 字段 key / label 工具方法（与排序配置保持一致的取值逻辑）
const getFieldKey = (field: any) =>
  field?.dataeaseName || field?.originName || field?.name || field?.id
const getFieldLabel = (field: any) =>
  field?.name || field?.originName || field?.dataeaseName || field?.id

// 字段类型图标：deType 0=text, 1=time, 2/3/4=value, 5=location
const getFieldIconType = (field: any) => {
  const deType = field?.deType ?? 0
  return [1, 2, 3, 4, 5].includes(deType) ? 2 : 0
}

const groupSelectedFields = computed(() => {
  const keys: string[] = (localConfig.value._groupFieldKeys as string[]) || []
  const options = sortFieldOptions.value
  return keys
    .map(key =>
      options.find(f => String(getFieldKey(f)) === String(key))
    )
    .filter(Boolean)
})

const aggSelectedField = computed(() => {
  const key = localConfig.value._aggFieldKey
  if (!key) return null
  const options = sortFieldOptions.value
  return options.find(f => String(getFieldKey(f)) === String(key)) || null
})

// 穿梭：选中的源字段
const selectedDataField = ref<any | null>(null)

const addGroupField = (field: any) => {
  const key = String(getFieldKey(field))
  const list: string[] = (localConfig.value._groupFieldKeys as string[]) || []
  if (!list.includes(key)) {
    localConfig.value._groupFieldKeys = [...list, key]
  }
}

const removeGroupField = (field: any) => {
  const key = String(getFieldKey(field))
  const list: string[] = (localConfig.value._groupFieldKeys as string[]) || []
  localConfig.value._groupFieldKeys = list.filter(k => k !== key)
}

const handleDropToGroup = () => {
  if (selectedDataField.value) {
    addGroupField(selectedDataField.value)
    selectedDataField.value = null
  }
}

const handleDropToAgg = () => {
  if (selectedDataField.value) {
    const key = String(getFieldKey(selectedDataField.value))
    localConfig.value._aggFieldKey = key
    selectedDataField.value = null
  }
}

const clearAggField = () => {
  localConfig.value._aggFieldKey = ''
}

// 拖拽支持（保留拖入配置区）
const draggingField = ref<any | null>(null)
const groupDragOver = ref(false)
const aggDragOver = ref(false)
const handleFieldDragStart = (field: any) => {
  draggingField.value = field
}
const handleFieldDragEnd = () => {
  draggingField.value = null
}
const handleDropToGroupZone = () => {
  const f = draggingField.value || selectedDataField.value
  if (f) addGroupField(f)
  draggingField.value = null
  selectedDataField.value = null
}
const handleDropToAggZone = () => {
  const f = draggingField.value || selectedDataField.value
  if (f) {
    localConfig.value._aggFieldKey = String(getFieldKey(f))
  }
  draggingField.value = null
  selectedDataField.value = null
}

// 将当前选择的分组 / 汇总字段同步回文本配置（兼容原有后端结构）
const syncGroupAggToText = () => {
  const cfg = localConfig.value
  const groupKeys: string[] = (cfg._groupFieldKeys as string[]) || []
  cfg.groupFields = groupKeys.join(', ')
  const aggKey = cfg._aggFieldKey
  cfg.aggField = aggKey || ''
}

// 将已有的文本配置解析为内部可用的 key 列表，方便拖拽 UI 展示
const initConfig = (node: Node) => {
  localConfig.value = node.operationConfig ? { ...node.operationConfig } : {}

  if (node.operationType === 'group') {
    const cfg = localConfig.value
    const options = sortFieldOptions.value

    const parseTokenToKey = (token: string) => {
      const trimmed = (token || '').trim()
      if (!trimmed) return null
      const found = options.find(f => {
        const candidates = [
          getFieldKey(f),
          f.name,
          f.originName,
          f.dataeaseName
        ]
        return candidates.some(v => String(v) === trimmed)
      })
      return found ? String(getFieldKey(found)) : trimmed
    }

    const rawGroup = cfg.groupFields
    if (typeof rawGroup === 'string') {
      const keys = rawGroup
        .split(',')
        .map(t => parseTokenToKey(t as string))
        .filter(Boolean) as string[]
      cfg._groupFieldKeys = keys
    } else if (Array.isArray(rawGroup)) {
      cfg._groupFieldKeys = rawGroup.map(v => String(v))
    }

    if (cfg.aggField) {
      const key = parseTokenToKey(cfg.aggField as string)
      cfg._aggFieldKey = key
    }
    if (!cfg.aggType) cfg.aggType = 'sum'
  }
}

const handleClose = () => {
  emits('update:visible', false)
}

const handleConfirm = () => {
  if (props.node?.operationType === 'group') {
    syncGroupAggToText()
  }
  emits('confirm', { ...localConfig.value })
  emits('update:visible', false)
}

defineExpose({ initConfig })
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="drawerTitle"
    size="720px"
    direction="rtl"
    :before-close="handleClose"
    custom-class="node-config-drawer"
  >
    <div class="config-content" v-if="node">
      <!-- Join config -->
      <template
        v-if="node.operationType === 'join' || (!node.operationType && node.type !== 'operation')"
      >
        <div class="config-section">
          <h4>联接配置</h4>
          <p class="config-tip">请通过点击画布上节点间的连线图标来配置联接关系</p>
        </div>
      </template>

      <!-- Union config -->
      <template v-else-if="node.operationType === 'union'">
        <div class="config-section">
          <h4>联合配置</h4>
          <el-form label-position="top">
            <el-form-item label="联合类型">
              <el-radio-group v-model="localConfig.unionMode">
                <el-radio label="all">全部联合（UNION ALL）</el-radio>
                <el-radio label="distinct">去重联合（UNION）</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
          <p class="config-tip">
            联合需连接两个输入节点，将两张表的行合并为一张表。两个输入的列数必须相同，且对应列的数据类型需一致（如均为文本、均为数值或均为时间），不满足时为无效联合，预览时会给出提示。
          </p>
        </div>
      </template>

      <!-- Transform config -->
      <template v-else-if="node.operationType === 'transform'">
        <div class="config-section">
          <h4>转换配置</h4>
          <el-form label-position="top">
            <el-form-item label="转换表达式">
              <el-input
                v-model="localConfig.expression"
                type="textarea"
                :rows="4"
                placeholder="请输入转换表达式"
              />
            </el-form-item>
          </el-form>
        </div>
      </template>

      <!-- Sample config -->
      <template v-else-if="node.operationType === 'sample'">
        <div class="config-section">
          <h4>抽样配置</h4>
          <el-form label-position="top">
            <el-form-item label="抽样方式">
              <el-radio-group v-model="localConfig.sampleType">
                <el-radio label="count">按数量</el-radio>
                <el-radio label="percent">按百分比</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="localConfig.sampleType === 'count'" label="抽样数量">
              <el-input-number v-model="localConfig.sampleCount" :min="1" :max="100000" />
            </el-form-item>
            <el-form-item v-if="localConfig.sampleType === 'percent'" label="抽样比例 (%)">
              <el-slider v-model="localConfig.samplePercent" :min="1" :max="100" />
            </el-form-item>
          </el-form>
        </div>
      </template>

      <!-- Sort config -->
      <template v-else-if="node.operationType === 'sort'">
        <div class="config-section">
          <h4>排序配置</h4>
          <el-form label-position="top">
            <el-form-item label="排序字段">
              <el-select
                v-model="localConfig.sortField"
                filterable
                clearable
                placeholder="请输入或搜索排序字段"
              >
                <el-option
                  v-for="field in sortFieldOptions"
                  :key="field.id"
                  :label="field.name || field.originName"
                  :value="field.dataeaseName || field.originName || field.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="排序方式">
              <el-radio-group v-model="localConfig.sortOrder">
                <el-radio label="asc">升序</el-radio>
                <el-radio label="desc">降序</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </div>
      </template>

      <!-- Pivot config -->
      <template v-else-if="node.operationType === 'pivot'">
        <div class="config-section">
          <h4>透视表配置</h4>
          <el-form label-position="top">
            <el-form-item label="行字段">
              <el-input v-model="localConfig.rowField" placeholder="请输入行字段" />
            </el-form-item>
            <el-form-item label="列字段">
              <el-input v-model="localConfig.colField" placeholder="请输入列字段" />
            </el-form-item>
            <el-form-item label="值字段">
              <el-input v-model="localConfig.valueField" placeholder="请输入值字段" />
            </el-form-item>
            <el-form-item label="聚合方式">
              <el-select v-model="localConfig.aggType" placeholder="选择聚合方式">
                <el-option label="求和 (SUM)" value="sum" />
                <el-option label="计数 (COUNT)" value="count" />
                <el-option label="平均值 (AVG)" value="avg" />
                <el-option label="最大值 (MAX)" value="max" />
                <el-option label="最小值 (MIN)" value="min" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </template>

      <!-- Unpivot config -->
      <template v-else-if="node.operationType === 'unpivot'">
        <div class="config-section">
          <h4>逆透视表配置</h4>
          <el-form label-position="top">
            <el-form-item label="要逆透视的列（逗号分隔）">
              <el-input v-model="localConfig.unpivotColumns" placeholder="col1, col2, col3" />
            </el-form-item>
            <el-form-item label="名称列名">
              <el-input v-model="localConfig.nameColumn" placeholder="attribute" />
            </el-form-item>
            <el-form-item label="值列名">
              <el-input v-model="localConfig.valueColumn" placeholder="value" />
            </el-form-item>
          </el-form>
        </div>
      </template>

      <!-- Group & Aggregate config -->
      <template v-else-if="node.operationType === 'group'">
        <div class="config-section group-agg-section">
          <h4>分组和汇总</h4>
          <div class="group-agg-layout">
            <!-- 左侧：数据列 -->
            <div class="data-columns">
              <div class="panel-header">
                <el-icon><DataLine /></el-icon>
                可用字段
              </div>
              <el-input
                v-model="dataColumnSearch"
                placeholder="搜索字段..."
                clearable
                :prefix-icon="Search"
                class="search-input"
              />
              <div class="field-list-body">
                <div
                  v-for="field in filteredDataColumns"
                  :key="field.id"
                  class="field-item"
                  :class="{ active: selectedDataField === field }"
                  draggable="true"
                  @click="selectedDataField = field"
                  @dragstart="handleFieldDragStart(field)"
                  @dragend="handleFieldDragEnd"
                >
                  <span class="field-icon">
                    <Icon
                      :name="`field_${fieldType[getFieldIconType(field)]}`"
                      :className="`field-icon-${fieldType[getFieldIconType(field)]}`"
                    />
                  </span>
                  <span class="field-name">{{ getFieldLabel(field) }}</span>
                  <el-icon class="field-arrow"><ArrowRight /></el-icon>
                </div>
                <div v-if="!filteredDataColumns.length" class="field-empty">
                  <el-icon class="empty-icon"><Search /></el-icon>
                  <span>暂无可用字段</span>
                </div>
              </div>
            </div>

            <!-- 中间：穿梭按钮 -->
            <div class="transfer-buttons">
              <div class="btn-item">
                <el-tooltip content="添加到分组列" placement="top">
                  <el-button
                    type="primary"
                    :disabled="!selectedDataField"
                    @click="handleDropToGroup"
                    class="transfer-btn"
                  >
                    <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </el-tooltip>
                <span class="btn-label">分组</span>
              </div>
              <div class="btn-item">
                <el-tooltip content="添加到汇总列" placement="top">
                  <el-button
                    type="primary"
                    :disabled="!selectedDataField"
                    @click="handleDropToAgg"
                    class="transfer-btn"
                  >
                    <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </el-tooltip>
                <span class="btn-label">汇总</span>
              </div>
            </div>

            <!-- 右侧：分组列 + 汇总列 -->
            <div class="config-panels">
              <!-- 分组列 -->
              <div class="panel-group group-panel">
                <div class="panel-header">
                  <el-icon><Rank /></el-icon>
                  分组列
                  <span class="field-count" v-if="groupSelectedFields.length">{{ groupSelectedFields.length }}</span>
                </div>
                <div
                  class="drop-zone"
                  :class="{ 'has-items': groupSelectedFields.length, 'drag-over': groupDragOver }"
                  @dragover.prevent="groupDragOver = true"
                  @dragleave="groupDragOver = false"
                  @drop.prevent="handleDropToGroupZone(); groupDragOver = false"
                >
                  <TransitionGroup name="field-list" tag="div" class="field-list">
                    <div
                      v-for="field in groupSelectedFields"
                      :key="getFieldKey(field)"
                      class="list-item"
                    >
                      <span class="item-icon">
                        <Icon
                          :name="`field_${fieldType[getFieldIconType(field)]}`"
                          :className="`field-icon-${fieldType[getFieldIconType(field)]}`"
                        />
                      </span>
                      <span class="item-name">{{ getFieldLabel(field) }}</span>
                      <el-button
                        link
                        type="danger"
                        size="small"
                        class="item-remove"
                        @click.stop="removeGroupField(field)"
                      >
                        <el-icon><Close /></el-icon>
                      </el-button>
                    </div>
                  </TransitionGroup>
                  <div v-if="!groupSelectedFields.length" class="drop-placeholder">
                    <el-icon class="placeholder-icon"><Plus /></el-icon>
                    <span>将字段拖入或选中后点击添加</span>
                  </div>
                </div>
              </div>

              <!-- 汇总列 -->
              <div class="panel-group agg-panel">
                <div class="panel-header">
                  <el-icon><Collection /></el-icon>
                  汇总列
                  <span class="field-count" v-if="aggSelectedField">1</span>
                </div>
                <div
                  class="drop-zone"
                  :class="{ 'has-items': !!aggSelectedField, 'drag-over': aggDragOver }"
                  @dragover.prevent="aggDragOver = true"
                  @dragleave="aggDragOver = false"
                  @drop.prevent="handleDropToAggZone(); aggDragOver = false"
                >
                  <template v-if="aggSelectedField">
                    <div class="agg-card">
                      <div class="agg-info">
                        <span class="agg-icon">
                          <Icon
                            :name="`field_${fieldType[getFieldIconType(aggSelectedField)]}`"
                            :className="`field-icon-${fieldType[getFieldIconType(aggSelectedField)]}`"
                          />
                        </span>
                        <span class="agg-name">{{ getFieldLabel(aggSelectedField) }}</span>
                      </div>
                      <div class="agg-config">
                        <el-select
                          v-model="localConfig.aggType"
                          size="small"
                          placeholder="选择聚合方式"
                          class="agg-type-select"
                        >
                          <el-option label="求和 (SUM)" value="sum">
                            <span>求和</span>
                            <span class="agg-option-en">SUM</span>
                          </el-option>
                          <el-option label="计数 (COUNT)" value="count">
                            <span>计数</span>
                            <span class="agg-option-en">COUNT</span>
                          </el-option>
                          <el-option label="平均值 (AVG)" value="avg">
                            <span>平均值</span>
                            <span class="agg-option-en">AVG</span>
                          </el-option>
                          <el-option label="最大值 (MAX)" value="max">
                            <span>最大值</span>
                            <span class="agg-option-en">MAX</span>
                          </el-option>
                          <el-option label="最小值 (MIN)" value="min">
                            <span>最小值</span>
                            <span class="agg-option-en">MIN</span>
                          </el-option>
                        </el-select>
                        <el-button
                          link
                          type="danger"
                          size="small"
                          class="agg-remove"
                          @click="clearAggField"
                        >
                          <el-icon><Delete /></el-icon>
                        </el-button>
                      </div>
                    </div>
                  </template>
                  <div v-else class="drop-placeholder">
                    <el-icon class="placeholder-icon"><Plus /></el-icon>
                    <span>将字段拖入或选中后点击添加</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- Self-loop config -->
      <template v-else-if="node.operationType === 'selfloop'">
        <div class="config-section">
          <h4>自循环列配置</h4>
          <el-form label-position="top">
            <el-form-item label="ID 列">
              <el-input v-model="localConfig.idColumn" placeholder="请输入 ID 列名" />
            </el-form-item>
            <el-form-item label="父 ID 列">
              <el-input v-model="localConfig.parentIdColumn" placeholder="请输入父 ID 列名" />
            </el-form-item>
            <el-form-item label="层级深度">
              <el-input-number v-model="localConfig.maxDepth" :min="1" :max="20" />
            </el-form-item>
          </el-form>
        </div>
      </template>

      <!-- Mirror config -->
      <template v-else-if="node.operationType === 'mirror'">
        <div class="config-section">
          <h4>镜像配置</h4>
          <p class="config-tip">镜像操作会创建当前数据的一份副本，不需要额外配置。</p>
        </div>
      </template>

      <!-- Deduplicate config -->
      <template v-else-if="node.operationType === 'deduplicate'">
        <div class="config-section">
          <h4>去重配置</h4>
          <el-form label-position="top">
            <el-form-item label="去重字段（逗号分隔，空表示全部字段）">
              <el-input
                v-model="localConfig.deduplicateFields"
                placeholder="field1, field2 或留空"
              />
            </el-form-item>
            <el-form-item label="保留方式">
              <el-radio-group v-model="localConfig.keepStrategy">
                <el-radio label="first">保留第一条</el-radio>
                <el-radio label="last">保留最后一条</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button secondary @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-drawer>
</template>

<style lang="less" scoped>
.config-content {
  padding: 0 4px;

  .config-section {
    margin-bottom: 24px;

    h4 {
      font-size: 15px;
      font-weight: 600;
      color: #1f2329;
      margin: 0 0 16px;
      padding-bottom: 12px;
      border-bottom: 1px solid #e5e6e8;
      display: flex;
      align-items: center;
      gap: 8px;

      &::before {
        content: '';
        width: 3px;
        height: 16px;
        background: linear-gradient(180deg, #409EFF 0%, #79bbff 100%);
        border-radius: 2px;
      }
    }

    .config-tip {
      color: #8d9199;
      font-size: 13px;
      line-height: 20px;
      padding: 12px;
      background: linear-gradient(135deg, #f0f5ff 0%, #f5f7fa 100%);
      border-radius: 6px;
      border-left: 3px solid #409EFF;
    }
  }

  .group-agg-section {
    .group-agg-layout {
      display: flex;
      gap: 16px;
      min-height: 480px;
      align-items: stretch;
    }

    .data-columns {
      flex: 1.3;
      min-width: 0;
      display: flex;
      flex-direction: column;
      border: 1px solid #ebeef5;
      border-radius: 12px;
      overflow: hidden;
      background: #fff;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
      transition: all 0.3s ease;

      &:hover {
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
      }
    }

    .config-panels {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-width: 0;
    }

    .panel-header {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 10px 12px;
      font-weight: 600;
      font-size: 13px;
      color: #303133;
      background: linear-gradient(135deg, #f8f9fc 0%, #f0f2f5 100%);
      border-bottom: 1px solid #ebeef5;

      .el-icon {
        font-size: 14px;
        color: #409EFF;
        padding: 3px;
        background: rgba(64, 158, 255, 0.1);
        border-radius: 4px;
      }

      .field-count {
        margin-left: auto;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 20px;
        height: 20px;
        padding: 0 6px;
        font-size: 11px;
        font-weight: 600;
        color: #fff;
        background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
        border-radius: 10px;
        box-shadow: 0 2px 4px rgba(64, 158, 255, 0.3);
      }
    }

    .search-input {
      margin: 8px 10px 6px;
      width: calc(100% - 20px);
      :deep(.el-input__wrapper) {
        border-radius: 6px;
        box-shadow: 0 0 0 1px #dcdfe6 inset;
        padding: 2px 8px;
        transition: all 0.2s ease;
        &:hover {
          box-shadow: 0 0 0 1px #c0c4cc inset;
        }
        &.is-focus {
          box-shadow: 0 0 0 1px #409EFF inset;
          border-color: #409EFF;
        }
      }
    }

    .field-list-body {
      flex: 1;
      overflow-y: auto;
      padding: 4px 8px 12px;

      &::-webkit-scrollbar {
        width: 6px;
      }
      &::-webkit-scrollbar-thumb {
        background: #dcdfe6;
        border-radius: 3px;
        &:hover {
          background: #c0c4cc;
        }
      }
    }

    .field-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 10px;
      font-size: 13px;
      cursor: pointer;
      border-radius: 6px;
      transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
      margin-bottom: 2px;
      border: 1px solid transparent;
      position: relative;
      overflow: hidden;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 0;
        bottom: 0;
        width: 2px;
        background: linear-gradient(180deg, #409EFF 0%, #79bbff 100%);
        opacity: 0;
        transition: opacity 0.2s;
      }

      &:hover {
        background: linear-gradient(135deg, #ecf5ff 0%, #f0f9ff 100%);
        border-color: #b3d8ff;
        transform: translateX(2px);

        &::before {
          opacity: 1;
        }
      }
      &.active {
        background: linear-gradient(135deg, #d9ecff 0%, #cce6ff 100%);
        border-color: #409EFF;
        box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);

        &::before {
          opacity: 1;
        }

        .field-arrow {
          opacity: 1;
          transform: translateX(2px);
        }
      }

      .field-icon {
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 22px;
        height: 22px;
        min-width: 22px;
        min-height: 22px;
        font-size: 12px;
        color: #409EFF;
        background: rgba(64, 158, 255, 0.1);
        padding: 0;
        border-radius: 4px;

        :deep(.svg-icon),
        :deep(svg) {
          width: 14px;
          height: 14px;
        }
      }
      .field-name {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-weight: 500;
        color: #303133;
        font-size: 13px;
      }
      .field-arrow {
        flex-shrink: 0;
        opacity: 0;
        color: #409EFF;
        transition: all 0.25s ease;
        font-size: 12px;
      }
    }

    .field-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 48px 20px;
      color: #909399;
      font-size: 13px;
      position: relative;

      &::before {
        content: '';
        width: 64px;
        height: 64px;
        background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
        border-radius: 50%;
        position: absolute;
        top: 20px;
      }
      
      .empty-icon {
        font-size: 24px;
        margin-bottom: 8px;
        color: #c0c4cc;
        position: relative;
        z-index: 1;
      }
      span {
        position: relative;
        z-index: 1;
      }
    }

    .transfer-buttons {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      padding: 0 8px;
      gap: 16px;

      .btn-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;

        .transfer-btn {
          width: 40px;
          height: 40px;
          border-radius: 10px;
          padding: 0;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 16px;
          background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
          border: none;
          box-shadow: 0 4px 12px rgba(64, 158, 255, 0.35);
          transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
          
          &:hover:not(:disabled) {
            transform: scale(1.1) translateY(-2px);
            box-shadow: 0 6px 16px rgba(64, 158, 255, 0.45);
          }
          &:active:not(:disabled) {
            transform: scale(0.95);
          }
          &:disabled {
            opacity: 0.4;
            cursor: not-allowed;
            box-shadow: none;
          }
        }

        .btn-label {
          font-size: 12px;
          color: #606266;
          font-weight: 600;
          background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
          padding: 4px 12px;
          border-radius: 12px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
        }
      }
    }

    .config-panels {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-width: 0;

      .panel-group {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        border: 1px solid #ebeef5;
        border-radius: 12px;
        overflow: hidden;
        background: #fff;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
        transition: all 0.3s ease;

        &:hover {
          box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
          border-color: #d9ecff;
        }

        &.group-panel {
          flex: 1.5;
        }

        &.agg-panel {
          .panel-header {
            .el-icon {
              background: rgba(103, 194, 58, 0.1);
              color: #67C23A;
            }
            .field-count {
              background: linear-gradient(135deg, #67C23A 0%, #85ce61 100%);
              box-shadow: 0 2px 4px rgba(103, 194, 58, 0.3);
            }
          }
        }

        .panel-header {
          flex-shrink: 0;
        }
      }
    }

    .drop-zone {
      flex: 1;
      min-height: 80px;
      padding: 12px;
      overflow-y: auto;
      background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
      transition: all 0.3s ease;

      &::-webkit-scrollbar {
        width: 6px;
      }
      &::-webkit-scrollbar-thumb {
        background: #dcdfe6;
        border-radius: 3px;
        &:hover {
          background: #c0c4cc;
        }
      }

      &.drag-over {
        background: linear-gradient(135deg, #ecf5ff 0%, #d9ecff 100%);
        border: 2px dashed #409EFF;
        box-shadow: inset 0 0 20px rgba(64, 158, 255, 0.1);
      }

      &.has-items {
        background: #fff;
      }

      .field-list {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }

      .list-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 6px 10px;
        font-size: 13px;
        border-radius: 6px;
        background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
        border: 1px solid #ebeef5;
        transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
        position: relative;
        overflow: hidden;

        &::before {
          content: '';
          position: absolute;
          left: 0;
          top: 0;
          bottom: 0;
          width: 2px;
          background: linear-gradient(180deg, #409EFF 0%, #79bbff 100%);
          opacity: 0;
          transition: opacity 0.2s;
        }

        &:hover {
          background: linear-gradient(135deg, #ecf5ff 0%, #f0f9ff 100%);
          border-color: #b3d8ff;
          transform: translateX(2px);
          box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);

          &::before {
            opacity: 1;
          }

          .item-remove {
            opacity: 1;
          }
        }

        .item-icon {
          flex-shrink: 0;
          display: flex;
          align-items: center;
          justify-content: center;
          width: 22px;
          height: 22px;
          min-width: 22px;
          min-height: 22px;
          color: #409EFF;
          background: rgba(64, 158, 255, 0.1);
          padding: 0;
          border-radius: 4px;
          font-size: 12px;

          :deep(.svg-icon),
          :deep(svg) {
            width: 14px;
            height: 14px;
          }
        }

        .item-name {
          flex: 1;
          font-weight: 500;
          color: #303133;
          font-size: 13px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .item-remove {
          flex-shrink: 0;
          opacity: 0;
          transition: all 0.2s ease;
          padding: 4px;
          color: #F56C6C;
          
          &:hover {
            background: rgba(245, 108, 108, 0.1);
            border-radius: 4px;
          }
        }
      }

      .drop-placeholder {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        height: 100%;
        min-height: 70px;
        color: #b0b3bb;
        font-size: 12px;
        text-align: center;
        padding: 16px;
        border: 2px dashed #e4e7ed;
        border-radius: 8px;
        background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
        transition: all 0.3s ease;

        &:hover {
          border-color: #c0c4cc;
          background: linear-gradient(135deg, #f5f7fa 0%, #f0f2f5 100%);
        }

        .placeholder-icon {
          font-size: 20px;
          margin-bottom: 6px;
          color: #c0c4cc;
          padding: 6px;
          background: rgba(192, 196, 204, 0.1);
          border-radius: 50%;
        }
      }
    }

      .agg-card {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 12px;
      background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
      border: 1px solid #ebeef5;
      border-radius: 8px;
      animation: slideIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
      position: relative;
      overflow: hidden;

      &::before {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: 2px;
        background: linear-gradient(90deg, #67C23A 0%, #85ce61 100%);
      }

      .agg-info {
        display: flex;
        align-items: center;
        gap: 8px;

        .agg-icon {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 22px;
          height: 22px;
          min-width: 22px;
          min-height: 22px;
          color: #67C23A;
          background: rgba(103, 194, 58, 0.1);
          padding: 0;
          border-radius: 4px;
          font-size: 12px;

          :deep(.svg-icon),
          :deep(svg) {
            width: 14px;
            height: 14px;
          }
        }

        .agg-name {
          flex: 1;
          font-weight: 600;
          color: #303133;
          font-size: 14px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .agg-config {
        display: flex;
        align-items: center;
        gap: 10px;
        padding-top: 8px;
        border-top: 1px dashed #ebeef5;

        .agg-type-select {
          flex: 1;
          min-width: 0;
          :deep(.el-select__wrapper) {
            border-radius: 6px;
            box-shadow: 0 0 0 1px #dcdfe6 inset;
            &:hover {
              box-shadow: 0 0 0 1px #c0c4cc inset;
            }
            &.is-focused {
              box-shadow: 0 0 0 1px #67C23A inset;
            }
          }
        }

        .agg-remove {
          flex-shrink: 0;
          padding: 6px;
          color: #F56C6C;
          transition: all 0.2s;
          border-radius: 6px;
          
          &:hover {
            background: rgba(245, 108, 108, 0.1);
          }
        }
      }
    }

    .agg-option-en {
      margin-left: 8px;
      font-size: 11px;
      color: #909399;
    }

    @keyframes slideIn {
      from {
        opacity: 0;
        transform: translateY(-15px) scale(0.95);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }

    .field-list-enter-active,
    .field-list-leave-active {
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .field-list-enter-from {
      opacity: 0;
      transform: translateX(-20px) scale(0.9);
    }

    .field-list-leave-to {
      opacity: 0;
      transform: translateX(20px) scale(0.9);
    }

    .field-list-move {
      transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }
  }
}
</style>

<style lang="less">
.node-config-drawer {
  .ed-drawer__header {
    font-weight: 500;
    font-size: 16px;
  }
}
</style>
