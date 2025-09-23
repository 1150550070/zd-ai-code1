<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getDeployedAppUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)

// Agent 模式选择
const useAgentMode = ref(false)

// 我的应用数据
const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 精选应用数据
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

// 优化提示词功能已移除

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      // 跳转到对话页面，确保ID是字符串类型，并传递 agent 模式参数
      const appId = String(res.data.data)
      const query = useAgentMode.value ? { agent: 'true' } : {}
      await router.push({ path: `/app/chat/${appId}`, query })
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

// 加载精选应用
const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployedAppUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 格式化时间函数已移除，不再需要显示创建时间

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()

  // 鼠标跟随光效
  const handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)

  // 清理事件监听器
  return () => {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- 网站标题和描述 -->
      <div class="hero-section">
        <h1 class="hero-title">AI 应用生成平台</h1>
        <p class="hero-description">一句话轻松创建网站应用</p>
      </div>

      <!-- 用户提示词输入框 -->
      <div class="input-section">
        <!-- 模式选择 - 紧凑版 -->
        <div class="mode-selector-compact">
          <div class="mode-toggle-group">
            <a-tooltip placement="bottom" :mouse-enter-delay="0.3">
              <template #title>
                <div class="tooltip-content">
                  <div class="tooltip-title">⚡ 传统模式</div>
                  <div class="tooltip-features">
                    <div class="tooltip-feature">🚀 快速生成代码</div>
                    <div class="tooltip-feature">💡 简洁高效流程</div>
                    <div class="tooltip-feature">📝 专注文本内容</div>
                    <div class="tooltip-feature">⏱️ 响应速度快</div>
                  </div>
                  <div class="tooltip-desc">适合快速原型和简单项目</div>
                </div>
              </template>
              <button
                :class="['mode-btn', { active: !useAgentMode }]"
                @click="useAgentMode = false"
                :disabled="creating"
              >
                <span class="mode-icon">⚡</span>
                <span class="mode-text">传统模式</span>
                <div class="mode-btn-glow"></div>
              </button>
            </a-tooltip>

            <a-tooltip placement="bottom" :mouse-enter-delay="0.3">
              <template #title>
                <div class="tooltip-content">
                  <div class="tooltip-title">🎨 Agent模式</div>
                  <div class="tooltip-features">
                    <div class="tooltip-feature">🖼️ 智能图片搜集</div>
                    <div class="tooltip-feature">🎯 多媒体内容优化</div>
                    <div class="tooltip-feature">⚡ 并发处理技术</div>
                    <div class="tooltip-feature">🧠 AI工作流引擎</div>
                  </div>
                  <div class="tooltip-desc">适合复杂项目和视觉效果要求高的应用</div>
                </div>
              </template>
              <button
                :class="['mode-btn', { active: useAgentMode }]"
                @click="useAgentMode = true"
                :disabled="creating"
              >
                <span class="mode-icon">🎨</span>
                <span class="mode-text">Agent模式</span>
                <div class="mode-btn-glow"></div>
              </button>
            </a-tooltip>
          </div>
          <div class="mode-description-compact">
            {{ useAgentMode ? '智能图片搜集，效果更佳' : '快速生成，简单高效' }}
          </div>
        </div>

        <a-textarea
          v-model:value="userPrompt"
          placeholder="帮我创建个人博客网站"
          :rows="4"
          :maxlength="1000"
          class="prompt-input"
        />
        <div class="input-actions">
          <a-button type="primary" size="large" @click="createApp" :loading="creating">
            <template #icon>
              <span>↑</span>
            </template>
          </a-button>
        </div>
      </div>

      <!-- 快捷按钮 -->
      <div class="quick-actions">
        <a-button
          type="default"
          @click="
            setPrompt(
              '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
            )
          "
          >个人博客网站</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
            )
          "
          >企业官网</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
            )
          "
          >在线商城</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
            )
          "
          >作品展示网站</a-button
        >
      </div>

      <!-- 我的作品 -->
      <div class="section">
        <h2 class="section-title">我的作品</h2>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <h2 class="section-title">精选案例</h2>
        <div class="featured-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 8%, #e2e8f0 20%, #cbd5e1 100%),
    radial-gradient(circle at 20% 80%, rgba(59, 130, 246, 0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(139, 92, 246, 0.12) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(16, 185, 129, 0.08) 0%, transparent 50%);
  position: relative;
  overflow: hidden;
}

/* 科技感网格背景 */
#homePage::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    linear-gradient(rgba(59, 130, 246, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(139, 92, 246, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(139, 92, 246, 0.04) 1px, transparent 1px);
  background-size:
    100px 100px,
    100px 100px,
    20px 20px,
    20px 20px;
  pointer-events: none;
  animation: gridFloat 20s ease-in-out infinite;
}

