import { useI18n } from '@/hooks/web/useI18n'
import SnowflakeId from 'snowflake-id'
import CryptoJS from 'crypto-js'

const snowflake = new SnowflakeId()

const { t } = useI18n()

const guid = () => {
  return snowflake.generate()
}

/** 与后端 TableUtils.fieldNameShort 一致，用于生成 dataeaseName */
const fieldNameShort = (dorisName: string) => {
  if (!dorisName || String(dorisName) === 'null' || String(dorisName) === 'undefined') return ''
  const md5 = CryptoJS.MD5(String(dorisName)).toString()
  return 'f_' + (md5.length >= 24 ? md5.substring(8, 24) : md5)
}

/** 规范化字段：确保 id、datasetTableId、dataeaseName 等不为空，避免 t_a_0.null */
const normalizeField = (f: Record<string, any>, nodeId: string, datasourceId: string) => {
  const o = { ...f }
  const nid = String(nodeId || '')
  const origin = String(o.originName || o.name || '').trim() || 'field'
  if (!o.datasetTableId) o.datasetTableId = nid
  if (!o.datasourceId) o.datasourceId = datasourceId || ''
  if (!o.originName) o.originName = origin
  if (!o.id) o.id = guid()
  if (!o.dataeaseName || o.dataeaseName === 'null') {
    o.dataeaseName = fieldNameShort(nid + '_' + origin)
    o.fieldShortName = o.dataeaseName
  }
  if (!o.fieldShortName) o.fieldShortName = o.dataeaseName || origin
  return o
}

const timestampFormatDate = (timestamp, showMs?: boolean) => {
  if (!timestamp || timestamp === -1) {
    return '-'
  }

  const date = new Date(timestamp)

  const y = date.getFullYear()

  let MM = date.getMonth() + 1
  MM = (MM < 10 ? '0' + MM : MM) as number

  let d = date.getDate()
  d = (d < 10 ? '0' + d : d) as number

  let h = date.getHours()
  h = (h < 10 ? '0' + h : h) as number

  let m = date.getMinutes()
  m = (m < 10 ? '0' + m : m) as number

  let s = date.getSeconds()
  s = (s < 10 ? '0' + s : s) as number

  let format = y + '-' + MM + '-' + d + ' ' + h + ':' + m + ':' + s

  if (showMs === true) {
    const ms = date.getMilliseconds()
    format += ':' + ms
  }

  return format
}

const defaultValueScopeList = [
  { label: t('dataset.scope_edit'), value: 'EDIT' },
  { label: t('dataset.scope_all'), value: 'ALLSCOPE' }
]
const fieldOptions = [
  { label: t('dataset.text'), value: 'TEXT' },
  { label: t('dataset.value'), value: 'LONG' },
  {
    label: t('dataset.value') + '(' + t('dataset.float') + ')',
    value: 'DOUBLE'
  },
  { label: t('dataset.time_year'), value: 'DATETIME-YEAR' },
  {
    label: t('dataset.time_year_month'),
    value: 'DATETIME-YEAR-MONTH',
    children: [
      { value: 'YYYY-MM', label: 'YYYY-MM' },
      { value: 'YYYY/MM', label: 'YYYY/MM' }
    ]
  },
  {
    label: t('dataset.time_year_month_day'),
    value: 'DATETIME-YEAR-MONTH-DAY',
    children: [
      { value: 'YYYY-MM-DD', label: 'YYYY-MM-DD' },
      { value: 'YYYY/MM/DD', label: 'YYYY/MM/DD' }
    ]
  },
  {
    label: t('dataset.time_all'),
    value: 'DATETIME',
    children: [
      { value: 'YYYY-MM-DD HH:mm:ss', label: 'YYYY-MM-DD HH:MI:SS' },
      { value: 'YYYY/MM/DD HH:mm:ss', label: 'YYYY/MM/DD HH:MI:SS' }
    ]
  }
]

const getFieldName = (fields, name) => {
  let n = name
  n = n + '_copy'
  for (let i = 0; i < fields.length; i++) {
    const field = fields[i]
    if (field.name === n) {
      n = getFieldName(fields, n)
    }
  }
  return n
}

const timeTypes = [
  'yyyy-MM-dd',
  'yyyy/MM/dd',
  'yyyy-MM-dd HH:mm:ss',
  'yyyy/MM/dd HH:mm:ss',
  'custom'
]

type NodeType = 'db' | 'sql' | 'operation' | 'result' | 'mirror' | 'dataset'
type UnionType = 'left' | 'right' | 'full' | 'inner' | '1:1' | '1:N' | 'N:1' | 'N:N'
type OperationType =
  | 'join'
  | 'union'
  | 'transform'
  | 'sample'
  | 'sort'
  | 'pivot'
  | 'unpivot'
  | 'group'
  | 'selfloop'
  | 'mirror'
  | 'deduplicate'

interface OperationDef {
  key: OperationType
  label: string
  icon: string
}

const operationList: OperationDef[] = [
  { key: 'join', label: '联接', icon: 'icon_left-association' },
  { key: 'union', label: '联合', icon: 'icon_full-association' },
  { key: 'transform', label: '转换', icon: 'icon_switch_outlined' },
  { key: 'sample', label: '抽样', icon: 'icon-filter' },
  { key: 'sort', label: '排序', icon: 'icon_sort_outlined' },
  { key: 'pivot', label: '透视表', icon: 'table-pivot' },
  { key: 'unpivot', label: '逆透视表', icon: 'icon_replace_outlined' },
  { key: 'group', label: '分组和汇总', icon: 'icon-group' },
  { key: 'selfloop', label: '自循环列', icon: 'icon_sync-play-round_outlined' },
  { key: 'mirror', label: '镜像', icon: 'icon_copy_outlined' },
  { key: 'deduplicate', label: '去重', icon: 'icon_clear_outlined' }
]

const NODE_W = 180
const NODE_H = 40
const PORT_R = 6

interface FlowNode {
  id: string
  tableName: string
  type: NodeType
  operationType?: OperationType
  datasourceId: string
  info: string
  currentDsFields: any[]
  unionType?: string
  unionFields?: any[]
  sqlVariableDetails?: string
  operationConfig?: Record<string, any>
  note?: string
  noteName?: string
  x: number
  y: number
  // 镜像节点特有属性
  sourceNodeId?: string // 源节点ID
  // 数据集引用节点特有属性
  datasetId?: string // 引用的数据集ID
}

interface FlowEdge {
  id: string
  sourceId: string
  targetId: string
}

interface UnionField {
  currentField: Field
  parentField: Field
}

interface Node {
  tableName: string
  type: NodeType
  datasourceId: string
  id: string
  unionType: UnionType
  unionFields: UnionField[]
  info: string
  sqlVariableDetails: string
  currentDsFields: Field[]
  children?: Node[]
  confirm?: boolean
  isShadow?: boolean
  flag?: string
  operationType?: OperationType
  operationConfig?: Record<string, any>
  note?: string
}

interface Field {
  checked: boolean
  deExtractType: number
  deType: number
  name: string
  type: string
  originName: string
  id: string
}

interface DataSource {
  id: string
  name: string
  children?: DataSource[]
}

export {
  NodeType,
  UnionType,
  UnionField,
  DataSource,
  Node,
  Field,
  OperationType,
  OperationDef,
  operationList,
  FlowNode,
  FlowEdge,
  NODE_W,
  NODE_H,
  PORT_R,
  timestampFormatDate,
  defaultValueScopeList,
  fieldOptions,
  guid,
  getFieldName,
  timeTypes,
  fieldNameShort,
  normalizeField
}
