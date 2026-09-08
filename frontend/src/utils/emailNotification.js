import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import webSocketService from './websocket'

class EmailNotificationService {
  constructor() {
    console.log('邮箱通知服务初始化 - 通过WebSocket发送邮件')
  }

  // 通过WebSocket发送邮件通知
  async sendEmailNotification(emailData) {
    try {
      const userStore = useUserStore()
      if (!userStore.token) {
        throw new Error('用户未登录')
      }

      if (!webSocketService.isConnected()) {
        throw new Error('WebSocket未连接')
      }

      // 通过WebSocket发送邮件请求到后端
      const success = webSocketService.sendMessage('/app/email/send', {
        ...emailData,
        userId: userStore.userInfo?.id,
        timestamp: new Date().toISOString()
      })

      if (success) {
        console.log('邮件发送请求已提交:', emailData)
        ElMessage.success('邮件发送请求已提交')
        return { success: true, message: '邮件发送请求已提交' }
      } else {
        throw new Error('WebSocket发送失败')
      }
    } catch (error) {
      console.error('发送邮件失败:', error)
      ElMessage.error('邮件发送失败: ' + error.message)
      throw error
    }
  }

  // 发送报名成功邮件
  async sendRegistrationSuccessEmail(emailData) {
    return await this.sendEmailNotification({
      ...emailData,
      type: 'REGISTRATION_SUCCESS'
    })
  }

  // 发送学生报名通知邮件
  async sendStudentRegistrationEmail(emailData) {
    return await this.sendEmailNotification({
      ...emailData,
      type: 'STUDENT_REGISTRATION'
    })
  }

  // 发送讲座通知邮件
  async sendLectureNotificationEmail(emailData) {
    return await this.sendEmailNotification({
      ...emailData,
      type: 'LECTURE_NOTIFICATION'
    })
  }

  // 发送即将开始的讲座邮件
  async sendUpcomingLectureEmail(emailData) {
    return await this.sendEmailNotification({
      ...emailData,
      type: 'UPCOMING_LECTURE'
    })
  }

  // 发送广播邮件
  async sendBroadcastEmail(emailData) {
    return await this.sendEmailNotification({
      ...emailData,
      type: 'BROADCAST'
    })
  }

  // 发送一般邮件
  async sendGeneralEmail(emailData) {
    return await this.sendEmailNotification({
      ...emailData,
      type: 'GENERAL'
    })
  }
}

// 创建单例实例
const emailNotificationService = new EmailNotificationService()
export default emailNotificationService