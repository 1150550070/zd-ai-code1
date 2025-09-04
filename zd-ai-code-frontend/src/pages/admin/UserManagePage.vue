<template>
  <div id="userManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />

    <!-- 表格 -->

    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #headerCell="{ column }">
        <template v-if="column.key === 'name'">
          <span>
            <smile-outlined />
            Name
          </span>
        </template>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image :src="record.userAvatar" :width="120" />
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <div v-if="record.userRole === 'admin'">
            <a-tag color="green">管理员</a-tag>
          </div>
          <div v-else>
            <a-tag color="blue">普通用户</a-tag>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" @click="doEdit(record)">编辑</a-button>
            <a-button danger @click="doDelete(record.id)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 编辑用户弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      title="编辑用户信息"
      @ok="handleEditOk"
      @cancel="handleEditCancel"
      :confirm-loading="editLoading"
    >
      <a-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        layout="vertical"
      >
        <a-form-item label="用户名" name="userName">
          <a-input v-model:value="editForm.userName" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="用户简介" name="userProfile">
          <a-textarea
            v-model:value="editForm.userProfile"
            placeholder="请输入用户简介"
            :rows="3"
          />
        </a-form-item>
        <a-form-item label="用户角色" name="userRole">
          <a-select v-model:value="editForm.userRole" placeholder="请选择用户角色">
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { SmileOutlined, DownOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { deleteUser, listUserVoByPage, updateUser } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { reactive } from 'vue'

const columns = [
  {
    title: 'id',
    dataIndex: 'id',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
  },
  {
    title: '用户名',
    dataIndex: 'userName',
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '操作',
    key: 'action',


},
]

// 数据
const data = ref<API.UserVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 获取数据
const fetchData = async () => {
  const res = await listUserVoByPage({
    ...searchParams,
  })
  if (res.data.data) {
    data.value = res.data.data.records ?? []
    total.value = res.data.data.totalRow ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}
// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})
// 表格变化处理
const doTableChange = (page: any) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})

// 获取数据
const doSearch = () => {
  // 重置页码
  searchParams.pageNum = 1
  fetchData()
}

// 删除数据
const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  const res = await deleteUser({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}

// 编辑用户相关
const editModalVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref()
const editForm = reactive<API.UserUpdateRequest>({
  id: undefined,
  userName: '',
  userProfile: '',
  userRole: 'user',
})

// 编辑表单验证规则 - 参考后端校验逻辑
const editRules = {
  userName: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 1, max: 50, message: '用户名长度在1到50个字符之间', trigger: 'blur' }
  ],
  userProfile: [
    { max: 500, message: '用户简介不能超过500个字符', trigger: 'blur' }
  ],
  userRole: [
    { required: true, message: '请选择用户角色', trigger: 'change' },
    {
      validator: (rule: any, value: string) => {
        if (!['user', 'admin'].includes(value)) {
          return Promise.reject('用户角色必须是user或admin')
        }
        return Promise.resolve()
      },
      trigger: 'change'
    }
  ]
}

// 打开编辑弹窗
const doEdit = (record: API.UserVO) => {
  editForm.id = record.id
  editForm.userName = record.userName || ''
  editForm.userProfile = record.userProfile || ''
  editForm.userRole = record.userRole || 'user'
  editModalVisible.value = true
}

// 编辑确认
const handleEditOk = async () => {
  try {
    // 表单验证
    await editFormRef.value.validate()

    // 参数校验 - 参考后端逻辑
    if (!editForm.userName || editForm.userName.trim() === '') {
      message.error('用户名不能为空')
      return
    }

    if (editForm.userName.length > 50) {
      message.error('用户名过长')
      return
    }

    if (editForm.userProfile && editForm.userProfile.length > 500) {
      message.error('用户简介过长')
      return
    }

    if (!['user', 'admin'].includes(editForm.userRole)) {
      message.error('用户角色无效')
      return
    }

    editLoading.value = true

    const res = await updateUser(editForm)
    if (res.data.code === 0) {
      message.success('编辑成功')
      editModalVisible.value = false
      // 刷新数据
      fetchData()
    } else {
      message.error('编辑失败：' + res.data.message)
    }
  } catch (error) {
    console.error('表单验证失败:', error)
  } finally {
    editLoading.value = false
  }
}

// 编辑取消
const handleEditCancel = () => {
  editModalVisible.value = false
  editFormRef.value?.resetFields()
}
</script>

<style scoped></style>
