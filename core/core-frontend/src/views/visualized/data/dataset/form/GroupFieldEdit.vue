<script lang="ts" setup>
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { ElMessage } from 'element-plus-secondary'
import { fieldType } from '@/utils/attr'
import { Plus, Delete } from '@element-plus/icons-vue'
import { Icon } from '@/components/icon-custom'

export interface ValueGroupItem {
  name: string
  values: string[]
}

export interface GroupFieldType {
  id?: string
  originName: string
  name: string
  groupType: 'd' | 'q'
  type: string
  deType: number
  extField: number
  checked: boolean
}

const { t } = useI18n()

const props = defineProps({
  crossDs: {
    type: Boolean,
    default: () => false
  }
})

const emit = defineEmits(['update:visible', 'confirm'])

const fields = [
  { label: t('dataset.text'), value: 0 },
  { label: t('dataset.time'), value: 1 },
  { label: t('dataset.value'), value: 2 },
  { label: t('dataset.value') + '(' + t('dataset.float') + ')', value: 3 },
  { label: t('dataset.location'), value: 5 }
]

const groupTypes = [
  { label: '等距分组', value: 'distance' },
  { label: '等量分组', value: 'quantity' },
  { label: '自定义分组', value: 'custom' }
]

const defaultForm = {
  originName: '',
  name: '',
  groupType: 'd',
  type: 'VARCHAR',
  deType: 0,
  extField: 3,
  id: '',
  checked: true
}

const fieldForm = reactive<GroupFieldType>({ ...defaultForm })

const state = reactive({
  availableFields: [],
  selectedField: null,
  groupType: 'distance',
  groupParams: {
    step: 10,
    quantity: 5,
    groups: []
  },
  valueGroupingMode: false,
  presetField: null as any,
  valueList: [] as string[],
  valueGroups: [] as Array<{ name: string; values: string[] }>,
  valueToGroup: {} as Record<string, string>,
  addRemainingToOther: false,
  valueSearchKeyword: '',
  selectedValues: [] as string[],
  addToGroupName: '',
  // 拖拽状态
  draggingValue: null as string | null,
  dragOverGroup: null as string | null,
  // 分组编辑状态
  editingGroupName: null as string | null
})

const groupTypeOptions = computed(() => {
  if (!state.selectedField) return []
  const deType = state.selectedField?.deType
  if (deType === 0 || deType === 1 || deType === 5) {
    return [{ label: '自定义分组', value: 'custom' }]
  }
  return groupTypes
})

const valueGroupNames = computed(() => state.valueGroups.map(g => g.name))

const filteredValueList = computed(() => {
  const list = state.valueList || []
  const kw = (state.valueSearchKeyword || '').trim().toLowerCase()
  if (!kw) return list
  return list.filter(v => String(v).toLowerCase().includes(kw))
})

const initEdit = (obj, availableFields, valueList?: string[]) => {
  Object.assign(fieldForm, { ...defaultForm, ...obj })
  state.availableFields = availableFields || []
  state.valueGroupingMode = false
  state.valueList = []
  state.valueGroups = []
  state.valueToGroup = {}
  state.addRemainingToOther = false
  state.valueSearchKeyword = ''
  state.selectedValues = []
  state.addToGroupName = ''

  if (obj.groupParams) {
    state.groupParams = { ...obj.groupParams }
  }
  if (obj.groupType) {
    state.groupType = obj.groupType
  }
  if (obj.sourceField && Array.isArray(valueList)) {
    state.valueGroupingMode = true
    state.presetField = obj.sourceField
    state.valueList = valueList
    state.selectedField = obj.sourceField
    fieldForm.deType = obj.sourceField.deType
    fieldForm.name = (obj.sourceField.name || '') + '-分组'
    fieldForm.originName = (obj.sourceField.name || '') + '_grp_custom'
    if (obj.groupParams?.valueGroups) {
      state.valueGroups = obj.groupParams.valueGroups.map((g: any) => ({
        name: g.name,
        values: [...(g.values || [])]
      }))
      const map = {} as Record<string, string>
      state.valueGroups.forEach(g => {
        (g.values || []).forEach(v => { map[v] = g.name })
      })
      state.valueToGroup = map
    }
    if (obj.groupParams?.addRemainingToOther != null) {
      state.addRemainingToOther = obj.groupParams.addRemainingToOther
    }
  } else {
    state.presetField = null
  }
}

const formField = ref()