/* 动态光效 */
#homePage::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(
      600px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
      rgba(59, 130, 246, 0.08) 0%,
      rgba(139, 92, 246, 0.06) 40%,
      transparent 80%
    ),
    linear-gradient(45deg, transparent 30%, rgba(59, 130, 246, 0.04) 50%, transparent 70%),
    linear-gradient(-45deg, transparent 30%, rgba(139, 92, 246, 0.04) 50%, transparent 70%);
  pointer-events: none;
  animation: lightPulse 8s ease-in-out infinite alternate;
}

/* 浮动粒子效果 */
.container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    radial-gradient(circle 2px at 20px 30px, rgba(59, 130, 246, 0.3), transparent),
    radial-gradient(circle 2px at 40px 70px, rgba(139, 92, 246, 0.3), transparent),
    radial-gradient(circle 1px at 90px 40px, rgba(16, 185, 129, 0.3), transparent),
    radial-gradient(circle 1px at 130px 80px, rgba(59, 130, 246, 0.3), transparent),
    radial-gradient(circle 2px at 160px 30px, rgba(139, 92, 246, 0.3), transparent);
  background-repeat: repeat;
  background-size: 200px 100px;
  animation: floatingParticles 25s linear infinite;
  pointer-events: none;
  z-index: 1;
}

@keyframes floatingParticles {
  0% {
    transform: translateY(0) translateX(0);
  }
  100% {
    transform: translateY(-100px) translateX(50px);
  }
}

@keyframes gridFloat {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(5px, 5px);
  }
}

@keyframes lightPulse {
  0% {
    opacity: 0.3;
  }
  100% {
    opacity: 0.7;
  }
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  position: relative;
  z-index: 2;
  width: 100%;
  box-sizing: border-box;
}

/* 移除居中光束效果 */

/* 英雄区域 */
.hero-section {
  text-align: center;
  padding: 80px 0 60px;
  margin-bottom: 28px;
  color: #1e293b;
  position: relative;
  overflow: hidden;
}

.hero-section::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(ellipse 800px 400px at center, rgba(59, 130, 246, 0.12) 0%, transparent 70%),
    linear-gradient(45deg, transparent 30%, rgba(139, 92, 246, 0.05) 50%, transparent 70%),
    linear-gradient(-45deg, transparent 30%, rgba(16, 185, 129, 0.04) 50%, transparent 70%);
  animation: heroGlow 10s ease-in-out infinite alternate;
}

@keyframes heroGlow {
  0% {
    opacity: 0.6;
    transform: scale(1);
  }
  100% {
    opacity: 1;
    transform: scale(1.02);
  }
}

@keyframes rotate {
  0% {
    transform: translate(-50%, -50%) rotate(0deg);
  }
  100% {
    transform: translate(-50%, -50%) rotate(360deg);
  }
}

.hero-title {
  font-size: 56px;
  font-weight: 700;
  margin: 0 0 20px;
  line-height: 1.2;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 50%, #10b981 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -1px;
  position: relative;
  z-index: 2;
  animation: titleShimmer 3s ease-in-out infinite;
}

@keyframes titleShimmer {
  0%,
  100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

.hero-description {
  font-size: 20px;
  margin: 0;
  opacity: 0.8;
  color: #64748b;
  position: relative;
  z-index: 2;
}

/* 紧凑模式选择器 */
.mode-selector-compact {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.mode-toggle-group {
  display: flex;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.98) 100%),
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.05) 0%, transparent 50%);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 6px;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.12),
    0 4px 16px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.4);
  position: relative;
  overflow: hidden;
}

.mode-toggle-group::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.3), rgba(139, 92, 246, 0.3), transparent);
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: none;
  background: transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  position: relative;
  overflow: hidden;
  letter-spacing: 0.3px;
}

.mode-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    linear-gradient(135deg, rgba(59, 130, 246, 0.08), rgba(139, 92, 246, 0.08)),
    radial-gradient(circle at center, rgba(255, 255, 255, 0.3) 0%, transparent 70%);
  opacity: 0;
  transition: all 0.4s ease;
  border-radius: 12px;
}

.mode-btn:hover::before {
  opacity: 1;
}

.mode-btn:hover {
  transform: translateY(-3px) scale(1.03);
  box-shadow:
    0 12px 30px rgba(59, 130, 246, 0.2),
    0 6px 16px rgba(0, 0, 0, 0.1);
  color: #3b82f6;
}

.mode-btn:hover .mode-btn-glow {
  opacity: 1;
  transform: scale(1.3);
}

