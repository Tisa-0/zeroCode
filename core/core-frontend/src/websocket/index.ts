import SockJS from 'sockjs-client/dist/sockjs.min.js'
import Stomp from 'stompjs'
import { useCache } from '@/hooks/web/useCache'
import { useEmitt } from '@/hooks/web/useEmitt'
import dev from '../../config/dev'

const { wsCache } = useCache()
const env = import.meta.env
const basePath = env.VITE_API_BASEPATH

let stompClient: Stomp.Client
let timeInterval: NodeJS.Timer | null = null
let socketEndpoint: string | null | undefined
let probingEndpoint = false

const ensureTrailingSlash = (value: string) => (value.endsWith('/') ? value : value + '/')

const buildRuntimePrefix = () => {
  if (window.DataEaseBi?.baseUrl) {
    return ensureTrailingSlash(window.DataEaseBi.baseUrl)
  }
  const href = window.location.href
  let prefix = href.substring(0, href.indexOf('#'))
  if (env.MODE === 'dev') {
    prefix = dev.server.proxy[basePath].target + '/'
  }
  return ensureTrailingSlash(prefix)
}

const resolveSocketEndpoint = async (prefix: string) => {
  if (socketEndpoint !== undefined) return socketEndpoint
  if (probingEndpoint) return null
  probingEndpoint = true
  const candidates = [prefix + 'websocket', prefix + 'de2api/websocket']
  try {
    for (const candidate of candidates) {
      try {
        const res = await fetch(`${candidate}/info?t=${Date.now()}`, {
          method: 'GET',
          credentials: 'include'
        })
        if (res.ok) {
          socketEndpoint = candidate
          return socketEndpoint
        }
      } catch {
        // ignore and try next endpoint
      }
    }
    socketEndpoint = null
    console.info('[websocket] endpoint not found, skip realtime connection')
    return null
  } finally {
    probingEndpoint = false
  }
}

export default {
  install() {
    const channels = [
      {
        topic: '/task-export-topic',
        event: 'task-export-topic-call'
      },
      {
        topic: '/report-notice',
        event: 'report-notice-call'
      }
    ]

    function isLoginStatus() {
      return wsCache.get('user.token') && wsCache.get('user.uid')
    }

    async function connection() {
      if (!isLoginStatus()) return
      if (stompClient && stompClient.connected) return
      const prefix = buildRuntimePrefix()
      const endpoint = await resolveSocketEndpoint(prefix)
      if (!endpoint) return

      const socket = new SockJS(endpoint + '?userId=' + wsCache.get('user.uid'))
      stompClient = Stomp.over(socket)
      const heads = {
        userId: wsCache.get('user.uid')
      }
      stompClient.connect(
        heads,
        () => {
          channels.forEach(channel => {
            stompClient.subscribe('/user/' + wsCache.get('user.uid') + channel.topic, res => {
              res && res.body && useEmitt().emitter.emit(channel.event, res.body)
            })
          })
        },
        error => {
          console.log('连接失败: ' + error)
        }
      )
    }

    function disconnect() {
      if (stompClient && stompClient.connected) {
        stompClient.disconnect(
          function () {
            console.log('断开连接')
          },
          function (error) {
            console.log('断开连接失败: ' + error)
          }
        )
      }
    }

    function initialize() {
      connection()
      timeInterval = setInterval(() => {
        if (!isLoginStatus()) {
          disconnect()
          return
        }
        if (!stompClient || !stompClient.connected) {
          connection()
        }
      }, 5000)
    }
    initialize()
  }
}
