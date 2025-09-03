import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
// 导入中文语言包
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import App from './App.vue'
import router from './router'

// 设置dayjs为中文
dayjs.locale('zh-cn')

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

// 全局提供中文语言配置
app.provide('antdLocale', zhCN)

app.mount('#app')
