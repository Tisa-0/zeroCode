<script lang="ts" setup>
import { reactive, ref, computed, nextTick, watch } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { Icon } from '@/components/icon-custom'
import { HandleMore } from '@/components/handle-more'
import { propTypes } from '@/utils/propTypes'
import { guid, operationList, NODE_W, NODE_H, PORT_R } from './util'
import type { FlowNode, FlowEdge, Field } from './util'
import { getTableField } from '@/api/dataset'
import { cloneDeep } from 'lodash-es'
import { ElMessage } from 'element-plus'
import AddSql from './AddSql.vue'
import UnionFieldList from './UnionFieldList.vue'
import type { SqlNode } from './AddSql.vue'
import zeroNodeImg from '@/assets/img/drag.png'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'

const appearanceStore = useAppearanceStoreWithOut()
const primaryColor = computed(() =>
  appearanceStore.themeColor === 'custom' ? appearanceStore.customColor : '#3370FF'
)

const { t } = useI18n()
const props = defineProps({
  maskShow: propTypes.bool.def(false),
  offsetX: propTypes.number.def(0),
  offsetY: propTypes.number.def(0),
  dragHeight: propTypes.number.def(260),
  getDsName: propTypes.func
})

const emits = defineEmits([
  'addComplete',
  'joinEditor',
  'updateAllfields',
  'changeUpdate',
  'editNode',
  'edit-node', // 与模板 @edit-node 一致，避免 Vue warn
  'refreshNode',
  'copyNode',
  // 选中节点，用于同步下方预览/管理区域（kebab-case，对应模板上的 @select-node）
  'select-node',
  // 加载数据集引用节点字段
  'load-dataset-fields',
  // 双击数据集节点：跳转到该数据集的编辑页面
  'edit-dataset'
])

const canvasRef = ref<HTMLElement>()
const nodes = reactive<FlowNode[]>([])
const edges = reactive<FlowEdge[]>([])
const selectedNodeId = ref('')
const selectedEdgeId = ref('')

const editSqlField = ref(false)
const sqlNode = ref<SqlNode>()
const editUnion = ref(false)
const nodeField = ref<Field[]>([])
const currentNode = ref<FlowNode>()

// ========== Drag State ==========
const dragging = reactive({
  active: false,
  nodeId: '',
  startMouseX: 0,
  startMouseY: 0,
  startNodeX: 0,
  startNodeY: 0
})

// ========== Connect State ==========
const connecting = reactive({
  active: false,
  sourceId: '',
  x1: 0,
  y1: 0,
  x2: 0,
  y2: 0
})

// ========== Default Result Node ==========
const ensureResultNode = () => {
  if (!nodes.find(n => n.type === 'result')) {
    nodes.push({
      id: 'result_output',
      tableName: '结果集',
      type: 'result',
      datasourceId: '',
      info: '',
      currentDsFields: [],
      x: 600,
      y: 180
    })
  }
}
ensureResultNode()

// ========== Canvas Size ==========
const canvasWidth = computed(() => {
  const maxX = nodes.reduce((m, n) => Math.max(m, n.x + NODE_W), 0)
  return Math.max(maxX + 300, 1200)
})
const canvasHeight = computed(() => {
  const maxY = nodes.reduce((m, n) => Math.max(m, n.y + NODE_H), 0)
  return Math.max(maxY + 200, props.dragHeight)
})

// ========== Helpers ==========
const getNodeIcon = (node: FlowNode) => {
  if (node.type === 'result') return 'icon_orde-list_outlined'
  if (node.type === 'operation') {
    return operationList.find(op => op.key === node.operationType)?.icon || 'icon_switch_outlined'
  }
  if (node.type === 'mirror') return 'icon_copy_outlined'
  if (node.type === 'sql') return 'icon_sql_outlined'
  if (node.type === 'dataset') return 'icon_dataset_outlined'
  return 'reference-table'
}

const getNodeColor = (node: FlowNode) => {
  if (node.type === 'result') return '#04B49C'
  if (node.type === 'operation') return '#409EFF'
  if (node.type === 'mirror') return '#9C27B0'
  if (node.type === 'dataset') return '#FF9800'
  return primaryColor.value
}

/** 数据表节点(db/sql/dataset)仅输出不接收，无输入端口；操作节点、结果集和镜像节点可接收 */
const isInputPortVisible = (node: FlowNode) =>
  node.type !== 'db' && node.type !== 'sql' && node.type !== 'dataset'

const getOutputPort = (node: FlowNode) => ({ x: node.x + NODE_W, y: node.y + NODE_H / 2 })
const getInputPort = (node: FlowNode) => ({ x: node.x, y: node.y + NODE_H / 2 })

// ========== Edge Paths ==========
const getEdgePath = (edge: FlowEdge) => {
  const src = nodes.find(n => n.id === edge.sourceId)
  const tgt = nodes.find(n => n.id === edge.targetId)
  if (!src || !tgt) return ''
  const sp = getOutputPort(src)
  const tp = getInputPort(tgt)
  const dx = Math.max(Math.abs(tp.x - sp.x) * 0.4, 40)
  return `M ${sp.x} ${sp.y} C ${sp.x + dx} ${sp.y}, ${tp.x - dx} ${tp.y}, ${tp.x} ${tp.y}`
}

const getTempEdgePath = () => {
  const { x1, y1, x2, y2 } = connecting
  const dx = Math.max(Math.abs(x2 - x1) * 0.4, 40)
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
}

// ========== SVG Coord Helper ==========
const svgCoord = (e: MouseEvent) => {
  const el = canvasRef.value
  if (!el) return { x: 0, y: 0 }
  const rect = el.getBoundingClientRect()
  return {
    x: e.clientX - rect.left + el.scrollLeft,
    y: e.clientY - rect.top + el.scrollTop
  }
}

// ========== Node Dragging / Selection ==========
const startDragNode = (node: FlowNode, e: MouseEvent) => {
  if ((e.target as HTMLElement)?.closest('.handle-more-trigger')) return
  e.preventDefault()
  selectedNodeId.value = node.id
  emits('select-node', node)
  const { x, y } = svgCoord(e)
  dragging.active = true
  dragging.nodeId = node.id
  dragging.startMouseX = x
  dragging.startMouseY = y
  dragging.startNodeX = node.x
  dragging.startNodeY = node.y
}

const handleNodeDblClick = (node: FlowNode, e: MouseEvent) => {
  e.preventDefault()
  e.stopPropagation()
  if (node.type === 'dataset') {
    emits('edit-dataset', node)
  }
}

const handleMouseMove = (e: MouseEvent) => {
  const { x, y } = svgCoord(e)
  if (dragging.active) {
    const node = nodes.find(n => n.id === dragging.nodeId)
    if (node) {
      node.x = Math.max(0, dragging.startNodeX + (x - dragging.startMouseX))
      node.y = Math.max(0, dragging.startNodeY + (y - dragging.startMouseY))
    }
  }
  if (connecting.active) {
    connecting.x2 = x
    connecting.y2 = y
  }
}

const handleMouseUp = () => {
  if (dragging.active) {
    dragging.active = false
      emits('changeUpdate')
    }
  if (connecting.active) {
    connecting.active = false
  }
}

// ========== Connection ==========
const startConnect = (node: FlowNode, e: MouseEvent) => {
  e.preventDefault()
  e.stopPropagation()
  const port = getOutputPort(node)
  connecting.active = true
  connecting.sourceId = node.id
  connecting.x1 = port.x
  connecting.y1 = port.y
  const { x, y } = svgCoord(e)
  connecting.x2 = x
  connecting.y2 = y
}

