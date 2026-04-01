<script lang="ts">
import { h } from 'vue'
import { ElMenuItem, ElSubMenu } from 'element-plus-secondary'
import Icon from '@/components/icon-custom/src/Icon.vue'

const title = props => {
  const { title } = props?.menu?.meta || {}
  return [h('span', null, { default: () => title })]
}

const expandIcon = (name: string) => {
  return h(Icon, { className: '', name })
}
const MAX_HEADER_MENU_DEPTH = 20
const HeaderMenuItem = (props: { menu?: any; index?: string; depth?: number }) => {
  if (!props?.menu) return null
  const depth = (props.depth ?? 0) + 1
  if (depth > MAX_HEADER_MENU_DEPTH) return null
  const { children = [], hidden, path } = props.menu
  if (hidden) {
    return null
  }

  if (children?.length) {
    return h(
      ElSubMenu,
      {
        index: path,
        'popper-class': 'popper-class-menu',
        showTimeout: 1,
        expandCloseIcon: expandIcon('icon_expand-down_filled'),
        expandOpenIcon: expandIcon('icon_expand-down_filled')
      },
      {
        title: () => title(props),
        default: () =>
          (Array.isArray(children) ? children.filter(Boolean) : [])
            .filter((ele: any) => ele && ele.path !== path)
            .map((ele: any) => h(HeaderMenuItem, { menu: ele, index: path, depth }))
      }
    )
  }

  return h(
    ElMenuItem,
    { index: props.index ? `${props.index}/${path}` : path },
    {
      title: () => title(props)
    }
  )
}
export default HeaderMenuItem
</script>

<style lang="less">
.popper-class-menu {
  --active-color: #1f2329;
  &.is-light {
    border: none;
    margin-top: -2px;
  }
  .popper-class-menu {
    width: 152px;
    border-radius: 4px;
    border: 1px solid #dee0e3 !important;

    .ed-menu--popup {
      min-width: 150px;
      padding: 3px 0;
      .ed-menu-item {
        padding-left: 11px !important;
      }
    }
  }
}
</style>
