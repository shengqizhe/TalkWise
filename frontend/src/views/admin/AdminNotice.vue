<template>
  <div class="adm-notice">
    <div class="adm-title-row">
      <h1 class="adm-page-h1">系统通知列表</h1>
    </div>

    <div class="adm-table-block">
      <table class="adm-table">
        <thead>
          <tr>
            <th>消息内容</th>
            <th>消息类型</th>
            <th>时间</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="msg in notices" :key="msg.id">
            <td>{{ msg.content }}</td>
            <td>{{ msg.type }}</td>
            <td>{{ msg.time }}</td>
            <td><span class="adm-tag-orange">未读</span></td>
          </tr>
        </tbody>
      </table>
      <p v-if="notices.length === 0" class="adm-empty">
        暂无通知记录。实时消息推送请查看右上角铃铛；<br />
        通知持久化列表需后端消息服务接入后开放。
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";

// 后端暂无通知存储接口，先展示空列表
const notices = ref([]);
</script>