const generateOriginName = () => {
  if (!state.selectedField) return ''
  const fieldName = state.selectedField.name
  const gType = state.groupType
  let suffix = ''
  switch (gType) {
    case 'distance':
      suffix = `_grp_${state.groupParams.step}`
      break
    case 'quantity':
      suffix = `_grp_${state.groupParams.quantity}`
      break
    case 'custom':
      suffix = '_grp_custom'
      break
  }
  fieldForm.originName = `${fieldName}${suffix}`
  fieldForm.name = `${fieldName}_分组`
}

const selectField = (field) => {
  state.selectedField = field
  fieldForm.deType = field.deType
  generateOriginName()
}

const handleGroupTypeChange = () => {
  generateOriginName()
}

const handleParamChange = () => {
  generateOriginName()
}

const addNewValueGroup = () => {
  const name = '分组' + (state.valueGroups.length + 1)
  state.valueGroups.push({ name, values: [] })
  state.addToGroupName = name
}

const renameValueGroup = () => {
  // 现在改为直接进入编辑模式（点击分组文件夹上的编辑图标）
  // 保留此函数以兼容，但不再使用弹窗
}

const startEditGroupName = (name: string) => {
  state.editingGroupName = name
  state.addToGroupName = name
  nextTick(() => {
    const input = document.querySelector('.folder-name-input input') as HTMLInputElement
    if (input) {
      input.focus()
      input.select()
    }
  })
}

const finishEditGroupName = (group: { name: string; values: string[] }) => {
  const newName = group.name?.trim()
  if (!newName) {
    ElMessage.warning('分组名称不能为空')
    return
  }
  const oldName = state.editingGroupName
  if (oldName && oldName !== newName) {
    // 更新所有值的分组映射
    Object.keys(state.valueToGroup).forEach(v => {
      if (state.valueToGroup[v] === oldName) {
        state.valueToGroup[v] = newName
      }
    })
    if (state.addToGroupName === oldName) {
      state.addToGroupName = newName
    }
  }
  state.editingGroupName = null
}

const cancelEditGroupName = () => {
  state.editingGroupName = null
}

const ungroupValueGroup = () => {
  const name = state.addToGroupName
  if (!name) {
    ElMessage.warning('请先在「添加到」中选择要取消的分组')
    return
  }
  const idx = state.valueGroups.findIndex(g => g.name === name)
  if (idx < 0) return
  const g = state.valueGroups[idx]
  g.values.forEach(v => delete state.valueToGroup[v])
  g.values = []
  state.valueGroups.splice(idx, 1)
  state.addToGroupName = ''
}

const resetValueGroups = () => {
  state.valueGroups = []
  state.valueToGroup = {}
  state.addRemainingToOther = false
  state.selectedValues = []
  state.addToGroupName = ''
}

const toggleValueSelection = (val: string) => {
  const i = state.selectedValues.indexOf(val)
  if (i === -1) state.selectedValues.push(val)
  else state.selectedValues.splice(i, 1)
}

// 拖拽事件处理
const onValueDragStart = (val: string, event: DragEvent) => {
  state.draggingValue = val
  event.dataTransfer?.setData('text/plain', val)
  event.dataTransfer!.effectAllowed = 'move'
}

const onValueDragEnd = () => {
  state.draggingValue = null
  state.dragOverGroup = null
}

const onGroupDragOver = (groupName: string, event: DragEvent) => {
  event.preventDefault()
  state.dragOverGroup = groupName
  event.dataTransfer!.dropEffect = 'move'
}

const onGroupDragLeave = () => {
  state.dragOverGroup = null
}

const onGroupDrop = (groupName: string, event: DragEvent) => {
  event.preventDefault()
  const val = event.dataTransfer?.getData('text/plain') || state.draggingValue
  if (!val) return

  // 如果是从其他组拖过来的，先从原组移除
  const oldGroupName = state.valueToGroup[val]
  if (oldGroupName && oldGroupName !== groupName) {
    const oldGroup = state.valueGroups.find(g => g.name === oldGroupName)
    if (oldGroup) {
      const idx = oldGroup.values.indexOf(val)
      if (idx > -1) oldGroup.values.splice(idx, 1)
    }
  }

  // 添加到新组
  let g = state.valueGroups.find(x => x.name === groupName)
  if (!g) {
    g = { name: groupName, values: [] }
    state.valueGroups.push(g)
  }
  if (!g.values.includes(val)) {
    g.values.push(val)
  }
  state.valueToGroup[val] = groupName

  state.draggingValue = null
  state.dragOverGroup = null
}

const getValueGroupName = (val: string) => state.valueToGroup[val] || ''

