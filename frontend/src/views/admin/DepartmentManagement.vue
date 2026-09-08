<template>
  <div class="department-management">
    <!-- 系别管理欢迎卡片 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="welcome-card">
          <div class="welcome-content">
            <div class="welcome-text">
              <h2>系别管理</h2>
              <p>这里可以管理所有系别的信息</p>
            </div>
            <div class="welcome-avatar">
              <el-avatar
                :size="80"
                :src="userStore.userInfo?.avatar"
                @click="openProfileDialog"
              >
                {{
                  userStore.userInfo?.realName?.charAt(0) ||
                  userStore.userInfo?.username?.charAt(0)
                }}
              </el-avatar>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-row :gutter="20" style="margin: 20px 0">
      <el-col :span="6">
        <el-input
          v-model="query.keyword"
          placeholder="请输入系别名称搜索"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch" type="primary">搜索</el-button>
          </template>
        </el-input>
      </el-col>
      <el-col :span="4">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="openAddDialog">新增系别</el-button>
      </el-col>
    </el-row>

    <!-- 系别表格 -->
    <el-table :data="departmentList" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="departmentName" label="系别名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="createdTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="200">
        <template #default="scope">
          <el-button
            size="small"
            type="primary"
            @click="openEditDialog(scope.row)"
            >编辑</el-button
          >
          <el-button size="small" type="danger" @click="handleDelete(scope.row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      style="margin-top: 20px; text-align: right"
      background
      layout="prev, pager, next, jumper"
      :total="total"
      :page-size="query.size"
      :current-page="query.page"
      @current-change="handleCurrentChange"
    />

    <!-- 新增/编辑系别弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="系别名称" prop="departmentName">
          <el-input v-model="form.departmentName" placeholder="请输入系别名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 个人信息对话框 -->
    <el-dialog v-model="profileDialogVisible" title="个人信息" width="500px">
      <el-form
        ref="profileFormRef"
        :model="profileForm"
        :rules="profileRules"
        label-width="100px"
      >
        <el-form-item label="头像">
          <div class="avatar-upload-container">
            <el-avatar
              :size="100"
              :src="profileForm.avatar || userStore.userInfo?.avatar"
            >
              {{
                userStore.userInfo?.realName?.charAt(0) ||
                userStore.userInfo?.username?.charAt(0)
              }}
            </el-avatar>
          </div>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="profileForm.username"
            placeholder="请输入用户名"
            disabled
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input
            v-model="profileForm.realName"
            placeholder="请输入真实姓名"
            disabled
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="profileDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 引入Vue相关API
import { ref, reactive, onMounted } from "vue";
// 引入Element Plus消息组件
import { ElMessage, ElMessageBox } from "element-plus";
// 引入用户存储
import { useUserStore } from "../../stores/user";
// 引入系别API
import { fetchAllDepartments, searchDepartments, createDepartment, updateDepartment, deleteDepartment } from "../../api/department";

// 获取用户存储
const userStore = useUserStore();

// 个人信息相关
const profileDialogVisible = ref(false);
const profileForm = reactive({
  id: "",
  username: "",
  realName: "",
  avatar: "",
});

// 表单校验规则
const profileRules = {};
const profileFormRef = ref(null);

// 打开个人信息对话框
const openProfileDialog = () => {
  // 填充表单数据
  Object.assign(profileForm, userStore.userInfo);
  profileDialogVisible.value = true;
};

// 系别列表数据
const departmentList = ref([]); // 系别列表
const total = ref(0); // 总数
const loading = ref(false); // 加载状态
const query = reactive({
  keyword: "", // 搜索关键字
  page: 1, // 当前页码
  size: 10, // 每页数量
});

// 弹窗相关
const dialogVisible = ref(false); // 弹窗显示状态
const dialogTitle = ref(""); // 弹窗标题
const isAdd = ref(true); // 是否为新增
const form = reactive({
  id: null,
  departmentName: "",
  description: "",
});
const formRef = ref(null);

// 校验规则
const rules = {
  departmentName: [{ required: true, message: "请输入系别名称", trigger: "blur" }],
};

// 获取系别列表
const getDepartmentList = async () => {
  loading.value = true;
  try {
    // 调用API获取系别列表
    let res;
    if (query.keyword && query.keyword.trim() !== '') {
      // 如果有搜索关键字，则调用搜索API
      res = await searchDepartments(query.keyword);
    } else {
      // 否则获取所有系别
      res = await fetchAllDepartments();
    }
    
    if (res && res.data) {
      // 处理返回的数据
      departmentList.value = res.data;
      // 如果后端返回了总数，则使用后端返回的总数
      total.value = res.data.length;
    } else {
      departmentList.value = [];
      total.value = 0;
    }
  } catch (error) {
    console.error("获取系别列表失败:", error);
    ElMessage.error("获取系别列表失败");
  } finally {
    loading.value = false;
  }
};

// 搜索处理
const handleSearch = () => {
  query.page = 1;
  getDepartmentList();
};

// 重置搜索
const handleReset = () => {
  query.keyword = "";
  query.page = 1;
  getDepartmentList();
};

// 分页处理
const handleCurrentChange = (page) => {
  query.page = page;
  getDepartmentList();
};

// 打开新增弹窗
const openAddDialog = () => {
  dialogTitle.value = "新增系别";
  isAdd.value = true;
  Object.assign(form, {
    id: null,
    departmentName: "",
    description: "",
  });
  dialogVisible.value = true;
};

// 打开编辑弹窗
const openEditDialog = (row) => {
  dialogTitle.value = "编辑系别";
  isAdd.value = false;
  // 确保表单字段与后端实体类字段一致
  Object.assign(form, {
    id: row.id,
    departmentName: row.departmentName,
    description: row.description
  });
  dialogVisible.value = true;
};

// 提交表单
const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return;

    try {
      if (isAdd.value) {
        // 新增系别
        const res = await createDepartment(form);
        // 使用后端返回的消息
        ElMessage.success(res.message || "新增系别成功");
      } else {
        // 编辑系别
        await updateDepartment(form);
        ElMessage.success("修改系别成功");
      }
      dialogVisible.value = false;
      getDepartmentList();
    } catch (error) {
      console.error("操作失败:", error);
      ElMessage.error(error.response?.data?.message || "操作失败");
    }
  });
};

// 删除系别
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除系别【${row.departmentName}】吗？`,
    "提示",
    { type: "warning" },
  ).then(async () => {
    try {
      await deleteDepartment(row.id);
      ElMessage.success("删除系别成功");
      await getDepartmentList();
    } catch (error) {
      console.error("删除失败:", error);
      ElMessage.error(error.response?.data?.message || "删除失败");
    }
  });
};

// 页面加载时获取数据
onMounted(() => {
  getDepartmentList();
});
</script>

<style scoped>
.department-management {
  padding: 20px;
}
.welcome-card {
  background: linear-gradient(135deg, #000 0%, #333 100%);
  color: white;
}
.welcome-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.welcome-text h2 {
  margin: 0 0 10px 0;
  font-size: 24px;
}
.welcome-text p {
  margin: 0;
  opacity: 0.9;
}
</style>