.mode-btn.active {
  color: #ffffff;
  background:
    linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%),
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.2) 0%, transparent 50%);
  box-shadow:
    0 8px 25px rgba(59, 130, 246, 0.4),
    0 4px 12px rgba(139, 92, 246, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
  transform: translateY(-2px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.mode-btn.active::before {
  opacity: 0;
}

.mode-btn.active::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  animation: activeShimmer 2s ease-in-out infinite;
}

@keyframes activeShimmer {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}

.mode-btn.active .mode-btn-glow {
  opacity: 0.6;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.4) 0%, transparent 70%);
}

.mode-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.mode-btn-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.15) 0%, transparent 70%);
  opacity: 0;
  transition: all 0.5s ease;
  pointer-events: none;
  border-radius: 50%;
  transform: scale(0.8);
}

.mode-icon {
  font-size: 16px;
  display: flex;
  align-items: center;
}

.mode-text {
  font-weight: 600;
  letter-spacing: 0.5px;
}

.mode-description-compact {
  font-size: 13px;
  color: #475569;
  text-align: center;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: all 0.4s ease;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.8) 0%, rgba(248, 250, 252, 0.9) 100%);
  backdrop-filter: blur(10px);
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  box-shadow:
    0 4px 12px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  position: relative;
  overflow: hidden;
  margin-top: 4px;
}

.mode-description-compact::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.1), transparent);
  transition: left 0.6s ease;
}

.mode-description-compact:hover::before {
  left: 100%;
}

.mode-description-compact:hover {
  color: #3b82f6;
  transform: translateY(-1px);
  box-shadow:
    0 6px 16px rgba(59, 130, 246, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

/* Tooltip 样式 - 精致高级版 */
.tooltip-content {
  max-width: 300px;
  padding: 20px 24px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.98) 100%),
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.05) 0%, transparent 50%),
    radial-gradient(circle at bottom left, rgba(139, 92, 246, 0.05) 0%, transparent 50%);
  backdrop-filter: blur(25px);
  border-radius: 16px;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.12),
    0 8px 25px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.6);
  position: relative;
  overflow: hidden;
}

.tooltip-content::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.3), rgba(139, 92, 246, 0.3), transparent);
}

.tooltip-content::after {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background:
    radial-gradient(circle, rgba(59, 130, 246, 0.03) 0%, transparent 70%);
  animation: tooltipShimmer 8s ease-in-out infinite;
  pointer-events: none;
}

@keyframes tooltipShimmer {
  0%, 100% {
    opacity: 0.3;
    transform: rotate(0deg) scale(1);
  }
  50% {
    opacity: 0.6;
    transform: rotate(180deg) scale(1.1);
  }
}

.tooltip-title {
  font-size: 17px;
  font-weight: 700;
  background: linear-gradient(135deg, #1e293b 0%, #3b82f6 50%, #8b5cf6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 12px;
  text-align: center;
  letter-spacing: 0.5px;
  position: relative;
  z-index: 2;
}

.tooltip-features {
  margin-bottom: 12px;
  position: relative;
  z-index: 2;
}

.tooltip-feature {
  font-size: 14px;
  color: #1e293b;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  line-height: 1.6;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.tooltip-feature::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.1), transparent);
  transition: left 0.5s ease;
}

.tooltip-feature:hover::before {
  left: 100%;
}

.tooltip-feature:hover {
  background: rgba(255, 255, 255, 0.6);
  border-color: rgba(59, 130, 246, 0.2);
  transform: translateX(4px);
}

.tooltip-feature:last-child {
  margin-bottom: 0;
}

.tooltip-desc {
  font-size: 13px;
  color: #475569;
  text-align: center;
  font-style: italic;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(226, 232, 240, 0.6);
  font-weight: 500;
  position: relative;
  z-index: 2;
  background: rgba(248, 250, 252, 0.5);
  padding: 12px;
  border-radius: 8px;
  backdrop-filter: blur(10px);
}

/* Ant Design Tooltip 全局样式覆盖 - 精致高级版 */
:deep(.ant-tooltip) {
  z-index: 9999;
  filter: drop-shadow(0 10px 40px rgba(0, 0, 0, 0.15));
}

:deep(.ant-tooltip-inner) {
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.98) 100%) !important;
  backdrop-filter: blur(25px) !important;
  border-radius: 16px !important;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.12),
    0 8px 25px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.9) !important;
  border: 1px solid rgba(255, 255, 255, 0.6) !important;
  color: #0f172a !important;
  font-weight: 500;
  padding: 0 !important;
  min-height: auto !important;
  position: relative;
  overflow: hidden;
}

:deep(.ant-tooltip-inner::before) {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.3), rgba(139, 92, 246, 0.3), transparent);
}

:deep(.ant-tooltip-inner::after) {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.03) 0%, transparent 70%);
  animation: tooltipShimmer 8s ease-in-out infinite;
  pointer-events: none;
}