const endConnect = (targetNode: FlowNode) => {
  if (!connecting.active) return
  if (connecting.sourceId === targetNode.id) return
  if (!isInputPortVisible(targetNode)) return
  if (edges.some(e => e.sourceId === connecting.sourceId && e.targetId === targetNode.id)) return

  const sourceNode = nodes.find(n => n.id === connecting.sourceId)

  // 联接操作节点（join）允许接收多个输入
  const isJoinOperation = targetNode.type === 'operation' && (targetNode as any).operationType === 'join'
  // 联合操作节点（union）允许接收多个输入
  const isUnionOperation = targetNode.type === 'operation' && (targetNode as any).operationType === 'union'
  // 镜像节点允许多个输入（多个数据节点/操作节点可以连接到同一个镜像节点）
  const isMirrorNode = targetNode.type === 'mirror'
  // 去重节点、排序节点等单输入操作节点
  const isSingleInputOperation = targetNode.type === 'operation' && 
    ((targetNode as any).operationType === 'deduplicate' || 
     (targetNode as any).operationType === 'sort' ||
     (targetNode as any).operationType === 'transform' ||
     (targetNode as any).operationType === 'sample' ||
     (targetNode as any).operationType === 'pivot' ||
     (targetNode as any).operationType === 'unpivot' ||
     (targetNode as any).operationType === 'group' ||
     (targetNode as any).operationType === 'selfloop')
  
  if (!isJoinOperation && !isUnionOperation && !isSingleInputOperation && !isMirrorNode) {
    // 非镜像节点只能有一个输入（目标节点只能有一条入边）
    if (sourceNode?.type !== 'mirror') {
      const existingEdge = edges.find(e => e.targetId === targetNode.id)
      if (existingEdge) {
        ElMessage.warning('该节点已有输入连接')
        connecting.active = false
        return
      }
    }
  } else if (isMirrorNode) {
    // 镜像节点：允许多个输入，不做限制
  } else if (isJoinOperation) {
    // JOIN 节点：检查是否已满两个输入
    const existingEdges = edges.filter(e => e.targetId === targetNode.id)
    if (existingEdges.length >= 2) {
      ElMessage.warning('联接节点最多只能接收两个输入')
      connecting.active = false
      return
    }
  } else if (isUnionOperation) {
    // UNION 节点：检查是否已满两个输入
    const existingEdges = edges.filter(e => e.targetId === targetNode.id)
    if (existingEdges.length >= 2) {
      ElMessage.warning('联合节点最多只能接收两个输入')
      connecting.active = false
      return
    }
  } else if (isSingleInputOperation) {
    // 单输入操作节点：检查是否已有一个输入
    const existingEdge = edges.find(e => e.targetId === targetNode.id)
    if (existingEdge) {
      ElMessage.warning('该节点已有输入连接')
      connecting.active = false
      return
    }
  }

  // 镜像节点：记录源节点ID（数据节点/操作节点 → 镜像节点）
  if (isMirrorNode) {
    targetNode.sourceNodeId = sourceNode?.id || ''
  }

  edges.push({ id: guid(), sourceId: connecting.sourceId, targetId: targetNode.id })
  connecting.active = false
  emits('changeUpdate')
  emits('addComplete')
}

// 获取镜像节点的源数据节点ID
const getSourceDataNodeId = (mirrorNode: FlowNode): string => {
  const edge = edges.find(e => e.sourceId === mirrorNode.id)
  if (!edge) return ''
  // 找到源节点的源（如果是镜像节点则递归查找）
  const sourceNode = nodes.find(n => n.id === edge?.sourceId)
  if (!sourceNode) return ''
  if (sourceNode.type === 'mirror') {
    return getSourceDataNodeId(sourceNode)
  }
  return sourceNode.id
}

// ========== Drop Handler ==========
const handleDrop = (ev: DragEvent) => {
  ev.preventDefault()
  const raw = ev.dataTransfer?.getData('text')
  if (!raw) return
  const data = JSON.parse(raw)
  const { x, y } = svgCoord(ev as unknown as MouseEvent)
  const nx = Math.max(0, x - NODE_W / 2)
  const ny = Math.max(0, y - NODE_H / 2)

  if (data.type === 'operation') {
    // 镜像节点特殊处理
    if (data.operationType === 'mirror') {
      nodes.push({
        id: guid(),
        tableName: '镜像',
        type: 'mirror',
        datasourceId: '',
        info: JSON.stringify({ operationType: 'mirror' }),
        currentDsFields: [],
        operationConfig: {},
        x: nx,
        y: ny,
        sourceNodeId: ''
      })
      emits('changeUpdate')
      emits('addComplete')
      return
    }

    nodes.push({
      id: guid(),
      tableName: data.tableName || data.name,
      type: 'operation',
      operationType: data.operationType,
      datasourceId: '',
      info: JSON.stringify({ table: data.tableName, operationType: data.operationType }),
      currentDsFields: [],
      operationConfig: {},
      x: nx,
      y: ny
    })
    emits('changeUpdate')
    emits('addComplete')
    return
  }

  if (data.type === 'sql') {
    const nodeId = guid()
    nodes.push({
      id: nodeId,
      tableName: data.tableName || '自定义SQL',
      type: 'sql',
      datasourceId: data.datasourceId,
      info: JSON.stringify({ table: data.tableName, sql: '' }),
      currentDsFields: [],
      sqlVariableDetails: null,
      x: nx,
      y: ny
    })
    sqlNode.value = {
      sql: '',
      tableName: data.tableName,
      id: nodeId,
      datasourceId: data.datasourceId
    }
    editSqlField.value = true
    emits('changeUpdate')
    emits('addComplete')
    return
  }

  // 数据集引用节点处理
  if (data.type === 'dataset') {
    const nodeId = guid()
    const newNode: FlowNode = {
      id: nodeId,
      tableName: data.tableName || data.name,
      type: 'dataset',
      datasourceId: '',
      info: JSON.stringify({ datasetId: data.datasetId, reference: true, table: data.tableName }),
      currentDsFields: [],
      noteName: data.name,
      x: nx,
      y: ny,
      datasetId: data.datasetId
    }
    nodes.push(newNode)
    emits('changeUpdate')
    emits('addComplete')
    // 触发事件让父组件加载数据集字段
    emits('load-dataset-fields', { node: newNode, datasetId: data.datasetId })
    return
  }

  const nodeId = guid()
  const newNode: FlowNode = {
    id: nodeId,
    tableName: data.tableName,
    type: data.type || 'db',
    datasourceId: data.datasourceId,
    info: JSON.stringify({ table: data.tableName, sql: '' }),
    currentDsFields: [],
    noteName: data.name,
    x: nx,
    y: ny
  }
  nodes.push(newNode)
  emits('changeUpdate')
  getTableField({
    datasourceId: data.datasourceId,
    id: nodeId,
    info: newNode.info,
    tableName: data.tableName,
    type: data.type || 'db'
  }).then(res => {
    const fields = res as unknown as Field[]
    if (!fields?.length) {
      ElMessage.warning('获取表字段失败，接口返回为空，请检查数据源配置或表是否存在')
    }
    ;(fields || []).forEach(f => (f.checked = true))
    newNode.currentDsFields = cloneDeep(fields || [])
    emits('addComplete')
    emits('updateAllfields')
    emits('select-node', newNode)
  }).catch(e => {
    ElMessage.error(e?.message?.includes('timeout') ? '获取表字段超时，请稍后重试或检查数据源连接' : '获取表字段失败')
  })
}

