import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import type { App } from 'vue'

export const routes: AppRouteRecordRaw[] = [
  {
    path: '/',
    name: 'index',
    redirect: '/data/dataset',
    hidden: true,
    meta: {}
  },
  {
    path: '/notSupport',
    name: 'notSupport',
    hidden: true,
    meta: {},
    component: () => import('@/views/common/NotSupport.vue')
  },
  {
    path: '/401',
    name: '401',
    hidden: true,
    meta: {},
    component: () => import('@/views/401/index.vue')
  },
  {
    path: '/dataset-form',
    name: 'dataset-form',
    hidden: true,
    meta: {},
    component: () => import('@/views/visualized/data/dataset/form/index.vue')
  },
  {
    path: '/data/dataset/:id?',
    name: 'dataset',
    hidden: true,
    meta: {},
    component: () => import('@/views/visualized/data/dataset/index.vue')
  },
  {
    path: '/data/datasource/:id?',
    name: 'datasource',
    hidden: true,
    meta: {},
    component: () => import('@/views/visualized/data/datasource/index.vue')
  },
  {
    path: '/dataset-embedded-form',
    name: 'dataset-embedded-form',
    hidden: true,
    meta: {},
    component: () => import('@/views/visualized/data/dataset/form/index.vue')
  },
  {
    path: '/:catchAll(.*)',
    name: 'catch-all',
    hidden: true,
    meta: {},
    redirect: '/data/dataset'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: routes as RouteRecordRaw[]
})

export const resetRouter = (): void => {
  const resetWhiteNameList = [
    'index',
    'notSupport',
    '401',
    'dataset-form',
    'dataset',
    'datasource',
    'dataset-embedded-form',
    'catch-all'
  ]
  router.getRoutes().forEach(route => {
    const { name } = route
    if (name && !resetWhiteNameList.includes(name as string)) {
      router.hasRoute(name) && router.removeRoute(name)
    }
  })
}

export const setupRouter = (app: App<Element>) => {
  app.use(router)
}

export default router
