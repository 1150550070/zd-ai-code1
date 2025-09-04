<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.png" alt="Logo" />
            <h1 class="site-title">慧搭应用生成</h1>
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
                {{ loginUserStore.loginUser.userName || '无名' }}
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="goToProfile">
                    <UserOutlined></UserOutlined>
                    个人中心
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined></LogoutOutlined>
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
import { h, ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { LogoutOutlined, HomeOutlined, UserOutlined } from '@ant-design/icons-vue'
import { logout as userLogout } from '@/api/userController.ts'
import checkAccess from '@/access/checkAccess'
import ACCESS_ENUM from '@/access/accessEnum'

//获取登录用户状态
const loginUserStore = useLoginUserStore()

// 监听页面焦点事件，当用户从 Swagger 切换回页面时自动检查登录状态
const handleFocus = async () => {
  await loginUserStore.fetchLoginUser()
}

// 组件挂载时获取登录用户信息
onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  window.addEventListener('focus', handleFocus)
})

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('focus', handleFocus)
})

const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
router.afterEach((to, from, next) => {
  selectedKeys.value = [to.path]
})



// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 跳转到个人中心
const goToProfile = () => {
  router.push('/user/profile')
}

//退出登录
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
      userRole: 'user', // 确保退出登录后角色重置为普通用户
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败' + res.data.message)
  }
}

// 路由菜单配置
const routes = [
  {
    path: '/',
    name: '主页',
    access: ACCESS_ENUM.NOT_LOGIN,
    icon: HomeOutlined,
  },
  {
    path: '/admin/userManage',
    name: '用户管理',
    access: ACCESS_ENUM.ADMIN,
    icon: UserOutlined,
  },
]

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => {
  const loginUser = loginUserStore.loginUser

  return routes
    .filter((route) => checkAccess(loginUser, route.access))
    .map((route) => ({
      key: route.path,
      icon: () => h(route.icon),
      label: route.name,
      title: route.name,
    }))
})
</script>

<style scoped>
.header {
  background: #fff;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 48px;
  width: 48px;
}

.site-title {
  margin: 0;
  font-size: 18px;
  color: #1890ff;
}

.ant-menu-horizontal {
  border-bottom: none !important;
}
</style>