// ========== Menus ==========
const dataMenu = [
  { svgName: 'icon_edit_outlined', label: '编辑节点', command: 'editNode' },
  { svgName: 'icon_text-box_outlined', label: '字段选择', command: 'editerField' },
  { svgName: 'icon_refresh_outlined', label: '刷新', command: 'refresh' },
  { svgName: 'icon_info_outlined', label: '添加备注', command: 'addNote' },
  { svgName: 'icon_copy_outlined', label: '复制', command: 'copyNode' },
  { svgName: 'icon_delete-trash_outlined', label: '删除节点', command: 'del' }
]
const opMenu = [
  { svgName: 'icon_edit_outlined', label: '编辑节点', command: 'editNode' },
  { svgName: 'icon_refresh_outlined', label: '刷新', command: 'refresh' },
  { svgName: 'icon_info_outlined', label: '添加备注', command: 'addNote' },
  { svgName: 'icon_copy_outlined', label: '复制', command: 'copyNode' },
  { svgName: 'icon_delete-trash_outlined', label: '删除节点', command: 'del' }
]
// 镜像节点菜单（无编辑节点）
const mirrorMenu = [
  { svgName: 'icon_refresh_outlined', label: '刷新', command: 'refresh' },
  { svgName: 'icon_info_outlined', label: '添加备注', command: 'addNote' },
  { svgName: 'icon_copy_outlined', label: '复制', command: 'copyNode' },
  { svgName: 'icon_delete-trash_outlined', label: '删除节点', command: 'del' }
]
const sqlExtraMenu = [
  { svgName: 'icon_edit_outlined', label: '编辑SQL', command: 'editerSql' },
  { svgName: 'icon_rename_outlined', label: t('datasource.field_rename'), command: 'rename' }
]
// 数据集引用节点菜单
const datasetMenu = [
  { svgName: 'icon_refresh_outlined', label: '刷新', command: 'refresh' },
  { svgName: 'icon_info_outlined', label: '添加备注', command: 'addNote' },
  { svgName: 'icon_copy_outlined', label: '复制', command: 'copyNode' },
  { svgName: 'icon_delete-trash_outlined', label: '删除节点', command: 'del' }
]
const getNodeMenuList = (n: FlowNode) => {
  if (n.type === 'operation') return opMenu
  if (n.type === 'sql') return [...sqlExtraMenu, ...dataMenu]
  if (n.type === 'mirror') return mirrorMenu
  if (n.type === 'dataset') return datasetMenu
  return dataMenu
}

// ========== Commands ==========
const dialogNote = ref(false)
const noteTarget = ref<FlowNode | null>(null)
const noteText = ref('')
const dialogRename = ref(false)
const renameForm = ref()
const renameParam = reactive({ name: '', id: '' })

const handleCommand = (node: FlowNode, cmd: string) => {
  switch (cmd) {
    case 'editNode':
      // 使用 kebab-case 事件名，与父组件上的 @edit-node 对齐
      emits('edit-node', node)
      break
    case 'editerField':
      currentNode.value = cloneDeep(node)
    getTableField({
        datasourceId: node.datasourceId,
        id: node.id,
        info: node.info,
        tableName: node.tableName,
        type: node.type
    }).then(res => {
      nodeField.value = res as unknown as Field[]
        nodeField.value.forEach(f => {
          f.checked = (node.currentDsFields || []).map(d => d.originName).includes(f.originName)
      })
      editUnion.value = true
    })
      break
    case 'refresh':
      emits('refreshNode', node)
      break
    case 'addNote':
      noteTarget.value = node
      noteText.value = node.note || ''
      dialogNote.value = true
      break
    case 'copyNode': {
      const copy: FlowNode = {
        ...cloneDeep(node),
        id: guid(),
        x: node.x + 30,
        y: node.y + 40
      }
      copy.tableName = node.tableName + '_copy'

      // 镜像节点复制：同时复制连接关系
      if (node.type === 'mirror') {
        // 找到原节点的所有输出边，复制到新节点
        const sourceEdges = edges.filter(e => e.sourceId === node.id)
        sourceEdges.forEach(edge => {
          edges.push({
            id: guid(),
            sourceId: copy.id,
            targetId: edge.targetId
          })
        })
        // 复制源节点ID
        copy.sourceNodeId = node.sourceNodeId
      }

      nodes.push(copy)
      emits('changeUpdate')
      break
    }
    case 'editerSql': {
      const info = JSON.parse(node.info || '{}')
      sqlNode.value = {
        sql: info.sql || '',
        tableName: node.tableName,
        id: node.id,
        variables: node.sqlVariableDetails ? JSON.parse(node.sqlVariableDetails) : undefined,
        datasourceId: node.datasourceId
      }
      editSqlField.value = true
      break
    }
    case 'rename':
      renameParam.name = node.tableName
      renameParam.id = node.id
      dialogRename.value = true
      break
    case 'delEdge':
      // 删除单条连线已改为单击连线后点击删除按钮，此处仅处理兼容
      break
    case 'del':
      deleteNode(node)
      break
  }
}

const deleteNode = (node: FlowNode) => {
  if (node.type === 'result') return
  const idx = nodes.findIndex(n => n.id === node.id)
  if (idx > -1) {
    nodes.splice(idx, 1)
    deleteEdges(node, false)
  }
}

// 删除与某节点相关的所有连线，但保留节点本身（默认触发更新）
const deleteEdges = (node: FlowNode, triggerUpdate = true) => {
  for (let i = edges.length - 1; i >= 0; i--) {
    if (edges[i].sourceId === node.id || edges[i].targetId === node.id) {
      edges.splice(i, 1)
    }
  }
  if (triggerUpdate) {
    emits('changeUpdate')
    emits('addComplete')
    emits('updateAllfields')
  }
}

const confirmNote = () => {
  if (noteTarget.value) {
    const target = noteTarget.value
    const n = nodes.find(x => x.id === target.id)
    if (n) n.note = noteText.value
  }
  dialogNote.value = false
}

const confirmRename = () => {
  renameForm.value?.validate(valid => {
    if (valid) {
      const n = nodes.find(x => x.id === renameParam.id)
      if (n) n.tableName = renameParam.name
      dialogRename.value = false
    }
  })
}

// ========== Field Editor ==========
const changeNodeFields = val => {
  if (currentNode.value) currentNode.value.currentDsFields = val
}
const closeEditUnion = () => {
  editUnion.value = false
  nodeField.value = []
  currentNode.value = null
}
const confirmEditUnion = () => {
  if (currentNode.value) {
    const cur = currentNode.value
    const n = nodes.find(x => x.id === cur.id)
    if (n) n.currentDsFields = cur.currentDsFields
              }
              closeEditUnion()
  emits('updateAllfields')
}

// ========== SQL Editor ==========
const saveSqlNode = (val, cb) => {
  const n = nodes.find(x => x.id === val.id)
  if (n) {
    n.info = JSON.stringify({ table: val.tableName, sql: val.sql })
    n.tableName = val.tableName
    n.sqlVariableDetails = val.sqlVariableDetails
    n.datasourceId = val.datasourceId
    if (!n.currentDsFields?.length) {
      getTableField({
        datasourceId: val.datasourceId,
        id: val.id,
        info: n.info,
        tableName: val.tableName,
        type: 'sql'
      }).then(res => {
        const fields = res as unknown as Field[]
        fields.forEach(f => (f.checked = true))
        n.currentDsFields = cloneDeep(fields)
        emits('addComplete')
                emits('updateAllfields')
              })
            }
          }
  editSqlField.value = false
  cb?.()
}
const closeSqlNode = () => {
  editSqlField.value = false
}

// 获取选中连线的位置信息
const selectedEdgePosition = computed(() => {
  if (!selectedEdgeId.value) return null
  const edge = edges.find(e => e.id === selectedEdgeId.value)
  if (!edge) return null
  const src = nodes.find(n => n.id === edge.sourceId)
  const tgt = nodes.find(n => n.id === edge.targetId)
  if (!src || !tgt) return null
  const sp = getOutputPort(src)
  const tp = getInputPort(tgt)
  return {
    x: (sp.x + tp.x) / 2,
    y: (sp.y + tp.y) / 2
  }
})

