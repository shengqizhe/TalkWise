<template>
  <router-view />
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useWebSocket } from './composables/useWebSocket'
import { useUserStore } from './stores/user'

const { connect, disconnect } = useWebSocket()
const userStore = useUserStore()

// 应用启动时连接WebSocket
onMounted(async () => {
  // 如果有token但没有用户信息，先尝试获取用户信息
  if (userStore.token && !userStore.userInfo) {
    try {
      await userStore.getUserInfoAction()
      console.log('用户信息恢复成功')
    } catch (error) {
      console.error('用户信息恢复失败:', error)
    }
  }
  
  // 等待用户信息加载完成后再连接WebSocket
  if (userStore.userInfo?.id) {
    try {
      await connect()
      console.log('WebSocket自动连接成功')
    } catch (error) {
      console.error('WebSocket自动连接失败:', error)
    }
  } else {
    console.log('用户未登录，跳过WebSocket连接')
  }
})

// 应用卸载时断开WebSocket连接
onUnmounted(() => {
  disconnect()
})
</script>

<style>
html,
body {
  margin: 0;
  padding: 0;
  height: 100%;
}

#app {
  height: 100%;
}
</style>