const addSelectedValuesToGroup = () => {
  const groupName = state.addToGroupName
  if (!groupName) {
    ElMessage.warning('请选择“添加到”的分组')
    return
  }
  let g = state.valueGroups.find(x => x.name === groupName)
  if (!g) {
    g = { name: groupName, values: [] }
    state.valueGroups.push(g)
  }
  state.selectedValues.forEach(v => {
    if (!g!.values.includes(v)) g!.values.push(v)
    state.valueToGroup[v] = groupName
  })
  state.selectedValues = []
}

const validateForm = () => {
  return new Promise((resolve) => {
    if (state.valueGroupingMode) {
      if (!fieldForm.name.trim()) {
        ElMessage.warning('请输入名称')
        resolve(false)
        return
      }
      resolve(true)
      return
    }
    if (!state.selectedField) {
      ElMessage.warning('请选择分组字段')
      resolve(false)
      return
    }
    if (!fieldForm.name.trim()) {
      ElMessage.warning('请输入字段名称')
      resolve(false)
      return
    }
    resolve(true)
  })
}

const getResult = async () => {
  const valid = await validateForm()
  if (!valid) return null

  if (state.valueGroupingMode) {
    const valueGroups = state.valueGroups.map(g => ({ name: g.name, values: [...g.values] }))
    if (state.addRemainingToOther) {
      let otherGroup = valueGroups.find(g => g.name === '其他')
      if (!otherGroup) {
        otherGroup = { name: '其他', values: [] }
        valueGroups.push(otherGroup)
      }
      state.valueList.forEach(v => {
        if (!state.valueToGroup[v]) otherGroup!.values.push(v)
      })
    }
    return {
      ...fieldForm,
      sourceField: state.presetField,
      groupType: 'custom',
      groupParams: {
        groups: valueGroups.map(g => ({ label: g.name, value: (g.values || []).join(',') })),
        valueGroups,
        addRemainingToOther: state.addRemainingToOther
      }
    }
  }

  return {
    ...fieldForm,
    sourceField: state.selectedField,
    groupType: state.groupType,
    groupParams: { ...state.groupParams }
  }
}

// 监听"添加到"下拉框变化，自动将选中的值添加到对应分组
watch(() => state.addToGroupName, (newGroupName) => {
  if (newGroupName && state.selectedValues.length > 0) {
    addSelectedValuesToGroup()
  }
})

defineExpose({
  initEdit,
  fieldForm,
  formField,
  getResult
})
</script>

