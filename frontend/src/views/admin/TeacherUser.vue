<template>
  <div class="teacher-user">
    <!-- 教师用户管理欢迎卡片 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="welcome-card">
          <div class="welcome-content">
            <div class="welcome-text">
              <h2>教师用户管理</h2>
              <p>这里可以管理所有教师用户的信息</p>
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
    <el-row :gutter="20" class="search-row">
      <el-col :lg="6" :md="6" :sm="12" :xl="6" :xs="24" class="search-item">
        <el-input
          v-model="query.keyword"
          placeholder="请输入用户名、姓名或教师编号搜索"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch" type="primary">搜索</el-button>
          </template>
        </el-input>
      </el-col>
      <el-col :lg="4" :md="4" :sm="12" :xl="4" :xs="24" class="search-item">
        <el-select
          v-model="query.departmentId"
          placeholder="选择系别"
          clearable
          @change="handleSearch"
        >
          <el-option
            v-for="dept in departmentList"
            :key="dept.id"
            :label="dept.departmentName"
            :value="dept.id"
          />
        </el-select>
      </el-col>
      <el-col :lg="4" :md="4" :sm="12" :xl="4" :xs="24" class="search-item button-container">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="openAddDialog">新增教师</el-button>
      </el-col>

      <!-- 导入导出按钮 -->
      <el-col :lg="10" :md="10" :sm="24" :xl="10" :xs="24" class="button-group">
        <el-button type="primary" @click="importDialogVisible = true"
          >导入名单</el-button
        >
        <el-dropdown @command="handleExportCommand">
          <el-button type="success">
            导出名单
            <el-icon class="el-icon--right">
              <arrow-down/>
            </el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="current">导出当前页面</el-dropdown-item>
              <el-dropdown-item command="all">导出全部教师</el-dropdown-item>
              <el-dropdown-item command="selected">导出选中教师</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- 下载模板按钮 -->
        <el-button @click="handleDownloadTemplate">下载模板</el-button>
      </el-col>
    </el-row>

    <!-- 教师用户表格 -->
    <el-table
        ref="teacherTableRef"
        v-loading="loading"
        :data="teacherList"
        border
        @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55"/>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="realName" label="姓名" />
      <el-table-column prop="studentTeacherId" label="教师编号" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="phone" label="电话" />
      <el-table-column label="系别" width="120">
        <template #default="scope">
          <span>{{ getDepartmentName(scope.row.departmentId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="scope">
          <el-button
            size="small"
            type="primary"
            @click="openEditDialog(scope.row)"
            >编辑</el-button
          >
          <el-button
            size="small"
            type="warning"
            @click="handleResetPassword(scope.row)"
            >重置密码</el-button
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

    <!-- 新增/编辑教师弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="isAdd">
          <el-input
            v-model="form.password"
            placeholder="请输入密码，留空则使用默认密码123456"
            type="password"
          />
        </el-form-item>
        <el-form-item label="工号" prop="studentTeacherId">
          <el-input
            v-model="form.studentTeacherId"
            placeholder="请输入工号，留空将自动生成"
          />
        </el-form-item>
        <el-form-item label="系别" prop="departmentId">
          <el-select
            v-model="form.departmentId"
            placeholder="请选择系别"
            clearable
          >
            <el-option
              v-for="dept in departmentList"
              :key="dept.id"
              :label="dept.departmentName"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入教师名单弹窗 -->
    <el-dialog
        title="导入教师名单"
      v-model="importDialogVisible"
        width="650px"
    >
      <ExcelImporter
          :import-url="'/teacher/import-json'"
          @import-success="handleImportSuccess"
          @import-error="handleImportError"
          @import-possible-success="handleImportPossibleSuccess"
      />
      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
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
        <el-form-item label="管理员ID" prop="studentTeacherId">
          <el-input
            v-model="profileForm.studentTeacherId"
            placeholder="请输入管理员ID"
            disabled
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="profileForm.email"
            placeholder="请输入邮箱"
            disabled
          />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="profileForm.phone"
            placeholder="请输入手机号"
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
// 引入Element Plus图标
// 引入Vue相关API
import {onMounted, reactive, ref} from "vue";
// 引入Element Plus消息组件
import {ElMessage, ElMessageBox} from "element-plus";
import {ArrowDown} from '@element-plus/icons-vue';
// 引入教师用户相关API
import {
  addTeacherUser,
  deleteTeacherUser,
  fetchAllTeacherUsers,
  fetchTeacherUsers,
  resetTeacherPassword,
  updateTeacherUser,
} from "../../api/teacherUser.js";
// 引入系别相关API
import {fetchAllDepartments} from "../../api/department.js";
// 引入用户存储
import {useUserStore} from "../../stores/user";
// 引入Excel导入组件
import ExcelImporter from "../../components/ExcelImporter.vue";
// 引入Excel导出工具
import {utils, writeFile} from "xlsx";

// 获取用户存储
const userStore = useUserStore();

// 导入导出相关逻辑
const importDialogVisible = ref(false);

// 导入成功处理
const handleImportSuccess = () => {
  ElMessage.success("导入成功");
  importDialogVisible.value = false;
  getTeacherList();
};

// 导入错误处理
const handleImportError = (message) => {
  ElMessage.error(message || "导入失败");
};

// 导入部分成功处理
const handleImportPossibleSuccess = (message) => {
  ElMessage.warning(message || "部分数据导入成功，请检查错误信息");
  getTeacherList();
};

// 处理导出命令
const handleExportCommand = (command) => {
  switch (command) {
    case 'current':
      handleExportCurrent();
      break;
    case 'all':
      handleExportAll();
      break;
    case 'selected':
      handleExportSelected();
      break;
  }
};

// 导出当前页面教师
const handleExportCurrent = async () => {
  try {
    if (teacherList.value.length === 0) {
      ElMessage.warning('当前页面没有教师数据');
      return;
    }

    const teacherData = teacherList.value;
    await exportTeacherData(teacherData, '当前页面教师名单.xlsx');
  } catch (error) {
    console.error('导出当前页面失败:', error);
    ElMessage.error('导出失败，请联系管理员');
  }
};

// 导出全部教师
const handleExportAll = async () => {
  try {
    // 构建查询所有教师的参数
    const allQuery = {
      keyword: query.keyword,
      departmentId: query.departmentId
    };

    const res = await fetchAllTeacherUsers(allQuery);
    const teacherData = res.data || [];

    if (teacherData.length === 0) {
      ElMessage.warning('没有教师数据可导出');
      return;
    }

    await exportTeacherData(teacherData, '全部教师名单.xlsx');
  } catch (error) {
    console.error('导出全部教师失败:', error);
    ElMessage.error('导出失败，请联系管理员');
  }
};

// 导出选中教师
const handleExportSelected = async () => {
  try {
    if (selectedTeachers.value.length === 0) {
      ElMessage.warning('请先选择要导出的教师');
      return;
    }

    await exportTeacherData(selectedTeachers.value, '选中教师名单.xlsx');
  } catch (error) {
    console.error('导出选中教师失败:', error);
    ElMessage.error('导出失败，请联系管理员');
  }
};

// 通用导出教师数据方法
const exportTeacherData = async (teacherData, filename) => {
  // 数据转换：departmentId→系别名称
  const formattedData = teacherData.map((teacher) => ({
    ...teacher,
    departmentName: getDepartmentName(teacher.departmentId),
  }));

  // 定义导出列顺序
  const columns = [
    "id",
    "username",
    "realName",
    "studentTeacherId",
    "email",
    "phone",
    "departmentName",
  ];
  const exportData = [
    ["ID", "用户名", "姓名", "教师编号", "邮箱", "手机号", "系别"], // 表头
    ...formattedData.map((teacher) =>
        columns.map((col) => teacher[col] || "-"),
    ), // 数据行，空值显示为'-'
  ];

  // 生成Excel
  const worksheet = utils.aoa_to_sheet(exportData);
  const workbook = utils.book_new();
  utils.book_append_sheet(workbook, worksheet, "教师名单");
  writeFile(workbook, filename);

  ElMessage.success(`导出成功，共导出 ${formattedData.length} 条记录`);
};

// 兼容旧的导出方法（保持向后兼容）
const handleExport = () => {
  handleExportCurrent();
};

// 个人信息相关
const profileDialogVisible = ref(false);
const profileForm = reactive({
  id: "",
  username: "",
  realName: "",
  studentTeacherId: "",
  email: "",
  phone: "",
  avatar: "",
  departmentId: null,
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

// 教师用户列表数据
const teacherList = ref([]); // 教师用户列表
const total = ref(0); // 总数
const loading = ref(false); // 加载状态
const departmentList = ref([]); // 系别列表
const selectedTeachers = ref([]); // 选中的教师列表
const teacherTableRef = ref(null); // 表格引用
const query = reactive({
  keyword: "", // 搜索关键字
  departmentId: null, // 系别ID
  page: 1, // 当前页码
  size: 10, // 每页数量
});

// 处理表格选择变化
const handleSelectionChange = (selection) => {
  selectedTeachers.value = selection;
};

// 弹窗相关
const dialogVisible = ref(false); // 弹窗显示状态
const dialogTitle = ref(""); // 弹窗标题
const isAdd = ref(true); // 是否为新增
const form = reactive({
  id: null,
  username: "",
  realName: "",
  email: "",
  phone: "",
  password: "",
  departmentId: null,
});
const formRef = ref(null);

// 校验规则
const rules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  realName: [{ required: true, message: "请输入姓名", trigger: "blur" }],
  email: [{ required: true, message: "请输入邮箱", trigger: "blur" }],
  phone: [{ required: true, message: "请输入电话", trigger: "blur" }],
  password: [
    {
      required: false,
      message: "请输入密码，留空则使用默认密码123456",
      trigger: "blur",
    },
  ],
  studentTeacherId: [
    { required: false, message: "请输入工号，留空将自动生成", trigger: "blur" },
  ],
  departmentId: [{ required: false, message: "请选择系别", trigger: "change" }],
};

// 获取系别列表
const getDepartmentList = async () => {
  try {
    const res = await fetchAllDepartments();
    departmentList.value = res.data;
  } catch (error) {
    console.error("获取系别列表失败:", error);
  }
};

// 根据系别ID获取系别名称
const getDepartmentName = (departmentId) => {
  if (!departmentId) return "-";
  const dept = departmentList.value.find((item) => item.id === departmentId);
  return dept ? dept.departmentName : "-";
};

// 获取教师用户列表
const getTeacherList = async () => {
  loading.value = true;
  try {
    // 调用API获取数据
    const res = await fetchTeacherUsers(query);
    teacherList.value = res.data.records; // 根据后端返回结构调整
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

// 搜索处理
const handleSearch = () => {
  query.page = 1;
  getTeacherList();
};

// 重置搜索
const handleReset = () => {
  query.keyword = "";
  query.departmentId = null;
  query.page = 1;
  getTeacherList();
};

// 分页处理
const handleCurrentChange = (page) => {
  query.page = page;
  getTeacherList();
};

// 打开新增弹窗
const openAddDialog = () => {
  dialogTitle.value = "新增教师";
  isAdd.value = true;
  Object.assign(form, {
    id: null,
    username: "",
    realName: "",
    email: "",
    phone: "",
    password: "",
    departmentId: null,
  });
  dialogVisible.value = true;
};

// 打开编辑弹窗
const openEditDialog = (row) => {
  dialogTitle.value = "编辑教师";
  isAdd.value = false;
  Object.assign(form, { ...row, password: "" }); // 编辑时密码不显示
  dialogVisible.value = true;
};

// 提交表单
const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    if (isAdd.value) {
      // 新增教师
      await addTeacherUser(form);
      ElMessage.success("新增成功");
    } else {
      // 编辑教师
      await updateTeacherUser(form);
      ElMessage.success("修改成功");
    }
    dialogVisible.value = false;
    getTeacherList();
  });
};

// 删除教师用户
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除教师【${row.realName || row.username}】吗？`,
    "提示",
    { type: "warning" },
  ).then(async () => {
    await deleteTeacherUser(row.id);
    ElMessage.success("删除成功");
    await getTeacherList();
  });
};

// 重置教师密码
const handleResetPassword = (row) => {
  ElMessageBox.confirm(
    `确定要将教师【${row.realName || row.username}】的密码重置为123456吗？`,
    "提示",
    { type: "warning" },
  ).then(async () => {
    await resetTeacherPassword(row.id);
    ElMessage.success("密码重置成功");
  });
};

// 下载模板方法
const handleDownloadTemplate = () => {
  const url = "/public/TeacherDownload.xlsx";
  const link = document.createElement("a");
  link.href = url;
  link.download = "教师导入模板.xlsx";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

// 页面加载时获取数据
onMounted(() => {
  getDepartmentList();
  getTeacherList();
});
</script>

<style scoped>
.teacher-user {
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

.search-row {
  margin: 20px 0;
  display: flex;
  flex-wrap: wrap;
}

.button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.search-item {
  margin-bottom: 10px;
}

.button-container {
  display: flex;
  flex-wrap: wrap;
}

.button-container .el-button:first-child {
  margin-right: 10px;
}

/* 响应式调整 */
@media screen and (max-width: 992px) and (min-width: 769px) {
  .button-group {
    justify-content: flex-start;
    margin-top: 10px;
    gap: 8px;
  }

  .el-select {
    width: 100%;
  }
}

@media screen and (max-width: 768px) and (min-width: 577px) {
  .button-group {
    justify-content: flex-start;
    margin-top: 15px;
  }

  .button-group .el-button {
    margin-bottom: 10px;
    margin-right: 10px;
  }

  .el-select {
    width: 100%;
  }

  .button-container {
    justify-content: flex-start;
    margin-top: 15px;
  }
}

@media screen and (max-width: 576px) {
  .button-group {
    justify-content: flex-start;
    width: 100%;
    flex-direction: column;
    align-items: flex-start;
  }

  .button-group .el-button {
    margin-right: 0;
    margin-bottom: 10px;
    width: 100%;
  }

  .search-item {
    margin-bottom: 15px;
  }

  .button-container {
    justify-content: flex-start;
    width: 100%;
    flex-direction: column;
    align-items: flex-start;
  }

  .button-container .el-button {
    margin-bottom: 10px;
    margin-right: 0 !important;
    width: 100%;
  }
}
</style>
