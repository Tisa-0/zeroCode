<script lang="tsx" setup>
import {
  ref,
  toRaw,
  unref,
  nextTick,
  reactive,
  shallowRef,
  computed,
  watch,
  provide,
  onMounted,
  onBeforeUnmount
} from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { useEmitt } from '@/hooks/web/useEmitt'
import { ElIcon, ElMessageBox, ElMessage } from 'element-plus-secondary'
import FixedSizeList from 'element-plus-secondary/es/components/virtual-list/src/components/fixed-size-list.mjs'
import type { Action } from 'element-plus-secondary'
import FieldMore from './FieldMore.vue'
import EmptyBackground from '@/components/empty-background/src/EmptyBackground.vue'
import { Icon } from '@/components/icon-custom'
import { useWindowSize } from '@vueuse/core'
import CalcFieldEdit from './CalcFieldEdit.vue'
import GroupFieldEdit from './GroupFieldEdit.vue'
import FillNullFieldEdit from './FillNullFieldEdit.vue'
import { useRoute, useRouter } from 'vue-router'
import UnionEdit from './UnionEdit.vue'
import type { FormInstance } from 'element-plus-secondary'
import type { BusiTreeNode } from '@/models/tree/TreeNode'
import CreatDsGroup from './CreatDsGroup.vue'
import {
  guid,
  getFieldName,
  timeTypes,
  type DataSource,
  normalizeField,
  fieldNameShort,
  operationList
} from './util'
import { fieldType } from '@/utils/attr'
import { cancelMap } from '@/config/axios/service'
import { useEmbedded } from '@/store/modules/embedded'
import { useAppStoreWithOut } from '@/store/modules/app'
import {
  getDatasourceList,
  getTables,
  getPreviewData,
  getDatasetPreview,
  getDatasetDetails,
  saveDatasetTree,
  barInfoApi,
  getTableField,
  multFieldValuesForPermissions,
  getDatasetTree,
  copilotChat
} from '@/api/dataset'
import type { Table } from '@/api/dataset'
import DatasetUnion from './DatasetUnion.vue'
import OperationToolbar from './OperationToolbar.vue'
import NodeConfigDrawer from './NodeConfigDrawer.vue'
import { cloneDeep, debounce } from 'lodash-es'
import { XpackComponent } from '@/components/plugin'
import treeSort from '@/utils/treeSortUtils'
import { useCache } from '@/hooks/web/useCache'
import { interactiveStoreWithOut } from '@/store/modules/interactive'
const interactiveStore = interactiveStoreWithOut()
interface DragEvent extends MouseEvent {
  dataTransfer: DataTransfer
}
interface Field {
  id?: string | number
  fieldShortName: string
  name: string
  dataeaseName: string
  originName: string
  deType: number
}
interface WorkspaceTabItem {
  key: string
  datasetId: string
  title: string
  snapshot?: WorkspaceSnapshot
  loaded?: boolean
}
interface WorkspaceSnapshot {
  nodeInfo: {
    id: string
    pid: string
    name: string
  }
  datasetName: string
  allfields: any[]
  nodeList: any[]
  graphState: Record<string, any> | null
  sortFields: Array<Record<string, any>>
  dataSource: string
}
const appStore = useAppStoreWithOut()
const embeddedStore = useEmbedded()
const { wsCache } = useCache()
const { t } = useI18n()
const route = useRoute()
const { push } = useRouter()
const workspaceActiveTab = ref('current')
const workspaceTabs = ref<WorkspaceTabItem[]>([])
const workspaceRootTitle = ref((route.query.title as string) || '未命名数据集')
const workspaceRootSnapshot = ref<WorkspaceSnapshot | null>(null)
const quotaTableHeight = ref(238)
const creatDsFolder = shallowRef()
const editCalcField = ref(false)
const editGroupField = ref(false)
const calcEdit = shallowRef()
const editUnion = ref(false)
const datasetDrag = shallowRef()
const datasetName = ref('未命名数据集')
const tabActive = ref('preview')
const activeName = ref('')
const dataSource = ref('')
const searchTable = ref('')
const showInput = ref(false)
const dsLoading = ref(false)
const LeftWidth = ref(240)
const offsetX = ref(0)
const offsetY = ref(0)
const showLeft = ref(true)
const maskShow = ref(false)
const loading = ref(false)
const showAiPanel = ref(false)
const aiInputText = ref('')
const aiMessages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const aiLoading = ref(false)
const aiPanelBody = shallowRef<HTMLElement>()
const updateCustomTime = ref(false)
const editerName = shallowRef()

