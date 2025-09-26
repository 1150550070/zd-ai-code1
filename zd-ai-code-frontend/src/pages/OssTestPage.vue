<template>
  <div class="oss-test-page">
    <h2>OSS图片访问测试</h2>

    <div class="test-section">
      <h3>OSS配置信息</h3>
      <div v-if="ossConfig">
        <p><strong>Host:</strong> {{ ossConfig.host }}</p>
        <p><strong>Bucket:</strong> {{ ossConfig.bucket }}</p>
        <p><strong>Region:</strong> {{ ossConfig.region }}</p>
        <p><strong>示例URL:</strong> {{ ossConfig.sampleUrl }}</p>
      </div>
      <a-button @click="loadOssConfig">获取OSS配置</a-button>
    </div>

    <div class="test-section">
      <h3>URL测试</h3>
      <a-input
        v-model:value="testKey"
        placeholder="输入OSS对象键，例如：screenshots/2025/09/10/test.jpg"
        style="margin-bottom: 10px;"
      />
      <a-button @click="testUrl">测试URL生成</a-button>

      <div v-if="urlTestResult" style="margin-top: 10px;">
        <p><strong>标准URL:</strong> {{ urlTestResult.standardUrl }}</p>
        <p><strong>备选URL:</strong> {{ urlTestResult.alternativeUrl }}</p>
      </div>
    </div>

    <div class="test-section">
      <h3>图片直接访问测试</h3>
      <a-input
        v-model:value="imageUrl"
        placeholder="输入完整的OSS图片URL"
        style="margin-bottom: 10px;"
      />
      <a-button @click="testImage">测试图片</a-button>

      <div v-if="imageUrl" style="margin-top: 10px;">
        <p>测试图片加载：</p>
        <img
          :src="imageUrl"
          alt="测试图片"
          @load="onImageLoad"
          @error="onImageError"
          style="max-width: 300px; border: 1px solid #ccc;"
        />
        <p v-if="imageStatus" :style="{ color: imageStatus.includes('成功') ? 'green' : 'red' }">
          {{ imageStatus }}
        </p>
      </div>
    </div>

    <div class="test-section">
      <h3>应用封面测试</h3>
      <div v-if="sampleApps.length > 0">
        <div v-for="app in sampleApps" :key="app.id" style="margin-bottom: 20px; border: 1px solid #eee; padding: 10px;">
          <p><strong>应用:</strong> {{ app.appName }}</p>
          <p><strong>封面URL:</strong> {{ app.cover }}</p>
          <div v-if="app.cover">
            <img
              :src="app.cover"
              :alt="app.appName"
              @load="() => onAppImageLoad(app.id)"
              @error="() => onAppImageError(app.id)"
              style="max-width: 200px; border: 1px solid #ccc;"
            />
            <p :style="{ color: appImageStatus[app.id]?.includes('成功') ? 'green' : 'red' }">
              {{ appImageStatus[app.id] || '等待加载...' }}
            </p>
          </div>
        </div>
      </div>
      <a-button @click="loadSampleApps">加载示例应用</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import request from '@/request'
import { listMyAppVoByPage } from '@/api/appController'

const ossConfig = ref<any>(null)
const testKey = ref('screenshots/2025/09/10/test.jpg')
const urlTestResult = ref<any>(null)
const imageUrl = ref('')
const imageStatus = ref('')
const sampleApps = ref<any[]>([])
const appImageStatus = reactive<Record<string, string>>({})

// 加载OSS配置
const loadOssConfig = async () => {
  try {
    const response = await request.get('/oss/debug/config')
    if (response.data.code === 0) {
      ossConfig.value = response.data.data
      message.success('OSS配置加载成功')
    }
  } catch (error) {
    console.error('加载OSS配置失败:', error)
    message.error('加载OSS配置失败')
  }
}

// 测试URL生成
const testUrl = async () => {
  try {
    const response = await request.get('/oss/debug/test-url', {
      params: { key: testKey.value }
    })
    if (response.data.code === 0) {
      urlTestResult.value = response.data.data
      message.success('URL生成测试完成')
    }
  } catch (error) {
    console.error('URL测试失败:', error)
    message.error('URL测试失败')
  }
}

// 测试图片
const testImage = () => {
  imageStatus.value = '正在加载...'
}

// 图片加载成功
const onImageLoad = () => {
  imageStatus.value = '图片加载成功！'
  message.success('图片加载成功')
}

// 图片加载失败
const onImageError = (event: Event) => {
  imageStatus.value = '图片加载失败！'
  console.error('图片加载失败:', event)
  message.error('图片加载失败')
}

// 应用图片加载成功
const onAppImageLoad = (appId: string) => {
  appImageStatus[appId] = '封面加载成功！'
}

// 应用图片加载失败
const onAppImageError = (appId: string) => {
  appImageStatus[appId] = '封面加载失败！'
}

// 加载示例应用
const loadSampleApps = async () => {
  try {
    const res = await listMyAppVoByPage({
      pageNum: 1,
      pageSize: 3,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      sampleApps.value = res.data.data.records || []
      message.success('示例应用加载成功')
    }
  } catch (error) {
    console.error('加载示例应用失败:', error)
    message.error('加载示例应用失败')
  }
}
</script>

<style scoped>
.oss-test-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.test-section {
  margin-bottom: 30px;
  padding: 20px;
  border: 1px solid #eee;
  border-radius: 8px;
}

.test-section h3 {
  margin-top: 0;
  color: #1890ff;
}
</style>