// ========== Edge Click ==========
const handleEdgeClick = (edge: FlowEdge) => {
  selectedEdgeId.value = edge.id
}

// 删除单条连线
const deleteSelectedEdge = () => {
  if (!selectedEdgeId.value) return
  const idx = edges.findIndex(e => e.id === selectedEdgeId.value)
  if (idx > -1) {
    edges.splice(idx, 1)
    selectedEdgeId.value = ''
    emits('changeUpdate')
    emits('addComplete')
    emits('updateAllfields')
  }
}

// 取消选中连线
const clearEdgeSelection = () => {
  selectedEdgeId.value = ''
}

// 允许通过节点 id 打开联接编辑（配合右键“编辑节点”）
const openJoinEditorByNodeId = (nodeId: string) => {
  const node = nodes.find(n => n.id === nodeId)
  if (!node) return

  // 情形一：联接操作节点（operationType === 'join'）
  if (node.type === 'operation' && (node as any).operationType === 'join') {
    // 找到两个直接上游的数据节点（db/sql）
    const upstreams = edges
      .filter(e => e.targetId === nodeId)
      .map(e => nodes.find(n => n.id === e.sourceId))
      .filter(
        (n): n is FlowNode =>
          !!n && (n.type === 'db' || n.type === 'sql' || n.type === 'dataset')
      )

    if (upstreams.length === 2) {
      // 固定 child/parent 顺序：把“已有联接配置（unionFields 非空）”的表作为 child（第一个参数），
      // 另一张表作为 parent（第二个参数），保证联接配置始终落到 child 表节点上（后端读取 child.unionToParent）。
      const [a, b] = upstreams
      const aHas = Array.isArray((a as any).unionFields) && (a as any).unionFields.length > 0
      const bHas = Array.isArray((b as any).unionFields) && (b as any).unionFields.length > 0
      if (aHas && !bHas) emits('joinEditor', [a, b])
      else if (bHas && !aHas) emits('joinEditor', [b, a])
      else emits('joinEditor', [a, b])
    }
      return
  }

  // 情形二：直接右键某张表节点（db/sql/dataset）
  if (node.type === 'db' || node.type === 'sql' || node.type === 'dataset') {
    const tgt = node
    // 优先找指向该表的上游表节点
    let edge = edges.find(e => e.targetId === nodeId)
    let src =
      edge &&
      nodes.find(
        n => n.id === edge!.sourceId && (n.type === 'db' || n.type === 'sql' || n.type === 'dataset')
      )

    // 如果没有上游表，再尝试把当前表作为上游，找一个下游表
    if (!src) {
      edge = edges.find(e => e.sourceId === nodeId)
      src =
        edge &&
        nodes.find(
          n => n.id === edge!.targetId && (n.type === 'db' || n.type === 'sql' || n.type === 'dataset')
        )
    }

    if (src) {
      emits('joinEditor', [tgt, src])
    }
  }
}

const handleCanvasClick = (e: MouseEvent) => {
  const tag = (e.target as Element)?.tagName?.toLowerCase()
  if (tag === 'svg' || tag === 'rect' || tag === 'pattern' || tag === 'path') {
    selectedNodeId.value = ''
    selectedEdgeId.value = ''
    emits('select-node', null)
  }
}

// ========== Cross Datasources ==========
const crossDatasources = computed(() => {
  const ids = nodes
    .filter(n => n.type === 'db' || n.type === 'sql')
    .map(n => n.datasourceId)
    .filter(Boolean)
  return new Set(ids).size > 1
})

const nodeNameList = computed(() =>
  nodes.filter(n => n.type !== 'result').map(n => `${n.tableName}${n.datasourceId}`)
)

// ========== Mirror Node Helper ==========
/**
 * 获取镜像节点的源节点（递归解析）
 * 镜像节点透明地传递源节点/操作节点的数据
 */
const getMirrorSourceNode = (nodeId: string): FlowNode | null => {
  const node = nodes.find(n => n.id === nodeId)
  if (!node) return null
  // 非镜像节点直接返回
  if (node.type !== 'mirror') return node
  // 镜像节点解析到源节点（数据节点或操作节点）
  if ((node as any).sourceNodeId) {
    const sourceNode = nodes.find(n => n.id === (node as any).sourceNodeId)
    if (sourceNode) {
      if (sourceNode.type === 'mirror') {
        return getMirrorSourceNode(sourceNode.id)
      }
      return sourceNode
    }
  }
  return null
}

