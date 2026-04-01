<script lang="ts" setup>
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { ElMessage } from 'element-plus-secondary'
import { Icon } from '@/components/icon-custom'
import { fieldType } from '@/utils/attr'

export interface FillNullFieldType {
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

// 替换策略选项
const fillStrategies = [
  { label: '固定值', value: 'fixed', icon: 'icon_edit_outlined' },
  { label: '空字符串', value: 'empty_string', icon: 'icon_text_outlined' },
  { label: '0', value: 'zero', icon: 'icon_number_outlined' },
  { label: '平均值（数值字段）', value: 'avg', icon: 'icon_statistics_outlined' },
  { label: '前一个值', value: 'forward', icon: 'icon_arrow_left_outlined' },
  { label: '后一个值', value: 'backward', icon: 'icon_arrow_right_outlined' },
  { label: '众数（最频繁值）', value: 'mode', icon: 'icon_list_outlined' }
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

const fieldForm = reactive<FillNullFieldType>({ ...defaultForm })

const state = reactive({
  selectedField: null as any,
  availableFields: [],
  fillStrategy: 'fixed' as string,
  customValue: '' as string,
  // 原值选择（空 或 空字符串）
  selectedNullType: '' as string,
  // 缺失值预览相关（保留用于预览）
  nullValueList: [] as string[],
  nullSearchKeyword: ''
})

// 原值类型选项
const nullTypeOptions = [
  { label: '空（NULL）', value: 'null' },
  { label: '空字符串', value: 'empty_string' }
]

const formField = ref()

// 根据字段类型过滤可用策略
const availableStrategies = computed(() => {
  if (!state.selectedField) return fillStrategies
  const deType = state.selectedField.deType
  // 数值字段支持所有策略
  if (deType === 2 || deType === 3 || deType === 4) {
    return fillStrategies
  }
  // 非数值字段不支持平均值
  return fillStrategies.filter(s => s.value !== 'avg')
})

// 策略是否需要自定义值
const needCustomValue = computed(() => {
  return state.fillStrategy === 'fixed'
})

const initEdit = (obj, availableFields, nullValues?: string[]) => {
  Object.assign(fieldForm, { ...defaultForm, ...obj })
  state.availableFields = availableFields || []
  state.selectedField = obj.sourceField || null
  state.nullValueList = nullValues || []
  state.nullSearchKeyword = ''
  state.customValue = ''
  state.fillStrategy = 'fixed'
  state.selectedNullType = ''

  if (state.selectedField) {
    fieldForm.deType = state.selectedField.deType
    fieldForm.name = (state.selectedField.name || '') + '-填充'
    fieldForm.originName = (state.selectedField.name || '') + '_filled'
  }
}

const selectField = (field) => {
  state.selectedField = field
  fieldForm.deType = field.deType
  fieldForm.name = (field.name || '') + '-填充'
  fieldForm.originName = (field.name || '') + '_filled'
  // 更新可用策略
  if (field.deType === 2 || field.deType === 3 || field.deType === 4) {
    state.fillStrategy = 'avg'
  } else {
    state.fillStrategy = 'fixed'
  }
}

const handleStrategyChange = () => {
  // 切换策略时清空自定义值
  state.customValue = ''
}

const getResult = () => {
  if (!fieldForm.name.trim()) {
    ElMessage.warning('请输入字段名称')
    return null
  }
  if (needCustomValue.value && !state.customValue.trim()) {
    ElMessage.warning('请输入自定义填充值')
    return null
  }

  return {
    ...fieldForm,
    fillStrategy: state.fillStrategy,
    customValue: state.customValue,
    selectedNullType: state.selectedNullType,
    sourceField: state.selectedField
  }
}

defineExpose({
  initEdit,
  getResult,
  fieldForm
})
</script>

<template>
  <div class="fill-null-field-edit">
    <el-form ref="formField" :model="fieldForm" label-position="top" require-asterisk-position="right">
      <!-- 原始字段信息（只读） -->
      <el-form-item :label="t('dataset.origin_field')">
        <div class="origin-field-info" v-if="state.selectedField">
          <el-icon>
            <Icon
              :className="`field-icon-${fieldType[state.selectedField.deType]}`"
              :name="`field_${fieldType[state.selectedField.deType]}`"
            ></Icon>
          </el-icon>
          <span class="field-name">{{ state.selectedField.name }}</span>
          <span class="field-origin">({{ state.selectedField.originName }})</span>
        </div>
        <span v-else class="no-field">{{ t('dataset.please_select_field') }}</span>
      </el-form-item>

      <!-- 新字段名称 -->
      <el-form-item :label="t('dataset.field_name')" prop="name" :rules="[{ required: true, message: t('dataset.input_edit_name'), trigger: 'blur' }]">
        <el-input v-model="fieldForm.name" :placeholder="t('commons.input_content')" />
      </el-form-item>

      <!-- 替换策略 -->
      <el-form-item :label="t('dataset.fill_strategy')">
        <el-select v-model="state.fillStrategy" @change="handleStrategyChange" style="width: 100%">
          <el-option
            v-for="strategy in availableStrategies"
            :key="strategy.value"
            :value="strategy.value"
            :label="strategy.label"
          >
            <div class="flex-align-center">
              <el-icon style="margin-right: 8px">
                <Icon :name="strategy.icon"></Icon>
              </el-icon>
              <span>{{ strategy.label }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- 自定义填充值（当选择固定值时显示） -->
      <el-form-item v-if="needCustomValue" :label="t('dataset.custom_fill_value')">
        <el-input
          v-model="state.customValue"
          :placeholder="t('dataset.input_fill_value')"
          clearable
        />
      </el-form-item>

      <!-- 原值选择（可选） -->
      <el-form-item :label="t('dataset.null_value_selection')">
        <el-select v-model="state.selectedNullType" style="width: 100%" clearable :placeholder="t('dataset.null_value_placeholder')">
          <el-option
            v-for="option in nullTypeOptions"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          >
            <span>{{ option.label }}</span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
  </div>
</template>

<style lang="less" scoped>
.fill-null-field-edit {
  padding: 0 8px;

  .origin-field-info {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: #f5f6f7;
    border-radius: 4px;
    border: 1px solid #e0e2e5;

    .field-name {
      font-weight: 500;
      color: #1f2329;
    }

    .field-origin {
      color: #8d9199;
      font-size: 12px;
    }
  }

  .no-field {
    color: #8d9199;
    font-style: italic;
  }
}
</style>