const scrollAiPanelToBottom = () => {
  nextTick(() => {
    const el = aiPanelBody.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

watch(
  () => [aiMessages.value.length, aiLoading.value],
  () => {
    scrollAiPanelToBottom()
  }
)
const currentField = ref({
  dateFormat: '',
  id: '',
  dateFormatType: '',
  name: '',
  idArr: []
})
let isUpdate = false

const fieldTypes = index => {
  return [
    t('dataset.text'),
    t('dataset.time'),
    t('dataset.value'),
    t('dataset.value') + '(' + t('dataset.float') + ')',
    t('dataset.value'),
    t('dataset.location')
  ][index]
}

const changeUpdate = () => {
  isUpdate = true
}

watch(
  datasetName,
  val => {
    const title = val?.trim() || (route.query.title as string) || 'DataEase'
    appStore.setTitle(title)
    if (workspaceActiveTab.value === 'current') {
      workspaceRootTitle.value = title
    } else {
      const tab = workspaceTabs.value.find(item => item.key === workspaceActiveTab.value)
      if (tab) tab.title = title
    }
  },
  { immediate: true }
)

const currentWorkspaceTitle = computed(() => workspaceRootTitle.value || '未命名数据集')

const getCurrentWorkspaceDatasetId = () => String(nodeInfo.id || route.query.id || '')
const getWorkspaceRootDatasetId = () => String(workspaceRootSnapshot.value?.nodeInfo?.id || route.query.id || '')

const buildWorkspaceSnapshotFromDataset = (res: any): WorkspaceSnapshot => {
  const arr: any[] = []
  const { id, pid, name } = res || {}
  const nextNodeInfo = {
    id: String(id || ''),
    pid: String(pid || ''),
    name: name || '未命名数据集'
  }
  dfsUnion(arr, res?.union || [])
  const [fir] = (res?.union || []) as { currentDs: { datasourceId: string } }[]
  return {
    nodeInfo: nextNodeInfo,
    datasetName: nextNodeInfo.name,
    allfields: cloneDeep(res?.allFields || []),
    nodeList: cloneDeep(arr),
    graphState: cloneDeep((res as any)?.graphState || null),
    sortFields: cloneDeep((res as any)?.sortFields || []),
    dataSource: fir?.currentDs?.datasourceId || ''
  }
}

const captureCurrentWorkspaceSnapshot = (): WorkspaceSnapshot => {
  const resultConfig = getResultOutputConfig()
  return {
    nodeInfo: cloneDeep(nodeInfo),
    datasetName: datasetName.value,
    allfields: cloneDeep(unref(allfields.value)),
    nodeList: cloneDeep(toRaw(datasetDrag.value?.getNodeList?.() || [])),
    graphState: cloneDeep(datasetDrag.value?.getGraphState?.() || null),
    sortFields: cloneDeep((resultConfig as any)?.sortFields || []),
    dataSource: dataSource.value
  }
}

const applyWorkspaceSnapshot = async (snapshot: WorkspaceSnapshot) => {
  loading.value = true
  try {
    nodeInfo = cloneDeep(snapshot.nodeInfo)
    datasetName.value = snapshot.datasetName || '未命名数据集'
    allfields.value = cloneDeep(snapshot.allfields || [])
    dataSource.value = snapshot.dataSource || ''
    if (dataSource.value) {
      await dsChange(dataSource.value)
    }
    await nextTick()
    datasetDrag.value?.initState(cloneDeep(snapshot.nodeList || []), {
      sortFields: cloneDeep(snapshot.sortFields || []),
      graphState: cloneDeep(snapshot.graphState || null)
    })
    await nextTick()
    handleSelectPreviewNode({ id: 'result_output', type: 'result' })
  } finally {
    loading.value = false
  }
}

const saveActiveWorkspaceSnapshot = () => {
  const snapshot = captureCurrentWorkspaceSnapshot()
  if (workspaceActiveTab.value === 'current') {
    workspaceRootSnapshot.value = snapshot
    workspaceRootTitle.value = snapshot.datasetName || workspaceRootTitle.value
    return
  }
  const tab = workspaceTabs.value.find(item => item.key === workspaceActiveTab.value)
  if (tab) {
    tab.snapshot = snapshot
    tab.loaded = true
    tab.title = snapshot.datasetName || tab.title
  }
}

const syncSavedDatasetMeta = (res?: any) => {
  if (!res || typeof res !== 'object') return
  const { id, pid, name, allFields } = res as Record<string, any>
  if (id !== undefined && id !== null) nodeInfo.id = String(id)
  if (pid !== undefined && pid !== null) nodeInfo.pid = String(pid)
  if (typeof name === 'string' && name.trim()) {
    nodeInfo.name = name
    datasetName.value = name
  }
  if (Array.isArray(allFields)) {
    allfields.value = allFields
  }
}

const buildSavedDatasetTreeNode = (res?: any) => {
  if (!res || typeof res !== 'object') return null
  const { id, pid, name, weight, extraFlag, createTime } = res as Record<string, any>
  if (id === undefined || id === null) return null
  return {
    id: String(id),
    pid: String(pid ?? '0'),
    name: name || datasetName.value || '未命名数据集',
    leaf: true,
    nodeType: 'dataset',
    weight: weight ?? 7,
    extraFlag: extraFlag ?? 0,
    type: 'dataset',
    createTime: createTime ?? Date.now(),
    children: undefined
  }
}

const upsertDatasetTreeNode = (nodes: any[], node: any) => {
  for (const item of nodes || []) {
    if (String(item.id) === String(node.id)) {
      item.name = node.name
      item.pid = node.pid
      item.leaf = true
      item.nodeType = 'dataset'
      item.type = 'dataset'
      item.createTime = node.createTime
      return true
    }
    if (item.children?.length && upsertDatasetTreeNode(item.children, node)) {
      return true
    }
  }
  return false
}

const appendDatasetTreeNode = (nodes: any[], pid: string, node: any) => {
  for (const item of nodes || []) {
    if (String(item.id) === String(pid)) {
      item.children = item.children || []
      const exists = item.children.some(child => String(child.id) === String(node.id))
      if (!exists) item.children.push(node)
      return true
    }
    if (item.children?.length && appendDatasetTreeNode(item.children, pid, node)) {
      return true
    }
  }
  return false
}

const syncDatasetPanelNode = (res?: any) => {
  const savedNode = buildSavedDatasetTreeNode(res)
  if (!savedNode) return
  const nextTree = cloneDeep(originDatasetListForPanel.value || [])
  if (upsertDatasetTreeNode(nextTree, savedNode)) {
    originDatasetListForPanel.value = nextTree
    applyDatasetPanelSort()
    nextTick(() => panelDatasetTreeRef.value?.filter(panelSearchKeyword.value))
    return
  }
  const inserted = appendDatasetTreeNode(nextTree, savedNode.pid, savedNode)
  if (inserted) {
    originDatasetListForPanel.value = nextTree
    applyDatasetPanelSort()
    nextTick(() => panelDatasetTreeRef.value?.filter(panelSearchKeyword.value))
  }
}

const refreshDatasetPanel = (res?: any) => {
  syncDatasetPanelNode(res)
  setTimeout(() => {
    getDatasetList()
  }, 300)
}

const buildDatasourceTableNodes = (datasourceId: string, tables: any[] = []) => {
  return tables.map((t: any) => ({
    ...t,
    id: `${datasourceId}_${t.tableName || t.name}`,
    name: t.tableName || t.name,
    tableName: t.tableName || t.name,
    datasourceId,
    leaf: true,
    children: undefined
  }))
}

const loadDatasourceTables = async (data: any, node?: any) => {
  if (!data || data.leaf || data._loadingTables) return
  if (data._tablesLoaded && Array.isArray(data.children)) {
    await nextTick()
    node?.expand?.()
    return
  }
  data._loadingTables = true
  try {
    const tables = await getTables({ datasourceId: data.id })
    data.children = Array.isArray(tables) ? buildDatasourceTableNodes(String(data.id), tables) : []
    data._tablesLoaded = true
    await nextTick()
    node?.expand?.()
  } catch (e) {
    console.error('鍔犺浇鏁版嵁婧愯〃澶辫触', e)
    data.children = []
    data._tablesLoaded = false
  } finally {
    data._loadingTables = false
  }
}

const preloadDatasourceTables = () => {
  const sources = (state.dataSourceList || []).filter((ds: any) => !ds?.leaf)
  if (!sources.length) return
  void Promise.allSettled(sources.map(ds => loadDatasourceTables(ds)))
}

const fetchWorkspaceSnapshotById = async (datasetId: string): Promise<WorkspaceSnapshot | null> => {
  const barRes = await barInfoApi(datasetId)
  if (!barRes || !barRes['id']) return null
  const res = await getDatasetDetails(datasetId)
  return buildWorkspaceSnapshotFromDataset(res)
}

const activateWorkspaceTab = async (targetKey: string) => {
  if (targetKey === workspaceActiveTab.value) return
  saveActiveWorkspaceSnapshot()
  if (targetKey === 'current') {
    workspaceActiveTab.value = 'current'
    if (workspaceRootSnapshot.value) {
      await applyWorkspaceSnapshot(workspaceRootSnapshot.value)
    }
    return
  }
  const targetTab = workspaceTabs.value.find(item => item.key === targetKey)
  if (!targetTab) return
  workspaceActiveTab.value = targetKey
  if (!targetTab.snapshot) {
    const snapshot = await fetchWorkspaceSnapshotById(targetTab.datasetId)
    if (!snapshot) {
      workspaceTabs.value = workspaceTabs.value.filter(item => item.key !== targetKey)
      workspaceActiveTab.value = 'current'
      return
    }
    targetTab.snapshot = snapshot
    targetTab.loaded = true
    targetTab.title = snapshot.datasetName || targetTab.title
  }
  await applyWorkspaceSnapshot(targetTab.snapshot)
}

const openWorkspaceDatasetTab = async (datasetId: string, title: string) => {
  const normalizedId = String(datasetId || '')
  if (!normalizedId) return
  if (normalizedId === getCurrentWorkspaceDatasetId() || normalizedId === getWorkspaceRootDatasetId()) {
    await activateWorkspaceTab('current')
    return
  }
  const key = `dataset-${normalizedId}`
  const existed = workspaceTabs.value.find(tab => tab.key === key)
  if (!existed) {
    workspaceTabs.value.push({
      key,
      datasetId: normalizedId,
      title: title || '未命名数据集'
    })
  }
  await activateWorkspaceTab(key)
}

const closeWorkspaceDatasetTab = (targetName: string) => {
  const idx = workspaceTabs.value.findIndex(tab => tab.key === targetName)
  if (idx < 0) return
  workspaceTabs.value.splice(idx, 1)
  if (workspaceActiveTab.value === targetName) {
    const fallback = workspaceTabs.value[idx - 1] || workspaceTabs.value[idx] || null
    activateWorkspaceTab(fallback?.key || 'current')
  }
}

const fieldOptions = [
  { label: t('dataset.text'), value: 0 },
  {
    label: t('dataset.time'),
    value: 1,
    children: [
      {
        value: 'yyyy-MM-dd',
        label: 'yyyy-MM-dd'
      },
      {
        value: 'yyyy/MM/dd',
        label: 'yyyy/MM/dd'
      },
      {
        value: 'yyyy-MM-dd HH:mm:ss',
        label: 'yyyy-MM-dd HH:mm:ss'
      },
      {
        value: 'yyyy/MM/dd HH:mm:ss',
        label: 'yyyy/MM/dd HH:mm:ss'
      },
      {
        value: 'custom',
        label: t('visualization.custom')
      }
    ]
  },
  { label: t('dataset.location'), value: 5 },
  { label: t('dataset.value'), value: 2 },
  {
    label: t('dataset.value') + '(' + t('dataset.float') + ')',
    value: 3
  }
]

const fieldOptionsText = [
  { label: t('dataset.text'), value: 0 },
  {
    label: t('dataset.time'),
    value: 1
  },
  { label: t('dataset.location'), value: 5 },
  { label: t('dataset.value'), value: 2 },
  {
    label: t('dataset.value') + '(' + t('dataset.float') + ')',
    value: 3
  }
]

const ruleFormRef = shallowRef<FormInstance>()
const ruleFormFieldRef = shallowRef<FormInstance>()

const rules = {
  name: [{ required: true, message: '自定义时间格式不能为空', trigger: 'blur' }]
}

const fieldRules = {
  name: [{ required: true, message: t('dataset.input_edit_name'), trigger: 'blur' }]
}

const sqlNode = reactive<Table>({
  datasourceId: '',
  name: '',
  tableName: '鑷畾涔塖QL',
  type: 'sql'
})

let nodeInfo = {
  id: '',
  pid: '',
  name: ''
}

const defaultProps = {
  children: 'children',
  label: 'label'
}
const dragHeight = ref(260)

let tableList = []

const dfsName = (arr, id) => {
  let name = ''
  arr.some(ele => {
    if (ele.id === id) {
      name = ele.name
      return true
    }

    if (!!ele.children?.length) {
      name = dfsName(ele.children, id) || name
    }
    return false
  })

  return name
}

const { height } = useWindowSize()

const dfsChild = arr => {
  return arr.filter(ele => {
    if (ele.leaf) {
      return true
    }
    if (!!ele.children?.length) {
      ele.children = dfsChild(ele.children || [])
    }
    return !!ele.children?.length
  })
}

const getDsName = (id: string) => {
  return dfsName(state.dataSourceList, id)
}

const pushDataset = () => {
  if (appStore.isDataEaseBi) {
    embeddedStore.clearState()
    useEmitt().emitter.emit('changeCurrentComponent', 'Dataset')
    return
  }
  push({
    name: 'dataset',
    params: {
      id: nodeInfo.id
    }
  })
}

const backToMain = () => {
  pushDataset()
}

const closeCustomTime = () => {
  if (!!currentField.value.idArr.length) {
    const { idArr } = currentField.value
    allfields.value.forEach(ele => {
      if (idArr.includes(ele.id)) {
        Object.assign(ele, { deTypeArr: [...oldArrValue] })
      }
    })
    delete currentField.value.name
    recoverSelection()
  } else {
    dimensions.value.concat(quota.value).some(ele => {
      if (ele.id === currentField.value.id) {
        delete currentField.value.name
        Object.assign(ele, { deTypeArr: [...oldArrValue] })
        return true
      }
      return false
    })
  }
  currentField.value.idArr = []
  currentField.value.id = ''
  updateCustomTime.value = false
}

const confirmCustomTime = () => {
  if (!!currentField.value.idArr.length) {
    const { name, idArr } = currentField.value
    allfields.value.forEach(ele => {
      if (idArr.includes(ele.id)) {
        Object.assign(ele, {
          deType: 1,
          dateFormatType: 'custom',
          dateFormat: name,
          deTypeArr: [1, 'custom']
        })
      }
    })
    delete currentField.value.name
    recoverSelection()
    updateCustomTime.value = false
  } else {
    ruleFormRef.value.validate(valid => {
      if (valid) {
        dimensions.value.concat(quota.value).some(ele => {
          if (ele.id === currentField.value.id) {
            ele.dateFormat = currentField.value.name
            ele.deType = 1
            ele.dateFormatType = 'custom'
            return true
          }
          return false
        })
        updateCustomTime.value = false
      }
    })
  }
}

watch(searchTable, val => {
  datasourceTableData.value = tableList.filter(ele =>
    ele.tableName.toLowerCase().includes(val.toLowerCase())
  )
})
const editeSave = () => {
  const union = []
  loading.value = true
  dfsNodeList(union, datasetDrag.value.getNodeList())
  const tableIds = collectUnionTableIds(union)
  const allFieldsToSave =
    tableIds.size > 0
      ? allfields.value.filter(f => tableIds.has(String((f as any).datasetTableId)))
      : allfields.value
  const resultConfig = getResultOutputConfig()
  saveDatasetTree({
    ...nodeInfo,
    name: datasetName.value,
    union,
    allFields: allFieldsToSave,
    nodeType: 'dataset',
    graphState: datasetDrag.value?.getGraphState?.(),
    ...resultConfig
  })
    .then(res => {
      syncSavedDatasetMeta(res)
      isUpdate = false
      ElMessage.success('淇濆瓨鎴愬姛')
      refreshDatasetPanel(res)
      // 淇濆瓨鎴愬姛鍚庯紝鍒锋柊棰勮鏁版嵁涓鸿仈鎺ョ粨鏋?
      setTimeout(() => {
        handleSelectPreviewNode({ id: 'result_output', type: 'result' })
      }, 500)
      if (willBack) {
        pushDataset()
      }
    })
    .finally(() => {
      loading.value = false
    })
}

const handleFieldMore = (ele, type) => {
  changeUpdate()
  if (tabActive.value === 'manage') {
    dimensionsSelection.value = dimensionsTable.value.getSelectionRows().map(ele => ele.id)
    quotaSelection.value = quotaTable.value.getSelectionRows().map(ele => ele.id)
  }
  const arr = ['text', 'time', 'value', 'float', 'value', 'location']
  if (arr.includes(type as string)) {
    ele.deType = arr.indexOf(type)
    ele.dateFormat = ''
    return
  }
  if (timeTypes.includes(type as string)) {
    currentField.value.dateFormat = ele.dateFormat
    currentField.value.dateFormatType = ele.dateFormatType

    ele.deType = 1
    ele.dateFormatType = type
    ele.dateFormat = type
  }
  switch (type) {
    case 'copy':
      copyField(ele)
      break
    case 'delete':
      deleteField(ele)
      break
    case 'translate':
      dqTrans(ele.id)
      break
    case 'editor':
      editField(ele)
      break
    case 'rename':
      renameField(ele)
      break
    case 'custom':
      currentField.value.id = ele.id
      updateCustomTime.value = true
      break
    default:
      break
  }

  if (tabActive.value === 'manage') {
    recoverSelection()
  }
}

const dqTrans = id => {
  const obj = allfields.value.find(ele => ele.id === id)
  obj.groupType = obj.groupType === 'd' ? 'q' : 'd'
}

const dqTransArr = groupType => {
  const idArr = fieldSelection.value.map(ele => ele.id)
  allfields.value.forEach(ele => {
    if (idArr.includes(ele.id)) {
      ele.groupType = groupType
    }
  })
  recoverSelection()
}

const copyField = item => {
  const param = cloneDeep(item)
  param.id = guid()
  param.extField = 2
  param.originName = item.extField === 2 ? item.originName : '[' + item.id + ']'
  param.name = getFieldName(dimensions.value.concat(quota.value), item.name)
  param.dataeaseName = null
  param.lastSyncTime = null
  const index = allfields.value.findIndex(ele => ele.id === item.id)
  allfields.value.splice(index + 1, 0, param)
}

const delFieldById = arr => {
  const delId = [...arr]
  while (delId.length) {
    const [targetId] = delId
    delId.shift()
    allfields.value = allfields.value.filter(ele => ele.id !== targetId)
    const allfieldsId = allfields.value.map(ele => ele.id)
    allfields.value = allfields.value.filter(ele => {
      if (ele.extField !== 2) return true
      const idMap = ele.originName.match(/\[(.+?)\]/g)
      if (!idMap) return true
      const result = idMap.every(itm => {
        const id = itm.slice(1, -1)
        return allfieldsId.includes(id)
      })
      if (result) return true
      delId.push(ele.id)
      return false
    })
  }
}

const delFieldByIdFake = (arr, fakeAllfields) => {
  const delId = [...arr]
  let idList = []
  while (delId.length) {
    const [targetId] = delId
    delId.shift()
    fakeAllfields = fakeAllfields.filter(ele => ele.id !== targetId)
    const allfieldsId = fakeAllfields.map(ele => ele.id)
    fakeAllfields = fakeAllfields.filter(ele => {
      if (ele.extField !== 2) return true
      const idMap = ele.originName.match(/\[(.+?)\]/g)
      if (!idMap) return true
      const result = idMap.every(itm => {
        const id = itm.slice(1, -1)
        return allfieldsId.includes(id)
      })
      if (result) return true
      delId.push(ele.id)
      idList.push(ele.id)
      return false
    })
  }

  return idList
}

const deleteField = item => {
  ElMessageBox.confirm(t('dataset.confirm_delete'), {
    confirmButtonText: t('dataset.confirm'),
    cancelButtonText: t('common.cancel'),
    showCancelButton: true,
    confirmButtonType: 'danger',
    type: 'warning',
    autofocus: false,
    showClose: false,
    callback: (action: Action) => {
      if (action === 'confirm') {
        delFieldById([item.id])
        datasetDrag.value.dfsNodeFieldBack(datasetDrag.value.getNodeList(), item)
        ElMessage({
          message: t('chart.delete_success'),
          type: 'success'
        })
      }
    }
  })
}

const addCalcField = groupType => {
  editCalcField.value = true
  calcTitle.value = t('dataset.add_calc_field')
  nextTick(() => {
    calcEdit.value.initEdit({ groupType, id: guid() }, dimensions.value, quota.value)
  })
}

/** 鏀寔鍊煎垎缁勭殑缁村害绫诲瀷锛氬竷灏斻€佸瓧绗︿覆銆佸瓧绗︺€佹椂闂存埑銆佹棩鏈熸垨鏃堕棿 鈫?deType 0(鏂囨湰)銆?(鏃堕棿)銆?(鍦扮悊浣嶇疆) */
const GROUPABLE_DIMENSION_TYPES = [0, 1, 5]

const getDistinctValuesForField = (field): string[] => {
  console.log('>>> getDistinctValuesForField - field:', JSON.stringify(field))
  if (!Array.isArray(tableData.value) || !tableData.value.length) {
    console.log('>>> getDistinctValuesForField - no tableData')
    return []
  }
  
  const firstRow = tableData.value[0]
  const firstRowKeys = Object.keys(firstRow)
  console.log('>>> getDistinctValuesForField - first row keys:', firstRowKeys)
  
  // 灏濊瘯澶氫釜鍙兘鐨?key
  const possibleKeys = [field?.dataeaseName, field?.originName, String(field?.id)]
  console.log('>>> getDistinctValuesForField - trying keys:', possibleKeys)
  
  for (const key of possibleKeys) {
    if (key && firstRowKeys.includes(key)) {
      console.log('>>> getDistinctValuesForField - found matching key:', key)
      const set = new Set<string>()
      tableData.value.forEach(row => {
        const v = row[key]
        if (v != null && v !== '') set.add(String(v))
      })
      console.log('>>> getDistinctValuesForField - found values count:', set.size)
      return Array.from(set)
    }
  }
  
  console.log('>>> getDistinctValuesForField - no matching key found')
  return []
}

/** 浠?API 鑾峰彇瀛楁鐨勫幓閲嶅€煎垪琛?*/
const fetchFieldValuesFromApi = async (field): Promise<string[]> => {
  try {
    // 妫€鏌ュ瓧娈电殑鎵€鏈夊睘鎬?
    console.log('>>> fetchFieldValuesFromApi - field keys:', Object.keys(field))
    console.log('>>> fetchFieldValuesFromApi - field:', JSON.stringify(field))
    
    // 灏濊瘯澶氫釜鍙兘鐨?ID 瀛楁
    // 鍚庣鏀逛负鎺ュ彈 String 绫诲瀷锛岄伩鍏?JavaScript 澶ф暟瀛楃簿搴︿涪澶?
    const fieldId = field.fieldId || field.id || field.dataeaseFieldId
    
    console.log('>>> fetchFieldValuesForPermissions - fieldId:', fieldId, 'type:', typeof fieldId)
    
    // 鍙戦€佸瓧绗︿覆鏍煎紡缁欏悗绔紝閬垮厤澶ф暟瀛楃簿搴︿涪澶?
    const requestData = { fieldIds: [String(fieldId)] }
    console.log('>>> 鍙戦€佺粰API鐨勬暟鎹?', JSON.stringify(requestData))
    
    // 灏濊瘯 multFieldValuesForPermissions
    const res1 = await multFieldValuesForPermissions(requestData)
    console.log('>>> fetchFieldValuesFromApi - response status:', res1?.code, 'data:', res1?.data, 'msg:', res1?.msg)
    
    // 妫€鏌ュ绉嶅彲鑳界殑鍝嶅簲缁撴瀯
    if (res1?.data && Array.isArray(res1.data) && res1.data.length > 0) {
      return res1.data.filter((v: any) => v != null && v !== '')
    }
    // 鐩存帴杩斿洖鏁扮粍鐨勬儏鍐?
    if (Array.isArray(res1) && res1.length > 0) {
      return res1.filter((v: any) => v != null && v !== '')
    }
    
    // 濡傛灉鍝嶅簲鏈夐敊璇秷鎭?
    if (res1?.msg) {
      console.warn('>>> API 杩斿洖娑堟伅:', res1.msg)
      ElMessage.warning(res1.msg)
    }
    
    return []
  } catch (e: any) {
    console.error('>>> 鑾峰彇瀛楁鍊煎け璐?- error:', e)
    // e 鏄瓧绗︿覆锛堝悗绔繑鍥炵殑閿欒娑堟伅锛?
    ElMessage.warning(e?.toString() || '获取字段值失败')
    return []
  }
}

/** 浠庢壒閲忕鐞嗘垨鏁版嵁棰勮鎸囧畾瀛楁鎵撳紑銆屾柊寤哄垎缁勩€嶅脊绐楋紙鍊煎垎缁勬ā寮忥級 */
const addGroupFieldWithField = async field => {
  if (!field || field.groupType !== 'd') return
  if (!GROUPABLE_DIMENSION_TYPES.includes(field.deType)) {
    ElMessage.warning('浠呮敮鎸佸鏂囨湰銆佹椂闂淬€佸湴鐞嗕綅缃被鍨嬬殑缁村害瀛楁鏂板缓鍒嗙粍')
    return
  }

  console.log('>>> addGroupFieldWithField - field:', JSON.stringify(field))
  console.log('>>> addGroupFieldWithField - tableData length:', tableData.value?.length)
  if (tableData.value?.length) {
    console.log('>>> addGroupFieldWithField - tableData first row keys:', Object.keys(tableData.value[0]))
  }
  
  // 鍏堜粠棰勮鏁版嵁鑾峰彇鍊硷紝濡傛灉娌℃湁鍒欒皟鐢?API
  let valueList = getDistinctValuesForField(field)
  console.log('>>> addGroupFieldWithField - from tableData:', valueList)
  
  if (!valueList || !valueList.length) {
    console.log('>>> addGroupFieldWithField - no data from tableData, calling API...')
    valueList = await fetchFieldValuesFromApi(field)
    console.log('>>> addGroupFieldWithField - from API:', valueList)
  }

  if (!valueList || !valueList.length) {
    ElMessage.warning('No available field values')
    return
  }

  editGroupField.value = true
  groupTitle.value = t('dataset.new_grouping')
  nextTick(() => {
    groupEdit.value.initEdit(
      { groupType: 'd', id: guid(), sourceField: field },
      allfields.value,
      valueList
    )
  })
}

/** 鏍规嵁棰勮鍒楁壘鍒板搴斿瓧娈碉紝鍒ゆ柇鏄惁鍙璇ュ垪浣跨敤銆屾柊寤哄垎缁勩€?*/
const getFieldForPreviewColumn = column => {
  if (!column?.dataKey) return null
  return allfields.value.find(
    f => (f.dataeaseName || f.originName || f.id) === column.dataKey
  ) || null
}

const canOpenGroupForColumn = column => {
  const field = getFieldForPreviewColumn(column)
  return !!(
    field &&
    field.groupType === 'd' &&
    GROUPABLE_DIMENSION_TYPES.includes(field.deType)
  )
}

const onPreviewColumnHeaderClick = column => {
  console.log('>>> onPreviewColumnHeaderClick - column:', JSON.stringify(column))
  if (!canOpenGroupForColumn(column)) {
    console.log('>>> onPreviewColumnHeaderClick - cannot open group for column')
    return
  }
  const field = getFieldForPreviewColumn(column)
  console.log('>>> onPreviewColumnHeaderClick - field:', field)
  if (field) addGroupFieldWithField(field)
}

/** 鐐瑰嚮宸ュ叿鏍忋€屾柊寤哄垎缁勫瓧娈点€嶏細蹇呴』鍏堥€変腑涓€涓彲鍒嗙粍缁村害瀛楁锛屽脊绐楀唴瀹逛笌鍙傝€冨浘涓€鑷达紙鍘熷瀛楁銆佸悕绉扮粦瀹氥€佸瓧娈靛€煎湪杈撳叆鎼滅储鏂囧瓧涓嬫柟锛?*/
const openGroupFieldDialog = () => {
  console.log('>>> openGroupFieldDialog called')
  const field = selectedGroupableFieldForButton.value
  console.log('>>> openGroupFieldDialog - field from computed:', field)
  if (!field) {
    ElMessage.warning('Please select a dimension field first')
    return
  }
  addGroupFieldWithField(field)
}

/** 澶勭悊棰勮琛ㄥご涓嬫媺鑿滃崟鍛戒护 */
const onPreviewColumnCommand = (column, command) => {
  console.log('>>> onPreviewColumnCommand - column:', JSON.stringify(column), 'command:', command)
  const field = getFieldForPreviewColumn(column)
  if (!field) return
  
  if (command === 'group') {
    onPreviewColumnHeaderClick(column)
  } else if (command === 'fill_null') {
    addFillNullFieldWithField(field)
  }
}

const editNormalField = ref(false)
const currentNormalField = ref({
  id: '',
  name: ''
})

const renameField = item => {
  const { id, name } = item
  currentNormalField.value = {
    id,
    name
  }
  editNormalField.value = true
}

const calcTitle = ref('')
const groupTitle = ref('')
const groupEdit = ref()
const fillNullTitle = ref('')
const fillNullEdit = ref()
const editFillNullField = ref(false)

const editField = item => {
  editCalcField.value = true
  nextTick(() => {
    calcTitle.value = t('dataset.edit_calc_field')
    calcEdit.value.initEdit(item, dimensions.value, quota.value)
  })
}

const closeNormalField = () => {
  currentNormalField.value.id = ''
  currentNormalField.value.name = ''
  editNormalField.value = false
}

const confirmNormalField = () => {
  ruleFormFieldRef.value.validate(val => {
    if (val) {
      allfields.value.some(ele => {
        if (ele.id === currentNormalField.value.id) {
          ele.name = currentNormalField.value.name
          return true
        }
        return false
      })
      closeNormalField()
    }
  })
}

const closeEditCalc = () => {
  editCalcField.value = false
}

const closeGroupField = () => {
  editGroupField.value = false
}

const confirmEditCalc = () => {
  calcEdit.value.formField.validate(val => {
    if (val) {
      calcEdit.value.setFieldForm()
      if (!calcEdit.value.fieldForm.originName.trim()) {
        ElMessage.error('琛ㄨ揪寮忎笉鑳戒负绌?')
        return
      }
      const obj = cloneDeep(calcEdit.value.fieldForm)
      const { deType, dateFormat, deExtractType } = obj
      obj.dateFormat = deType === 1 ? dateFormat : ''
      obj.dateFormatType = deType === 1 ? dateFormat : ''
      obj.deTypeArr = deType === 1 && deExtractType === 0 ? [deType, dateFormat] : [deType]
      const result = allfields.value.findIndex(ele => obj.id === ele.id)
      if (result !== -1) {
        allfields.value.splice(result, 1, obj)
      } else {
        allfields.value.push(obj)
      }
      editCalcField.value = false
    }
  })
}

const confirmGroupField = async () => {
  const result = await groupEdit.value.getResult()
  if (!result) return
  
  const obj = {
    ...result,
    dateFormat: '',
    dateFormatType: '',
    deTypeArr: [result.deType]
  }
  
  const existIndex = allfields.value.findIndex(ele => obj.id === ele.id)
  if (existIndex !== -1) {
    allfields.value.splice(existIndex, 1, obj)
  } else {
    allfields.value.push(obj)
  }
  editGroupField.value = false
  
  // 淇濆瓨鍒嗙粍瀛楁鍚庯紝鍒锋柊鏁版嵁棰勮
  nextTick(() => {
    datasetPreview()
  })
}

/** 鑾峰彇瀛楁鐨勭己澶卞€硷紙null鍜岀┖瀛楃涓诧級鍒楄〃 */
const getNullValuesForField = (field): string[] => {
  if (!Array.isArray(tableData.value) || !tableData.value.length) {
    return []
  }
  
  const firstRow = tableData.value[0]
  const firstRowKeys = Object.keys(firstRow)
  
  // 灏濊瘯澶氫釜鍙兘鐨?key
  const possibleKeys = [field?.dataeaseName, field?.originName, String(field?.id)]
  
  for (const key of possibleKeys) {
    if (key && firstRowKeys.includes(key)) {
      const nullSet = new Set<string>()
      tableData.value.forEach(row => {
        const v = row[key]
        // 鏀堕泦 null銆乽ndefined銆佺┖瀛楃涓层€佷互鍙婂瓧绗︿覆 "null"銆?undefined"
        if (v == null || v === '' || v === 'null' || v === 'NULL' || v === 'undefined') {
          nullSet.add(String(v ?? ''))
        }
      })
      return Array.from(nullSet)
    }
  }
  
  return []
}

/** 浠庢壒閲忕鐞嗘垨鏁版嵁棰勮鎸囧畾瀛楁鎵撳紑銆岀己澶卞€煎～鍏呫€嶅脊绐?*/
const addFillNullFieldWithField = async field => {
  if (!field) return

  // 鑾峰彇璇ュ瓧娈电殑缂哄け鍊煎垪琛?
  let nullValueList = getNullValuesForField(field)
  
  if (!nullValueList || !nullValueList.length) {
    // 濡傛灉娌℃湁浠庨瑙堟暟鎹幏鍙栧埌锛屽皾璇曡皟鐢ˋPI
    try {
      const values = await fetchFieldValuesFromApi(field)
      if (values && values.length) {
        // 绛涢€夊嚭缂哄け鍊?
        nullValueList = values.filter(v => v == null || v === '' || v === 'null' || v === 'NULL' || v === 'undefined')
      }
    } catch (e) {
      console.error('Failed to fetch null-like field values', e)
    }
  }

  editFillNullField.value = true
  fillNullTitle.value = t('dataset.fill_null_field')
  nextTick(() => {
    fillNullEdit.value.initEdit(
      { groupType: field.groupType || 'd', id: guid(), sourceField: field },
      allfields.value,
      nullValueList
    )
  })
}

const closeFillNullField = () => {
  editFillNullField.value = false
}

const confirmFillNullField = async () => {
  const result = await fillNullEdit.value.getResult()
  if (!result) return
  
  const { fillStrategy, customValue, selectedNullValues, sourceField } = result
  
  // 鍒涘缓濉厖瀛楁
  const obj = {
    id: result.id || guid(),
    name: result.name,
    originName: result.originName,
    groupType: sourceField.groupType || 'd',
    type: sourceField.type || 'VARCHAR',
    deType: sourceField.deType || 0,
    extField: 2, // 璁＄畻瀛楁
    checked: true,
    dateFormat: '',
    dateFormatType: '',
    deTypeArr: [sourceField.deType || 0],
    // 瀛樺偍濉厖閰嶇疆淇℃伅
    fillConfig: {
      sourceFieldId: sourceField.id,
      sourceFieldName: sourceField.name,
      fillStrategy: fillStrategy,
      customValue: customValue,
      selectedNullValues: selectedNullValues
    }
  }
  
  const existIndex = allfields.value.findIndex(ele => obj.id === ele.id)
  if (existIndex !== -1) {
    allfields.value.splice(existIndex, 1, obj)
  } else {
    allfields.value.push(obj)
  }
  editFillNullField.value = false
  
  // 淇濆瓨濉厖瀛楁鍚庯紝鍒锋柊鏁版嵁棰勮
  nextTick(() => {
    datasetPreview()
  })
}

const generateColumns = (arr: Field[]) =>
  arr.map(ele => ({
    key: String(ele.dataeaseName || ele.originName || ele.id || ''),
    deType: ele.deType,
    dataKey: String(ele.dataeaseName || ele.originName || ele.id || ''),
    title: ele.name,
    width: 150,
    headerCellRenderer: ({ column }) => (
      <div class="flex-align-center">
        <ElIcon>
          <Icon
            name={`field_${fieldType[column.deType]}`}
            className={`field-icon-${fieldType[column.deType]}`}
          ></Icon>
        </ElIcon>
        <span class="ellipsis" title={column.title} style={{ width: '120px', marginLeft: '4px' }}>
          {column.title}
        </span>
      </div>
    )
  }))

const dsChange = (val: string) => {
  dsLoading.value = true
  sqlNode.datasourceId = dataSource.value
  return getTables({ datasourceId: val })
    .then(res => {
      tableList = res || []
      datasourceTableData.value = [...tableList]
    })
    .finally(() => {
      dsLoading.value = false
    })
}

// 澶勭悊鏁版嵁婧愭爲鑺傜偣鐐瑰嚮
const handleDsTreeNodeClick = async (data: any, node: any) => {
  if (!data.leaf) {
    // 鐐瑰嚮鏁版嵁婧愭枃浠跺す锛屽姞杞藉叾涓嬬殑琛?
    dataSource.value = data.id
    await loadDatasourceTables(data, node)
    return
  }
  // 鐐瑰嚮鐨勬槸鏁版嵁琛紝璁剧疆褰撳墠閫変腑鐨勮〃
  const parentDsId = data.datasourceId || data.pid
  dataSource.value = parentDsId
  const tableInfo = {
    datasourceId: parentDsId,
    tableName: data.name,
    name: data.name,
    type: 'db'
  }
  setActiveName(tableInfo)
}

const getTableName = async (datasourceId, tableName) => {
  await dsChange(datasourceId)
  if (!!tableName) {
    searchTable.value = tableName
  }
}

const initEdite = async () => {
  let { id, datasourceId, tableName } = route.query
  let { id: copyId } = route.params
  if (appStore.getIsDataEaseBi) {
    id = embeddedStore.datasetId
    datasourceId = embeddedStore.datasourceId
    tableName = embeddedStore.tableName
    copyId = embeddedStore.datasetCopyId || copyId
  }
  if (copyId || id) {
    const barRes = await barInfoApi(copyId || id)
    if (!barRes || !barRes['id']) {
      return
    }
  }
  if (datasourceId) {
    dataSource.value = datasourceId as string
    getTableName(datasourceId as string, tableName)
  }
  if (!id && !copyId) return

  loading.value = true
  getDatasetDetails(copyId || id)
    .then(res => {
      let arr = []
      const { id, pid, name } = res || {}
      nodeInfo = {
        id,
        pid,
        name: copyId ? '复制数据集' : name
      }
      if (copyId) {
        nodeInfo.id = ''
      }
      datasetName.value = nodeInfo.name
      allfields.value = res.allFields || []
      dfsUnion(arr, res.union || [])
      const [fir] = res.union as { currentDs: { datasourceId: string } }[]
      dataSource.value = fir?.currentDs?.datasourceId
      dsChange(dataSource.value)
      datasetDrag.value.initState(arr, {
        sortFields: (res as any).sortFields || [],
        graphState: (res as any).graphState || null
      })
    })
    .catch(err => {
      const msg = err?.message || err?.data?.message || String(err)
      if (/NullPointerException|tableInfoDTO|getTable/.test(msg)) {
        ElMessage.error(
          'Failed to load dataset details: incomplete node table info'
        )
      } else {
        ElMessage.error('鍔犺浇鏁版嵁闆嗚鎯呭け璐ワ細' + (msg || '鏈煡閿欒'))
      }
    })
    .finally(() => {
      loading.value = false
    })
}

const joinEditor = (arr: []) => {
  state.editArr = cloneDeep(arr)
  editUnion.value = true
  nextTick(() => {
    fieldUnion.value.initState()
  })
}

const editNodeVisible = ref(false)
const editNodeTarget = ref(null)
const nodeConfigDrawer = shallowRef()

const handleEditNode = node => {
  editNodeTarget.value = cloneDeep(node)
  if (node.type === 'operation' && node.operationType !== 'join') {
    editNodeVisible.value = true
    nextTick(() => {
      nodeConfigDrawer.value?.initConfig(node)
    })
  } else {
    // 闈炩€滄搷浣滆妭鐐光€濇垨鑱旀帴鑺傜偣锛氬皾璇曢€氳繃鐢诲竷缁勪欢鎵撳紑鑱旀帴缂栬緫妗?
    // 浼樺厛璁╃敾甯冩牴鎹綋鍓嶈妭鐐?id 鍜岃繛绾垮叧绯诲喅瀹氬弬涓庤仈鎺ョ殑涓ゅ紶琛?
    datasetDrag.value?.openJoinEditorByNodeId?.(node.id)
  }
}

// 鍙屽嚮榛勮壊鏁版嵁闆嗚妭鐐癸細鍦ㄥ綋鍓嶇紪杈戝櫒涓墦寮€鍐呴儴宸ヤ綔鍖?tab
const handleEditDatasetNode = async node => {
  if (node.type !== 'dataset') return
  const datasetId = String((node as any).datasetId || node.id || '')
  const title = node.tableName || node.name || '未命名数据集'
  if (!datasetId) return
  await openWorkspaceDatasetTab(datasetId, title)
}

const findNodeParent = (id, list, parent = null) => {
  for (const item of list) {
    if (item.id === id && parent) {
      return [item, parent]
    }
    if (item.children?.length) {
      const result = findNodeParent(id, item.children, item)
      if (result) return result
    }
  }
  return null
}

const handleNodeConfigConfirm = config => {
  if (editNodeTarget.value) {
    editNodeTarget.value.operationConfig = config
    datasetDrag.value?.setNodeOperationConfig?.(editNodeTarget.value.id, config)
    if (selectedPreviewNodeId.value === editNodeTarget.value.id) {
      selectedPreviewNode.value = { ...(selectedPreviewNode.value || {}), operationConfig: config }
      datasetPreview()
    }
  }
}

const syncDatasetReferenceNode = async (node: any, datasetId: string, options?: { preview?: boolean }) => {
  if (!node || !datasetId) return
  datasetNodeLoading.value = true
  try {
    const res = await getDatasetDetails(datasetId)
    if (!res) return

    const fields = res.allFields || []
    const dimensions = fields.filter((f: any) => f.deType === 0 || f.deType === 1 || f.deType === 5)
    const quotas = fields.filter((f: any) => f.deType === 2 || f.deType === 3 || f.deType === 4)
    const dataSourceId =
      (res as any).dataSourceId ||
      (res as any).datasourceId ||
      getDatasourceIdFromUnion((res as any).union || []) ||
      (fields.find((f: any) => !!f?.datasourceId) as any)?.datasourceId ||
      ''
    const nextTitle = (res as any).name || node.tableName || node.name || '未命名数据集'

    const convertedFields = [...dimensions, ...quotas].map((f: any) => {
      const origin = f.originName || f.name || 'field'
      const dataeaseName = fieldNameShort(node.id + '_' + origin)
      return {
        ...f,
        id: f.id || guid(),
        datasetTableId: node.id,
        datasetGroupId: f.datasetGroupId || datasetId,
        datasourceId: f.datasourceId || dataSourceId,
        originName: f.originName || f.name,
        dataeaseName: f.dataeaseName || dataeaseName,
        fieldShortName: f.fieldShortName || dataeaseName,
        checked: true
      }
    })

    node.datasetId = datasetId
    node.tableName = nextTitle
    node.currentDsFields = convertedFields
    if (!node.currentDs) {
      node.currentDs = {}
    }
    node.currentDs.datasourceId = dataSourceId
    node.currentDs.id = node.id
    node.currentDs.datasetGroupId = datasetId
    node.currentDs.tableName = nextTitle
    node.currentDs.type = 'dataset'

    node.datasourceId = dataSourceId
    try {
      const infoObj = JSON.parse(node.info || '{}') as any
      infoObj.datasetId = datasetId
      infoObj.reference = true
      infoObj.table = nextTitle
      infoObj.datasourceId = dataSourceId
      node.info = JSON.stringify(infoObj)
    } catch (e) {
      node.info = JSON.stringify({
        datasetId,
        reference: true,
        table: nextTitle,
        datasourceId: dataSourceId
      })
    }
    node.currentDs.info = node.info

    addComplete()
    updateAllfields()

    datasetNodeLoading.value = false
    await nextTick()
    if (options?.preview !== false) {
      handleSelectPreviewNode(node)
    }
  } catch (error) {
    console.error('鍔犺浇鏁版嵁闆嗗瓧娈靛け璐?', error)
    ElMessage.error('Failed to load dataset fields')
  } finally {
    if (datasetNodeLoading.value) {
      datasetNodeLoading.value = false
    }
  }
}

const handleRefreshNode = async node => {
  if (node?.type === 'dataset') {
    const datasetId = getReferenceDatasetId(node) || String((node as any).datasetId || '')
    if (datasetId) {
      await syncDatasetReferenceNode(node, datasetId, { preview: true })
      return
    }
  }
  if (node) {
    handleSelectPreviewNode(node)
    return
  }
  datasetPreview()
}

const handleCopyNode = node => {
  const copied = cloneDeep(node)
  copied.id = guid()
  copied.tableName = node.tableName + '_copy'
  ElMessage.success('Node copied')
}

const handleToolbarDragStart = e => {
  offsetX.value = e.offsetX
  offsetY.value = e.offsetY
  maskShow.value = true
}

const handleToolbarDragEnd = () => {
  maskShow.value = false
}

const columns = shallowRef([])
const tableData = shallowRef([])
const quota = computed(() => {
  const fields = allfields.value
  // 浠呭湪閫変腑鍏蜂綋琛ㄨ妭鐐规椂鎵嶆寜 datasetTableId 杩囨护锛涙搷浣滆妭鐐规樉绀哄叏閮紙閬垮厤璇繃婊や负绌猴級
  if (
    selectedPreviewNodeId.value &&
    selectedPreviewNodeId.value !== 'result_output' &&
    ['db', 'sql'].includes((selectedPreviewNode.value as any)?.type)
  ) {
    return fields.filter(
      ele => ele.groupType === 'q' && (ele as any).datasetTableId === selectedPreviewNodeId.value
    )
  }
  return fields.filter(ele => ele.groupType === 'q')
})

const dimensions = computed(() => {
  const fields = allfields.value
  // 浠呭湪閫変腑鍏蜂綋琛ㄨ妭鐐规椂鎵嶆寜 datasetTableId 杩囨护锛涙搷浣滆妭鐐规樉绀哄叏閮紙閬垮厤璇繃婊や负绌猴級
  if (
    selectedPreviewNodeId.value &&
    selectedPreviewNodeId.value !== 'result_output' &&
    ['db', 'sql'].includes((selectedPreviewNode.value as any)?.type)
  ) {
    return fields.filter(
      ele => ele.groupType === 'd' && (ele as any).datasetTableId === selectedPreviewNodeId.value
    )
  }
  return fields.filter(ele => ele.groupType === 'd')
})

const tabChange = val => {
  if (val === 'preview') return
  allfields.value.forEach(ele => {
    if (!Array.isArray(ele.deTypeArr)) {
      ele.deTypeArr =
        ele.deType === 1 && ele.deExtractType === 0
          ? [ele.deType, ele.dateFormatType]
          : [ele.deType]
    } else {
      const [type] = ele.deTypeArr
      if (ele.deTypeArr.length && type !== ele.deType) {
        ele.deTypeArr.splice(0, 1, ele.deType)
      }
    }
  })
}

const addComplete = () => {
  state.nodeNameList = [...datasetDrag.value.nodeNameList]
  if (!state.nodeNameList?.length) {
    columns.value = []
    tableData.value = []
    cancelMap['/datasetData/previewData']?.()
    datasetPreviewLoading.value = false
  }
}

const state = reactive({
  nodeNameList: [],
  editArr: [],
  dataSourceList: [],
  fieldCollapse: ['dimension', 'quota'],
  datasetList: [] as any[], // 宸插垱寤虹殑鏁版嵁闆嗗垪琛?
  datasetLoading: false
})

const datasourceTableData = shallowRef([])

/** 宸︿晶鏁版嵁闆嗘爲锛氫笌鏁版嵁闆嗗垪琛ㄩ〉涓€鑷寸殑鎼滅储銆佹帓搴?*/
const panelSearchKeyword = ref('')
const panelDatasetTreeRef = ref()
const panelDatasourceTreeRef = ref()
const datasourceExpandedKeys = ref<string[]>([])
const panelDatasetSortType = ref('time_desc')
const originDatasetListForPanel = shallowRef<BusiTreeNode[]>([])

const datasetPanelSortList = [
  { name: '按创建时间升序', value: 'time_asc' },
  { name: '按创建时间降序', value: 'time_desc', divided: true },
  { name: '鎸夌収鍚嶇О鍗囧簭', value: 'name_asc' },
  { name: '鎸夌収鍚嶇О闄嶅簭', value: 'name_desc' }
]

const datasetPanelSortTip = computed(
  () => datasetPanelSortList.find(ele => ele.value === panelDatasetSortType.value)?.name || ''
)

const datasetTreeDefaultProps = { children: 'children', label: 'name' }

const applyDatasetPanelSort = () => {
  if (!originDatasetListForPanel.value?.length) {
    state.datasetList = []
    return
  }
  state.datasetList = treeSort(cloneDeep(originDatasetListForPanel.value), panelDatasetSortType.value) as any[]
}

const panelDatasetSortChange = (val: string) => {
  panelDatasetSortType.value = val
  wsCache.set('TreeSort-dataset', val)
  applyDatasetPanelSort()
  nextTick(() => panelDatasetTreeRef.value?.filter(panelSearchKeyword.value))
}

const panelFilterNode = (value: string, data: BusiTreeNode) => {
  if (!value) return true
  return data.name?.toLowerCase().includes(value.toLowerCase())
}

watch(panelSearchKeyword, val => {
  nextTick(() => panelDatasetTreeRef.value?.filter(val))
})

const panelDatasourceSearch = ref('')
const panelDatasourceFilterNode = (value: string, data: BusiTreeNode) => {
  if (!value) return true
  return data.name?.toLowerCase().includes(value.toLowerCase())
}

watch(panelDatasourceSearch, val => {
  nextTick(() => panelDatasourceTreeRef.value?.filter(val))
})

const getIconName = (type: number) => {
  if (type === 1) {
    return 'time'
  }

  if (type === 0) {
    return 'text'
  }

  if ([2, 3, 4].includes(type)) {
    return 'value'
  }
  if (type === 5) {
    return 'location'
  }
}

const allfields = ref([])

/** 褰撳墠閫変腑鐨勭敾甯冭妭鐐癸紝鐢ㄤ簬鏋勯€犳寜鑺傜偣/瀛愭爲棰勮鐨?union */
const selectedPreviewNode = ref<any | null>(null)
/** 褰撳墠閫変腑鐨勭敾甯冭妭鐐?id锛岀敤浜庨瑙堝尯鍙樉绀鸿鑺傜偣瀵瑰簲琛ㄧ殑瀛楁 */
const selectedPreviewNodeId = ref('')

provide('allfields', allfields)

let num = +new Date()

const expandedD = ref(true)
const expandedQ = ref(true)
const setGuid = (arr, id, datasourceId, oldArr) => {
  arr.forEach(ele => {
    if (!ele.id) {
      ele.id = oldArr.find(itx => itx.originName === ele.originName)?.id || `${++num}`
      ele.datasetTableId = id
      ele.datasourceId = datasourceId
    }
  })
}

const dfsFields = (arr, list) => {
  list.forEach(ele => {
    if (ele.children?.length) {
      dfsFields(arr, ele.children)
    }
    const { currentDsFields, id, datasourceId } = ele
    const fields = (currentDsFields || []).map(f =>
      normalizeField(cloneDeep(f), id, datasourceId || '')
    )
    arr.push(...fields)
  })
}

const getDelIdArr = (newArr, oldArr) => {
  const idMapNew = newArr.map(ele => ele.id)
  return [
    ...oldArr.filter(ele => ele.extField !== 2).filter(ele => !idMapNew.includes(ele.id)),
    ...oldArr.filter(ele => ele.extField === 2)
  ]
}

const diffArr = (newArr, oldArr) => {
  const idMapNew = newArr.map(ele => ele.id)
  const idMapOld = oldArr.map(ele => ele.id)
  const arr = newArr.filter(ele => !idMapOld.includes(ele.id))
  return cloneDeep([
    ...oldArr.filter(ele => ele.extField === 2),
    ...arr,
    ...oldArr.filter(ele => idMapNew.includes(ele.id))
  ])
}

const closeEditUnion = () => {
  notConfirmEditUnion()
  fieldUnion.value.clearState()
  editUnion.value = false
}
const fieldUnion = shallowRef()

const setFieldAll = () => {
  const arr = []
  dfsFields(arr, datasetDrag.value.getNodeList())
  const delIdArr = getDelIdArr(arr, allfields.value)
  allfields.value = diffArr(arr, allfields.value)
  delFieldById(delIdArr)
  tabChange('manage')
  fieldUnion.value?.clearState()
}

const dfsNode = (arr, id) => {
  return arr.reduce((pre, next) => {
    if (next.id === id) {
      pre = [...next.currentDsFields]
    } else if (next.children?.length) {
      pre = dfsNode(next.children, id)
    }
    return pre
  }, [])
}

const dfsFieldsTips = (arr, list, idArr) => {
  list.forEach(ele => {
    if (ele.children?.length) {
      dfsFieldsTips(arr, ele.children, idArr)
    }
    if (!idArr.includes(ele.id)) {
      const { currentDsFields } = ele
      arr.push(...cloneDeep(currentDsFields))
    }
  })
}
const confirmEditUnion = () => {
  const { node, parent } = fieldUnion.value
  const to = node.id
  const from = parent.id
  let unionFieldsLost = node.unionFields.some(ele => {
    const { currentField, parentField } = ele
    return !currentField || !parentField
  })

  if (unionFieldsLost) {
    ElMessage.error('鍏宠仈瀛楁涓嶈兘涓虹┖!')
    return
  }

  const nodeOldCurrentDsFields = dfsNode(datasetDrag.value.getNodeList(), to)
  const parentOldCurrentDsFields = dfsNode(datasetDrag.value.getNodeList(), from)

  setGuid(node.currentDsFields, node.id, node.datasourceId, nodeOldCurrentDsFields)
  setGuid(parent.currentDsFields, parent.id, parent.datasourceId, parentOldCurrentDsFields)
  const top = cloneDeep(node)
  const bottom = cloneDeep(parent)

  let arr = []
  dfsFieldsTips(arr, datasetDrag.value.getNodeList(), [node.id, parent.id])
  arr = [...arr, ...node.currentDsFields, ...parent.currentDsFields]
  const delIdArr = getDelIdArr(arr, allfields.value)
  let fakeAllfields = diffArr(arr, allfields.value)
  const idList = delFieldByIdFake(delIdArr, fakeAllfields)
  if (!!idList.length) {
    const idArr = allfields.value.reduce((pre, next) => {
      if (idList.includes(next.id)) {
        const idMap = next.originName.match(/\[(.+?)\]/g)
        const result = idMap.map(itm => {
          return itm.slice(1, -1)
        })
        pre = [...result, ...pre]
      }
      return pre
    }, [])

    ElMessageBox.confirm(
      '字段' +
        allfields.value
          .filter(ele => [...new Set(idArr)].includes(ele.id) && ele.extField !== 2)
          .map(ele => ele.name)
          .join(',') +
        '未被选择，其相关的新建字段将被删除，是否继续？',
      {
        confirmButtonText: t('dataset.confirm'),
        cancelButtonText: t('common.cancel'),
        showCancelButton: true,
        confirmButtonType: 'danger',
        type: 'warning',
        autofocus: false,
        showClose: false,
        callback: (action: Action) => {
          if (action === 'confirm') {
            datasetDrag.value.setStateBack(top, bottom)
            setFieldAll()
            editUnion.value = false
            addComplete()
            datasetDrag.value.setChangeStatus(to, from)
            // 缂栬緫鑱旀帴鍏崇郴鍚庯紝鑷姩鍒锋柊棰勮
            updateAllfields()
          }
        }
      }
    )
    return
  }

  datasetDrag.value.setStateBack(top, bottom)
  setFieldAll()
  editUnion.value = false
  addComplete()
  datasetDrag.value.setChangeStatus(to, from)
  // 缂栬緫鑱旀帴鍏崇郴鍚庯紝鑷姩鍒锋柊棰勮
  updateAllfields()
}

const updateAllfields = () => {
  setFieldAll()
  nextTick(() => {
    // 瀛楁鍒楄〃鏇存柊瀹屾垚鍚庯紝鏍规嵁鏈€杩戞柊澧炵殑鑺傜偣鎴栭€変腑鐨勮妭鐐硅嚜鍔ㄨЕ鍙戦瑙堬紝
    // 纭繚鎷栧叆鑺傜偣鏃朵篃浼氬儚鐐瑰嚮鑺傜偣涓€鏍疯皟鐢ㄩ瑙堟帴鍙ｃ€?
    const lastAdded = datasetDrag.value?.getLastAddedNode?.()
    if (lastAdded) {
      handleSelectPreviewNode({ id: lastAdded.id, type: lastAdded.type })
    } else if (allfields.value.length > 0) {
      // 鏈夊瓧娈垫椂锛屾牴鎹€変腑鐘舵€佸喅瀹氶瑙堣寖鍥?
      if (selectedPreviewNode.value && selectedPreviewNode.value.type === 'result') {
        handleSelectPreviewNode({ id: 'result_output', type: 'result' })
      } else if (
        selectedPreviewNode.value &&
        (selectedPreviewNode.value as any)?.operationType === 'join'
      ) {
        handleSelectPreviewNode({
          id: selectedPreviewNode.value.id,
          type: 'operation'
        })
      } else {
        datasetPreview()
      }
    } else {
      // 娌℃湁瀛楁鏃朵粛鐒跺皾璇曢瑙堬紙鍙兘鏄┖缁撴灉闆嗭級
      datasetPreview()
    }
  })
}

const notConfirmEditUnion = () => {
  datasetDrag.value.notConfirm()
}

const dragstart = (e: DragEvent, ele) => {
  offsetX.value = e.offsetX
  offsetY.value = e.offsetY
  e.dataTransfer.setData('text/plain', JSON.stringify(ele))
  maskShow.value = true
}
const setActiveName = (data: Table) => {
  if (data.unableCheck) return
  activeName.value = data.tableName
}

const isDragging = ref(false)

const mousedownDrag = () => {
  isDragging.value = true
  document.querySelector('body').style.userSelect = 'none'
  document.querySelector('.dataset-db').addEventListener('mousemove', calculateWidth)
}
const mouseupDrag = () => {
  isDragging.value = false
  document.querySelector('body').style.userSelect = 'auto'
  const dom = document.querySelector('.dataset-db')
  dom.removeEventListener('mousemove', calculateWidth)
  dom.removeEventListener('mousemove', calculateHeight)
}

const crossDatasources = computed(() => {
  return datasetDrag.value?.crossDatasources
})
const calculateWidth = (e: MouseEvent) => {
  if (e.pageX < 240) {
    LeftWidth.value = 240
    return
  }
  if (e.pageX > 500) {
    LeftWidth.value = 500
    return
  }
  LeftWidth.value = e.pageX
}

const mousedownDragH = () => {
  document.querySelector('.dataset-db').addEventListener('mousemove', calculateHeight)
}
const getTopOffset = () => {
  const toolbar = document.querySelector('.operation-toolbar') as HTMLElement
  const toolbarH = toolbar ? toolbar.offsetHeight : 0
  return 56 + toolbarH
}
const calculateHeight = (e: MouseEvent) => {
  const clientHeight = document.documentElement.clientHeight
  const topOffset = getTopOffset()
  if (e.pageY - topOffset < 64) {
    dragHeight.value = 64
    sqlResultHeight.value = clientHeight - dragHeight.value - topOffset
    return
  }
  if (e.pageY > clientHeight - 57) {
    dragHeight.value = clientHeight - topOffset - 57
    sqlResultHeight.value = clientHeight - dragHeight.value - topOffset
    return
  }
  dragHeight.value = e.pageY - topOffset
  sqlResultHeight.value = clientHeight - dragHeight.value - topOffset
  quotaTableHeight.value = sqlResultHeight.value - 242
}

const sqlResultHeight = ref(0)
const handleResize = debounce(() => {
  const clientHeight = document.documentElement.clientHeight
  const topOffset = getTopOffset()
  if (clientHeight - sqlResultHeight.value - topOffset < 64) {
    dragHeight.value = 64
    sqlResultHeight.value = clientHeight - dragHeight.value - topOffset
    return
  }
  dragHeight.value = clientHeight - sqlResultHeight.value - topOffset
}, 60)
let willBack = false
const saveAndBack = () => {
  if (!willBack) return
  pushDataset()
}

let p = null
const XpackLoaded = () => p(true)
onMounted(async () => {
  await new Promise(r => (p = r))
  await initEdite()
  workspaceRootSnapshot.value = captureCurrentWorkspaceSnapshot()
  workspaceRootTitle.value = datasetName.value?.trim() || workspaceRootTitle.value
  getDatasource()
  getDatasetList()
  useEmitt({
    name: 'onDatasetSave',
    callback: saveAndBack
  })
  window.addEventListener('resize', handleResize)
  getSqlResultHeight()
  quotaTableHeight.value = sqlResultHeight.value - 242
})

onBeforeUnmount(() => {
  try {
    window.removeEventListener('resize', handleResize)
  } catch (_) {
    // 閬垮厤鍦ㄥ疄渚嬪凡閿€姣佹椂瑙﹀彂鐢熷懡鍛ㄦ湡瀵艰嚧鐨?Vue warn
  }
})
const getSqlResultHeight = () => {
  sqlResultHeight.value = (document.querySelector('.sql-result') as HTMLElement).offsetHeight
}
const getDatasource = () => {
  getDatasourceList().then(res => {
    const _list = (res as unknown as DataSource[]) || []
    // 鏍硅妭鐐?id === '0' 鐨勬儏鍐碉紝鐩存帴鍙?children
    if (_list.length > 0 && _list[0].id === '0') {
      state.dataSourceList = (_list[0].children || []).map((ds: any) => ({
        ...ds,
        leaf: false,
        children: undefined
      }))
    } else {
      state.dataSourceList = _list.map((ds: any) => ({
        ...ds,
        leaf: false,
        children: undefined
      }))
    }
    datasourceExpandedKeys.value = state.dataSourceList.map((ds: any) => String(ds.id))
    preloadDatasourceTables()
  })
}

// 鑾峰彇宸插垱寤虹殑鏁版嵁闆嗗垪琛紙鐢ㄤ簬鎷栨嫿鍒扮敾甯冧綔涓哄紩鐢級
const getDatasetList = () => {
  state.datasetLoading = true
  interactiveStore.setInteractive({ busiFlag: 'dataset' } as any)
    .then((res: any) => {
      const list: BusiTreeNode[] = res || []
      originDatasetListForPanel.value = cloneDeep(list)
      const history = wsCache.get('TreeSort-dataset') as string | undefined
      if (history) {
        panelDatasetSortType.value = history
      }
      applyDatasetPanelSort()
    })
    .finally(() => {
      state.datasetLoading = false
      nextTick(() => panelDatasetTreeRef.value?.filter(panelSearchKeyword.value))
    })
}

/** 涓庢暟鎹泦鍒楄〃椤典竴鑷达細鍙跺瓙鑺傜偣涓哄彲寮曠敤鐨勬暟鎹泦 */
const isDatasetTreeLeaf = (data: any) =>
  data?.leaf === true || data?.nodeType === 'dataset'

// 澶勭悊鏁版嵁闆嗘嫋鎷藉紑濮?
const datasetDragStart = (e: DragEvent, dataset: any) => {
  if (!isDatasetTreeLeaf(dataset)) return
  offsetX.value = e.offsetX
  offsetY.value = e.offsetY
  const dragData = {
    type: 'dataset',
    datasetId: dataset.id,
    tableName: dataset.name,
    name: dataset.name,
    datasourceId: '',
    info: JSON.stringify({ datasetId: dataset.id, reference: true })
  }
  e.dataTransfer.setData('text/plain', JSON.stringify(dragData))
  maskShow.value = true
}

const getReferenceDatasetId = (node: any) => {
  if (node?.datasetId) return String(node.datasetId)
  try {
    const info = JSON.parse(node?.info || '{}') as any
    return String(info?.datasetId || '')
  } catch {
    return ''
  }
}

const getDatasourceIdFromUnion = (unionList: any[] = []): string => {
  const stack = [...unionList]
  while (stack.length) {
    const item = stack.shift()
    const datasourceId = item?.currentDs?.datasourceId
    if (datasourceId) return String(datasourceId)
    if (Array.isArray(item?.childrenDs) && item.childrenDs.length) {
      stack.push(...item.childrenDs)
    }
  }
  return ''
}

// 澶勭悊鍔犺浇鏁版嵁闆嗗紩鐢ㄨ妭鐐瑰瓧娈?
const handleLoadDatasetFields = async ({ node, datasetId }: { node: any, datasetId: string }) => {
  await syncDatasetReferenceNode(node, datasetId, { preview: true })
}

const normalizeAiText = (value: any) => String(value || '').trim().toLowerCase()
const assistantAllowedOperationTypes = new Set((operationList || []).map((item: any) => String(item?.key || '')))
const ASSISTANT_NODE_WIDTH = 180
const ASSISTANT_NODE_HEIGHT = 40
const ASSISTANT_NODE_GAP_Y = 120

const normalizeAssistantJoinType = (joinType: any) => {
  const raw = normalizeAiText(joinType).replace(/[_-]/g, ' ')
  if (!raw) return 'left'
  if (raw.includes('left')) return 'left'
  if (raw.includes('right')) return 'right'
  if (raw.includes('inner')) return 'inner'
  if (raw.includes('full')) return 'full'
  return 'left'
}

const normalizeAssistantOperationType = (operationType: any) => {
  const raw = normalizeAiText(operationType)
  if (!raw) return ''
  if (raw === 'join' || raw.includes('join')) return 'join'
  if (raw === 'union' || raw.includes('union')) return 'union'
  if (raw === 'mirror' || raw.includes('mirror')) return 'mirror'
  return raw
}

const isAssistantOperationAllowed = (operationType: string) => {
  return assistantAllowedOperationTypes.has(String(operationType || ''))
}

const buildAssistantNodePositionMap = (planNodes: any[]) => {
  const map: Record<string, { x: number; y: number }> = {}
  const graphNodes = datasetDrag.value?.getGraphState?.()?.nodes || []
  const occupied: Array<{ x: number; y: number }> = graphNodes
    .map((node: any) => ({
      x: Number(node?.x ?? 0),
      y: Number(node?.y ?? 0)
    }))
    .filter((pos: any) => Number.isFinite(pos.x) && Number.isFinite(pos.y))

  const maxY = occupied.length ? Math.max(...occupied.map(pos => pos.y)) : 0
  const baseY = Math.max(80, maxY + 120)
  const counters: Record<string, number> = {
    source: 0,
    operation: 0,
    result: 0
  }
  const columnX = {
    source: 100,
    operation: 420,
    result: 760
  }

  const intersects = (x: number, y: number) =>
    occupied.some(pos => {
      const overlapX = Math.abs(pos.x - x) < ASSISTANT_NODE_WIDTH + 28
      const overlapY = Math.abs(pos.y - y) < ASSISTANT_NODE_HEIGHT + 24
      return overlapX && overlapY
    })

  const allocatePosition = (kind: 'source' | 'operation' | 'result') => {
    const x = columnX[kind]
    let y = baseY + counters[kind] * ASSISTANT_NODE_GAP_Y
    while (intersects(x, y)) {
      counters[kind] += 1
      y = baseY + counters[kind] * ASSISTANT_NODE_GAP_Y
    }
    counters[kind] += 1
    occupied.push({ x, y })
    return { x, y }
  }

  ;(planNodes || []).forEach((node: any) => {
    const key = String(node?.key || '')
    if (!key) return
    const nodeType = String(node?.nodeType || '')
    if (nodeType === 'result') {
      map[key] = allocatePosition('result')
      return
    }
    if (nodeType === 'operation') {
      map[key] = allocatePosition('operation')
      return
    }
    map[key] = allocatePosition('source')
  })

  return map
}

const enrichAssistantJoinPlan = (plan: any) => {
  const nodes = Array.isArray(plan?.nodes) ? cloneDeep(plan.nodes) : []
  const links = Array.isArray(plan?.links) ? cloneDeep(plan.links) : []
  const joinConfig = plan?.joinConfig
  const leftKey = String(joinConfig?.leftNode || '')
  const rightKey = String(joinConfig?.rightNode || '')
  if (!leftKey || !rightKey) {
    return { nodes, links }
  }

  let joinNode = nodes.find(
    (n: any) =>
      String(n?.nodeType || '') === 'operation' &&
      normalizeAssistantOperationType(n?.operationType) === 'join'
  )
  let joinKey = String(joinNode?.key || 'assistant_join')
  if (!joinNode) {
    joinNode = {
      key: joinKey,
      nodeType: 'operation',
      operationType: 'join',
      name: 'Join'
    }
    nodes.push(joinNode)
  } else {
    joinNode.operationType = 'join'
    joinNode.name = joinNode.name || 'Join'
    if (!joinNode.key) {
      joinNode.key = joinKey
    } else {
      joinKey = String(joinNode.key)
    }
  }

  const resultNode = nodes.find((n: any) => String(n?.nodeType || '') === 'result')
  const resultKey = String(resultNode?.key || 'result_output')
  const isResultKey = (key: string) => key === resultKey || key === 'result_output'
  const dedup: Set<string> = new Set()
  const nextLinks: Array<{ from: string; to: string }> = []
  const pushLink = (from: string, to: string) => {
    if (!from || !to) return
    const id = from + '__' + to
    if (dedup.has(id)) return
    dedup.add(id)
    nextLinks.push({ from, to })
  }

  for (const link of links) {
    const from = String(link?.from || '')
    const to = String(link?.to || '')
    if (!from || !to) continue
    if ((from === leftKey || from === rightKey) && isResultKey(to)) continue
    if ((from === leftKey || from === rightKey) && to === joinKey) continue
    if (from === joinKey && isResultKey(to)) continue
    pushLink(from, to)
  }

  pushLink(leftKey, joinKey)
  pushLink(rightKey, joinKey)
  pushLink(joinKey, resultKey)

  return { nodes, links: nextLinks }
}

const flattenDatasetTreeLeaves = (list: any[] = []) => {
  const result: any[] = []
  const walk = (items: any[] = []) => {
    items.forEach(item => {
      if (!item) return
      if (isDatasetTreeLeaf(item)) {
        result.push(item)
        return
      }
      if (Array.isArray(item.children) && item.children.length) {
        walk(item.children)
      }
    })
  }
  walk(list)
  return result
}

const flattenDatasourceTables = (list: any[] = []) => {
  const result: any[] = []
  list.forEach(ds => {
    const datasourceId = String(ds?.id || '')
    const datasourceName = ds?.name || ''
    ;(ds?.children || []).forEach(table => {
      result.push({
        ...table,
        datasourceId,
        datasourceName,
        tableName: table?.tableName || table?.name,
        name: table?.name || table?.tableName
      })
    })
  })
  return result
}

const ensureAssistantDatasourceTables = async () => {
  const sources = (state.dataSourceList || []).filter((ds: any) => !ds?.leaf)
  if (!sources.length) return
  await Promise.allSettled(sources.map(ds => loadDatasourceTables(ds)))
}

const getAssistantDatasetCandidates = () => {
  return flattenDatasetTreeLeaves(originDatasetListForPanel.value || []).map((item: any) => ({
    id: String(item.id || ''),
    name: item.name || '',
    label: item.label || item.name || ''
  }))
}

const getAssistantTableCandidates = () => {
  return flattenDatasourceTables(state.dataSourceList || []).map((item: any) => ({
    datasourceId: String(item.datasourceId || ''),
    datasourceName: item.datasourceName || '',
    tableName: item.tableName || item.name || '',
    name: item.name || item.tableName || ''
  }))
}

const normalizeAssistantPlan = (res: any) => {
  const root = res?.chartData?.plan
  if (!root || typeof root !== 'object') return null
  const plan = root?.plan && typeof root.plan === 'object' ? root.plan : root
  return {
    reply: String(res?.apiMsg || root?.reply || ''),
    plan
  }
}

const findAssistantDatasetCandidate = (nodeSpec: any) => {
  const target = normalizeAiText(nodeSpec?.datasetName || nodeSpec?.name)
  if (!target) return null
  const candidates = getAssistantDatasetCandidates()
  return (
    candidates.find(item => normalizeAiText(item.name) === target) ||
    candidates.find(item => normalizeAiText(item.name).includes(target) || target.includes(normalizeAiText(item.name))) ||
    null
  )
}

const findAssistantTableCandidate = (nodeSpec: any) => {
  const tableName = normalizeAiText(nodeSpec?.tableName || nodeSpec?.name)
  const datasourceName = normalizeAiText(nodeSpec?.datasourceName)
  const tables = getAssistantTableCandidates()
  const exact = tables.find(item => {
    const tableMatch = normalizeAiText(item.tableName) === tableName
    const dsMatch = !datasourceName || normalizeAiText(item.datasourceName) === datasourceName
    return tableMatch && dsMatch
  })
  if (exact) return exact
  return (
    tables.find(item => normalizeAiText(item.tableName) === tableName) ||
    tables.find(
      item =>
        normalizeAiText(item.tableName).includes(tableName) ||
        tableName.includes(normalizeAiText(item.tableName))
    ) ||
    null
  )
}

const findExistingCanvasNode = (nodeSpec: any, resolvedCandidate?: any) => {
  const graphNodes = datasetDrag.value?.getGraphState?.()?.nodes || []
  const nodeType = String(nodeSpec?.nodeType || '')
  const targetName = normalizeAiText(nodeSpec?.name || nodeSpec?.datasetName || nodeSpec?.tableName)
  for (const item of graphNodes) {
    const canvasNode = datasetDrag.value?.getCanvasNode?.(item.id)
    if (!canvasNode) continue
    if (nodeType === 'dataset' && canvasNode.type === 'dataset') {
      const datasetId = String(
        canvasNode.datasetId ||
          (() => {
            try {
              return JSON.parse(canvasNode.info || '{}')?.datasetId || ''
            } catch {
              return ''
            }
          })()
      )
      if (
        (resolvedCandidate?.id && datasetId === String(resolvedCandidate.id)) ||
        normalizeAiText(canvasNode.tableName) === targetName
      ) {
        return canvasNode
      }
    }
    if ((nodeType === 'table' || nodeType === 'db') && canvasNode.type === 'db') {
      if (
        (resolvedCandidate?.datasourceId &&
          String(canvasNode.datasourceId || '') === String(resolvedCandidate.datasourceId) &&
          normalizeAiText(canvasNode.tableName) === normalizeAiText(resolvedCandidate.tableName)) ||
        normalizeAiText(canvasNode.tableName) === targetName
      ) {
        return canvasNode
      }
    }
  }
  return null
}

const buildAssistantNodePosition = (nodeSpec: any, index: number, total: number) => {
  const nodeType = String(nodeSpec?.nodeType || '')
  if (nodeType === 'operation') {
    return { x: 420, y: 140 + Math.max(0, total - 2) * 20 }
  }
  if (nodeType === 'result') {
    return { x: 740, y: 160 }
  }
  return {
    x: 100,
    y: 80 + index * 140
  }
}

const ensureAssistantPlanNode = async (
  nodeSpec: any,
  index: number,
  total: number,
  positionMap?: Record<string, { x: number; y: number }>
) => {
  const nodeType = String(nodeSpec?.nodeType || '')
  const planKey = String(nodeSpec?.key || '')
  const plannedPosition = (planKey && positionMap?.[planKey]) || buildAssistantNodePosition(nodeSpec, index, total)
  if (nodeType === 'result') {
    return datasetDrag.value?.getCanvasNode?.('result_output') || { id: 'result_output', type: 'result' }
  }

  if (nodeType === 'operation') {
    const normalizedOperationType = normalizeAssistantOperationType(nodeSpec?.operationType)
    if (!normalizedOperationType || !isAssistantOperationAllowed(normalizedOperationType)) {
      throw new Error(
        'Unsupported assistant operation node: ' +
          String(nodeSpec?.operationType || normalizedOperationType || 'unknown')
      )
    }
    return await datasetDrag.value?.addNodeByPayload?.(
      {
        type: 'operation',
        operationType: normalizedOperationType || nodeSpec?.operationType,
        tableName: nodeSpec?.name || (normalizedOperationType === 'join' ? 'Join' : '')
      },
      plannedPosition
    )
  }

  if (nodeType === 'dataset') {
    const candidate = findAssistantDatasetCandidate(nodeSpec)
    if (!candidate) {
      throw new Error('Dataset not found: ' + String(nodeSpec?.datasetName || nodeSpec?.name || ''))
    }
    const reused = findExistingCanvasNode(nodeSpec, candidate)
    if (reused) {
      if (!reused.currentDsFields?.length) {
        await syncDatasetReferenceNode(reused, candidate.id, { preview: false })
      }
      return reused
    }
    const newNode = await datasetDrag.value?.addNodeByPayload?.(
      {
        type: 'dataset',
        datasetId: candidate.id,
        tableName: candidate.name,
        name: candidate.name
      },
      plannedPosition,
      { hydrateDatasetFields: false }
    )
    if (newNode) {
      await syncDatasetReferenceNode(newNode, candidate.id, { preview: false })
    }
    return newNode
  }

  const tableCandidate = findAssistantTableCandidate(nodeSpec)
  if (!tableCandidate) {
    throw new Error('Table not found: ' + String(nodeSpec?.tableName || nodeSpec?.name || ''))
  }
  const reused = findExistingCanvasNode(nodeSpec, tableCandidate)
  if (reused) {
    if (!reused.currentDsFields?.length) {
      reused.currentDsFields = await loadRawFieldsForNode(reused)
    }
    return reused
  }
  return await datasetDrag.value?.addNodeByPayload?.(
    {
      type: 'db',
      datasourceId: tableCandidate.datasourceId,
      tableName: tableCandidate.tableName,
      name: tableCandidate.tableName
    },
    plannedPosition,
    { fetchFields: true }
  )
}

const executeAssistantCanvasPlan = async (normalized: { reply: string; plan: any }) => {
  const plan = normalized?.plan || {}
  if (plan?.intent !== 'build_dataset_canvas') {
    throw new Error('Assistant did not return an executable canvas plan')
  }
  const enhancedPlan = enrichAssistantJoinPlan(plan)
  const nodes = Array.isArray(enhancedPlan?.nodes) ? enhancedPlan.nodes : []
  const links = Array.isArray(enhancedPlan?.links) ? enhancedPlan.links : []
  if (!nodes.length) {
    throw new Error('Model did not return executable nodes')
  }

  const positionMap = buildAssistantNodePositionMap(nodes)
  const nodeIdMap: Record<string, string> = {}
  for (let i = 0; i < nodes.length; i++) {
    const spec = nodes[i]
    const ensuredNode = await ensureAssistantPlanNode(spec, i, nodes.length, positionMap)
    if (!ensuredNode?.id) {
      throw new Error('Failed to create node: ' + String(spec?.name || spec?.key || 'unknown'))
    }
    if (spec?.key) {
      nodeIdMap[String(spec.key)] = String(ensuredNode.id)
    }
  }

  for (const link of links) {
    const fromId = nodeIdMap[String(link?.from || '')]
    const toKey = String(link?.to || '')
    const toId = nodeIdMap[toKey] || (toKey === 'result_output' ? 'result_output' : '')
    if (!fromId || !toId) continue
    const connected = datasetDrag.value?.connectNodes?.(fromId, toId, {
      clearTargetIncoming: toId === 'result_output'
    })
    if (!connected) {
      throw new Error('Failed to connect link: ' + String(link?.from || '') + ' -> ' + String(link?.to || ''))
    }
  }

  const joinConfig = plan?.joinConfig
  if (joinConfig?.leftNode && joinConfig?.rightNode) {
    const applied = datasetDrag.value?.applyJoinConfig?.({
      leftNodeId: nodeIdMap[String(joinConfig.leftNode)],
      rightNodeId: nodeIdMap[String(joinConfig.rightNode)],
      joinType: normalizeAssistantJoinType(joinConfig.joinType),
      mappings: joinConfig.mappings || []
    })
    if (!applied) {
      throw new Error('Failed to apply join field mappings')
    }
  }

  addComplete()
  changeUpdate()
  updateAllfields()
  await nextTick()
  handleSelectPreviewNode({ id: 'result_output', type: 'result' })
}

const handleAiSend = async () => {
  const question = aiInputText.value.trim()
  if (!question || aiLoading.value) return
  aiMessages.value.push({ role: 'user', content: question })
  aiInputText.value = ''
  aiLoading.value = true

  try {
    await ensureAssistantDatasourceTables()
    const res = await copilotChat({
      question,
      msgType: 'dataset_canvas',
      engineType: 'volcengine',
      datasetGroupId: nodeInfo.id || undefined,
      chartData: {
        datasetName: datasetName.value,
        availableDatasets: getAssistantDatasetCandidates(),
        availableTables: getAssistantTableCandidates(),
        currentCanvas: datasetDrag.value?.getGraphState?.() || null,
        model: 'doubao-seed-2-0-code-preview-260215'
      }
    })

    const normalized = normalizeAssistantPlan(res)
    if (!normalized) {
      throw new Error(res?.errMsg || '鍔╂墜杩斿洖鍐呭鏃犳硶瑙ｆ瀽')
    }

    await executeAssistantCanvasPlan(normalized)
    aiMessages.value.push({
      role: 'assistant',
      content: normalized.reply || '已根据描述更新画布'
    })
  } catch (error) {
    console.error('handleAiSend error:', error)
    const message =
      error?.response?.data?.message ||
      error?.response?.data?.msg ||
      error?.response?.data?.errMsg ||
      error?.message ||
      '鏅鸿兘鍔╂墜鎵ц澶辫触'
    aiMessages.value.push({
      role: 'assistant',
      content: '执行失败：' + String(message || '')
    })
    ElMessage.error(message)
  } finally {
    aiLoading.value = false
  }
}

const resetDfsFields = (arr, idMap) => {
  for (let i in arr) {
    const id = guid()
    idMap[arr[i].currentDs.id] = id
    arr[i].currentDs.id = id
    if (!!arr[i].childrenDs?.length) {
      resetDfsFields(arr[i].childrenDs, idMap)
    }
  }
}

const resetAllfieldsId = arr => {
  const idMap = {}
  for (let i in allfields.value) {
    const id = guid()
    idMap[allfields.value[i].id] = id
    allfields.value[i].id = id
    allfields.value[i].datasetGroupId = ''
  }
  resetDfsFields(arr, idMap)
  return idMap
}

const resetAllfieldsUnionId = (arr, idMap) => {
  let strUnion = JSON.stringify(arr) as string
  let strNodeList = JSON.stringify(toRaw(datasetDrag.value.getNodeList())) as string
  let strAllfields = JSON.stringify(unref(allfields.value)) as string
  Object.entries(idMap).forEach(([key, value]) => {
    strUnion = strUnion.replaceAll(key, value as string)
    strAllfields = strAllfields.replaceAll(key, value as string)
    strNodeList = strNodeList.replaceAll(key, value as string)
  })
  allfields.value = JSON.parse(strAllfields)
  datasetDrag.value.initState(JSON.parse(strNodeList))
  return JSON.parse(strUnion)
}

/** 缁撴灉闆嗚惤鐩橈細鏀堕泦杩炲埌缁撴灉闆嗚妭鐐圭殑杈撳嚭閰嶇疆锛堝鎺掑簭锛夛紝渚涗繚瀛樻椂涓€骞舵彁浜?*/
const getResultOutputConfig = () => {
  const node = datasetDrag.value?.getResultInputNode?.()
  if (!node || (node as any).operationType !== 'sort') return {}
  const sortFields = buildSortFieldsForPreview(node, allfields.value)
  return sortFields.length ? { sortFields } : {}
}

const datasetSave = () => {
  if (nodeInfo.id) {
    editeSave()
    return
  }
  let union = []
  dfsNodeList(union, datasetDrag.value.getNodeList())
  const pid = appStore.getIsDataEaseBi ? embeddedStore.datasetPid : route.query.pid || nodeInfo.pid
  if (!union.length) {
    ElMessage.error('Dataset cannot be empty')
    return
  }
  if (nodeInfo.pid && !nodeInfo.id) {
    union = resetAllfieldsUnionId(union, resetAllfieldsId(union))
  }
  const tableIds = collectUnionTableIds(union)
  const allfieldsToSave =
    tableIds.size > 0
      ? allfields.value.filter(f => tableIds.has(String((f as any).datasetTableId)))
      : allfields.value
  const resultConfig = getResultOutputConfig()

  creatDsFolder.value.createInit(
    'dataset',
    {
      id: pid || '0',
      union,
      allfields: allfieldsToSave,
      graphState: datasetDrag.value?.getGraphState?.(),
      ...resultConfig
    },
    '',
    datasetName.value
  )
}
const datasetSaveAndBack = () => {
  willBack = true
  datasetSave()
}

const datasetPreviewLoading = ref(false)

/** dataset 寮曠敤鑺傜偣姝ｅ湪鍔犺浇瀛楁锛氶槻姝?handleDrop -> select-node 绔炴€佽Е鍙戦瑙堟椂 datasourceId 鏈氨缁?*/
const datasetNodeLoading = ref(false)

/** 鐢诲竷閫変腑鑺傜偣鍙樺寲锛氭洿鏂伴€変腑鎬佸苟绔嬪嵆鎸夎妭鐐瑰埛鏂伴瑙?
 * - 鏁版嵁琛?SQL 鑺傜偣锛氬彧棰勮璇ヨ〃
 * - 鎿嶄綔鑺傜偣锛氭寜璇ヨ妭鐐瑰洖婧瓙鏍戦瑙?
 * - 缁撴灉闆嗚妭鐐癸細浣跨敤鏁存５鍥鹃瑙?
 */
const handleSelectPreviewNode = (node: { id: string; type?: string } | null) => {
  selectedPreviewNodeId.value = node ? node.id : ''
  selectedPreviewNode.value = node
  // 浠绘剰鑺傜偣锛堝寘鎷粨鏋滈泦锛夌偣鍑绘椂锛屽疄鏃跺埛鏂伴瑙堬細
  // - 缁撴灉闆嗚妭鐐癸細棰勮鏁存５缁撴灉闆嗭紙鑱斿悎/鍏宠仈鍚庣殑鏁版嵁锛?
  // - 鍏跺畠鑺傜偣锛氭寜鑺傜偣绫诲瀷棰勮锛堣〃/SQL/鎿嶄綔锛?
  if (node) {
    datasetPreview()
  }
}

/** 鎸夐€変腑鐨勭敾甯冭妭鐐硅繃婊ら瑙堝垪锛氭湭閫夋垨閫夌粨鏋滈泦鏃舵樉绀哄叏閮紝鍚﹀垯鍙樉绀鸿鑺傜偣瀵瑰簲琛ㄧ殑瀛楁 */
const previewColumnsForDisplay = computed(() => {
  const fields = previewFieldsFull.value
  if (!fields.length) return []
  if (
    !selectedPreviewNodeId.value ||
    selectedPreviewNodeId.value === 'result_output' ||
    !['db', 'sql', 'dataset'].includes((selectedPreviewNode.value as any)?.type)
  ) {
    return generateColumns(fields)
  }
  const filtered = fields.filter(f => (f as any).datasetTableId === selectedPreviewNodeId.value)
  return generateColumns(filtered)
})

// 鍏煎澶氱杩斿洖缁撴瀯锛氬悗绔负 { data: { fields, data } }锛坓etPreviewData 宸插彇 res.data 鏃?res 鍗冲唴灞傦級
const getPreviewPayload = (res: any) => {
  if (!res) return { fields: [], data: [] }
  const inner = res?.data ?? res
  // 瀛楁鍙兘鍦?inner.fields锛屾垨鍦?inner.data.fields锛堝祵濂?data 鏃讹級
  const fields = (inner?.fields ?? inner?.data?.fields ?? res?.allFields ?? []) as Field[]
  const data = (Array.isArray(inner?.data)
    ? inner.data
    : Array.isArray(inner?.data?.data)
      ? inner.data.data
      : []) as Array<Record<string, unknown>>
  return { fields, data }
}

/** 鏈€杩戜竴娆￠瑙堣繑鍥炵殑瀹屾暣瀛楁鍒楄〃锛堝惈 datasetTableId锛夛紝鐢ㄤ簬鎸夎妭鐐硅繃婊?*/
const previewFieldsFull = ref<Array<Field & { datasetTableId?: string }>>([])

// 棰勮鍗曞厓鏍兼樉绀猴細瀵硅薄杞瓧绗︿覆锛岄暱鏂囨湰鎴柇锛岄伩鍏?[object Object] 鎴栨暣娈?JSON 鍗犳弧
const formatPreviewCell = (value: unknown): string => {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  const s = String(value)
  const maxLen = 200
  if (s.length <= maxLen) return s
  return s.slice(0, maxLen) + '...'
}

const previewDatasetReferenceNode = async (node: any) => {
  const datasetId = getReferenceDatasetId(node)
  if (!datasetId) return false
  datasetPreviewLoading.value = true
  try {
    const res = await getDatasetPreview(datasetId)
    const sourceFields = ((res as any)?.allFields || (res as any)?.data?.fields || []) as any[]
    if (!sourceFields.length) return false

    const datasourceId =
      node.datasourceId ||
      getDatasourceIdFromUnion((res as any)?.union || []) ||
      sourceFields.find((f: any) => !!f?.datasourceId)?.datasourceId ||
      ''

    const normalizedFields = sourceFields.map((f: any) =>
      normalizeField(
        {
          ...cloneDeep(f),
          datasetTableId: node.id,
          datasetGroupId: f.datasetGroupId || datasetId,
          checked: f.checked ?? true
        },
        node.id,
        datasourceId
      )
    ) as Array<Field & { datasetTableId?: string }>

    node.currentDsFields = normalizedFields
    if (datasourceId) node.datasourceId = datasourceId

    allfields.value = [
      ...allfields.value.filter((f: any) => (f as any).datasetTableId !== node.id),
      ...normalizedFields
    ]

    previewFieldsFull.value = normalizedFields
    columns.value = generateColumns(normalizedFields as Field[])
    tableData.value = ((res as any)?.data?.data || []) as Array<Record<string, any>>
    return true
  } catch (e) {
    console.error('previewDatasetReferenceNode error:', e)
    return false
  } finally {
    datasetPreviewLoading.value = false
  }
}

const loadRawFieldsForNode = async (node: any) => {
  const { datasourceId, id, info, tableName, type } = node || {}
  if (!datasourceId || !id || !tableName) return []
  try {
    const res = await getTableField({ datasourceId, id, info, tableName, type })
    const raw = (res || []) as any[]
    // 缁熶竴璧?normalizeField锛岃ˉ榻?id/datasetTableId/datasourceId 绛変俊鎭紝渚夸簬棰勮鍜屽悗缁瓫閫?
    const normalized = raw.map(f => normalizeField(cloneDeep(f), id, datasourceId || ''))
    // 灏嗗綋鍓嶈〃鐨勫師濮嬪瓧娈靛悎骞惰繘 allfields锛屼繚璇佺淮搴?鎸囨爣鏍戝湪鍒囨崲鑺傜偣鏃惰兘鏄剧ず璇ヨ〃瀛楁
    const hasTableFields = allfields.value.some(ele => (ele as any).datasetTableId === id)
    if (!hasTableFields && normalized.length) {
      allfields.value = [...allfields.value, ...normalized]
    }
    return normalized
  } catch (e) {
    console.error('loadRawFieldsForNode error:', e)
    return []
  }
}

const parseFieldsFromConfig = (val: any): string[] => {
  if (!val) return []
  if (Array.isArray(val)) return val.map(v => String(v).trim()).filter(Boolean)
  return String(val)
    .split(',')
    .map(v => v.trim())
    .filter(Boolean)
}

const resolveRowFieldKey = (
  f: string,
  fields: Array<Field & { datasetTableId?: string }>
): string => {
  const hit = fields.find(
    x =>
      (x as any).dataeaseName === f ||
      (x as any).originName === f ||
      (x as any).name === f ||
      String((x as any).id) === String(f)
  )
  return (hit as any)?.dataeaseName || (hit as any)?.originName || f
}

/** 浠庡疄闄呰鏁版嵁涓В鏋愭帓搴忕敤閿紙浠ョ涓€琛岀湡瀹?key 涓哄噯锛屽吋瀹?dataeaseName / originName锛?*/
const resolveSortKeyFromRow = (
  sortField: string,
  fields: Array<Field & { datasetTableId?: string }>,
  sampleRow: Record<string, any> | null
): string => {
  if (!sortField) return ''
  if (!sampleRow || typeof sampleRow !== 'object') return resolveRowFieldKey(sortField, fields)
  const rowKeys = Object.keys(sampleRow)
  if (!rowKeys.length) return resolveRowFieldKey(sortField, fields)
  // 1) 閰嶇疆鐨?sortField 涓庤 key 瀹屽叏涓€鑷?
  if (rowKeys.includes(sortField)) return sortField
  // 2) 閫氳繃 fields 鎵惧埌瀵瑰簲瀛楁锛岀敤鍏?dataeaseName/originName 鍦ㄨ閲屾壘
  const hit = fields.find(
    x =>
      (x as any).dataeaseName === sortField ||
      (x as any).originName === sortField ||
      (x as any).name === sortField ||
      String((x as any).id) === String(sortField)
  )
  if (hit) {
    const candidates = [(hit as any).dataeaseName, (hit as any).originName, (hit as any).name, sortField].filter(Boolean)
    const found = candidates.find(c => c && rowKeys.includes(c))
    if (found) return found
  }
  // 3) 鏃?fields 鏃讹細琛?key 浠?sortField 缁撳熬锛堝 xxx.dept_id銆乫_xxx 瀵瑰簲 originName锛?
  const bySuffix = rowKeys.find(k => k === sortField || k.endsWith('.' + sortField) || k.endsWith('_' + sortField))
  if (bySuffix) return bySuffix
  return resolveRowFieldKey(sortField, fields)
}

const compareValue = (a: any, b: any) => {
  if (a === b) return 0
  if (a === null || a === undefined) return -1
  if (b === null || b === undefined) return 1
  const an = Number(a)
  const bn = Number(b)
  if (!Number.isNaN(an) && !Number.isNaN(bn)) return an - bn
  return String(a).localeCompare(String(b))
}

const applyOperationPreview = (
  node: any,
  rows: Array<Record<string, any>>,
  fields: Array<Field & { datasetTableId?: string }>
) => {
  const cfg = node?.operationConfig || {}
  const op = node?.operationType
  if (!op) return rows

  if (op === 'sort') {
    if (!cfg.sortField || !rows.length) return rows
    const sampleRow = rows[0]
    const key = resolveSortKeyFromRow(cfg.sortField, fields, sampleRow)
    if (!key || !(key in sampleRow)) return rows
    const order = (cfg.sortOrder || 'asc') === 'desc' ? -1 : 1
    return [...rows].sort((r1, r2) => compareValue(r1[key], r2[key]) * order)
  }

  if (op === 'sample') {
    if (cfg.sampleType === 'percent') {
      const p = Math.max(1, Math.min(100, Number(cfg.samplePercent || 10)))
      const size = Math.max(1, Math.floor((rows.length * p) / 100))
      return rows.slice(0, size)
    }
    const size = Math.max(1, Number(cfg.sampleCount || 10))
    return rows.slice(0, size)
  }

  if (op === 'deduplicate') {
    const fieldsCfg = parseFieldsFromConfig(cfg.deduplicateFields)
    const keys = fieldsCfg.map(f => resolveRowFieldKey(f, fields))
    const keepLast = cfg.keepStrategy === 'last'
    const source = keepLast ? [...rows].reverse() : rows
    const seen = new Set<string>()
    const out: Array<Record<string, any>> = []
    for (const row of source) {
      const sign =
        keys.length > 0 ? JSON.stringify(keys.map(k => row[k])) : JSON.stringify(Object.values(row))
      if (seen.has(sign)) continue
      seen.add(sign)
      out.push(row)
    }
    return keepLast ? out.reverse() : out
  }

  if (op === 'group') {
    const groupFields = parseFieldsFromConfig(cfg.groupFields)
    const aggField = cfg.aggField ? resolveRowFieldKey(cfg.aggField, fields) : ''
    const aggType = cfg.aggType || 'sum'
    const groupKeys = groupFields.map(f => resolveRowFieldKey(f, fields))
    if (!groupKeys.length || !aggField) return rows
    const bucket = new Map<string, { base: Record<string, any>; values: number[] }>()
    for (const row of rows) {
      const gk = JSON.stringify(groupKeys.map(k => row[k]))
      if (!bucket.has(gk)) {
        const base: Record<string, any> = {}
        groupKeys.forEach(k => (base[k] = row[k]))
        bucket.set(gk, { base, values: [] })
      }
      const n = Number(row[aggField])
      if (!Number.isNaN(n)) bucket.get(gk)?.values.push(n)
    }
    const result: Array<Record<string, any>> = []
    bucket.forEach(({ base, values }) => {
      let agg = null
      if (aggType === 'count') agg = values.length
      else if (aggType === 'avg')
        agg = values.length ? values.reduce((a, b) => a + b, 0) / values.length : 0
      else if (aggType === 'max') agg = values.length ? Math.max(...values) : null
      else if (aggType === 'min') agg = values.length ? Math.min(...values) : null
      else agg = values.length ? values.reduce((a, b) => a + b, 0) : 0
      result.push({ ...base, [aggField]: agg })
    })
    return result
  }

  // join/union/transform/pivot/unpivot/selfloop/mirror 鏆備笉鏀瑰彉琛岄泦锛屼粎灞曠ず涓婃父鍘熷棰勮
  return rows
}

/** 涓烘帓搴忚妭鐐规瀯寤哄悗绔渶瑕佺殑 sortFields锛圤RDER BY锛夛紝鐢ㄤ簬棰勮鏃剁敱鏈嶅姟绔繑鍥炴帓搴忕粨鏋?*/
const buildSortFieldsForPreview = (
  node: any,
  fields: Array<Record<string, any>>
): Array<Record<string, any>> => {
  if (!node?.operationType || node.operationType !== 'sort') return []
  const cfg = node.operationConfig || {}
  const sortField = cfg.sortField
  if (!sortField) return []
  const hit = fields.find(
    f =>
      (f as any).dataeaseName === sortField ||
      (f as any).originName === sortField ||
      (f as any).name === sortField ||
      String((f as any).id) === String(sortField)
  )
  if (!hit) return []
  const orderDirection = (cfg.sortOrder || 'asc') === 'desc' ? 'desc' : 'asc'
  return [{ ...cloneDeep(hit), orderDirection }]
}

const datasetPreview = async () => {
  if (datasetPreviewLoading.value) return
  // 濡傛灉閫変腑浜?dataset 鑺傜偣浣嗗瓧娈靛皻鏈姞杞藉畬鎴愶紙handleLoadDatasetFields 寮傛鎵ц涓級锛岀洿鎺ヨ繑鍥?
  // 闃叉 handleDrop -> select-node 绔炴€侊細榧犳爣鐐瑰嚮瑙﹀彂棰勮鏃?datasourceId 灏氭湭灏辩华
  const selectedNode = selectedPreviewNode.value
  if (selectedNode?.type === 'dataset' && datasetNodeLoading.value) return
  const arr: any[] = []
  const nodeList = datasetDrag.value.getNodeList()
  let fieldsForRequest: any[] = allfields.value

  // 璁＄畻鈥滄湁鏁堥瑙堣妭鐐光€濓細
  // - 鏈€変腑鑺傜偣鎴栭€変腑缁撴灉闆?result)锛氱粨鏋滈泦棰勮浼樺厛澶嶇敤鈥滄暟鎹泦椤甸潰鈥濈殑棰勮閫昏緫锛岀‘淇濅袱杈逛竴鑷?
  // - 鑻ラ渶瑕佹湰鍦伴瑙堬紙鏂板缓鎴栨湁鏈繚瀛樹慨鏀癸級锛屽啀鏍规嵁涓婃父鍏崇郴鍐冲畾鏄惁璧拌仈鍚堣妭鐐归瑙堟垨鏁存５鍥?
  // - 閫変腑鍏蜂綋鑺傜偣锛氭寜鑺傜偣绫诲瀷棰勮锛堣〃鑺傜偣浠呴瑙堣琛紱鎿嶄綔鑺傜偣棰勮鍏朵笂娓歌緭鍏ュ苟搴旂敤鎿嶄綔锛?
  let effectiveNode: any = selectedPreviewNode.value
  const isResultSelected =
    !effectiveNode ||
    selectedPreviewNodeId.value === 'result_output' ||
    effectiveNode?.type === 'result'
  // 涓嶅啀鍦ㄧ紪杈戞€佷娇鐢?getDatasetPreview锛岀粺涓€璧?getPreviewData + 褰撳墠鐢诲竷 graphState锛屼繚璇佷繚瀛樺墠鍚庨瑙堜竴鑷?

  if (isResultSelected) {
    const resultInput = datasetDrag.value?.getResultInputNode?.()
    if (
      resultInput &&
      (resultInput as any).type === 'operation' &&
      // 缁撴灉闆嗙洿鎺ヨ繛鍒版搷浣滆妭鐐规椂锛氱粺涓€鐢卞悗绔繑鍥炴搷浣滃悗鐨勬渶缁堥瑙?
      ['union', 'deduplicate', 'sample', 'group'].includes((resultInput as any).operationType)
    ) {
      effectiveNode = resultInput
    } else {
      dfsNodeList(arr, nodeList)
      effectiveNode = null
    }
  }

  // 鑻ユ湁鏈夋晥棰勮鑺傜偣锛屽垯鏍规嵁鑺傜偣绫诲瀷鍐冲畾棰勮鑼冨洿锛?
  // - db/sql锛氫粎璇ヨ〃锛坈hildren 缃┖锛?
  // - operation锛氫互璇ユ搷浣滅殑涓婃父鏁版嵁鑺傜偣浣滀负杈撳叆锛屽啀鍦ㄦ湰鍦板簲鐢?operation锛堝 sample/group 绛夛級
  // - mirror锛氶瑙堟簮鑺傜偣鐨勬暟鎹紙闀滃儚鑺傜偣閫忔槑浼犻€掍笂娓告暟鎹級
  // 淇锛歯odeList 涓虹┖鏃讹紙鑺傜偣鏈繛鎺ュ埌缁撴灉闆嗭級锛岀洿鎺ョ敤 effectiveNode 鏋勫缓棰勮
  if (effectiveNode && effectiveNode.id !== 'result_output') {
    const node: any = effectiveNode
    if (node.type === 'mirror') {
      // 闀滃儚鑺傜偣锛氳В鏋愬埌婧愯妭鐐瑰苟棰勮
      const sourceNode = datasetDrag.value?.getMirrorSourceNode?.(node.id)
      if (sourceNode && (sourceNode.type === 'db' || sourceNode.type === 'sql')) {
        // 婧愯妭鐐规槸鏁版嵁琛細鐩存帴棰勮璇ヨ〃
        const isolatedNode = { ...cloneDeep(sourceNode), children: [] }
        dfsNodeList(arr, [isolatedNode])
        const tableId = sourceNode.id
        fieldsForRequest = allfields.value.filter(f => (f as any).datasetTableId === tableId)
        if (!fieldsForRequest.length) {
          const raw = await loadRawFieldsForNode(sourceNode)
          if (raw.length) {
            fieldsForRequest = raw
          }
        }
      } else if (sourceNode && sourceNode.type === 'operation') {
        // 婧愯妭鐐规槸鎿嶄綔鑺傜偣锛氱粺涓€鐢卞悗绔牴鎹?previewNodeId 杩斿洖鏈€缁堥瑙?
        const opType = (sourceNode as any).operationType
        if (opType === 'join') {
          // 鑱旀帴鑺傜偣锛氳幏鍙栦袱涓笂娓歌緭鍏ュ苟鎵ц JOIN
          const upstreams = datasetDrag.value?.getAllUpstreamNodes?.(sourceNode.id) || []
          const dataUpstreams = upstreams.filter((n: any) => n && (n.type === 'db' || n.type === 'sql'))
          if (dataUpstreams.length === 2) {
            const [root, child] = dataUpstreams
            const rootTree = { ...cloneDeep(root), children: [{ ...cloneDeep(child), children: [] }] }
            dfsNodeList(arr, [rootTree])
            const ids = new Set([root.id, child.id])
            fieldsForRequest = allfields.value.filter(f => ids.has((f as any).datasetTableId))
          } else {
            ElMessage.warning('鑱旀帴鑺傜偣闇€瑕佹伆濂借繛鎺ヤ袱涓暟鎹〃鑺傜偣')
            return
          }
        } else {
          const resolveDataNode = (n: any) => {
            if (!n) return null
            if (n.type === 'db' || n.type === 'sql') return n
            if (n.type === 'mirror') return datasetDrag.value?.getMirrorSourceNode?.(n.id) || null
            return datasetDrag.value?.getUpstreamDataNode?.(n.id) || null
          }
          let upstream = datasetDrag.value?.getUpstreamDataNode?.(sourceNode.id)
          let extraFields: any[] = []
          if (opType === 'union') {
            const upstreams = datasetDrag.value?.getUnionDirectUpstreams?.(sourceNode.id) || []
            if (upstreams.length !== 2) {
              ElMessage.warning('Union node requires exactly two input nodes')
              return
            }
            const leftNode = resolveDataNode(upstreams[0])
            const rightNode = resolveDataNode(upstreams[1])
            if (!leftNode || !rightNode) {
              ElMessage.warning('Both union inputs must resolve to data table nodes')
              return
            }
            upstream = leftNode
            extraFields = allfields.value.filter(f => (f as any).datasetTableId === rightNode.id)
            if (!extraFields.length) {
              extraFields = await loadRawFieldsForNode(rightNode)
            }
          }
          if (upstream) {
            const isolatedNode = { ...cloneDeep(upstream), children: [] }
            dfsNodeList(arr, [isolatedNode])
            const tableId = upstream.id
            fieldsForRequest = allfields.value.filter(f => (f as any).datasetTableId === tableId)
            if (!fieldsForRequest.length) {
              const raw = await loadRawFieldsForNode(upstream)
              if (raw.length) {
                fieldsForRequest = raw
              }
            }
            if (extraFields.length) {
              const seen = new Set(fieldsForRequest.map((f: any) => String(f.id)))
              fieldsForRequest = [
                ...fieldsForRequest,
                ...extraFields.filter((f: any) => !seen.has(String(f.id)))
              ]
            }
            effectiveNode = sourceNode
          } else {
            ElMessage.warning('Operation node is not connected to a data source')
            return
          }
        }
      } else {
        // 闀滃儚鑺傜偣鏈繛鎺ュ埌婧愯妭鐐规椂锛屾彁绀虹敤鎴?
        ElMessage.warning('Please connect a data or operation node to the mirror node first')
        return
      }
    } else if (node.type === 'db' || node.type === 'sql') {
      const isolatedNode = { ...cloneDeep(node), children: [] }
      dfsNodeList(arr, [isolatedNode])
      // 鍗曡〃/SQL 棰勮鏃讹紝浠呮惡甯﹁琛ㄧ浉鍏冲瓧娈碉紝閬垮厤寮曠敤鍏朵粬琛ㄧ殑瀛楁瀵艰嚧 SQL unknown column
      const tableId = node.id
      fieldsForRequest = allfields.value.filter(f => (f as any).datasetTableId === tableId)
      // 濡傛灉褰撳墠鑺傜偣杩樻病鏈変换浣曞瓧娈甸厤缃紝鑷姩鎸夆€滃師濮嬭〃瀛楁鈥濆仛涓€娆℃噿鍔犺浇棰勮
      if (!fieldsForRequest.length) {
        const raw = await loadRawFieldsForNode(node)
        if (raw.length) {
          fieldsForRequest = raw
        }
      }
    } else if (node.type === 'dataset') {
      const previewed = await previewDatasetReferenceNode(node)
      if (previewed) {
        return
      }

      // dataset 寮曠敤鑺傜偣锛氳妭鐐瑰凡鏈夊瓧娈碉紙handleLoadDatasetFields 鍔犺浇锛夛紝鐩存帴鐢ㄤ簬棰勮
      const isolatedNode = { ...cloneDeep(node), children: [] }
      dfsNodeList(arr, [isolatedNode])
      const tableId = node.id
      
      // 浼樺厛浠?allfields 涓幏鍙栬鑺傜偣鐨勫瓧娈?
      fieldsForRequest = allfields.value.filter(f => (f as any).datasetTableId === tableId)
      
      // 濡傛灉 allfields 涓病鏈夎鑺傜偣鐨勫瓧娈碉紝浣嗚妭鐐瑰凡鏈?currentDsFields锛屽垯浣跨敤鑺傜偣鐨勫瓧娈?
      if (!fieldsForRequest.length && node.currentDsFields?.length) {
        fieldsForRequest = (node.currentDsFields || []).map((f: any) =>
          normalizeField(cloneDeep(f), tableId, node.datasourceId || '')
        )
        // 鍚屾椂鍚屾鍒?allfields锛堜繚璇佸悗缁瑙堟椂鑳戒粠涓鍙栵級
        const existingIds = new Set(allfields.value.map((f: any) => f.id))
        const newFields = fieldsForRequest.filter((f: any) => !existingIds.has(f.id))
        if (newFields.length) {
          allfields.value = [...allfields.value, ...newFields]
        }
      }
      
      if (!fieldsForRequest.length) {
        // 寮曠敤鏁版嵁闆嗚妭鐐瑰瓧娈靛皻鏈氨缁垨涓虹┖鏃堕潤榛樿繑鍥烇紝閬垮厤鎷栧叆鐢诲竷鏃跺嚭鐜版墦鏂€ф彁绀?
        return
      }
    } else if (node.type === 'operation' && node.operationType === 'union') {
      // 鑱斿悎鑺傜偣棰勮缁熶竴璧板悗绔紝閬垮厤鍓嶇鍙岃姹傚悎骞跺鑷?total 涓庨〉闈㈢粨鏋滀笉涓€鑷?
      const upstreams = datasetDrag.value?.getUnionDirectUpstreams?.(node.id) || []
      if (upstreams.length !== 2) {
        ElMessage.warning('Union node requires exactly two input nodes')
        return
      }
      const resolveDataNode = (n: any) => {
        if (!n) return null
        if (n.type === 'db' || n.type === 'sql') return n
        return datasetDrag.value?.getUpstreamDataNode?.(n.id) || null
      }
      const dataNode0 = resolveDataNode(upstreams[0])
      const dataNode1 = resolveDataNode(upstreams[1])
      if (!dataNode0 || !dataNode1) {
        ElMessage.warning('Both union inputs must resolve to data table nodes')
        return
      }
      const isolatedNode = { ...cloneDeep(dataNode0), children: [] }
      dfsNodeList(arr, [isolatedNode])
      let fields0 = allfields.value.filter(f => (f as any).datasetTableId === dataNode0.id)
      if (!fields0.length) fields0 = await loadRawFieldsForNode(dataNode0)
      let fields1 = allfields.value.filter(f => (f as any).datasetTableId === dataNode1.id)
      if (!fields1.length) fields1 = await loadRawFieldsForNode(dataNode1)
      fieldsForRequest = [...fields0, ...fields1]
      effectiveNode = node
    } else if (node.type === 'operation' && node.operationType === 'join') {
      // 鑱旀帴鑺傜偣锛氱敱鍚庣鏍规嵁 union 缁撴瀯鐢熸垚 JOIN SQL 棰勮鑱旀帴鍚庣殑缁撴灉
      // 娉ㄦ剰锛氱敾甯冪殑 getNodeList() 浠モ€滅粨鏋滈泦鑺傜偣鈥濅负閿氱偣鏋勫缓 union 鏍戯紱
      // 鑻ヨ仈鎺ヨ妭鐐规湭杩炲埌缁撴灉闆嗭紝getNodeList() 鍙兘涓虹┖锛屽鑷撮瑙堜负绌恒€?
      // 鍥犳鑱旀帴鑺傜偣棰勮浠モ€滃綋鍓?join 鑺傜偣鈥濅负閿氱偣锛岀洿鎺ユ姄鍙栧叾涓ゅ紶涓婃父琛ㄦ瀯寤?union 鏍戙€?
      const ups = (datasetDrag.value?.getAllUpstreamNodes?.(node.id) || []).filter(
        (n: any) => n && (n.type === 'db' || n.type === 'sql')
      )
      if (ups.length !== 2) {
        ElMessage.warning('鑱旀帴鑺傜偣闇€瑕佹伆濂借繛鎺ヤ袱涓暟鎹〃鑺傜偣')
        return
      }
      const [a, b] = ups as any[]
      const aHas = Array.isArray(a.unionFields) && a.unionFields.length > 0
      const bHas = Array.isArray(b.unionFields) && b.unionFields.length > 0
      const root = aHas && !bHas ? b : bHas && !aHas ? a : a
      const child = root.id === a.id ? b : a

      const rootTree = { ...cloneDeep(root), children: [{ ...cloneDeep(child), children: [] }] }
      dfsNodeList(arr, [rootTree])

      // 浠呮惡甯﹁繖涓ゅ紶琛ㄧ浉鍏冲瓧娈碉紝閬垮厤寮曠敤鍏跺畠琛ㄥ瓧娈靛鑷?SQL unknown column
      const ids = new Set([root.id, child.id])
      fieldsForRequest = allfields.value.filter(f => ids.has((f as any).datasetTableId))
      effectiveNode = null
    } else if (node.type === 'operation') {
      // 鎿嶄綔鑺傜偣棰勮锛氬洖婧渶杩戜笂娓告暟鎹妭鐐逛綔涓洪瑙堣緭鍏?
      const upstream = datasetDrag.value?.getUpstreamDataNode?.(node.id)
      if (upstream) {
        const isolatedNode = { ...cloneDeep(upstream), children: [] }
        dfsNodeList(arr, [isolatedNode])
        const tableId = upstream.id
        fieldsForRequest = allfields.value.filter(f => (f as any).datasetTableId === tableId)
        if (!fieldsForRequest.length) {
          const raw = await loadRawFieldsForNode(upstream)
          if (raw.length) fieldsForRequest = raw
        }
      }
    } else {
      // 鍏朵粬绫诲瀷锛堝 result锛夎蛋鏁存５鍥?
      dfsNodeList(arr, nodeList)
    }
  } else {
    // 宸插湪涓婃柟 isResultSelected 鍒嗘敮澶勭悊鈥滅粨鏋滈泦/鏁存５鍥锯€濋瑙?
  }

  if (!arr.length) {
    columns.value = []
    tableData.value = []
    previewFieldsFull.value = []
    return
  }

  // 鑻ュ綋鍓嶉瑙堣寖鍥村唴娌℃湁浠讳綍瀛楁锛屽垯涓嶇粰鍚庣鍙戣姹傦細
  // - 琛ㄨ妭鐐癸紙db/sql锛夛細浼樺厛灏濊瘯鎳掑姞杞戒竴娆″師濮嬪瓧娈碉紱鑻ヤ粛鏃犲瓧娈靛垯闈欓粯杩斿洖
  // - 鎿嶄綔鑺傜偣绛夛細闈欓粯杩斿洖
  if (!fieldsForRequest.length) {
    if (effectiveNode && ['db', 'sql'].includes((effectiveNode as any).type)) {
      try {
        const raw = await loadRawFieldsForNode(effectiveNode as any)
        if (raw.length) {
          fieldsForRequest = raw
        } else {
          return
        }
      } catch (e) {
        console.error('loadRawFieldsForNode error:', e)
        return
      }
    } else {
      return
    }
  }

  // 鎺掑簭鑺傜偣锛氱敱鍚庣 ORDER BY 杩斿洖鎺掑簭缁撴灉锛屼笉鍐嶅墠绔帓搴?
  // - 鑻ュ綋鍓嶉€変腑鐨勬槸鎺掑簭鑺傜偣锛屽垯浼樺厛鎸夎鑺傜偣閰嶇疆鎺掑簭
  // - 鍚﹀垯锛岃嫢缁撴灉闆嗙洿鎺ヨ繛鍒颁簡鏌愪釜鎺掑簭鑺傜偣锛屽垯鎸夌粨鏋滈泦涓婃父鐨勬帓搴忚妭鐐归厤缃帓搴?
  let sortNode: any = null
  const selectedNodeAny: any = effectiveNode
  if (selectedNodeAny?.type === 'operation' && selectedNodeAny.operationType === 'sort') {
    sortNode = selectedNodeAny
  } else {
    const resultInput = datasetDrag.value?.getResultInputNode?.()
    if (
      resultInput &&
      (resultInput as any).type === 'operation' &&
      (resultInput as any).operationType === 'sort'
    ) {
      sortNode = resultInput
    }
  }
  const sortFieldsForRequest =
    sortNode && sortNode.operationType === 'sort'
      ? buildSortFieldsForPreview(sortNode, fieldsForRequest)
      : []
  const previewNodeIdForRequest =
    effectiveNode &&
    (effectiveNode as any).type === 'operation' &&
    ['union', 'sample', 'deduplicate', 'group'].includes((effectiveNode as any).operationType)
      ? (effectiveNode as any).id
      : undefined

  datasetPreviewLoading.value = true
  try {
    const reqBody: Record<string, any> = {
      union: arr,
      allFields: fieldsForRequest,
      // 鎼哄甫褰撳墠鐢诲竷鐨?graphState锛岃鍚庣鍦ㄩ瑙堟椂涔熷簲鐢ㄦ娊鏍?鎺掑簭绛夌粨鏋滈泦鎿嶄綔锛?
      // 淇濊瘉缂栬緫鎬佷笌鏈€缁堟暟鎹泦璇箟涓€鑷达紙鍚屾椂鏀寔鏈繚瀛樼殑鏈€鏂扮紪鎺掞級銆?
      graphState: datasetDrag.value?.getGraphState?.() || null,
      // 鑻ユ槸宸插瓨鍦ㄧ殑鏁版嵁闆嗭紝浼犲叆 id锛屼究浜庡悗绔湪棰勮鏃跺簲鐢ㄤ笌鏁版嵁闆嗛〉闈㈢浉鍚岀殑鏉冮檺/鎶芥牱绛夐€昏緫
      id: nodeInfo.id || undefined
    }
    if (sortFieldsForRequest.length) reqBody.sortFields = sortFieldsForRequest
    if (previewNodeIdForRequest) reqBody.previewNodeId = previewNodeIdForRequest
    const res = await getPreviewData(reqBody)
    // 妫€鏌ヨ繑鍥炵粨鏋滄槸鍚︽槸閿欒
    if (res?.code && res.code !== 0 && res.code !== 200) {
      ElMessage.error(res.msg || '棰勮鏁版嵁澶辫触')
      tableData.value = []
      columns.value = []
      previewFieldsFull.value = []
      return
    }
    const payload = getPreviewPayload(res)
    const rawFields = (payload.fields || []) as Array<Field & { datasetTableId?: string }>
    const withTableId = rawFields.map(f => {
      if (f.datasetTableId) return f
      const fromAll = allfields.value.find(
        af =>
          (af as any).id === (f as any).id || (af as any).dataeaseName === (f as any).dataeaseName
      )
      return { ...f, datasetTableId: (fromAll as any)?.datasetTableId }
    })
    previewFieldsFull.value = withTableId
    columns.value = generateColumns(withTableId)
    let previewRows = (payload.data || []) as Array<Record<string, any>>
    if ((effectiveNode as any)?.type === 'operation') {
      if (
        previewNodeIdForRequest ||
        (sortNode?.operationType === 'sort' && sortFieldsForRequest.length)
      ) {
        // 宸茶蛋鏈嶅姟绔帓搴忥紝鏃犻渶鍓嶇鍐嶆帓
      } else {
        previewRows = applyOperationPreview(effectiveNode, previewRows, withTableId)
      }
    }
    tableData.value = previewRows
  } finally {
    datasetPreviewLoading.value = false
  }
}

const dfsNodeList = (arr, list) => {
  list.forEach(ele => {
    const childrenDs = []
    if (ele.children?.length) {
      dfsNodeList(childrenDs, ele.children)
    }
    const {
      tableName,
      type,
      datasourceId,
      id,
      info,
      unionType,
      unionFields,
      currentDsFields,
      sqlVariableDetails,
      datasetId
    } = ele
    const normalizedDatasourceId =
      type === 'dataset'
        ? datasourceId || (currentDsFields || []).find((f: any) => !!f?.datasourceId)?.datasourceId || ''
        : datasourceId || ''
    // 淇濆瓨鍓嶈鑼冨寲瀛楁锛岀‘淇濇瘡涓瓧娈甸兘鏈?id锛岄伩鍏嶄繚瀛樺悗瀛楁 ID 涓㈠け
    const normalizedFields = (currentDsFields || []).map(f =>
      normalizeField(cloneDeep(f), id, normalizedDatasourceId)
    )
    arr.push({
      currentDs: {
        sqlVariableDetails,
        tableName,
        type,
        datasourceId: normalizedDatasourceId,
        id,
        info,
        datasetGroupId: datasetId
      },
      currentDsFields: normalizedFields,
      childrenDs,
      unionToParent: {
        unionType,
        unionFields
      }
    })
  })
}

/** 浠庡凡鏋勫缓鐨?union 鏍戯紙dfsNodeList 浜у嚭锛変腑鏀堕泦鎵€鏈夎〃鑺傜偣 id锛岀敤浜庝繚瀛樻椂鍙彁浜よ繖浜涜〃瀵瑰簲鐨勫瓧娈碉紝閬垮厤 SQL 寮曠敤涓嶅瓨鍦ㄧ殑鍒?*/
const collectUnionTableIds = (unionArr: any[]): Set<string> => {
  const ids = new Set<string>()
  const walk = (list: any[]) => {
    if (!list || !Array.isArray(list)) return
    list.forEach(item => {
      const id = item?.currentDs?.id
      if (id) ids.add(String(id))
      walk(item?.childrenDs || [])
    })
  }
  walk(unionArr)
  return ids
}

const quotaTable = shallowRef()
const dimensionsTable = shallowRef()

const dimensionsSelection = ref([])
const quotaSelection = ref([])

const deTypeSelection = ref([])
const fieldSelection = ref([])

const showCascaderBatch = computed(() => {
  return !!deTypeSelection.value.length && Array.from(new Set(deTypeSelection.value)).length === 1
})

/** 褰撳墠閫変腑鐨勫彲鍒嗙粍缁村害瀛楁锛堜粎褰撳湪鎵归噺绠＄悊涓€変腑涓€涓?鏂囨湰/鏃堕棿/鍦扮悊浣嶇疆 缁村害涓斾负鍘熷瀛楁鏃舵湁鏁堬紝鐢ㄤ簬銆屾柊寤哄垎缁勫瓧娈点€嶆寜閽級 */
const selectedGroupableFieldForButton = computed(() => {
  const sel = fieldSelection.value || []
  console.log('>>> [computed] selectedGroupableFieldForButton - fieldSelection length:', sel.length)
  if (sel.length !== 1) {
    console.log('>>> [computed] selectedGroupableFieldForButton - sel.length !== 1, returning null')
    return null
  }
  const f = sel[0]
  console.log('>>> [computed] selectedGroupableFieldForButton - f.groupType:', f.groupType, 'f.extField:', f.extField, 'f.deType:', f.deType)
  if (
    f.groupType !== 'd' ||
    f.extField !== 0 ||
    !GROUPABLE_DIMENSION_TYPES.includes(f.deType)
  ) {
    console.log('>>> [computed] selectedGroupableFieldForButton - conditions not met, returning null')
    return null
  }
  console.log('>>> [computed] selectedGroupableFieldForButton - returning field')
  return f
})

const clearSelection = () => {
  dimensionsTable.value.clearSelection()
  quotaTable.value.clearSelection()
}
const deTypeArr = ref([])

const setDeTypeSelection = () => {
  fieldSelection.value = [
    ...dimensionsTable.value.getSelectionRows(),
    ...quotaTable.value.getSelectionRows()
  ]
  deTypeSelection.value = fieldSelection.value.map(ele => ele.deExtractType)
  let deTypes = fieldSelection.value.map(ele => ele.deType)
  const [obj] = fieldSelection.value
  nextTick(() => {
    dimensionsSelection.value = dimensionsTable.value.getSelectionRows().map(ele => ele.id)
    quotaSelection.value = quotaTable.value.getSelectionRows().map(ele => ele.id)
  })
  if (Array.from(new Set(deTypes)).length !== 1) {
    deTypeArr.value = []
    return
  }
  deTypeArr.value =
    obj.deType === 1 && obj.deExtractType === 0 ? [1, obj.dateFormatType] : [obj.deType]
}

let oldArrValue = []

const cascaderChangeArr = val => {
  const [deType, dateFormat] = val
  dimensionsSelection.value = dimensionsTable.value.getSelectionRows().map(ele => ele.id)
  quotaSelection.value = quotaTable.value.getSelectionRows().map(ele => ele.id)

  const arr = [...quotaSelection.value, ...dimensionsSelection.value]
  if (dateFormat === 'custom') {
    const [obj] = allfields.value.filter(ele => arr.includes(ele.id))
    oldArrValue = obj.deType === 1 ? [1, obj.dateFormatType] : [obj.deType]
    currentField.value.id = ''
    currentField.value.idArr = [...arr]
    currentField.value.dateFormat = ''
    currentField.value.dateFormatType = dateFormat
    updateCustomTime.value = true
    recoverSelection()
    return
  }
  allfields.value.forEach(ele => {
    if (arr.includes(ele.id)) {
      ele.deType = deType
      ele.dateFormat = deType === 1 ? dateFormat : ''
      ele.dateFormatType = deType === 1 ? dateFormat : ''
      ele.deTypeArr = deType === 1 && ele.deExtractType === 0 ? [deType, dateFormat] : [deType]
    }
  })
  recoverSelection()
}
const recoverSelection = () => {
  nextTick(() => {
    quota.value.forEach(ele => {
      if (quotaSelection.value.includes(ele.id)) {
        quotaTable.value.toggleRowSelection(ele, true)
      }
    })
    dimensions.value.forEach(ele => {
      if (dimensionsSelection.value.includes(ele.id)) {
        dimensionsTable.value.toggleRowSelection(ele, true)
      }
    })
  })
}

const dragEnd = () => {
  maskShow.value = false
}

const cascaderChange = (row, val) => {
  const [deType, dateFormat] = val
  if (dateFormat === 'custom') {
    oldArrValue = row.deType === 1 ? [1, row.dateFormatType] : [row.deType]
    currentField.value.id = row.id
    updateCustomTime.value = true
    return
  }
  row.deType = deType
  row.dateFormat = deType === 1 ? dateFormat : ''
  row.dateFormatType = deType === 1 ? dateFormat : ''
}

const dfsUnion = (arr, list) => {
  list.forEach(ele => {
    const children = []
    if (ele.childrenDs?.length) {
      dfsUnion(children, ele.childrenDs)
    }
    const { unionToParent, currentDsFields, currentDs } = ele
    const { tableName, type, datasourceId, id, info, sqlVariableDetails } = currentDs || {}
    const { unionType, unionFields } = unionToParent || {}
    arr.push({
      sqlVariableDetails,
      tableName,
      type,
      datasourceId,
      id,
      info,
      currentDsFields,
      children,
      unionType,
      unionFields
    })
  })
}
const handleClick = () => {
  showInput.value = true
  nextTick(() => {
    editerName.value.focus()
  })
}

const finish = res => {
  syncSavedDatasetMeta(res)
  const { id, pid, name } = res
  datasetName.value = name
  nodeInfo = {
    id,
    pid,
    name
  }
  allfields.value = res.allFields || []
  refreshDatasetPanel(res)
  // 淇濆瓨鎴愬姛鍚庯紝绛夊緟鏁版嵁瀹屽叏淇濆瓨鍜屽瓧娈垫洿鏂板悗锛屽埛鏂伴瑙堜负鑱旀帴缁撴灉
  setTimeout(() => {
    handleSelectPreviewNode({ id: 'result_output', type: 'result' })
  }, 500)
}

const errorTips = ref('')

const handleDatasetName = () => {
  errorTips.value = ''
  if (!datasetName.value.trim()) {
    errorTips.value = t('commons.input_content')
  }

  if (datasetName.value.trim().length < 2) {
    errorTips.value = t('datasource.input_limit_2_25', [2, 64])
  }
  showInput.value = !!errorTips.value
}

const treeProps = {
  children: 'children',
  label: 'name',
  disabled: data => {
    return (!data.children?.length && !data.leaf) || data.extraFlag < 0
  }
}

const pluginDs = ref([])
const loadDsPlugin = data => {
  pluginDs.value = data
}
const getDsIcon = data => {
  if (pluginDs?.value.length === 0) return null
  if (!data.leaf) return null

  const arr = pluginDs.value.filter(ele => {
    return ele.type === data.type
  })
  return arr && arr.length > 0 ? arr[0].icon : null
}

const getDsIconName = data => {
  if (!data.leaf) return 'dv-folder'
  return String(data.type) + '-ds'
}
</script>

<template>
  <div class="de-dataset-form" v-loading="loading">
    <div class="top">
      <span class="name">
        <el-icon @click="backToMain">
          <Icon name="icon_left_outlined"></Icon>
        </el-icon>
        <template v-if="showInput">
          <el-input
            maxlength="64"
            ref="editerName"
            v-model="datasetName"
            @blur="handleDatasetName"
          />
          <div class="ed-form-item__error" v-if="errorTips">{{ errorTips }}</div>
        </template>
        <template v-else>
          <span @click="handleClick" class="dataset-name ellipsis" style="margin-left: 12px">{{
            datasetName
          }}</span>
        </template>
      </span>
      <div class="workspace-top-tabs">
        <div
          class="workspace-editor-tab"
          :class="{ active: workspaceActiveTab === 'current' }"
          @click="activateWorkspaceTab('current')"
        >
          <span class="workspace-editor-tab-title" :title="currentWorkspaceTitle">{{ currentWorkspaceTitle }}</span>
        </div>
        <div
          v-for="tab in workspaceTabs"
          :key="tab.key"
          class="workspace-editor-tab"
          :class="{ active: workspaceActiveTab === tab.key }"
          @click="activateWorkspaceTab(tab.key)"
        >
          <span class="workspace-editor-tab-title" :title="tab.title">{{ tab.title }}</span>
          <span class="workspace-editor-tab-close" @click.stop="closeWorkspaceDatasetTab(tab.key)">x</span>
        </div>
      </div>
      <span class="oprate">
        <el-button class="ai-entry-btn" :disabled="showInput" @click="showAiPanel = !showAiPanel">
          <template #icon>
            <el-icon class="ai-entry-icon">
              <Icon name="icon_viewinchat_outlined"></Icon>
            </el-icon>
          </template>
          {{ showAiPanel ? '收起助手' : '智能助手' }}
        </el-button>
        <el-button :disabled="showInput" type="primary" @click="datasetSaveAndBack">
          保存并返回
        </el-button>
        <el-button :disabled="showInput" type="primary" @click="datasetSave">保存</el-button>
      </span>
    </div>
    <div class="container dataset-db" @mouseup="mouseupDrag">
      <p v-show="!showLeft" class="arrow-right" @click="showLeft = true">
        <el-icon>
          <Icon name="icon_right_outlined"></Icon>
        </el-icon>
      </p>
      <div
        v-show="showLeft"
        :style="{ left: LeftWidth + 'px' }"
        class="drag-left"
        :class="isDragging && 'is-dragging'"
        @mousedown="mousedownDrag"
      />
      <div
        v-loading="dsLoading || state.datasetLoading"
        v-show="showLeft"
        class="table-list"
        :style="{ width: LeftWidth + 'px' }"
      >
        <div class="table-list-top">
          <p class="select-ds">
            {{ t('auth.dataset') }} / {{ t('auth.datasource') }}
            <span class="left-outlined">
              <el-icon style="color: #1f2329" @click="showLeft = false">
                <Icon name="icon_left_outlined" />
              </el-icon>
            </span>
          </p>

          <!-- 鍙傝€冨浘甯冨眬锛氬乏鍙充袱鍒?-->
          <div class="panel-two-col">
            <!-- 宸﹀垪锛氭垜鐨勬暟鎹泦 -->
            <div class="panel-col">
              <div class="panel-col-header">
                <span class="panel-col-title">{{ t('auth.dataset') }}</span>
                <el-dropdown trigger="click" @command="panelDatasetSortChange">
                  <el-icon class="panel-filter-icon">
                    <el-tooltip :offset="16" effect="dark" :content="datasetPanelSortTip" placement="top">
                      <Icon
                        v-if="panelDatasetSortType.includes('asc')"
                        name="dv-sort-asc"
                        class="panel-sort-icon"
                      />
                    </el-tooltip>
                    <el-tooltip :offset="16" effect="dark" :content="datasetPanelSortTip" placement="top">
                      <Icon
                        v-show="panelDatasetSortType.includes('desc')"
                        name="dv-sort-desc"
                        class="panel-sort-icon"
                      />
                    </el-tooltip>
                  </el-icon>
                  <template #dropdown>
                    <el-dropdown-menu style="width: 200px">
                      <template :key="ele.value" v-for="ele in datasetPanelSortList">
                        <el-dropdown-item
                          class="ed-select-dropdown__item"
                          :class="ele.value === panelDatasetSortType && 'selected'"
                          :command="ele.value"
                        >
                          {{ ele.name }}
                        </el-dropdown-item>
                        <li v-if="ele.divided" class="ed-dropdown-menu__item--divided"></li>
                      </template>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <el-input
                v-model="panelSearchKeyword"
                clearable
                class="panel-col-search"
                :placeholder="t('commons.search')"
              >
                <template #prefix>
                  <el-icon>
                    <Icon name="icon_search-outline_outlined" />
                  </el-icon>
                </template>
              </el-input>
              <div class="panel-col-body">
                <div v-if="state.datasetLoading" class="dataset-loading-inline">
                  <el-icon class="is-loading"><Icon name="icon_loading_outlined" /></el-icon>
                  <span>鍔犺浇涓?..</span>
                </div>
                <el-tree
                  v-else
                  ref="panelDatasetTreeRef"
                  class="form-dataset-tree"
                  node-key="id"
                  highlight-current
                  expand-on-click-node
                  default-expand-all
                  :data="state.datasetList"
                  :props="datasetTreeDefaultProps"
                  :filter-node-method="panelFilterNode"
                >
                  <template #default="{ node, data }">
                    <span
                      class="custom-tree-node form-left-tree-node"
                      :draggable="isDatasetTreeLeaf(data)"
                      @dragstart="e => isDatasetTreeLeaf(data) && datasetDragStart(e, data)"
                      @dragend="maskShow = false"
                    >
                      <el-icon v-if="!isDatasetTreeLeaf(data)" style="font-size: 18px">
                        <Icon name="dv-folder" />
                      </el-icon>
                      <el-icon v-else style="font-size: 18px">
                        <Icon name="icon_dataset" />
                      </el-icon>
                      <span :title="node.label" class="label-tooltip ellipsis">{{ node.label }}</span>
                    </span>
                  </template>
                </el-tree>
                <div
                  v-if="!state.datasetLoading && !state.datasetList?.length"
                  class="empty-tip"
                >
                  鏆傛棤鍙紩鐢ㄧ殑鏁版嵁闆?
                </div>
              </div>
            </div>

            <!-- 鍙冲垪锛氭暟鎹簮 -->
            <div class="panel-col">
              <div class="panel-col-header">
                <span class="panel-col-title">{{ t('auth.datasource') }}</span>
              </div>
              <el-input
                v-model="panelDatasourceSearch"
                clearable
                class="panel-col-search"
                :placeholder="t('commons.search')"
              >
                <template #prefix>
                  <el-icon>
                    <Icon name="icon_search-outline_outlined" />
                  </el-icon>
                </template>
              </el-input>
              <div class="panel-col-body">
                <el-tree
                  ref="panelDatasourceTreeRef"
                  class="form-datasource-tree"
                  node-key="id"
                  highlight-current
                  default-expand-all
                  :default-expanded-keys="datasourceExpandedKeys"
                  :data="state.dataSourceList"
                  :props="{
                    label: 'name',
                    children: 'children'
                  }"
                  :filter-node-method="panelDatasourceFilterNode"
                  @node-click="(data, node) => handleDsTreeNodeClick(data, node)"
                >
                  <template #default="{ data }">
                    <span
                      class="custom-tree-node form-left-tree-node"
                      :draggable="!!data.leaf"
                      @dragstart="e => data.leaf && dragstart(e, data)"
                      @dragend="dragEnd"
                    >
                      <el-icon style="font-size: 18px">
                        <Icon :name="data.leaf ? 'reference-table' : 'dv-folder'" />
                      </el-icon>
                      <span class="label-tooltip ellipsis" :title="data.name">{{ data.name }}</span>
                    </span>
                  </template>
                </el-tree>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div
        class="drag-right"
        :class="{ 'with-ai-panel': showAiPanel }"
        :style="{ width: `calc(100vw - ${showLeft ? LeftWidth : 0}px)` }"
      >
        <operation-toolbar @drag-start="handleToolbarDragStart" @drag-end="handleToolbarDragEnd" />
        <div v-show="showAiPanel" class="ai-assistant-panel">
          <div class="ai-assistant-header">
            <div class="ai-assistant-brand">
              <div class="ai-assistant-badge">
                <el-icon>
                  <Icon name="icon_viewinchat_outlined"></Icon>
                </el-icon>
              </div>
              <div>
                <div class="ai-assistant-title">智能助手</div>
                <div class="ai-assistant-tip">用自然语言描述你想在画布上完成的数据集编排</div>
              </div>
            </div>
            <el-button text @click="showAiPanel = false">鏀惰捣</el-button>
          </div>
          <div ref="aiPanelBody" class="ai-assistant-body">
            <div v-if="!aiMessages.length" class="ai-assistant-empty">
              渚嬪锛氭妸鈥滈暅鍍?鈥濆拰鈥滆仈鍚堟祴璇曗€濋€氳繃 `id` 瀛楁宸﹁繛鎺ワ紝骞惰緭鍑哄埌缁撴灉闆?            </div>
            <div
              v-for="(msg, idx) in aiMessages"
              :key="`${msg.role}-${idx}`"
              class="ai-message"
              :class="msg.role"
            >
              <div class="ai-message-role">{{ msg.role === 'user' ? '我' : '助手' }}</div>
              <div class="ai-message-content">{{ msg.content }}</div>
            </div>
            <div v-if="aiLoading" class="ai-message assistant loading">
              <div class="ai-message-role">鍔╂墜</div>
              <div class="ai-message-content">姝ｅ湪鐢熸垚骞跺簲鐢ㄧ敾甯冪紪鎺掕鍒?..</div>
            </div>
          </div>
          <div class="ai-assistant-input">
            <el-input
              v-model="aiInputText"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="请输入自然语言描述，例如：将数据集A和数据集B按 user_id 左连接"
              @keydown.enter.exact.prevent="handleAiSend"
            />
            <div class="ai-assistant-actions">
              <el-button @click="aiInputText = ''">娓呯┖</el-button>
              <el-button type="primary" :loading="aiLoading" @click="handleAiSend">发送</el-button>
            </div>
          </div>
        </div>
        <div v-if="crossDatasources" class="different-datasource">
          <el-icon>
            <Icon name="icon_warning_colorful"></Icon>
          </el-icon>
          鎮ㄦ鍦ㄨ繘琛岃法鏁版嵁婧愮殑琛ㄥ叧鑱?璇风‘淇濅娇鐢╟alcite鐨勬爣鍑嗚娉曞拰鍑芥暟,鍚﹀垯浼氬鑷存暟鎹泦鎶ラ敊
        </div>
        <dataset-union
          @join-editor="joinEditor"
          @changeUpdate="changeUpdate"
          @edit-node="handleEditNode"
          @refresh-node="handleRefreshNode"
          @copy-node="handleCopyNode"
          @select-node="handleSelectPreviewNode"
          @load-dataset-fields="handleLoadDatasetFields"
          @edit-dataset="handleEditDatasetNode"
          :maskShow="maskShow"
          :dragHeight="dragHeight"
          :getDsName="getDsName"
          :offsetX="offsetX"
          :offsetY="offsetY"
          ref="datasetDrag"
          @updateAllfields="updateAllfields"
          @addComplete="addComplete"
        ></dataset-union>
        <div
          class="sql-result"
        >
          <div class="sql-title">
            <span class="drag" @mousedown="mousedownDragH" />
            <div class="field-data">
              <el-button :disabled="!allfields.length" @click="addCalcField('q')" secondary>
                <template #icon>
                  <el-icon>
                    <Icon name="icon_add_outlined"></Icon>
                  </el-icon>
                </template>
                {{ t('dataset.add_calc_field') }}
              </el-button>
              <el-button
                :disabled="!allfields.length || !selectedGroupableFieldForButton"
                :title="selectedGroupableFieldForButton ? '' : t('dataset.please_select_groupable_field')"
                @click="openGroupFieldDialog"
                secondary
              >
                <template #icon>
                  <el-icon>
                    <Icon name="icon_add_outlined"></Icon>
                  </el-icon>
                </template>
                {{ t('dataset.add_group_field') }}
              </el-button>
              <el-button
                style="min-width: 70px"
                :disabled="!allfields.length"
                v-loading="datasetPreviewLoading"
                @click="datasetPreview"
                secondary
              >
                <template #icon>
                  <el-icon>
                    <Icon name="icon_refresh_outlined"></Icon>
                  </el-icon>
                </template>
                鍒锋柊鏁版嵁
              </el-button>
            </div>
          </div>
          <el-tabs class="padding-24" v-model="tabActive" @tab-change="tabChange">
            <el-tab-pane :label="t('chart.data_preview')" name="preview" />
            <el-tab-pane :label="t('dataset.batch_manage')" name="manage" />
          </el-tabs>
          <div v-show="tabActive === 'preview' && !!allfields.length" class="table-preview">
            <div class="preview-field">
              <div :class="['field-d', { open: expandedD }]">
                <div :class="['title', { expanded: expandedD }]" @click="expandedD = !expandedD">
                  <ElIcon class="expand">
                    <Icon name="icon_expand-right_filled"></Icon>
                  </ElIcon>
                  &nbsp;{{ t('chart.dimension') }}
                </div>
                <el-tree v-if="expandedD" :data="dimensions" :props="defaultProps">
                  <template #default="{ data }">
                    <span class="custom-tree-node father">
                      <el-icon>
                        <Icon
                          :name="`field_${fieldType[data.deType]}`"
                          :className="`field-icon-${
                            fieldType[[2, 3].includes(data.deType) ? 2 : 0]
                          }`"
                        ></Icon>
                      </el-icon>
                      <span :title="data.name" class="label-tooltip">{{ data.name }}</span>
                      <div class="operate child">
                        <field-more
                          :extField="data.extField"
                          trans-type="转换为指标"
                          :show-time="data.deExtractType === 0"
                          @handle-command="type => handleFieldMore(data, type)"
                        ></field-more>
                      </div>
                    </span>
                  </template>
                </el-tree>
              </div>
              <div :class="['field-q', { open: expandedQ }]">
                <div :class="['title', { expanded: expandedQ }]" @click="expandedQ = !expandedQ">
                  <ElIcon class="expand">
                    <Icon name="icon_expand-right_filled"></Icon>
                  </ElIcon>
                  &nbsp;{{ t('chart.quota') }}
                </div>
                <el-tree v-if="expandedQ" :data="quota" :props="defaultProps">
                  <template #default="{ data }">
                    <span class="custom-tree-node father">
                      <el-icon>
                        <Icon
                          :name="`field_${fieldType[data.deType]}`"
                          :className="`field-icon-${
                            fieldType[[2, 3].includes(data.deType) ? 2 : 0]
                          }`"
                        ></Icon>
                      </el-icon>
                      <span :title="data.name" class="label-tooltip">{{ data.name }}</span>
                      <div class="operate child">
                        <field-more
                          trans-type="转换为维度"
                          typeColor="green-color"
                          :show-time="data.deExtractType === 0"
                          :extField="data.extField"
                          @handle-command="type => handleFieldMore(data, type)"
                        ></field-more>
                      </div>
                    </span>
                  </template>
                </el-tree>
              </div>
            </div>
            <div class="preview-data">
              <el-table
                v-loading="datasetPreviewLoading"
                header-class="header-cell"
                :data="tableData"
                border
                style="width: 100%; height: 100%"
              >
                <el-table-column
                  :key="column.dataKey + column.deType"
                  v-for="(column, index) in previewColumnsForDisplay"
                  :prop="column.dataKey"
                  :label="column.title"
                  :width="previewColumnsForDisplay.length - 1 === index ? 150 : 'auto'"
                  :fixed="previewColumnsForDisplay.length - 1 === index ? 'right' : false"
                >
                  <template #header>
                    <el-dropdown trigger="click" @command="cmd => onPreviewColumnCommand(column, cmd)" :hide-timeout="200">
                      <div
                        class="flex-align-center preview-header-cell"
                        :class="{ 'can-group': canOpenGroupForColumn(column) }"
                      >
                        <ElIcon style="margin-right: 6px">
                          <Icon
                            :name="`field_${fieldType[column.deType]}`"
                            :className="`field-icon-${fieldType[column.deType]}`"
                          ></Icon>
                        </ElIcon>
                        <span class="ellipsis" :title="column.title" style="width: 120px">
                          {{ column.title }}
                        </span>
                      </div>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="group" v-if="canOpenGroupForColumn(column)">
                            <el-icon><Icon name="icon_add_outlined"></Icon></el-icon>
                            {{ t('dataset.add_group_field') }}
                          </el-dropdown-item>
                          <el-dropdown-item command="fill_null">
                            <el-icon><Icon name="icon_edit_outlined"></Icon></el-icon>
                            {{ t('dataset.fill_null_field') }}
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </template>
                  <template #default="{ row }">
                    <el-tooltip
                      v-if="
                        row[column.dataKey] !== null &&
                        row[column.dataKey] !== undefined &&
                        (typeof row[column.dataKey] === 'object' ||
                          String(row[column.dataKey]).length > 200)
                      "
                      :content="
                        typeof row[column.dataKey] === 'object'
                          ? JSON.stringify(row[column.dataKey])
                          : String(row[column.dataKey])
                      "
                      placement="top"
                      :show-after="300"
                    >
                      <span class="preview-cell-text">{{
                        formatPreviewCell(row[column.dataKey])
                      }}</span>
                    </el-tooltip>
                    <span v-else class="preview-cell-text">{{
                      formatPreviewCell(row[column.dataKey])
                    }}</span>
                  </template>
                </el-table-column>
                <template #empty>
                  <empty-background description="鏆傛棤鏁版嵁" img-type="noneWhite" />
                </template>
              </el-table>
            </div>
          </div>
          <div v-show="tabActive !== 'preview' && !!allfields.length" class="batch-area">
            <div class="manage-container">
              <el-collapse v-model="state.fieldCollapse" class="style-collapse">
                <el-collapse-item
                  name="dimension"
                  :title="t('chart.dimension')"
                  class="dimension-manage-header manage-header"
                >
                  <el-table
                    @selection-change="setDeTypeSelection"
                    ref="dimensionsTable"
                    :data="dimensions"
                    :height="quotaTableHeight"
                    style="width: 100%"
                  >
                    <el-table-column type="selection" width="40" />
                    <el-table-column prop="name" :label="t('dataset.field_name')" width="264">
                      <template #default="scope">
                        <div class="column-style">
                          <el-input
                            v-model="scope.row.name"
                            :placeholder="t('commons.input_content')"
                          />
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="originName"
                      :label="t('dataset.origin_name')"
                      width="240"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span v-if="scope.row.extField === 0">{{ scope.row.originName }}</span>
                          <span style="color: #8d9199" v-else>{{ t('dataset.calc_field') }}</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="description"
                      :label="t('deDataset.description')"
                      width="240"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span v-if="scope.row.extField === 0">{{ scope.row.description }}</span>
                          <span style="color: #8d9199" v-else>&nbsp;</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column prop="deType" :label="t('dataset.field_type')" width="200">
                      <template #default="scope">
                        <el-cascader
                          :class="
                            !!scope.row.deTypeArr && !!scope.row.deTypeArr.length && 'select-type'
                          "
                          popper-class="cascader-panel"
                          v-model="scope.row.deTypeArr"
                          @change="val => cascaderChange(scope.row, val)"
                          :options="scope.row.deExtractType === 0 ? fieldOptions : fieldOptionsText"
                        >
                          <template v-slot="{ data }">
                            <el-icon>
                              <Icon
                                :className="`field-icon-${
                                  fieldType[[2, 3].includes(data.value) ? 2 : 0]
                                }`"
                                :name="`field_${getIconName(data.value)}`"
                              ></Icon>
                            </el-icon>
                            <span>{{ data.label }}</span>
                          </template>
                        </el-cascader>
                        <span class="select-svg-icon">
                          <el-icon>
                            <Icon
                              :className="`field-icon-${
                                fieldType[[2, 3].includes(scope.row.deType) ? 2 : 0]
                              }`"
                              :name="`field_${getIconName(scope.row.deType)}`"
                            ></Icon>
                          </el-icon>
                        </span>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="deExtraType"
                      :label="t('dataset.origin_type')"
                      width="168"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span class="flex-align-center icon" v-if="scope.row.extField === 0">
                            <el-icon>
                              <Icon
                                className="primary-color"
                                :name="`field_${getIconName(scope.row.deExtractType)}`"
                              ></Icon>
                            </el-icon>
                            {{ fieldTypes(scope.row.deExtractType) }}
                          </span>
                          <span v-else style="color: #8d9199">{{ t('dataset.calc_field') }}</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column fixed="right" :label="t('chart.dimension')">
                      <template #default="scope">
                        <el-tooltip effect="dark" content="转换为指标" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'translate')">
                              <template #icon>
                                <Icon name="icon_switch_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                      </template>
                    </el-table-column>

                    <el-table-column fixed="right" width="168" :label="t('dataset.operator')">
                      <template #default="scope">
                        <el-tooltip
                          v-if="scope.row.extField === 0 && GROUPABLE_DIMENSION_TYPES.includes(scope.row.deType)"
                          effect="dark"
                          :content="t('dataset.add_group_field')"
                          placement="top"
                        >
                          <template #default>
                            <el-button text @click="addGroupFieldWithField(scope.row)">
                              <template #icon>
                                <Icon name="icon_add_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                        <el-tooltip
                          v-if="scope.row.extField === 0"
                          effect="dark"
                          :content="t('dataset.fill_null_field')"
                          placement="top"
                        >
                          <template #default>
                            <el-button text @click="addFillNullFieldWithField(scope.row)">
                              <template #icon>
                                <Icon name="icon_edit_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                        <el-tooltip effect="dark" :content="t('dataset.copy')" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'copy')">
                              <template #icon>
                                <Icon name="icon_copy_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>

                        <el-tooltip effect="dark" :content="t('dataset.delete')" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'delete')">
                              <template #icon>
                                <Icon name="icon_delete-trash_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>

                        <el-tooltip
                          v-if="scope.row.extField === 2"
                          effect="dark"
                          :content="t('dataset.edit')"
                          placement="top"
                        >
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'editor')">
                              <template #icon>
                                <Icon name="icon_edit_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-collapse-item>
                <el-collapse-item
                  name="quota"
                  :title="t('chart.quota')"
                  class="quota-manage-header manage-header"
                >
                  <el-table
                    @selection-change="setDeTypeSelection"
                    ref="quotaTable"
                    :height="quotaTableHeight"
                    :data="quota"
                    style="width: 100%"
                  >
                    <el-table-column type="selection" width="40" />
                    <el-table-column prop="name" :label="t('dataset.field_name')" width="264">
                      <template #default="scope">
                        <div class="column-style">
                          <el-input
                            v-model="scope.row.name"
                            :placeholder="t('commons.input_content')"
                          />
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="originName"
                      :label="t('dataset.origin_name')"
                      width="240"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span v-if="scope.row.extField === 0">{{ scope.row.originName }}</span>
                          <span v-else style="color: #8d9199">{{ t('dataset.calc_field') }}</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="description"
                      :label="t('deDataset.description')"
                      width="240"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span v-if="scope.row.extField === 0">{{ scope.row.description }}</span>
                          <span style="color: #8d9199" v-else>&nbsp;</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column prop="deType" :label="t('dataset.field_type')" width="200">
                      <template #default="scope">
                        <el-cascader
                          :class="
                            !!scope.row.deTypeArr && !!scope.row.deTypeArr.length && 'select-type'
                          "
                          popper-class="cascader-panel"
                          v-model="scope.row.deTypeArr"
                          @change="val => cascaderChange(scope.row, val)"
                          :options="scope.row.deExtractType === 0 ? fieldOptions : fieldOptionsText"
                        >
                          <template v-slot="{ data }">
                            <el-icon>
                              <Icon
                                :className="`field-icon-${
                                  fieldType[[2, 3].includes(data.value) ? 2 : 0]
                                }`"
                                :name="`field_${getIconName(data.value)}`"
                              ></Icon>
                            </el-icon>
                            <span>{{ data.label }}</span>
                          </template>
                        </el-cascader>
                        <span class="select-svg-icon">
                          <el-icon>
                            <Icon
                              :className="`field-icon-${
                                fieldType[[2, 3].includes(scope.row.deType) ? 2 : 0]
                              }`"
                              :name="`field_${getIconName(scope.row.deType)}`"
                            ></Icon>
                          </el-icon>
                        </span>
                      </template>
                    </el-table-column>

                    <el-table-column
                      prop="deExtraType"
                      :label="t('dataset.origin_type')"
                      width="168"
                    >
                      <template #default="scope">
                        <div class="column-style">
                          <span class="flex-align-center icon" v-if="scope.row.extField === 0">
                            <el-icon>
                              <Icon
                                className="green-color"
                                :name="`field_${getIconName(scope.row.deExtractType)}`"
                              ></Icon>
                            </el-icon>
                            {{ fieldTypes(scope.row.deExtractType) }}
                          </span>
                          <span v-else style="color: #8d9199">{{ t('dataset.calc_field') }}</span>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column fixed="right" :label="t('chart.quota')">
                      <template #default="scope">
                        <el-tooltip effect="dark" content="转换为维度" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'translate')">
                              <template #icon>
                                <Icon name="icon_switch_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                      </template>
                    </el-table-column>

                    <el-table-column fixed="right" width="168" :label="t('dataset.operator')">
                      <template #default="scope">
                        <el-tooltip
                          v-if="scope.row.extField === 0"
                          effect="dark"
                          :content="t('dataset.fill_null_field')"
                          placement="top"
                        >
                          <template #default>
                            <el-button text @click="addFillNullFieldWithField(scope.row)">
                              <template #icon>
                                <Icon name="icon_edit_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                        <el-tooltip effect="dark" :content="t('dataset.copy')" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'copy')">
                              <template #icon>
                                <Icon name="icon_copy_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>

                        <el-tooltip effect="dark" :content="t('dataset.delete')" placement="top">
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'delete')">
                              <template #icon>
                                <Icon name="icon_delete-trash_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>

                        <el-tooltip
                          v-if="scope.row.extField === 2"
                          effect="dark"
                          :content="t('dataset.edit')"
                          placement="top"
                        >
                          <template #default>
                            <el-button text @click="handleFieldMore(scope.row, 'editor')">
                              <template #icon>
                                <Icon name="icon_edit_outlined"></Icon>
                              </template>
                            </el-button>
                          </template>
                        </el-tooltip>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-collapse-item>
              </el-collapse>
            </div>
            <div class="batch-operate flex-align-center" v-if="!!deTypeSelection.length">
              <div class="flex-align-center">
                宸查€夋嫨
                <span class="num">{{ deTypeSelection.length }}</span>
                鏉?
                <el-button @click="clearSelection" text style="margin-left: 16px">{{
                  t('commons.clear')
                }}</el-button>
              </div>
              <div class="cascader-batch" v-if="showCascaderBatch">
                <el-cascader
                  :class="!!deTypeArr.length && 'select-type'"
                  v-model="deTypeArr"
                  @change="cascaderChangeArr"
                  popper-class="cascader-panel"
                  :options="
                    deTypeSelection.every(ele => ele === 0) ? fieldOptions : fieldOptionsText
                  "
                >
                  <template v-slot="{ data }">
                    <el-icon>
                      <Icon
                        :className="`field-icon-${fieldType[[2, 3].includes(data.value) ? 2 : 0]}`"
                        :name="`field_${getIconName(data.value)}`"
                      ></Icon>
                    </el-icon>
                    <span>{{ data.label }}</span>
                  </template>
                </el-cascader>
                <span class="select-svg-icon">
                  <el-icon>
                    <Icon
                      :className="`field-icon-${getIconName(deTypeArr[0])}`"
                      :name="`field_${getIconName(deTypeArr[0])}`"
                    ></Icon>
                  </el-icon>
                </span>
              </div>
              <el-button
                @click="dqTransArr('q')"
                v-if="fieldSelection.every(ele => ele.groupType === 'd')"
                plain
                style="margin-left: 200px"
              >
                杞崲涓烘寚鏍?
              </el-button>
              <el-button
                @click="dqTransArr('d')"
                v-else-if="fieldSelection.every(ele => ele.groupType === 'q')"
                plain
                style="margin-left: 200px"
              >
                杞崲涓虹淮搴?
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <el-drawer
      :title="t('dataset.edit_union_relation')"
      v-model="editUnion"
      custom-class="union-dataset-drawer"
      size="840px"
      :before-close="closeEditUnion"
      direction="rtl"
    >
      <union-edit ref="fieldUnion" :editArr="state.editArr" />
      <template #footer>
        <el-button secondary @click="closeEditUnion">{{ t('dataset.cancel') }} </el-button>
        <el-button type="primary" @click="confirmEditUnion">{{ t('dataset.confirm') }} </el-button>
      </template>
    </el-drawer>
  </div>
  <node-config-drawer
    ref="nodeConfigDrawer"
    v-model:visible="editNodeVisible"
    :node="editNodeTarget"
    @confirm="handleNodeConfigConfirm"
  />
  <creat-ds-group @finish="finish" ref="creatDsFolder"></creat-ds-group>
  <el-dialog
    custom-class="calc-field-edit-dialog"
    v-model="editCalcField"
    width="1000px"
    :title="calcTitle"
  >
    <calc-field-edit ref="calcEdit" :crossDs="crossDatasources" />
    <template #footer>
      <el-button secondary @click="closeEditCalc()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmEditCalc()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-dialog>
  <el-dialog
    custom-class="group-field-edit-dialog"
    v-model="editGroupField"
    width="800px"
    :title="groupTitle"
  >
    <group-field-edit ref="groupEdit" :crossDs="crossDatasources" />
    <template #footer>
      <el-button secondary @click="closeGroupField()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmGroupField()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-dialog>
  <el-dialog
    custom-class="fill-null-field-dialog"
    v-model="editFillNullField"
    width="700px"
    :title="fillNullTitle"
  >
    <fill-null-field-edit ref="fillNullEdit" :crossDs="crossDatasources" />
    <template #footer>
      <el-button secondary @click="closeFillNullField()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmFillNullField()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-dialog>
  <el-dialog class="create-dialog" title="鏍煎紡缂栬緫" v-model="updateCustomTime" width="1000px">
    <el-form ref="ruleFormRef" :rules="rules" :model="currentField" label-width="120px">
      <el-form-item prop="name" label="自定义时间格式">
        <el-input v-model="currentField.name" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button secondary @click="closeCustomTime()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmCustomTime()">{{ t('dataset.confirm') }} </el-button>
    </template>
  </el-dialog>
  <el-dialog
    class="create-dialog"
    :title="t('datasource.field_rename')"
    v-model="editNormalField"
    width="420px"
  >
    <el-form
      ref="ruleFormFieldRef"
      :rules="fieldRules"
      :model="currentNormalField"
      require-asterisk-position="right"
      label-position="top"
      label-width="120px"
    >
      <el-form-item prop="name" :label="t('dataset.field_name')">
        <el-input v-model="currentNormalField.name" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button secondary @click="closeNormalField()">{{ t('dataset.cancel') }} </el-button>
      <el-button type="primary" @click="confirmNormalField()"
        >{{ t('dataset.confirm') }}
      </el-button>
    </template>
  </el-dialog>
  <XpackComponent
    jsname="L2NvbXBvbmVudC9lbWJlZGRlZC1pZnJhbWUvTmV3V2luZG93SGFuZGxlcg=="
    @loaded="XpackLoaded"
    @load-fail="XpackLoaded"
  />
  <XpackComponent
    jsname="L2NvbXBvbmVudC9wbHVnaW5zLWhhbmRsZXIvRHNDYXRlZ29yeUhhbmRsZXI="
    @load-ds-plugin="loadDsPlugin"
  />
</template>

<style lang="less" scoped>
@import '@/style/mixin.less';

.ed-table {
  --ed-table-header-bg-color: #f5f6f7;
}

.workspace-editor-tab {
  height: 32px;
  min-width: 120px;
  max-width: 220px;
  padding: 0 10px;
  border: 1px solid rgba(31, 35, 41, 0.12);
  border-bottom: none;
  border-top-left-radius: 6px;
  border-top-right-radius: 6px;
  background: #e9edf2;
  color: #646a73;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;

  &.active {
    background: #fff;
    color: #1f2329;
  }
}

.workspace-editor-tab-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-editor-tab-close {
  width: 16px;
  height: 16px;
  line-height: 14px;
  text-align: center;
  border-radius: 50%;
  color: #909399;

  &:hover {
    background: rgba(31, 35, 41, 0.08);
    color: #1f2329;
  }
}

.de-dataset-form {
  color: #1f2329;
  height: 100vh;
  max-height: 100vh;
  overflow: hidden;
  position: relative;

  :deep(.ed-table__border-left-patch),
  :deep(.ed-table--border .ed-table__inner-wrapper::after) {
    display: none !important;
  }

  --ed-border-color-lighter: #1f232926 !important;
  .top {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 0 24px;
    background: #050e21;
    box-shadow: 0px 2px 4px 0px rgba(31, 35, 41, 0.12);

    .name {
      color: #fff;
      font-family: '闃块噷宸村反鏅儬浣?3.0 55 Regular L3';
      font-size: 16px;
      font-weight: 400;
      display: flex;
      align-items: center;
      width: auto;
      max-width: 320px;
      flex-shrink: 0;
      position: relative;

      .ed-form-item__error {
        top: 19px !important;
        left: 16px !important;
      }
      .dataset-name {
        cursor: pointer;
        width: 294px;
      }

      .ed-input {
        width: 302px;
        line-height: 24px;
        height: 24px;
        :deep(.ed-input__wrapper) {
          background-color: #050e21;
          box-shadow: 0 0 0 1px var(--ed-color-primary);
          padding: 0 4px;
        }
        :deep(.ed-input__inner) {
          color: #fff;
          font-size: 16px;
        }
      }
      i {
        cursor: pointer;
      }
    }

    .workspace-top-tabs {
      flex: 1;
      min-width: 0;
      display: flex;
      align-items: flex-end;
      gap: 4px;
      overflow-x: auto;
      overflow-y: hidden;
      padding-top: 8px;
    }

    .oprate {
      flex-shrink: 0;
      display: flex;
      align-items: center;
      gap: 8px;

      .ai-entry-btn {
        position: relative;
        overflow: hidden;
        color: #eaf2ff;
        border-color: rgba(120, 168, 255, 0.32);
        background:
          linear-gradient(135deg, rgba(74, 132, 255, 0.32), rgba(40, 208, 176, 0.18)),
          rgba(10, 18, 36, 0.9);
        box-shadow:
          inset 0 1px 0 rgba(255, 255, 255, 0.08),
          0 10px 24px rgba(9, 18, 38, 0.28);

        :deep(.ed-button__text) {
          font-weight: 500;
          letter-spacing: 0.3px;
        }
      }

      .ai-entry-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 18px;
        height: 18px;
        border-radius: 999px;
        background: linear-gradient(135deg, #8bb8ff 0%, #6cf0d0 100%);
        color: #06162f;
        box-shadow: 0 0 18px rgba(111, 193, 255, 0.32);
      }
    }
  }

  .container {
    width: 100%;
    height: calc(100% - 56px);
    min-height: 0;
    position: relative;
    overflow: hidden;
    .drag-left {
      position: absolute;
      height: 100%;
      width: 4px;
      top: 0;
      z-index: 2;
      cursor: col-resize;

      &.is-dragging::after,
      &:hover::after {
        width: 1px;
        height: 100%;
        content: '';
        position: absolute;
        left: -1px;
        top: 0;
        background: var(--ed-color-primary);
      }
    }

    .arrow-right {
      position: absolute;
      top: 15px;
      z-index: 2;
      cursor: pointer;
      margin: 0;
      display: flex;
      align-items: center;
      left: 0;
      height: 24px;
      width: 20px;
      box-shadow: 0px 4px 8px rgba(0, 0, 0, 0.1);
      border: 1px solid var(--deCardStrokeColor, #dee0e3);
      display: flex;
      align-items: center;
      padding-left: 2px;
      border-top-right-radius: 12px;
      border-bottom-right-radius: 12px;
      background: #fff;
      font-size: 12px;

      &:hover {
        padding-left: 4px;
        width: 24px;
        .ed-icon {
          color: var(--ed-color-primary, #3370ff);
        }
      }
    }

    .table-list {
      display: flex;
      flex-direction: column;
      height: 100%;
      min-height: 0;

      .list-item_primary {
        padding: 8px;
      }
      .table-list-top {
        padding: 16px;
        padding-bottom: 0;
        height: 100%;
        min-height: 0;
        display: flex;
        flex-direction: column;
        flex-shrink: 0;
        overflow: hidden;
      }

      width: 240px;
      padding-bottom: 16px;

      font-family: '闃块噷宸村反鏅儬浣?3.0 55 Regular L3';
      border-right: 1px solid rgba(31, 35, 41, 0.15);

      .select-ds {
        font-size: 14px;
        font-weight: 500;
        display: flex;
        justify-content: space-between;
        align-items: center;
        color: var(--deTextPrimary, #1f2329);
        position: relative;

        i {
          cursor: pointer;
          font-size: 12px;
          color: var(--deTextPlaceholder, rgba(31, 35, 41, 0.15));
        }

        .left-outlined {
          position: absolute;
          font-size: 12px;
          right: -30px;
          top: -5px;
          height: 24px;
          border: 1px solid #dee0e3;
          width: 24px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #fff;
          box-shadow: 0px 5px 10px 0px #1f23291a;
          z-index: 10;
          &:hover {
            .ed-icon {
              color: var(--ed-color-primary, #3370ff) !important;
            }
          }
        }
      }

      .table-num {
        .num {
          display: flex;
          align-items: center;
          font-weight: 400;
          font-size: 14px;
          color: #646a73;
          .ed-icon {
            margin-right: 5.33px;
          }
        }

        i {
          cursor: auto;
          font-size: 16px;
          color: var(--deTextPlaceholder, #646a73);
        }
      }

      .search {
        margin: 12px 0;
      }

      .ds-list {
        margin: 12px 0 24px 0;
        width: 100%;
      }

      // 涓ゅ垪甯冨眬
      .panel-two-col {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 0;
        gap: 12px;
        margin-bottom: 8px;
      }

      .panel-col {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 0;
      }

      .panel-col-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 8px;
      }

      .panel-col-title {
        font-size: 13px;
        font-weight: 500;
        color: #1f2329;
      }

      .panel-col-search {
        width: 100%;
        margin-bottom: 8px;
      }

      .panel-col-body {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        overflow-x: hidden;
      }

      .panel-filter-icon {
        flex-shrink: 0;
        border: 1px solid #bbbfc4;
        width: 32px;
        height: 32px;
        border-radius: 4px;
        color: #1f2329;
        padding: 8px;
        font-size: 16px;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        box-sizing: border-box;

        .panel-sort-icon:focus {
          outline: none !important;
        }
        &:hover {
          background: #f5f6f7;
        }
        &:active {
          background: #eff0f1;
        }
      }

      .form-left-section-title {
        font-size: 12px;
        font-weight: 500;
        color: #646a73;
        margin: 8px 0 4px;
        padding-left: 2px;
      }

      .form-left-tree-scroll {
        flex: 1;
        min-height: 0;
        padding: 0 4px;
        overflow-y: auto;
        overflow-x: hidden;
      }

      .form-left-tree-scroll--dataset {
        max-height: none;
      }

      .form-left-tree-scroll--datasource {
        max-height: none;
      }

      .dataset-loading-inline {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        padding: 16px;
        color: #646a73;
        font-size: 13px;
      }

      .form-dataset-tree,
      .form-datasource-tree {
        :deep(.el-tree-node__content) {
          height: 32px;
        }
      }

      .form-left-tree-node {
        width: calc(100% - 24px);
        display: inline-flex;
        align-items: center;
        min-width: 0;
        box-sizing: border-box;

        .label-tooltip {
          flex: 1;
          margin-left: 8px;
          min-width: 0;
        }

        &[draggable='true'] {
          cursor: grab;
        }
      }

      .empty-tip {
        text-align: center;
        color: #909399;
        font-size: 13px;
        padding: 12px 0;
      }

      // 琛ㄥ垪琛ㄥ鍣?
      .table-checkbox-list-wrapper {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        padding: 0 8px;
        display: flex;
        flex-direction: column;

        .not-allow {
          cursor: not-allowed;
          color: var(--deTextDisable, #bbbfc4);
        }
      }

      .table-checkbox-list {
        height: 100%;
        overflow-y: auto;
        padding: 0 8px;

        .not-allow {
          cursor: not-allowed;
          color: var(--deTextDisable, #bbbfc4);
        }
      }
    }
  }

  .dataset-db {
    display: flex;
    height: 100%;
    min-height: 0;
    overflow: hidden;
    .drag-right {
      height: 100%;
      min-height: 0;
      display: flex;
      flex-direction: column;
      flex: 1;
      min-width: 0;
      position: relative;
      overflow: hidden;

      &.with-ai-panel {
        padding-right: 284px;
      }

      .ai-assistant-panel {
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        width: 268px;
        height: 100%;
        min-height: 0;
        box-sizing: border-box;
        border: 1px solid var(--deCardStrokeColor, #dee0e3);
        border-radius: 0;
        background: linear-gradient(180deg, #f9fbff 0%, #ffffff 100%);
        overflow: hidden;
        z-index: 5;
        display: flex;
        flex-direction: column;
        box-shadow: none;

        .ai-assistant-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 12px 16px;
          border-bottom: 1px solid rgba(31, 35, 41, 0.08);
        }

        .ai-assistant-brand {
          display: flex;
          align-items: center;
          gap: 12px;
        }

        .ai-assistant-badge {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 36px;
          height: 36px;
          border-radius: 12px;
          background:
            radial-gradient(circle at 30% 20%, rgba(255, 255, 255, 0.7), transparent 42%),
            linear-gradient(135deg, #4d86ff 0%, #29d1b4 100%);
          color: #fff;
          box-shadow:
            0 8px 18px rgba(77, 134, 255, 0.22),
            inset 0 1px 0 rgba(255, 255, 255, 0.35);

          .ed-icon {
            font-size: 18px;
          }
        }

        .ai-assistant-title {
          font-size: 14px;
          font-weight: 600;
          color: #1f2329;
        }

        .ai-assistant-tip {
          margin-top: 4px;
          font-size: 12px;
          color: #646a73;
        }

        .ai-assistant-body {
          flex: 1;
          min-height: 0;
          overflow-y: auto;
          padding: 12px 16px 16px;
        }

        .ai-assistant-empty {
          padding: 12px;
          border-radius: 8px;
          background: #f5f7fa;
          color: #646a73;
          font-size: 13px;
        }

        .ai-message {
          margin-bottom: 12px;

          .ai-message-role {
            font-size: 12px;
            color: #8f959e;
            margin-bottom: 4px;
          }

          .ai-message-content {
            white-space: pre-wrap;
            word-break: break-word;
            padding: 10px 12px;
            border-radius: 8px;
            font-size: 13px;
            line-height: 20px;
          }

          &.user .ai-message-content {
            background: #3370ff;
            color: #fff;
          }

          &.assistant .ai-message-content {
            background: #f5f7fa;
            color: #1f2329;
          }
        }

        .ai-assistant-input {
          margin-top: auto;
          padding: 12px 16px 16px;
          border-top: 1px solid rgba(31, 35, 41, 0.08);
          background: #fff;
          flex-shrink: 0;
        }

        .ai-assistant-actions {
          margin-top: 12px;
          display: flex;
          justify-content: flex-end;
          gap: 8px;
        }
      }
      .different-datasource {
        height: 40px;
        width: 100%;
        background: #ffe7cc;
        color: #1f2329;
        font-size: 14px;
        font-weight: 400;
        line-height: 22px;
        display: flex;
        align-items: center;
        padding: 0 16px;

        .ed-icon {
          font-size: 16px;
          margin-right: 8px;
        }
      }
      .sql-result {
        font-family: '闃块噷宸村反鏅儬浣?3.0 55 Regular L3';
        font-size: 14px;
        overflow: hidden;
        box-sizing: border-box;
        flex: 1;
        min-height: 0;
        min-height: 0;
        display: flex;
        flex-direction: column;
        :deep(.ed-tabs) {
          position: relative;
          z-index: 4;
        }

        .sql-title {
          user-select: none;
          height: 10px;
          position: relative;
          z-index: 5;
          color: var(--deTextPrimary, #1f2329);
          flex-shrink: 0;

          .field-data {
            position: absolute;
            right: 24px;
            top: 13px;
            width: 50%;
            z-index: 2;
            text-align: right;
          }

          .drag {
            position: absolute;
            top: 4px;
            left: 0;
            height: 7px;
            width: 100%;
            cursor: row-resize;
            &::after {
              content: '';
              height: 7px;
              width: 100px;
              border-radius: 3.5px;
              position: absolute;
              left: 50%;
              top: 0;
              transform: translateX(-50%);
              background: rgba(31, 35, 41, 0.1);
            }
          }
        }

        .padding-24 {
          .border-bottom-tab(24px);
          flex-shrink: 0;
          :deep(.ed-tabs__header::after) {
            display: none;
          }
        }

        .table-preview {
          flex: 1;
          min-height: 0;
          box-sizing: border-box;
          display: flex;
          overflow: hidden;

          .preview-data {
            flex: 1;
            min-width: 0;
            height: 100%;

            .preview-cell-text {
              display: block;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
              max-width: 100%;
            }

            :deep(.ed-table-v2__header-cell) {
              background-color: #f5f6f7 !important;
            }

            :deep(.header-cell) {
              border-top: none;
            }

            .preview-header-cell.can-group {
              cursor: pointer;
              &:hover {
                color: var(--ed-color-primary, #3370ff);
              }
            }
          }

          .preview-field {
            width: 260px;
            flex-shrink: 0;
            height: 100%;
            position: relative;
            overflow-y: auto;

            :deep(.ed-tree-node__content) {
              border-radius: 4px;
              &:hover {
                background: rgba(31, 35, 41, 0.1);
              }
            }

            :deep(.ed-tree-node.is-current > .ed-tree-node__content:not(.is-menu):after) {
              display: none;
            }

            .custom-tree-node {
              width: calc(100% - 32px);
              display: flex;
              align-items: center;
              padding-right: 8px;
              box-sizing: content-box;

              .label-tooltip {
                margin-left: 5.33px;
                width: 70%;
                overflow: hidden;
                white-space: nowrap;
                text-overflow: ellipsis;
              }

              .operate {
                margin-left: auto;
                position: relative;
                z-index: 5;
              }
            }

            .field-d,
            .field-q {
              padding: 0 8px;
              position: relative;
              height: 49px;

              &.open {
                height: 50%;
              }
              .title {
                cursor: pointer;
                position: sticky;
                margin: 1px;
                top: 1px;
                height: 49px;
                font-family: '闃块噷宸村反鏅儬浣?3.0 55 Regular L3';
                font-style: normal;
                font-weight: 500;
                font-size: 14px;
                line-height: 22px;
                color: #1f2329;
                display: flex;
                align-items: center;
                z-index: 10;
                background: #fff;

                .add {
                  margin-left: auto;
                }
                i {
                  color: #646a73;
                }

                .expand {
                  font-size: 10px;
                }

                &.expanded {
                  .expand {
                    transform: rotate(90deg);
                  }
                }
              }
              overflow-y: auto;
            }

            .field-d {
              max-height: calc(100% - 50px);
              border-bottom: 1px solid rgba(31, 35, 41, 0.15);
            }
          }
        }
      }
    }
  }
}
.icon-color {
  color: #646a73;
}

.ed-button.is-secondary.is-disabled {
  color: #bbbfc4 !important;
  border-color: #bbbfc4 !important;
}

.father .child {
  visibility: hidden;
}

.father:hover .child {
  visibility: visible;
}

.manage-container {
  padding: 12px 24px 0;
  flex: 1;
  overflow: auto;
}

.style-collapse {
  :deep(.ed-collapse-item__header),
  :deep(.ed-collapse-item__wrap) {
    border-bottom: none !important;
  }
  :deep(.ed-collapse-item__content) {
    padding: 0 !important;
  }

  &.data-tab-collapse {
    border-bottom: none;
    border-top: 1px solid var(--ed-collapse-border-color);

    :deep(.ed-collapse-item.ed-collapse--dark .ed-collapse-item__wrap) {
      background-color: #1a1a1a;
    }

    :deep(.ed-collapse-item__wrap) {
      border-top: none !important;
    }
    :deep(.ed-collapse-item__content) {
      padding: 0 !important;
      border-top: none !important;
    }
    :deep(.ed-collapse-item__header) {
      background-color: transparent;
      border-bottom: none !important;
    }
  }
}

.column-style {
  display: flex;
  align-items: center;
}

.select-svg-icon {
  position: absolute;
  left: 24px;
  top: 50%;
  height: 14px;
  transform: translateY(-50%);
  line-height: 14px;
}

.cascader-panel {
  .ed-cascader-node__label {
    display: flex;
    align-items: center;
    .ed-icon {
      margin-right: 5px;
    }
  }
}

.batch-operate {
  width: 100%;
  height: 64px;
  padding: 0 24px;
  z-index: 2;
  box-shadow: 0px -2px 4px rgba(31, 35, 41, 0.08);

  .select-svg-icon {
    left: 11px;
  }

  .flex-align-center {
    white-space: nowrap;
    .num {
      margin: 0 4px;
    }
    .is-text {
      margin-left: 16px;
    }
  }

  .cascader-batch {
    position: relative;
    margin-left: 30%;
    width: 176px;
  }
}

.batch-area {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.dimension-manage-header {
  :deep(.ed-collapse-item__header) {
    background: #ebf1ff;
  }
}
.quota-manage-header {
  :deep(.ed-collapse-item__header) {
    background: #e6f7f5;
  }
}
.manage-header {
  :deep(.ed-collapse-item__header) {
    height: 30px;
  }
  :deep(.ed-table th.ed-table__cell) {
    background: #f5f6f7;
  }
}
</style>

<style lang="less">
.select-type {
  .ed-input__wrapper {
    padding-left: 32px;
  }
}
.green-color {
  color: #04b49c;
}
.ed-select-dropdown__item {
  display: flex;
  align-items: center;
  .ed-icon {
    font-size: 14px;
    margin-right: 5.25px;
  }
}
.tree-select-ds_popper {
  .ed-tree-node.is-current > .ed-tree-node__content:not(.is-menu):after {
    display: none !important;
  }

  .flex-align-center {
    padding-right: 15px;
  }
}
.calc-field-edit-dialog {
  .ed-dialog__footer {
    padding-top: 24px;
    border: 1px solid rgba(31, 35, 41, 0.15);
  }
}
</style>