// ========== Convert Flow → Union Tree（结构集同步上游 + 根据连线形成数据流） ==========
const getNodeList = () => {
  const dataNodes = nodes.filter(n => n.type === 'db' || n.type === 'sql' || n.type === 'dataset')
  if (!dataNodes.length) return []
  const resultNode = nodes.find(n => n.type === 'result')
  if (!resultNode) return []

  // 鎵惧埌鐩存帴杩炲埌 result 鐨勮妭鐐癸紙鍙兘鏄暟鎹妭鐐广€佹搷浣滆妭鐐规垨闀滃儚鑺傜偣锛?
  const directToResult = edges
    .filter(e => e.targetId === resultNode.id)
    .map(e => nodes.find(n => n.id === e.sourceId))
    .filter(Boolean) as FlowNode[]

  if (!directToResult.length) return []

  // 瑙ｆ瀽闀滃儚鑺傜偣鑾峰彇鐪熷疄璧峰鑺傜偣
  let startNode = directToResult[0]
  if (startNode?.type === 'mirror') {
    const resolved = getMirrorSourceNode(startNode.id)
    if (resolved) startNode = resolved
    else startNode = dataNodes[0]
  }

  const startId = startNode?.type === 'operation' ? startNode.id : (startNode?.id || dataNodes[0].id)

  // 鏀堕泦鏁版嵁鑺傜偣鍜岀埗瀛愬叧绯?
  const visited = new Set<string>()
  const dataNodeMap = new Map<string, FlowNode>()
  const nodeChildren = new Map<string, FlowNode[]>()
  const stack: string[] = [startId]

  while (stack.length) {
    const currentId = stack.pop() as string
    if (visited.has(currentId)) continue
    visited.add(currentId)

    const currentNode = nodes.find(n => n.id === currentId)
    if (!currentNode) continue

    // 瑙ｆ瀽褰撳墠鑺傜偣锛堝鏋滄槸闀滃儚锛?
    let resolvedNode = currentNode
    if (currentNode.type === 'mirror') {
      const sourceNode = getMirrorSourceNode(currentId)
      if (sourceNode) resolvedNode = sourceNode
    }

    // 鏀堕泦鏁版嵁鑺傜偣
    if (resolvedNode.type === 'db' || resolvedNode.type === 'sql' || resolvedNode.type === 'dataset') {
      dataNodeMap.set(resolvedNode.id, resolvedNode)
    }

    // 鑾峰彇涓婃父鑺傜偣
    const incoming = edges.filter(e => e.targetId === currentId && e.sourceId !== resultNode.id)
    incoming.forEach(edge => {
      const src = nodes.find(n => n.id === edge.sourceId)
      if (!src || visited.has(src.id)) return

      // 瑙ｆ瀽涓婃父闀滃儚鑺傜偣
      let resolvedSrc = src
      if (src.type === 'mirror') {
        const mirrorSrc = getMirrorSourceNode(src.id)
        if (mirrorSrc) resolvedSrc = mirrorSrc
      }

      // 璁板綍鐖跺瓙鍏崇郴
      if (!nodeChildren.has(currentId)) {
        nodeChildren.set(currentId, [])
      }
      const children = nodeChildren.get(currentId)!
      if (!children.find(c => c.id === resolvedSrc.id)) {
        children.push(resolvedSrc)
      }

      stack.push(src.id)
    })
  }

  // 确定 root 节点
  let root: FlowNode | null = startNode?.type === 'db' || startNode?.type === 'sql' || startNode?.type === 'dataset' ? startNode : null

  if (!root && startNode?.type === 'mirror') {
    root = getMirrorSourceNode(startNode.id) || null
  }

  // 如果起始节点是操作节点（非join），回溯到其上游数据节点作为 root
  if (!root && startNode?.type === 'operation' && (startNode as any).operationType !== 'join') {
    const upstreamEdge = edges.find(e => e.targetId === startNode.id && e.sourceId !== resultNode.id)
    if (upstreamEdge) {
      const upstreamNode = nodes.find(n => n.id === upstreamEdge.sourceId)
      if (upstreamNode) {
        if (upstreamNode.type === 'mirror') {
          const resolved = getMirrorSourceNode(upstreamNode.id)
          if (resolved && (resolved.type === 'db' || resolved.type === 'sql')) {
            root = resolved
          }
        } else if (upstreamNode.type === 'db' || upstreamNode.type === 'sql') {
          root = upstreamNode
        }
      }
    }
  }

  if (!root && startNode?.type === 'operation' && (startNode as any).operationType === 'join') {
    const upstreamTables = edges
      .filter(e => e.targetId === startNode.id)
      .map(e => nodes.find(n => n.id === e.sourceId))
      .filter((n): n is FlowNode => !!n && (n.type === 'db' || n.type === 'sql' || n.type === 'dataset'))
    if (upstreamTables.length === 2) {
      const [a, b] = upstreamTables
      const aHas = Array.isArray((a as any).unionFields) && (a as any).unionFields.length > 0
      const bHas = Array.isArray((b as any).unionFields) && (b as any).unionFields.length > 0
      if (aHas && !bHas) root = b
      else if (bHas && !aHas) root = a
      else root = a
    }
  }
  if (!root) root = dataNodeMap.values().next().value || dataNodes[0]

  // 鏋勫缓 children锛堟帓闄よ嚜韬拰閲嶅锛?
  const buildChildren = (parentId: string): FlowNode[] => {
    const directChildren = nodeChildren.get(parentId) || []
    return directChildren.filter(c => c.id !== parentId && c.id !== root!.id)
  }

  const children = buildChildren(root.id)
  return [buildNodeItem(root, children)]
}
const buildNodeItem = (node: FlowNode, children: FlowNode[]) => ({
  tableName: node.tableName,
  type: node.type,
  datasourceId: node.datasourceId,
  id: node.id,
  info: node.info,
  unionType: node.unionType || 'left',
  unionFields: node.unionFields || [],
  currentDsFields: node.currentDsFields || [],
  sqlVariableDetails: node.sqlVariableDetails,
  children: children.map(c => buildNodeItem(c, []))
})

// ========== Helpers for reconstructing operation nodes / layout ==========
const ensureSortNodeBetweenRootAndResult = (sortFields: Array<Record<string, any>> | undefined | null) => {
  if (!sortFields || !sortFields.length) return
  const resultNode = nodes.find(n => n.type === 'result')
  const dataNodes = nodes.filter(n => n.type !== 'result')
  if (!resultNode || !dataNodes.length) return

  // 若已经存在排序节点且位于结果集上游，则不重复创建
  const existingSort = nodes.find(n => n.type === 'operation' && n.operationType === 'sort')
  if (existingSort) return

  const root = dataNodes[0]
  // 移除 root 直连 result 的边，改由 root → sort → result
  const directIdx = edges.findIndex(e => e.sourceId === root.id && e.targetId === resultNode.id)
  if (directIdx === -1) return
  edges.splice(directIdx, 1)

  // 从 sortFields 推断排序配置
  const sf = sortFields[0] as any
  const sortField =
    sf?.dataeaseName || sf?.originName || sf?.name || (sf?.id != null ? String(sf.id) : '')
  const sortOrder = sf?.orderDirection === 'desc' ? 'desc' : 'asc'

  const sortNodeId = guid()
  const sortNode: FlowNode = {
    id: sortNodeId,
    tableName: '排序',
    type: 'operation',
    operationType: 'sort',
    datasourceId: root.datasourceId,
    info: '',
    currentDsFields: root.currentDsFields || [],
    operationConfig: {
      sortField,
      sortOrder
    },
    x: (root.x + resultNode.x) / 2,
    y: (root.y + resultNode.y) / 2
  }
  nodes.push(sortNode)
  edges.push({ id: guid(), sourceId: root.id, targetId: sortNodeId })
  edges.push({ id: guid(), sourceId: sortNodeId, targetId: resultNode.id })
}

