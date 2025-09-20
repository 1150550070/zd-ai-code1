<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.png" alt="Logo" />
            <h1 class="site-title">慧搭AI应用生成</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <a-space>
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { logout } from '@/api/userController.ts'
import { LogoutOutlined, HomeOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
router.afterEach((to, from, next) => {
  selectedKeys.value = [to.path]
})

// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },

]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 退出登录
const doLogout = async () => {
  const res = await logout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  padding: 0 24px;
  border-bottom: 1px solid rgba(102, 126, 234, 0.2);
  box-shadow: 0 2px 20px rgba(102, 126, 234, 0.1);
  position: relative;
  z-index: 10;
}

.header::before {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(165, 180, 252, 0.4), transparent);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.3s ease;
}

.header-left:hover {
  transform: translateY(-1px);
}

.logo {
  height: 48px;
  width: 48px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
}

.logo:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.site-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #a855f7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  transition: all 0.3s ease;
}

.site-title:hover {
  transform: translateX(2px);
}

.ant-menu-horizontal {
  border-bottom: none !important;
  background: transparent !important;
}

.user-login-status {
  display: flex;
  align-items: center;
}

/* 美化登录按钮 */
:deep(.ant-btn-primary) {
  background: linear-gradient(135deg, #a5b4fc 0%, #c7d2fe 100%);
  border: none;
  border-radius: 20px;
  padding: 4px 20px;
  height: auto;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(165, 180, 252, 0.25);
  transition: all 0.3s ease;
  color: #4338ca;
}

:deep(.ant-btn-primary:hover) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(165, 180, 252, 0.35);
  background: linear-gradient(135deg, #8b5cf6 0%, #a855f7 100%);
  color: white;
}

/* 美化用户头像区域 */
:deep(.ant-avatar) {
  border: 2px solid rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
}

:deep(.ant-avatar:hover) {
  border-color: #667eea;
  transform: scale(1.05);
}

/* 美化菜单项 */
:deep(.ant-menu-item) {
  border-radius: 8px;
  margin: 0 4px;
  transition: all 0.3s ease;
}

:deep(.ant-menu-item:hover) {
  background: rgba(102, 126, 234, 0.1) !important;
  color: #667eea !important;
}

:deep(.ant-menu-item-selected) {
  background: rgba(102, 126, 234, 0.15) !important;
  color: #667eea !important;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header {
    padding: 0 16px;
  }

  .site-title {
    font-size: 16px;
  }

  .logo {
    height: 40px;
    width: 40px;
  }
}

@media (max-width: 480px) {
  .site-title {
    display: none; /* 在小屏幕上隐藏标题 */
  }
}
</style>