:deep(.ant-tooltip-arrow) {
  display: none !important;
}

/* 工具提示入场动画 */
:deep(.ant-tooltip) {
  animation: tooltipFadeIn 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes tooltipFadeIn {
  0% {
    opacity: 0;
    transform: translateY(10px) scale(0.9);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 输入区域 */
.input-section {
  position: relative;
  margin: 0 auto 24px;
  max-width: 800px;
}

.prompt-input {
  border-radius: 16px;
  border: none;
  font-size: 16px;
  padding: 20px 60px 20px 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.prompt-input::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.1), transparent);
  transition: left 0.6s ease;
}

.prompt-input:hover::before {
  left: 100%;
}

.prompt-input:focus {
  background: rgba(255, 255, 255, 1);
  box-shadow:
    0 15px 50px rgba(0, 0, 0, 0.2),
    0 0 0 3px rgba(59, 130, 246, 0.1);
  transform: translateY(-3px) scale(1.01);
}

.prompt-input:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 45px rgba(0, 0, 0, 0.18);
}

.input-actions {
  position: absolute;
  bottom: 12px;
  right: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 快捷按钮 */
.quick-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 60px;
  flex-wrap: wrap;
}

.quick-actions .ant-btn {
  border-radius: 25px;
  padding: 10px 24px;
  height: auto;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(59, 130, 246, 0.15);
  color: #475569;
  backdrop-filter: blur(20px);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  font-weight: 500;
  letter-spacing: 0.3px;
}

.quick-actions .ant-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.15), transparent);
  transition: left 0.6s ease;
}

.quick-actions .ant-btn::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.1) 0%, transparent 70%);
  transition: all 0.4s ease;
  transform: translate(-50%, -50%);
  border-radius: 50%;
}

.quick-actions .ant-btn:hover::before {
  left: 100%;
}

.quick-actions .ant-btn:hover::after {
  width: 200px;
  height: 200px;
}

.quick-actions .ant-btn:hover {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(59, 130, 246, 0.4);
  color: #3b82f6;
  transform: translateY(-3px) scale(1.02);
  box-shadow:
    0 10px 30px rgba(59, 130, 246, 0.2),
    0 0 0 1px rgba(59, 130, 246, 0.1);
}

.quick-actions .ant-btn:active {
  transform: translateY(-1px) scale(0.98);
}

/* 区域标题 */
.section {
  margin-bottom: 60px;
  position: relative;
}

.section::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.3), transparent);
  animation: sectionGlow 4s ease-in-out infinite;
}

.section-title {
  font-size: 32px;
  font-weight: 600;
  margin-bottom: 32px;
  color: #1e293b;
  position: relative;
  display: inline-block;
  overflow: hidden;
}

.section-title::before {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 0;
  height: 3px;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6, #10b981);
  transition: width 0.6s ease;
}

.section:hover .section-title::before {
  width: 100%;
}

.section-title::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  transition: left 0.8s ease;
}

.section:hover .section-title::after {
  left: 100%;
}

@keyframes sectionGlow {
  0%, 100% {
    opacity: 0.3;
    transform: scaleX(0.8);
  }
  50% {
    opacity: 0.8;
    transform: scaleX(1.2);
  }
}

/* 我的作品网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
  animation: fadeInUp 0.8s ease-out;
}

/* 精选案例网格 */
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
  animation: fadeInUp 1s ease-out 0.2s both;
}

@keyframes fadeInUp {
  0% {
    opacity: 0;
    transform: translateY(30px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
  position: relative;
}

.pagination-wrapper::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 200px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.3), transparent);
  transform: translate(-50%, -50%);
  z-index: -1;
}

/* 分页组件美化 */
.pagination-wrapper :deep(.ant-pagination) {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination-wrapper :deep(.ant-pagination-item) {
  border-radius: 8px;
  border: 1px solid rgba(59, 130, 246, 0.2);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  transition: all 0.3s ease;
}

.pagination-wrapper :deep(.ant-pagination-item:hover) {
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(255, 255, 255, 1);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
}

.pagination-wrapper :deep(.ant-pagination-item-active) {
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  border-color: transparent;
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 15px rgba(59, 130, 246, 0.3);
}

.pagination-wrapper :deep(.ant-pagination-prev),
.pagination-wrapper :deep(.ant-pagination-next) {
  border-radius: 8px;
  border: 1px solid rgba(59, 130, 246, 0.2);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  transition: all 0.3s ease;
}

.pagination-wrapper :deep(.ant-pagination-prev:hover),
.pagination-wrapper :deep(.ant-pagination-next:hover) {
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(255, 255, 255, 1);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }

  .hero-description {
    font-size: 16px;
  }

  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    justify-content: center;
  }
}
</style>