// ========== Init from existing tree ==========
let isUpdate = false
const initState = (
  nodeList: any[],
  options?: { sortFields?: Array<Record<string, any>>; graphState?: Record<string, any> }
) => {
  nodes.splice(0, nodes.length)
  edges.splice(0, edges.length)
  ensureResultNode()

  const gs = options?.graphState
  const hasGraphState =
    gs &&
    Array.isArray(gs.nodes) &&
    gs.nodes.length > 0 &&
    Array.isArray(gs.edges)

  const xStep = 250
  const yStep = 70
  const yIdx = { value: 0 }

  const addTree = (list: any[], depth: number) => {
    list.forEach(item => {
      const x = 50 + depth * xStep
      const y = 80 + yIdx.value * yStep
      yIdx.value++
      const nodeId = item.id || guid()
      nodes.push({
        id: nodeId,
        tableName: item.tableName,
        type: item.type || 'db',
        datasourceId: item.datasourceId,
        info: item.info,
        currentDsFields: item.currentDsFields || [],
        unionType: item.unionType,
        unionFields: item.unionFields,
        sqlVariableDetails: item.sqlVariableDetails,
        x,
        y,
        // 镜像节点：保存源节点关联
        sourceNodeId: item.type === 'mirror' ? (item.sourceNodeId || '') : undefined
      })
      if (item.children?.length) addTree(item.children, depth)
    })
  }
  addTree(nodeList, 0)

  if (hasGraphState) {
    // ---- 完整还原：以 graphState 为真相恢复所有节点（含联合时两个数据表节点）和连线 ----
    const gsNodes = gs.nodes as Array<Record<string, any>>
    const gsEdges = gs.edges as Array<Record<string, any>>

    // 从 nodeList（union 树）收集 id -> 表信息，用于还原 db/sql 的 info、currentDsFields 等
    const nodeListDataById = new Map<string, any>()
    const collectNodeListData = (list: any[]) => {
      if (!list || !Array.isArray(list)) return
      list.forEach(item => {
        const id = item?.id
        if (id) nodeListDataById.set(id, item)
        if (item?.children?.length) collectNodeListData(item.children)
      })
    }
    collectNodeListData(nodeList)

    const existingIds = new Set(nodes.map(n => n.id))
    gsNodes.forEach(saved => {
      if (existingIds.has(saved.id)) return
      const id = saved.id
      const type = saved.type
      if (type === 'operation') {
        const opNode: FlowNode = {
          id,
          tableName: saved.tableName || saved.operationType || 'operation',
          type: 'operation' as any,
          operationType: saved.operationType,
          datasourceId: saved.datasourceId || '',
          info: '',
          currentDsFields: [],
          operationConfig: saved.operationConfig || {},
          x: saved.x ?? 300,
          y: saved.y ?? 150
        }
        // 兼容旧 graphState：未保存过“配置摘要标题”的去重节点，回显时自动生成标题
        if (
          opNode.operationType === 'deduplicate' &&
          (!saved.tableName || saved.tableName === 'deduplicate' || saved.tableName === '去重')
        ) {
          opNode.tableName = buildDeduplicateTitle(opNode.operationConfig)
        }
        nodes.push(opNode)
      } else if (type === 'db' || type === 'sql' || type === 'dataset') {
        const fromList = nodeListDataById.get(id)
        // graphState 里可能残留已删除/未落库的数据表节点；这类孤儿节点没有完整 info，
        // 若继续恢复会在编辑数据集时把空节点再次提交给后端并触发 tableInfoDTO is null。
        // 兼容“同表联合”等场景：union 树可能缺少其中一个表节点，此时允许从 graphState 还原（前提：info 存在）。
        const info = fromList?.info ?? saved?.info ?? ''
        if (!info) return
        nodes.push({
          id,
          tableName:
            saved.tableName ?? fromList?.tableName ?? (type === 'sql' ? 'SQL' : type === 'dataset' ? '数据集' : '表'),
          type: type as any,
          datasourceId: saved.datasourceId ?? fromList?.datasourceId ?? '',
          info,
          currentDsFields:
            fromList?.currentDsFields ?? saved?.currentDsFields ?? [],
          unionType: fromList?.unionType ?? saved?.unionType,
          unionFields: fromList?.unionFields ?? saved?.unionFields,
          sqlVariableDetails: fromList?.sqlVariableDetails ?? saved?.sqlVariableDetails,
          datasetId: (saved as any).datasetId ?? fromList?.datasetId,
          x: saved.x ?? 300,
          y: saved.y ?? 150
        })
      } else if (type === 'mirror') {
        // 镜像节点：恢复节点及其源节点关联
        const fromList = nodeListDataById.get(id)
        nodes.push({
          id,
          tableName: saved.tableName || '镜像',
          type: 'mirror' as any,
          datasourceId: '',
          info: JSON.stringify({ operationType: 'mirror' }),
          currentDsFields: [],
          operationConfig: {},
          x: saved.x ?? 300,
          y: saved.y ?? 150,
          sourceNodeId: saved.sourceNodeId || ''
        })
      }
    })

    // 应用所有节点的保存位置（覆盖 addTree 的默认布局）
    const posMap = new Map(gsNodes.map(n => [n.id, n]))
    nodes.forEach(n => {
      const layout = posMap.get(n.id)
      if (layout) {
        n.x = layout.x
        n.y = layout.y
      }
    })

    // 使用 graphState 保存的连线替代自动生成的连线
    edges.splice(0, edges.length)
    const allNodeIds = new Set(nodes.map(n => n.id))
    gsEdges.forEach(saved => {
      if (allNodeIds.has(saved.sourceId) && allNodeIds.has(saved.targetId)) {
        edges.push({
          id: saved.id || guid(),
          sourceId: saved.sourceId,
          targetId: saved.targetId
        })
      }
    })
  } else {
    // ---- 无 graphState（首次 / 旧数据）：自动布局 + 按 sortFields 插入排序节点 ----
    const resultNode = nodes.find(n => n.type === 'result')
    const dataNodes = nodes.filter(n => n.type !== 'result')
    if (dataNodes.length && resultNode) {
      edges.push({ id: guid(), sourceId: dataNodes[0].id, targetId: resultNode.id })
      for (let i = 1; i < dataNodes.length; i++) {
        edges.push({ id: guid(), sourceId: dataNodes[i].id, targetId: dataNodes[0].id })
      }
      resultNode.x = Math.max(...dataNodes.map(n => n.x)) + xStep
      resultNode.y = dataNodes.reduce((s, n) => s + n.y, 0) / dataNodes.length
    }
    if (options?.sortFields?.length) {
      ensureSortNodeBetweenRootAndResult(options.sortFields)
    }
  }

    nextTick(() => {
    isUpdate = true
      emits('addComplete')
    })
}

const getGraphState = () => {
  return {
    nodes: nodes.map(n => ({
      id: n.id,
      x: n.x,
      y: n.y,
      type: n.type,
      tableName: n.tableName,
      datasourceId: n.datasourceId,
      // db/sql/dataset 节点：用于在回显时即使 union 树缺失也能完整恢复节点
      info: n.type === 'db' || n.type === 'sql' || n.type === 'dataset' ? n.info : undefined,
      currentDsFields:
        n.type === 'db' || n.type === 'sql' || n.type === 'dataset' ? (n.currentDsFields || []) : undefined,
      unionType: n.type === 'db' || n.type === 'sql' || n.type === 'dataset' ? n.unionType : undefined,
      unionFields: n.type === 'db' || n.type === 'sql' || n.type === 'dataset' ? (n.unionFields || []) : undefined,
      sqlVariableDetails: n.type === 'sql' ? (n as any).sqlVariableDetails : undefined,
      datasetId: n.type === 'dataset' ? (n as any).datasetId : undefined,
      operationType: (n as any).operationType || undefined,
      operationConfig: (n as any).operationConfig || undefined,
      // 镜像节点：保存源节点关联
      sourceNodeId: n.type === 'mirror' ? (n as any).sourceNodeId : undefined
    })),
    edges: edges.map(e => ({
      id: e.id,
      sourceId: e.sourceId,
      targetId: e.targetId
    }))
  }
}

watch(
  nodes,
  () => {
    if (isUpdate) emits('changeUpdate')
  },
  { deep: true }
)

// ========== Expose for parent ==========
const setStateBack = (node, parent) => {
  const n = nodes.find(x => x.id === node.id)
  const p = nodes.find(x => x.id === parent.id)
  if (n)
    Object.assign(n, {
      currentDsFields: node.currentDsFields,
      unionType: node.unionType,
      unionFields: node.unionFields
    })
  if (p) Object.assign(p, { currentDsFields: parent.currentDsFields })
}
const notConfirm = () => {
  return undefined
}
const dfsNodeFieldBack = (_list, { originName, datasetTableId }) => {
  const n = nodes.find(x => x.id === datasetTableId)
  if (n) n.currentDsFields = n.currentDsFields.filter(f => f.originName !== originName)
}
const setChangeStatus = () => {
  return undefined
}

const buildDeduplicateTitle = (config: Record<string, any> | undefined | null) => {
  const label = operationList.find(op => op.key === 'deduplicate')?.label || '去重'
  const raw = String(config?.deduplicateFields || '').trim()
  const keep = config?.keepStrategy === 'last' ? '保留最后' : ''
  const tokens = raw
    ? raw
        .split(',')
        .map(s => s.trim())
        .filter(Boolean)
    : []
  const fieldsText = tokens.length ? tokens.slice(0, 3).join(',') : '全部字段'
  const more = tokens.length > 3 ? '...' : ''
  const suffix = [fieldsText + more, keep].filter(Boolean).join('，')
  return suffix ? `${label}(${suffix})` : label
}

const setNodeOperationConfig = (nodeId: string, config: Record<string, any>) => {
  const n = nodes.find(x => x.id === nodeId)
  if (!n || n.type !== 'operation') return
  n.operationConfig = { ...(n.operationConfig || {}), ...(config || {}) }
  // 保存配置后，同步刷新操作节点展示名称（用于回显配置摘要）
  if (n.operationType === 'deduplicate') {
    n.tableName = buildDeduplicateTitle(n.operationConfig)
  }
  emits('changeUpdate')
}

