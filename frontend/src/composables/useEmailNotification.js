import emailNotificationService from '../utils/emailNotification'
import webSocketService from '../utils/websocket'

// 主要的邮箱发送服务composable - 通过WebSocket发送
export function useEmailNotification() {
  return {
    sendEmail: (emailData) => emailNotificationService.sendEmailNotification(emailData),
    sendRegistrationSuccessEmail: (emailData) => emailNotificationService.sendRegistrationSuccessEmail(emailData),
    sendStudentRegistrationEmail: (emailData) => emailNotificationService.sendStudentRegistrationEmail(emailData),
    sendLectureNotificationEmail: (emailData) => emailNotificationService.sendLectureNotificationEmail(emailData),
    sendUpcomingLectureEmail: (emailData) => emailNotificationService.sendUpcomingLectureEmail(emailData),
    sendBroadcastEmail: (emailData) => emailNotificationService.sendBroadcastEmail(emailData),
    sendGeneralEmail: (emailData) => emailNotificationService.sendGeneralEmail(emailData),
    // 直接通过WebSocket发送邮件的方法
    sendEmailViaWebSocket: (destination, emailData) => {
      return webSocketService.sendMessage(destination, emailData)
    },
    // 检查WebSocket连接状态
    isWebSocketConnected: () => webSocketService.isConnected()
  }
}

// 邮箱发送工具函数 - 支持WebSocket发送
export function useEmailSender() {
  const emailService = useEmailNotification()
  
  // 创建标准邮件数据格式
  const createEmailData = (to, subject, content, type = 'GENERAL') => {
    return {
      to: Array.isArray(to) ? to : [to],
      subject,
      content,
      type,
      timestamp: new Date().toISOString()
    }
  }
  
  // 直接通过WebSocket发送邮件的便捷方法
  const sendEmailDirectly = (to, subject, content, type = 'GENERAL') => {
    const emailData = createEmailData(to, subject, content, type)
    return emailService.sendEmailViaWebSocket('/app/email/send', emailData)
  }
  
  return {
    createEmailData,
    sendEmailDirectly,
    ...emailService
  }
}