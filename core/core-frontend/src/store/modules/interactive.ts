import { defineStore } from 'pinia'
import { store } from '../index'
import { getDatasetTree } from '@/api/dataset'
import { listDatasources } from '@/api/datasource'
import type { BusiTreeRequest, BusiTreeNode } from '@/models/tree/TreeNode'
import { pathValid } from '@/store/modules/permission'
import { useAppStoreWithOut } from '@/store/modules/app'
const appStore = useAppStoreWithOut()

export interface InnerInteractive {
  rootManage: boolean
  anyManage: boolean
  treeNodes: BusiTreeNode[]
  leafNodeCount: number
  menuAuth: boolean
}

interface InteractiveState {
  data: Record<number, InnerInteractive>
}

type InteractiveFlag = 'dataset' | 'datasource'
type InteractiveItem = {
  busiFlag: InteractiveFlag
  index: number
  path: string
  method: (param: BusiTreeRequest) => Promise<any>
}

const interactiveItems: InteractiveItem[] = [
  { busiFlag: 'dataset', index: 2, path: '/data/dataset', method: getDatasetTree },
  { busiFlag: 'datasource', index: 3, path: '/data/datasource', method: listDatasources }
]

export const interactiveStore = defineStore('interactive', {
  state: (): InteractiveState => ({
    data: {}
  }),
  getters: {
    getPanel(): InnerInteractive {
      return this.data[0]
    },
    getScreen(): InnerInteractive {
      return this.data[1]
    },
    getDataset(): InnerInteractive {
      return this.data[2]
    },
    getDatasource(): InnerInteractive {
      return this.data[3]
    },
    getData(): InteractiveState {
      return this.data
    }
  },
  actions: {
    async setInteractive(param: BusiTreeRequest) {
      const item = interactiveItems.find(it => it.busiFlag === (param.busiFlag as InteractiveFlag))
      if (!item) {
        return []
      }
      if (!hasMenuAuth(item.path) && !window.DataEaseBi && !appStore.getIsIframe) {
        const tempData: InnerInteractive = {
          rootManage: false,
          anyManage: false,
          treeNodes: [],
          leafNodeCount: 0,
          menuAuth: false
        }
        this.data[item.index] = tempData
        return []
      }
      const res = await item.method(param)
      this.data[item.index] = convertInteractive(res)
      return res
    },
    async initInteractive(refresh?: boolean) {
      for (const item of interactiveItems) {
        if (!this.data[item.index] || refresh) {
          await this.setInteractive({ busiFlag: item.busiFlag } as BusiTreeRequest)
        }
      }
    },
    clear() {
      this.data = {}
    }
  }
})

export const interactiveStoreWithOut = () => interactiveStore(store)

const convertInteractive = (list): InnerInteractive => {
  const result: InnerInteractive = {
    rootManage: list[0]['weight'] >= 7,
    anyManage: false,
    treeNodes: (list as unknown as BusiTreeNode[]) || [],
    leafNodeCount: 0,
    menuAuth: true
  }
  const stack = [...list]
  let leafNodeCount = 0
  while (stack.length) {
    const node = stack.pop()
    if (!node['leaf'] && node['weight'] >= 7) {
      result.anyManage = true
      // break
    }
    if (node['leaf'] && node['weight']) {
      ++leafNodeCount
    }
    if (node?.children?.length) {
      node.children.forEach(kid => stack.push(kid))
    }
  }
  result.leafNodeCount = leafNodeCount
  return result
}

const hasMenuAuth = (path: string): boolean => pathValid(path)
