import { onMounted, onUnmounted } from 'vue'
import webSocketService from '../utils/websocket'

export function useWebSocket() {
  return {
    connect: () => webSocketService.connect(),
    disconnect: () => webSocketService.disconnect(),
    sendMessage: (destination, message) => webSocketService.sendMessage(destination, message),
    subscribe: (destination, callback) => webSocketService.subscribe(destination, callback),
    unsubscribe: (destination) => webSocketService.unsubscribe(destination),
    registerMessageHandler: (type, handler) => webSocketService.registerMessageHandler(type, handler),
    removeMessageHandler: (type) => webSocketService.removeMessageHandler(type),
    isConnected: () => webSocketService.isConnected(),
    setBellNotificationInstance: (instance) => webSocketService.setBellNotificationInstance(instance)
  }
}

// 用于监听特定类型的WebSocket消息
export function useWebSocketMessage(eventType, handler) {
  const handleMessage = (event) => {
    handler(event.detail)
  }

  onMounted(() => {
    window.addEventListener(`websocket-${eventType}`, handleMessage)
  })

  onUnmounted(() => {
    window.removeEventListener(`websocket-${eventType}`, handleMessage)
  })
}

// 用于报名成功通知
export function useRegistrationSuccessNotification(handler) {
  useWebSocketMessage('registration-success', handler)
}

// 用于学生报名通知（教师使用）
export function useStudentRegistrationNotification(handler) {
  useWebSocketMessage('student-registration', handler)
}



// 用于邮件发送结果通知
export function useEmailResultNotification(handler) {
  useWebSocketMessage('email-result', handler)
}