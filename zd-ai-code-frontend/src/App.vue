<script setup lang="ts">
import { inject } from 'vue'
import { ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { healthCheck } from '@/api/healthController'

// 注入中文语言配置，如果注入失败则使用默认的中文配置
const locale = inject('antdLocale', zhCN)

healthCheck().then((res) => {
  console.log(res)
})
</script>

<template>
  <ConfigProvider :locale="zhCN">
    <BasicLayout />
  </ConfigProvider>
</template>

<style></style>
