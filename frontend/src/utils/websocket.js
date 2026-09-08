import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

class WebSocketService {
  constructor() {
    this.client = null
    this.connected = false
    this.subscriptions = new Map()
    this.messageHandlers = new Map()
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 3000
    this.bellNotificationInstance = null
  }

  // 设置BellNotification实例
  setBellNotificationInstance(instance) {
    this.bellNotificationInstance = instance
  }

  // 连接WebSocket
  connect() {
    if (this.connected) {
      console.log('WebSocket已连接')
      return Promise.resolve()
    }

    return new Promise((resolve, reject) => {
      try {
        this.client = new Client({
          webSocketFactory: () => new SockJS('http://localhost:8080/api/ws'),
          connectHeaders: {},
          debug: (str) => {
            console.log('STOMP Debug:', str)
          },
          reconnectDelay: this.reconnectDelay,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
          onConnect: (frame) => {
            console.log('WebSocket连接成功:', frame)
            this.connected = true
            this.reconnectAttempts = 0
            this.setupDefaultSubscriptions()
            resolve()
          },
          onStompError: (frame) => {
            console.error('STOMP错误:', frame.headers['message'])
            console.error('详细信息:', frame.body)
            this.connected = false
            reject(new Error(frame.headers['message']))
          },
          onWebSocketClose: (event) => {
            console.log('WebSocket连接关闭:', event)
            this.connected = false
            this.handleReconnect()
          },
          onWebSocketError: (event) => {
            console.error('WebSocket错误:', event)
            this.connected = false
          }
        })

        this.client.activate()
      } catch (error) {
        console.error('WebSocket连接失败:', error)
        reject(error)
      }
    })
  }

  // 断开连接
  disconnect() {
    if (this.client && this.connected) {
      this.client.deactivate()
      this.connected = false
      this.subscriptions.clear()
      console.log('WebSocket连接已断开')
    }
  }

