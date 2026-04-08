import router from './router'
import { useAppStoreWithOut } from '@/store/modules/app'
import { useNProgress } from '@/hooks/web/useNProgress'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { usePageLoading } from '@/hooks/web/usePageLoading'
import { isMobile, checkPlatform } from '@/utils/utils'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'
import { useEmbedded } from '@/store/modules/embedded'

const appearanceStore = useAppearanceStoreWithOut()
const permissionStore = usePermissionStoreWithOut()
const appStore = useAppStoreWithOut()

const { start, done } = useNProgress()

const { loadStart, loadDone } = usePageLoading()

const whiteList = ['/', '/data/dataset', '/dataset-form', '/dataset-embedded-form', '/notSupport', '/401']

const isAllowedPath = (path: string) => {
  return (
    whiteList.includes(path) || path.startsWith('/data/dataset') || path.startsWith('/data/datasource')
  )
}

router.beforeEach(async (to, _from, next) => {
  start()
  loadStart()
  checkPlatform()
  if (isMobile() && to.path !== '/notSupport') {
    next('/notSupport')
    return
  }

  await appearanceStore.setAppearance()
  appStore.setDesktop(true)

  const embeddedStore = useEmbedded()
  if (embeddedStore.getToken && appStore.getIsIframe && to.path === '/dataset-form') {
    next({ path: '/dataset-embedded-form', query: to.query })
    return
  }

  if (!isAllowedPath(to.path)) {
    permissionStore.setCurrentPath('/data/dataset')
    next('/data/dataset')
    return
  }

  permissionStore.setCurrentPath(to.path)
  next()
})

router.afterEach(() => {
  done()
  loadDone()
})