<template>
  <div class="group-field">
    <!-- 值分组模式：从批量管理/数据预览指定字段打开 -->
    <div v-if="state.valueGroupingMode" class="value-grouping-cont">
      <div class="value-grouping-header">
        <div class="form-row">
          <span class="label">{{ t('dataset.original_field') }}</span>
          <span class="value">{{ state.presetField?.name || '-' }}</span>
        </div>
        <div class="form-row">
          <span class="label">{{ t('dataset.name_label') }}</span>
          <el-input v-model="fieldForm.name" class="name-input" :placeholder="t('dataset.input_name')" />
        </div>
        <div class="form-row">
          <span class="label">{{ t('dataset.add_to') }}</span>
          <el-select
            v-model="state.addToGroupName"
            class="add-to-select"
            :placeholder="t('dataset.add_to')"
            clearable
            filterable
            allow-create
            default-first-option
          >
            <el-option
              v-for="name in valueGroupNames"
              :key="name"
              :label="name"
              :value="name"
            />
          </el-select>
        </div>
      </div>
      <div class="form-row search-row">
        <span class="label">{{ t('dataset.search_value_placeholder') }}</span>
        <el-input
          v-model="state.valueSearchKeyword"
          class="search-input"
          :placeholder="t('dataset.search_value_placeholder')"
          clearable
        />
      </div>
      <div class="value-list-section">
        <!-- 分组文件夹区域 -->
        <div v-if="state.valueGroups.length > 0" class="group-folders">
          <div
            v-for="group in state.valueGroups"
            :key="group.name"
            :class="['group-folder', { 'drag-over': state.dragOverGroup === group.name, 'editing': state.editingGroupName === group.name }]"
            @dragover="onGroupDragOver(group.name, $event)"
            @dragleave="onGroupDragLeave"
            @drop="onGroupDrop(group.name, $event)"
          >
            <el-icon class="folder-icon">
              <Icon name="icon_folder_outlined" />
            </el-icon>
            <el-input
              v-if="state.editingGroupName === group.name"
              v-model="group.name"
              class="folder-name-input"
              size="small"
              @blur="finishEditGroupName(group)"
              @keyup.enter="finishEditGroupName(group)"
              @keyup.escape="cancelEditGroupName"
              ref="groupNameInput"
            />
            <template v-else>
              <span class="folder-name" @dblclick="startEditGroupName(group.name)">{{ group.name }}</span>
              <span class="folder-count">({{ group.values.length }})</span>
              <el-icon class="edit-icon" @click.stop="startEditGroupName(group.name)">
                <Icon name="icon_edit_outlined" />
              </el-icon>
            </template>
          </div>
        </div>
        <div class="value-list">
          <div
            v-for="val in filteredValueList"
            :key="val"
            :class="[
              'value-item',
              { selected: state.selectedValues.includes(val) },
              { dragging: state.draggingValue === val }
            ]"
            draggable="true"
            @click="toggleValueSelection(val)"
            @dragstart="onValueDragStart(val, $event)"
            @dragend="onValueDragEnd"
          >
            <el-icon class="value-icon">
              <Icon name="icon_text_outlined" />
            </el-icon>
            <span class="value-text">{{ val }}</span>
            <span v-if="getValueGroupName(val)" class="value-group-tag">{{ getValueGroupName(val) }}</span>
          </div>
        </div>
      </div>
      <div class="value-grouping-footer">
        <label class="checkbox-row">
          <el-checkbox v-model="state.addRemainingToOther" />
          <span>{{ t('dataset.add_remaining_to_other') }}</span>
        </label>
        <div class="action-buttons">
          <el-button text @click="addNewValueGroup">{{ t('dataset.new_group') }}</el-button>
          <el-button text @click="ungroupValueGroup">{{ t('dataset.ungroup') }}</el-button>
          <el-button text @click="resetValueGroups">{{ t('dataset.reset') }}</el-button>
        </div>
      </div>
    </div>

    <div v-else class="group-cont">
      <div class="field-select-section">
        <div class="section-title">{{ t('dataset.select_group_field') }}</div>
        <div class="field-list">
          <div
            v-for="field in state.availableFields"
            :key="field.id"
            :class="['field-item', { active: state.selectedField?.id === field.id }]"
            @click="selectField(field)"
          >
            <el-icon>
              <Icon
                :name="`field_${fieldType[field.deType]}`"
                :className="`field-icon-${fieldType[field.deType]}`"
              ></Icon>
            </el-icon>
            <span class="field-name">{{ field.name }}</span>
          </div>
        </div>
      </div>

      <div class="group-config-section">
        <div class="section-title">{{ t('dataset.group_config') }}</div>
        
        <el-form label-position="top">
          <el-form-item :label="t('dataset.field_edit_name')">
            <el-input v-model="fieldForm.name" @input="generateOriginName" />
          </el-form-item>

          <el-form-item :label="t('dataset.data_type')">
            <div class="btn-select">
              <el-button
                @click="fieldForm.groupType = 'd'"
                :class="[fieldForm.groupType === 'd' && 'is-active']"
                text
              >
                {{ t('chart.dimension') }}
              </el-button>
              <el-button
                @click="fieldForm.groupType = 'q'"
                :class="[fieldForm.groupType === 'q' && 'is-active']"
                text
              >
                {{ t('chart.quota') }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item :label="t('dataset.field_type')">
            <el-select v-model="fieldForm.deType" style="width: 100%">
              <el-option
                v-for="item in fields"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
                <span style="display: flex; align-items: center">
                  <el-icon>
                    <Icon
                      :name="`field_${fieldType[item.value]}`"
                      :className="`field-icon-${fieldType[item.value]}`"
                    ></Icon>
                  </el-icon>
                </span>
                <span style="margin-left: 5px; font-size: 12px; color: #8492a6">{{ item.label }}</span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item :label="t('dataset.group_type')">
            <el-select 
              v-model="state.groupType" 
              style="width: 100%"
              @change="handleGroupTypeChange"
            >
              <el-option
                v-for="item in groupTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <template v-if="state.groupType === 'distance'">
            <el-form-item :label="t('dataset.group_step')">
              <el-input-number
                v-model="state.groupParams.step"
                :min="1"
                @change="handleParamChange"
                style="width: 100%"
              />
            </el-form-item>
          </template>

          <template v-if="state.groupType === 'quantity'">
            <el-form-item :label="t('dataset.group_quantity')">
              <el-input-number
                v-model="state.groupParams.quantity"
                :min="1"
                @change="handleParamChange"
                style="width: 100%"
              />
            </el-form-item>
          </template>

          <template v-if="state.groupType === 'custom'">
            <el-form-item :label="t('dataset.custom_groups')">
              <div class="custom-groups">
                <div class="group-item" v-for="(group, index) in state.groupParams.groups" :key="index">
                  <el-input v-model="group.label" placeholder="分组名称" />
                  <el-input v-model="group.value" placeholder="匹配值" />
                  <el-button type="danger" text @click="state.groupParams.groups.splice(index, 1)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button @click="state.groupParams.groups.push({ label: '', value: '' })" text>
                  <el-icon><Plus /></el-icon>
                  添加分组
                </el-button>
              </div>
            </el-form-item>
          </template>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
.group-field {
  .value-grouping-cont {
    .value-grouping-header,
    .form-row {
      margin-bottom: 16px;
    }
    .form-row {
      display: flex;
      align-items: center;
      .label {
        width: 80px;
        flex-shrink: 0;
        font-size: 14px;
        color: #1f2329;
      }
      .value {
        color: #1f2329;
      }
    }
    .name-input {
      flex: 1;
      max-width: 320px;
    }
    .add-to-select {
      flex: 1;
      max-width: 200px;
    }
    .search-row .search-input {
      flex: 1;
      max-width: 320px;
    }
    .value-list-section {
      border: 1px solid #dee0e3;
      border-radius: 4px;
      max-height: 320px;
      overflow-y: auto;
      margin-bottom: 12px;
    }
    .group-folders {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 12px;
      border-bottom: 1px solid #dee0e3;
      background: #fafafa;
    }
    .group-folder {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 6px 12px;
      background: #fff;
      border: 1px solid #dcdfe6;
      border-radius: 4px;
      cursor: pointer;
      transition: all 0.2s;
      &:hover {
        border-color: #409eff;
        background: #ecf5ff;
        .edit-icon {
          display: inline-flex;
        }
      }
      &.drag-over {
        border-color: #409eff;
        background: #ecf5ff;
        box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
      }
      &.editing {
        padding: 4px 8px;
      }
      .folder-icon {
        color: #e6a23c;
        font-size: 16px;
      }
      .folder-name {
        font-size: 14px;
        color: #303133;
        cursor: text;
      }
      .folder-name-input {
        width: 100px;
      }
      .folder-count {
        font-size: 12px;
        color: #909399;
      }
      .edit-icon {
        display: none;
        margin-left: 4px;
        color: #909399;
        font-size: 14px;
        cursor: pointer;
        &:hover {
          color: #409eff;
        }
      }
    }
    .value-list .value-item {
      display: flex;
      align-items: center;
      padding: 8px 12px;
      cursor: pointer;
      border-bottom: 1px solid #f0f0f0;
      &:last-child {
        border-bottom: none;
      }
      &:hover {
        background: rgba(51, 112, 255, 0.05);
      }
      &.selected {
        background: rgba(51, 112, 255, 0.1);
      }
      &.dragging {
        opacity: 0.5;
        background: rgba(51, 112, 255, 0.15);
      }
      .value-icon {
        margin-right: 8px;
        font-size: 14px;
        color: #8f959e;
      }
      .value-text {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .value-group-tag {
        font-size: 12px;
        color: #3370ff;
        margin-left: 8px;
      }
    }
    .value-grouping-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
    }
    .checkbox-row {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      color: #1f2329;
      cursor: pointer;
    }
    .action-buttons {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
  }

  .group-cont {
    display: flex;
    gap: 16px;
  }

  .section-title {
    font-size: 14px;
    font-weight: 500;
    margin-bottom: 12px;
    color: #1f2329;
  }

  .field-select-section {
    width: 280px;
    flex-shrink: 0;
    
    .field-list {
      border: 1px solid #dee0e3;
      border-radius: 4px;
      max-height: 400px;
      overflow-y: auto;
    }

    .field-item {
      display: flex;
      align-items: center;
      padding: 8px 12px;
      cursor: pointer;
      border-bottom: 1px solid #f0f0f0;
      
      &:last-child {
        border-bottom: none;
      }

      &:hover {
        background: rgba(51, 112, 255, 0.05);
      }

      &.active {
        background: rgba(51, 112, 255, 0.1);
        border-color: #3370ff;
      }

      .field-name {
        margin-left: 8px;
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  .group-config-section {
    flex: 1;
  }

  .btn-select {
    width: 100px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #ffffff;
    border: 1px solid #bbbfc4;
    border-radius: 4px;

    .is-active {
      background: var(--ed-color-primary-1a, rgba(51, 112, 255, 0.1));
    }

    .ed-button:not(.is-active) {
      color: #1f2329;
    }
  }

  .custom-groups {
    .group-item {
      display: flex;
      gap: 8px;
      margin-bottom: 8px;
      align-items: center;
    }
  }
}
</style>
