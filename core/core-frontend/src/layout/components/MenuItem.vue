<script lang="ts">
import { h } from 'vue'
import { Icon } from '@/components/icon-custom'
import { ElMenuItem, ElSubMenu, ElIcon } from 'element-plus-secondary'

const MAX_MENU_DEPTH = 20

const titleWithIcon = props => {
  const { title, icon } = props?.menu?.meta || {}
  return [
    h(ElIcon, null, { default: () => h(Icon, { className: 'logo', name: icon }) }),
    h('span', null, { default: () => title })
  ]
}

const MenuItem = (props: { menu?: any; depth?: number }) => {
  if (!props?.menu) return null
  const depth = (props.depth ?? 0) + 1
  if (depth > MAX_MENU_DEPTH) return null
  const { children, hidden, path } = props.menu
  if (hidden) {
    return null
  }
  const childList = Array.isArray(children) ? children.filter(Boolean) : []
  if (childList.length) {
    return h(
      ElSubMenu,
      { index: path },
      {
        title: () => titleWithIcon(props),
        default: () =>
          childList
            .filter((ele: any) => ele && ele.path !== path)
            .map((ele: any) => h(MenuItem, { menu: ele, depth }))
      }
    )
  }
  const { title, icon } = props.menu?.meta || {}
  return h(
    ElMenuItem,
    { index: path },
    {
      title: h('span', null, { default: () => title }),
      default: h(ElIcon, null, { default: () => h(Icon, { className: 'logo', name: icon }) })
    }
  )
}
export default MenuItem
</script>