  // 处理重连
  handleReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)
      
      setTimeout(() => {
        this.connect().catch(error => {
          console.error('重连失败:', error)
        })
      }, this.reconnectDelay * this.reconnectAttempts)
    } else {
      console.error('达到最大重连次数，停止重连')
      ElMessage.error('WebSocket连接失败，请刷新页面重试')
    }
  }

  // 设置默认订阅
  setupDefaultSubscriptions() {
    const userStore = useUserStore()
    const userId = userStore.userInfo?.id
    
    if (userId) {
      // 订阅用户专属消息
      this.subscribe(`/user/${userId}/queue/messages`, (message) => {
        this.handleUserMessage(JSON.parse(message.body))
      })

      // 订阅报名成功通知
      this.subscribe(`/user/${userId}/queue/registration-success`, (message) => {
        this.handleRegistrationSuccessNotification(JSON.parse(message.body))
      })

      // 订阅学生报名通知（教师接收）
      this.subscribe(`/user/${userId}/queue/student-registration`, (message) => {
        this.handleStudentRegistrationNotification(JSON.parse(message.body))
      })

      // 订阅邮件发送结果通知
      this.subscribe(`/user/${userId}/queue/email-result`, (message) => {
        this.handleEmailResult(JSON.parse(message.body))
      })

      // 订阅取消报名通知
      this.subscribe(`/user/${userId}/queue/registration-cancel`, (message) => {
        this.handleRegistrationCancelNotification(JSON.parse(message.body))
      })

      // 订阅学生取消报名通知（教师接收）
      this.subscribe(`/user/${userId}/queue/student-cancel`, (message) => {
        this.handleStudentCancelNotification(JSON.parse(message.body))
      })
    }
  }

  // 订阅消息
  subscribe(destination, callback) {
    if (!this.connected || !this.client) {
      console.warn('WebSocket未连接，无法订阅:', destination)
      return
    }

    try {
      const subscription = this.client.subscribe(destination, callback)
      this.subscriptions.set(destination, subscription)
      console.log('订阅成功:', destination)
      return subscription
    } catch (error) {
      console.error('订阅失败:', destination, error)
    }
  }

  // 取消订阅
  unsubscribe(destination) {
    const subscription = this.subscriptions.get(destination)
    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      console.log('取消订阅:', destination)
    }
  }

  // 发送消息
  sendMessage(destination, message) {
    if (!this.connected || !this.client) {
      console.warn('WebSocket未连接，无法发送消息')
      return false
    }

    try {
      this.client.publish({
        destination: destination,
        body: JSON.stringify(message)
      })
      console.log('消息发送成功:', destination, message)
      return true
    } catch (error) {
      console.error('消息发送失败:', error)
      return false
    }
  }

  // 处理用户消息
  handleUserMessage(message) {
    console.log('收到用户消息:', message)
    
    // 直接调用BellNotification组件的方法
    if (this.bellNotificationInstance) {
      this.bellNotificationInstance.addMessage('USER_MESSAGE', message.content, message)
    }
    
    // 保留自定义事件以兼容其他组件
    window.dispatchEvent(new CustomEvent('websocket-user-message', {
      detail: message
    }))
  }

  // 处理报名成功通知
  handleRegistrationSuccessNotification(message) {
    console.log('收到报名成功通知:', message)
    
    // 直接调用BellNotification组件的方法
    if (this.bellNotificationInstance) {
      this.bellNotificationInstance.addMessage('REGISTRATION_SUCCESS', `您已成功报名讲座：${message.content}`, message)
    }
    
    // 保留自定义事件以兼容其他组件
    window.dispatchEvent(new CustomEvent('websocket-registration-success', {
      detail: message
    }))
  }

  // 处理学生报名通知（教师接收）
  handleStudentRegistrationNotification(message) {
    console.log('收到学生报名通知:', message)
    
    // 直接调用BellNotification组件的方法
    if (this.bellNotificationInstance) {
      this.bellNotificationInstance.addMessage('STUDENT_REGISTRATION', message.content, message)
    }
    
    // 保留自定义事件以兼容其他组件
    window.dispatchEvent(new CustomEvent('websocket-student-registration', {
      detail: message
    }))
  }

  // 处理取消报名通知
  handleRegistrationCancelNotification(message) {
    console.log('收到取消报名通知:', message)
    
    // 直接调用BellNotification组件的方法
    if (this.bellNotificationInstance) {
      this.bellNotificationInstance.addMessage('REGISTRATION_CANCEL', message.content, message)
    }
    
    // 保留自定义事件以兼容其他组件
    window.dispatchEvent(new CustomEvent('websocket-registration-cancel', {
      detail: message
    }))
  }

  // 处理学生取消报名通知（教师接收）
  handleStudentCancelNotification(message) {
    console.log('收到学生取消报名通知:', message)
    
    // 直接调用BellNotification组件的方法
    if (this.bellNotificationInstance) {
      this.bellNotificationInstance.addMessage('STUDENT_CANCEL', message.content, message)
    }
    
    // 保留自定义事件以兼容其他组件
    window.dispatchEvent(new CustomEvent('websocket-student-cancel', {
      detail: message
    }))
  }



  // 处理邮件发送结果
  handleEmailResult(message) {
    console.log('收到邮件发送结果:', message)
    
    // 根据发送结果显示不同的消息
    if (message.success) {
      ElMessage.success(`邮件发送成功: ${message.message || '邮件已发送'}`)
    } else {
      ElMessage.error(`邮件发送失败: ${message.error || '发送失败'}`)
    }
    
    // 直接调用BellNotification组件的方法
    if (this.bellNotificationInstance) {
      this.bellNotificationInstance.addMessage('EMAIL_RESULT', 
        message.success ? '邮件发送成功' : '邮件发送失败', 
        message)
    }
    
    // 保留自定义事件以兼容其他组件
    window.dispatchEvent(new CustomEvent('websocket-email-result', {
      detail: message
    }))
  }

  // 注册消息处理器
  registerMessageHandler(type, handler) {
    this.messageHandlers.set(type, handler)
  }

  // 移除消息处理器
  removeMessageHandler(type) {
    this.messageHandlers.delete(type)
  }

  // 获取连接状态
  isConnected() {
    return this.connected
  }
}

// 创建全局WebSocket服务实例
const webSocketService = new WebSocketService()

export default webSocketService