/** 找到某节点最近的上游数据节点（db/sql）；若上游是 operation 或 mirror，则继续回溯 */
const getUpstreamDataNode = (nodeId: string) => {
  const visited = new Set<string>()
  const queue: string[] = [nodeId]
  while (queue.length) {
    const current = queue.shift() as string
    if (visited.has(current)) continue
    visited.add(current)
    const incoming = edges.filter(e => e.targetId === current)
    for (const edge of incoming) {
      const src = nodes.find(n => n.id === edge.sourceId)
      if (!src) continue
      if (src.type === 'db' || src.type === 'sql') return cloneDeep(src)
      if (src.type === 'operation') queue.push(src.id)
      // 镜像节点：解析到源节点
      if (src.type === 'mirror') {
        const resolved = getMirrorSourceNode(src.id)
        if (resolved && (resolved.type === 'db' || resolved.type === 'sql')) {
          return cloneDeep(resolved)
        }
      }
    }
  }
  return null
}

/** 获取连到结果集的节点（用于落盘时收集排序等输出配置） */
const getResultInputNode = (): FlowNode | null => {
  const resultNode = nodes.find(n => n.type === 'result')
  if (!resultNode) return null
  const edge = edges.find(e => e.targetId === resultNode.id)
  if (!edge) return null
  let src = nodes.find(n => n.id === edge.sourceId)
  // 解析镜像节点
  if (src?.type === 'mirror') {
    src = getMirrorSourceNode(src.id) || src
  }
  return src ? cloneDeep(src) : null
}

/** 获取某节点的所有直接上游节点（即所有连到该节点的源节点，镜像节点会被解析为源节点） */
const getAllUpstreamNodes = (nodeId: string): FlowNode[] => {
  const upstreamNodes = edges
    .filter(e => e.targetId === nodeId)
    .map(e => nodes.find(n => n.id === e.sourceId))
    .filter(Boolean) as FlowNode[]
  
  // 解析所有镜像节点
  return upstreamNodes.map(n => {
    if (n.type === 'mirror') {
      const resolved = getMirrorSourceNode(n.id)
      return resolved || n
    }
    return n
  })
}

/** 联合节点专用：获取两个直接上游分支（用于校验列数/类型并合并行）。若不足或超过 2 个上游则返回空数组。 */
const getUnionDirectUpstreams = (unionNodeId: string): FlowNode[] => {
  const list = getAllUpstreamNodes(unionNodeId)
  if (list.length !== 2) return []
  return list.map(n => cloneDeep(n))
}

// 最近新增的节点（用于父组件在 addComplete 时获取，自动触发预览）
const getLastAddedNode = (): FlowNode | null => {
  if (!nodes.length) return null
  // 最后一个非结果集节点优先，其次才是结果集
  const nonResult = [...nodes].reverse().find(n => n.type !== 'result')
  return cloneDeep((nonResult || nodes[nodes.length - 1]) as FlowNode)
}

defineExpose({
  nodeNameList,
  getNodeList,
  setStateBack,
  notConfirm,
  dfsNodeFieldBack,
  initState,
  setChangeStatus,
  crossDatasources,
  setNodeOperationConfig,
  getUpstreamDataNode,
  getResultInputNode,
  getAllUpstreamNodes,
  getUnionDirectUpstreams,
  getGraphState,
  getLastAddedNode,
  getMirrorSourceNode,
  openJoinEditorByNodeId
})
</script>

<template>
  <div
    class="flow-canvas"
    ref="canvasRef"
    :style="{ height: dragHeight + 'px' }"
    @mousemove="handleMouseMove"
    @mouseup="handleMouseUp"
    @click="handleCanvasClick"
    @drop="handleDrop"
    @dragover.prevent
    @dragenter.prevent
  >
    <svg
      class="flow-svg"
      :width="canvasWidth"
      :height="canvasHeight"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <pattern id="flowGrid" width="20" height="20" patternUnits="userSpaceOnUse">
          <path d="M 20 0 L 0 0 0 20" fill="none" stroke="rgba(0,0,0,0.04)" stroke-width="0.5" />
        </pattern>
        <marker id="arrow" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
          <polygon points="0 0, 10 3.5, 0 7" fill="#BBBFC4" />
        </marker>
        <marker id="arrowBlue" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
          <polygon points="0 0, 10 3.5, 0 7" fill="#3370FF" />
        </marker>
      </defs>
      <rect width="100%" height="100%" fill="url(#flowGrid)" />

      <!-- Edges -->
      <path
        v-for="edge in edges"
        :key="edge.id"
        :d="getEdgePath(edge)"
        :stroke="selectedEdgeId === edge.id ? '#3370FF' : '#BBBFC4'"
        :stroke-width="selectedEdgeId === edge.id ? 3 : 2"
        fill="none"
        :marker-end="selectedEdgeId === edge.id ? 'url(#arrowBlue)' : 'url(#arrow)'"
        class="flow-edge"
        @click.stop="handleEdgeClick(edge)"
      />

      <!-- Temp edge while connecting -->
      <path
        v-if="connecting.active"
        :d="getTempEdgePath()"
        stroke="#3370FF"
        stroke-width="2"
        stroke-dasharray="6,3"
        fill="none"
        marker-end="url(#arrowBlue)"
      />

      <!-- ===== NODES ===== -->
      <g v-for="node in nodes" :key="node.id">
        <!-- Node background -->
        <rect
          :x="node.x"
          :y="node.y"
          :width="NODE_W"
          :height="NODE_H"
          rx="6"
          :fill="
            node.type === 'result' ? '#F0FBF9' : node.type === 'operation' ? '#F0F7FF' : node.type === 'dataset' ? '#FFF8E1' : '#fff'
          "
          :stroke="selectedNodeId === node.id ? primaryColor : '#DEE0E3'"
          stroke-width="1"
          :cursor="dragging.active && dragging.nodeId === node.id ? 'grabbing' : 'grab'"
          @mousedown.prevent="startDragNode(node, $event)"
          @dblclick="handleNodeDblClick(node, $event)"
        />
        <!-- Left color bar -->
        <rect
          :x="node.x"
          :y="node.y"
          width="4"
          :height="NODE_H"
          :rx="2"
          :fill="getNodeColor(node)"
        />

        <!-- Input port: 数据表节点仅输出不接收，无输入端口；操作节点和结果集可接收 -->
        <circle
          v-if="isInputPortVisible(node)"
          :cx="node.x"
          :cy="node.y + NODE_H / 2"
          :r="PORT_R"
          :fill="connecting.active && connecting.sourceId !== node.id ? '#E8F4FF' : '#fff'"
          :stroke="connecting.active && connecting.sourceId !== node.id ? '#409EFF' : '#BBBFC4'"
          stroke-width="1.5"
          class="port-circle"
          @mouseup.stop="endConnect(node)"
        />
        <!-- Output port circle -->
        <circle
          v-if="node.type !== 'result'"
          :cx="node.x + NODE_W"
          :cy="node.y + NODE_H / 2"
          :r="PORT_R"
          fill="#fff"
          stroke="#BBBFC4"
          stroke-width="1.5"
          class="port-circle port-output"
          @mousedown.stop.prevent="startConnect(node, $event)"
        />

        <!-- Node content via foreignObject -->
        <foreignObject :x="node.x + 10" :y="node.y + 2" :width="NODE_W - 20" :height="NODE_H - 4">
          <div
            class="node-content"
            @mousedown.prevent="startDragNode(node, $event)"
            @dblclick="handleNodeDblClick(node, $event)"
          >
            <el-icon :size="14" style="flex-shrink: 0">
              <Icon :name="getNodeIcon(node)" />
          </el-icon>
            <span class="node-label" :title="node.tableName">{{ node.tableName }}</span>
            <el-tooltip v-if="node.note" :content="node.note" placement="top">
              <template #default>
                <span style="display: inline-flex; align-items: center">
                  <el-icon :size="11" style="margin-left: 2px; color: #909399; flex-shrink: 0">
                    <Icon name="icon_info_outlined" />
                  </el-icon>
                </span>
              </template>
            </el-tooltip>
            <div class="handle-more-trigger" v-if="node.type !== 'result'" @mousedown.stop>
          <handle-more
            iconName="icon_more-vertical_outlined"
                :menuList="getNodeMenuList(node)"
                @handle-command="cmd => handleCommand(node, cmd)"
              />
        </div>
        </div>
      </foreignObject>
      </g>
    </svg>

    <!-- Mask overlay for external drag -->
    <div
      class="flow-mask"
      :class="{ 'flow-mask-active': maskShow && nodes.length <= 1 }"
      v-if="maskShow"
    />

    <!-- Empty state -->
    <div class="flow-empty" v-if="nodes.length <= 1">
      <img :src="zeroNodeImg" alt="" />
      <p>将左侧的数据表、操作节点</p>
      <p>拖拽到画布中创建数据流</p>
    </div>

    <!-- 删除连线按钮 -->
    <div
      v-if="selectedEdgeId && selectedEdgePosition"
      class="edge-delete-btn"
      :style="{ left: selectedEdgePosition.x + 'px', top: (selectedEdgePosition.y - 18) + 'px' }"
      @click.stop="deleteSelectedEdge"
      title="删除连线"
    >
      <el-icon :size="14"><Icon name="icon_delete-trash_outlined" /></el-icon>
    </div>
  </div>

  <!-- ===== Dialogs ===== -->
  <el-dialog v-model="dialogNote" title="添加备注" width="420px" :close-on-click-modal="false">
    <el-input v-model="noteText" type="textarea" :rows="4" placeholder="请输入备注信息" />
    <template #footer>
      <el-button secondary @click="dialogNote = false">取消</el-button>
      <el-button type="primary" @click="confirmNote">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="dialogRename"
    :close-on-press-escape="false"
    :close-on-click-modal="false"
    title="重命名"
    width="420px"
  >
    <el-form
      ref="renameForm"
      require-asterisk-position="right"
      :model="renameParam"
      label-position="top"
    >
      <el-form-item
        prop="name"
        label="名称"
        :rules="[{ required: true, message: t('commons.cannot_be_null') }]"
      >
        <el-input :placeholder="t('common.inputText')" v-model="renameParam.name" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button secondary @click="dialogRename = false">取消</el-button>
      <el-button type="primary" @click="confirmRename">确定</el-button>
    </template>
  </el-dialog>

  <!-- Field selection drawer -->
  <el-drawer
    :before-close="closeEditUnion"
    v-model="editUnion"
    custom-class="union-item-drawer"
    size="600px"
    direction="rtl"
  >
    <template #header v-if="currentNode">
      <div class="info-content">
        <div class="info">
          <span class="label">表名</span
          ><span class="name ellipsis" :title="currentNode.tableName">{{
            currentNode.tableName
          }}</span>
        </div>
        <div class="info">
          <span class="label">表备注</span
          ><span class="name ellipsis" :title="currentNode.noteName" style="max-width: 240px">{{
            currentNode.noteName || '-'
          }}</span>
        </div>
        <span
          class="ds ellipsis"
          :title="getDsName?.(currentNode.datasourceId)"
          style="max-width: 550px"
          >{{ t('auth.datasource') }}:{{ getDsName?.(currentNode.datasourceId) }}</span
        >
      </div>
    </template>
    <union-field-list
      :field-list="nodeField"
      :node="currentNode"
      v-if="nodeField.length"
      @checkedFields="changeNodeFields"
    />
    <template #footer>
      <el-button secondary @click="closeEditUnion">{{ t('dataset.cancel') }}</el-button>
      <el-button type="primary" @click="confirmEditUnion">{{ t('dataset.confirm') }}</el-button>
    </template>
  </el-drawer>

  <!-- SQL editor drawer -->
  <el-drawer
    direction="btt"
    :close-on-click-modal="false"
    size="calc(100% - 100px)"
    :with-header="false"
    :close-on-press-escape="false"
    modal-class="sql-drawer-fullscreen"
    v-model="editSqlField"
  >
    <add-sql @save="saveSqlNode" @close="closeSqlNode" :sqlNode="sqlNode" />
  </el-drawer>
