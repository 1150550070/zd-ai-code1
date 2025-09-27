<template>
  <div class="user-profile-page">
    <a-card title="个人信息" :bordered="false">
      <a-form
        ref="profileFormRef"
        :model="profileForm"
        :rules="profileRules"
        layout="vertical"
        @finish="handleUpdateProfile"
      >
        <a-row :gutter="24">
          <a-col :span="12">
            <a-form-item label="用户账号">
              <a-input
                v-model:value="displayUserInfo.userAccount"
                disabled
                placeholder="用户账号不可修改"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="用户名" name="userName">
              <a-input
                v-model:value="profileForm.userName"
                placeholder="请输入用户名"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="24">
          <a-col :span="24">
            <a-form-item label="用户简介" name="userProfile">
              <a-textarea
                v-model:value="profileForm.userProfile"
                placeholder="请输入用户简介"
                :rows="4"
                show-count
                :maxlength="500"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="24">
          <a-col :span="12">
            <a-form-item label="用户角色">
              <a-tag v-if="(profileForm as any).userRole === 'admin'" color="green">
                管理员
              </a-tag>
              <a-tag v-else color="blue">
                普通用户
              </a-tag>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="注册时间">
              <span>{{ formatTime((profileForm as any).createTime) }}</span>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="updateLoading">
              保存修改
            </a-button>
            <a-button @click="resetForm">
              重置
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { updateMyUser } from '@/api/userController'
import dayjs from 'dayjs'

const loginUserStore = useLoginUserStore()
const profileFormRef = ref()
const updateLoading = ref(false)

// 个人信息表单
const profileForm = reactive<API.UserUpdateMyRequest>({
  userName: '',
  userProfile: '',
})

// 显示用的完整用户信息
const displayUserInfo = reactive({
  userAccount: '',
  userRole: '',
  createTime: '',
})

// 表单验证规则
const profileRules = {
  userName: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 1, max: 50, message: '用户名长度在1到50个字符之间', trigger: 'blur' }
  ],
  userProfile: [
    { max: 500, message: '用户简介不能超过500个字符', trigger: 'blur' }
  ]
}

// 格式化时间
const formatTime = (time: string) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

// 加载用户信息
const loadUserInfo = async () => {
  await loginUserStore.fetchLoginUser()
  const user = loginUserStore.loginUser

  if (user) {
    profileForm.userName = user.userName || ''
    profileForm.userProfile = user.userProfile || ''

    displayUserInfo.userAccount = user.userAccount || ''
    displayUserInfo.userRole = user.userRole || ''
    displayUserInfo.createTime = user.createTime || ''
  }
}

// 更新个人信息
const handleUpdateProfile = async () => {
  try {
    // 参数校验
    if (!profileForm.userName || profileForm.userName.trim() === '') {
      message.error('用户名不能为空')
      return
    }

    if (profileForm.userName.length > 50) {
      message.error('用户名过长')
      return
    }

    if (profileForm.userProfile && profileForm.userProfile.length > 500) {
      message.error('用户简介过长')
      return
    }

    updateLoading.value = true

    const res = await updateMyUser(profileForm)
    if (res.data.code === 0) {
      message.success('更新成功')
      // 重新获取用户信息
      await loadUserInfo()
    } else {
      message.error('更新失败：' + res.data.message)
    }
  } catch (error) {
    console.error('更新失败:', error)
    message.error('更新失败')
  } finally {
    updateLoading.value = false
  }
}

// 重置表单
const resetForm = () => {
  loadUserInfo()
}

// 页面加载时获取用户信息
onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.user-profile-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.ant-card {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>
