import mitt from 'mitt'
import { getCurrentInstance, onBeforeUnmount } from 'vue'

interface Option {
  name: string // 事件名称
  callback: Fn // 回调
}

const emitter = mitt()

export const useEmitt = (option?: Option) => {
  if (option) {
    emitter.on(option.name, option.callback)

    // 仅在存在当前组件实例（即在 setup 内部）时注册生命周期钩子，
    // 避免在无 active instance 场景下触发 Vue 的 onBeforeUnmount 警告。
    const instance = getCurrentInstance()
    if (instance) {
      onBeforeUnmount(() => {
        emitter.off(option.name, option.callback)
      })
    }
  }

  return {
    emitter
  }
}