</template>

<style lang="less">
.flow-canvas {
  background: #f5f6f7;
  overflow: auto;
  position: relative;
  width: 100%;
  border: none !important;
  flex-shrink: 0;

  .flow-svg {
    display: block;
    min-width: 100%;
    min-height: 100%;
  }

  .flow-edge {
    cursor: pointer;
    &:hover {
      stroke: #3370ff;
      stroke-width: 2.5;
    }
  }

  .port-circle {
    cursor: crosshair;
    transition: all 0.15s;
    &:hover {
      r: 8;
      stroke: #3370ff;
      fill: #e8f4ff;
    }
  }
  .port-output:hover {
    fill: #e8f4ff;
    stroke: #3370ff;
  }

  .node-content {
    display: flex;
    align-items: center;
  height: 100%;
  font-family: '阿里巴巴普惠体 3.0 55 Regular L3';
    font-size: 13px;
  color: #1f2329;
    cursor: grab;
    gap: 5px;
    user-select: none;

    .node-label {
      flex: 1;
      min-width: 0;
    overflow: hidden;
      text-overflow: ellipsis;
    white-space: nowrap;
  }

    .handle-more-trigger {
      flex-shrink: 0;
      margin-left: auto;
    }
  }

  .flow-mask {
    position: absolute;
    left: 0;
    top: 0;
  width: 100%;
  height: 100%;
  z-index: 5;
  user-select: none;
    pointer-events: none;
}
  .flow-mask-active {
  background-color: #e5ebf8;
    border: 1px dashed var(--ed-color-primary);
    pointer-events: auto;
}

  .flow-empty {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    z-index: 6;
    user-select: none;
    display: flex;
    align-items: center;
    flex-direction: column;
    padding-top: 42px;
    pointer-events: none;

    img {
      width: 125px;
      height: 125px;
      margin-bottom: 8px;
      -webkit-user-drag: none;
    }
    p {
      font-family: '阿里巴巴普惠体 3.0 55 Regular L3';
      font-size: 14px;
      line-height: 22px;
      text-align: center;
      color: #646a73;
      margin: 0;
    }
  }

  .edge-delete-btn {
    position: absolute;
    z-index: 10;
    width: 28px;
    height: 28px;
    background: #fff;
    border: 1px solid #DEE0E3;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transform: translate(-50%, -50%);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    transition: all 0.15s;

    &:hover {
      background: #fef0f0;
      border-color: #f56c6c;
      color: #f56c6c;
    }
  }
}

.sql-drawer-fullscreen {
  .ed-drawer.btt > .ed-drawer__body {
    padding: 0;
  }
}

.union-item-drawer {
  .ed-drawer__header {
    height: 82px;
    font-family: '阿里巴巴普惠体 3.0 55 Regular L3';
    .ed-drawer__close-btn {
      top: 26px;
    }
    .info-content {
      display: flex;
      flex-wrap: wrap;
    }
    .info {
      display: flex;
      flex-direction: column;
      width: 50%;
      .label {
        font-weight: 500;
        font-size: 16px;
        color: #1f2329;
        max-width: 500px;
      }
      .name {
        font-weight: 400;
        font-size: 14px;
      }
      .ds {
        font-weight: 400;
        font-size: 14px;
        max-width: 500px;
        color: #646a73;
      }
    }
  }
  .field-block-body {
    height: calc(100% - 70px) !important;
  }
}
</style>
