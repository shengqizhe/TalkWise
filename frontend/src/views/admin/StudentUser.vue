<template>
  <div class="student-user">
    <!-- 学生用户管理欢迎卡片 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="welcome-card">
          <div class="welcome-content">
            <div class="welcome-text">
              <h2>学生用户管理</h2>
              <p>这里可以管理所有学生用户的信息</p>
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
      <el-col :lg="6" :md="6" :sm="12" :xl="6" :xs="24" class="search-item">
        <el-input
            v-model="query.keyword"
            clearable
            placeholder="请输入姓名或学号搜索"
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
            clearable
            placeholder="选择系别"
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
      <el-col :lg="4" :md="4" :sm="12" :xl="4" :xs="24" class="search-item">
        <el-button style="width: 100%;" @click="handleReset">重置</el-button>
      </el-col>
      <!-- 新增：导入导出按钮 -->
      <el-col :lg="10" :md="10" :sm="24" :xl="10" :xs="24" class="button-group">
        <el-button type="primary" @click="importDialogVisible = true"
        >导入名单
        </el-button
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
              <el-dropdown-item command="all">导出全部学生</el-dropdown-item>
              <el-dropdown-item command="selected">导出选中学生</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- 新增：下载模板按钮 -->
        <el-button @click="handleDownloadTemplate">下载模板</el-button>
      </el-col>
    </el-row>

    <!-- 学生用户表格 -->
    <el-table
        ref="studentTableRef"
        v-loading="loading"
        :data="studentList"
        border
        @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55"/>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="realName" label="姓名" />
      <el-table-column prop="studentTeacherId" label="学号" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="phone" label="电话" />
      <el-table-column label="系别" width="120">
        <template #default="scope">
          <span>{{ getDepartmentName(scope.row.departmentId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="scope">
          <!-- 编辑按钮 -->
          <el-button
              size="small"
              type="primary"
              @click="openEditDialog(scope.row)"
          >编辑
          </el-button
          >
          <!-- 设为老师按钮 -->
          <el-button
              size="small"
              type="success"
              @click="setAsTeacher(scope.row)"
          >设为老师
          </el-button
          >
          <el-button size="small" type="danger" @click="handleDelete(scope.row)"
          >删除
          </el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
        :current-page="query.page"
        :page-size="query.size"
        :total="total"
        background
        layout="prev, pager, next, jumper"
        style="margin-top: 20px; text-align: right"
        @current-change="handleCurrentChange"
    />

    <!-- 编辑学生弹窗 -->
    <el-dialog :title="'编辑学生'" v-model="editDialogVisible" width="500px">
      <el-form :model="editForm" ref="editFormRef" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="editForm.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="学号">
          <el-input
              v-model="editForm.studentTeacherId"
              placeholder="请输入学号"
          />
        </el-form-item>
        <el-form-item label="系别">
          <el-select
              v-model="editForm.departmentId"
              clearable
              placeholder="请选择系别"
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
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新的导入学生名单弹窗 -->
    <el-dialog
        v-model="importDialogVisible"
        title="导入学生名单"
        width="650px"
    >
      <ExcelImporter
          :import-url="'/student/import-json'"
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
              disabled
              placeholder="请输入用户名"
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input
              v-model="profileForm.realName"
              disabled
              placeholder="请输入真实姓名"
          />
        </el-form-item>
        <el-form-item label="管理员ID" prop="studentTeacherId">
          <el-input
              v-model="profileForm.studentTeacherId"
              disabled
              placeholder="请输入管理员ID"
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
              v-model="profileForm.email"
              disabled
              placeholder="请输入邮箱"
          />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
              v-model="profileForm.phone"
              disabled
              placeholder="请输入手机号"
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
import {onMounted, reactive, ref} from "vue";
// 引入Element Plus消息组件
import {ElMessage, ElMessageBox} from "element-plus";
import {ArrowDown} from '@element-plus/icons-vue';
// 引入学生用户相关API
import {
  deleteStudentUser,
  fetchAllStudentUsers,
  fetchStudentUsers,
  importStudentList,
  setStudentAsTeacher,
  updateStudentUser,
} from "../../api/studentUser.js";
import ExcelImporter from "../../components/ExcelImporter.vue";
// 引入系别相关API
import {fetchAllDepartments} from "../../api/department.js";
import {utils, writeFile} from "xlsx";
// 引入用户存储
import {useUserStore} from "../../stores/user";

// 获取用户存储
const userStore = useUserStore();

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

// 学生用户列表数据
const studentList = ref([]); // 学生用户列表
const total = ref(0); // 总数
const loading = ref(false); // 加载状态
const departmentList = ref([]); // 系别列表
const selectedStudents = ref([]); // 选中的学生列表
const studentTableRef = ref(null); // 表格引用
const query = reactive({
  keyword: "", // 搜索关键字
  departmentId: null, // 系别ID
  page: 1, // 当前页码
  size: 10, // 每页数量
});

// 新增：编辑弹窗相关数据
const editDialogVisible = ref(false); // 编辑弹窗显示状态
const editForm = ref({}); // 编辑表单数据
const editFormRef = ref(null); // 表单ref

// 新增：导入导出相关逻辑
const importDialogVisible = ref(false);
const importActionUrl = importStudentList; // 这里假设importStudentList为字符串URL，如为函数请调整
const uploadHeaders = {}; // 如需token: { Authorization: 'Bearer xxx' }

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

// 获取学生用户列表
const getStudentList = async () => {
  loading.value = true;
  try {
    // 调用API获取数据
    const res = await fetchStudentUsers(query);
    studentList.value = res.data.records; // 根据后端返回结构调整
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

// 搜索处理
const handleSearch = () => {
  query.page = 1;
  getStudentList();
};

// 重置搜索
const handleReset = () => {
  query.keyword = "";
  query.departmentId = null;
  query.page = 1;
  getStudentList();
};

// 分页处理
const handleCurrentChange = (page) => {
  query.page = page;
  getStudentList();
};

// 删除学生用户
const handleDelete = (row) => {
  ElMessageBox.confirm(
      `确定要删除学生【${row.realName || row.username}】吗？`,
      "提示",
      {type: "warning"},
  ).then(async () => {
    await deleteStudentUser(row.id);
    ElMessage.success("删除成功");
    await getStudentList();
  });
};

// 打开编辑弹窗
const openEditDialog = (row) => {
  editForm.value = { ...row }; // 复制当前行数据
  editDialogVisible.value = true;
};

// 提交编辑
const submitEdit = async () => {
  try {
    await updateStudentUser(editForm.value); // 调用API更新
    ElMessage.success("修改成功");
    editDialogVisible.value = false;
    getStudentList();
  } catch (e) {
    ElMessage.error("修改失败");
  }
};

// 设为老师
const setAsTeacher = async (row) => {
  try {
    await setStudentAsTeacher(row.id); // 调用API设为老师
    ElMessage.success("已设为老师");
    getStudentList();
  } catch (e) {
    ElMessage.error("操作失败");
  }
};

// 导入成功回调
const handleImportSuccess = (response) => {
  // 不再显示重复的成功消息，因为ExcelImporter组件已经显示了
  importDialogVisible.value = false;
  getStudentList();
};

// 导入失败回调
const handleImportError = (error) => {
  console.error('导入失败:', error);
  // 不再显示重复的错误消息，因为ExcelImporter组件已经显示了
  // 只记录错误日志，不再显示额外的错误提示
};

// 处理可能成功的导入（系统错误但数据可能已导入）
const handleImportPossibleSuccess = () => {
  console.log('可能已成功导入，刷新数据');
  // 关闭导入对话框
  importDialogVisible.value = false;
  // 刷新学生列表数据
  getStudentList();
  // 不再显示重复的提示，因为ExcelImporter组件已经显示了
};

// 处理表格选择变化
const handleSelectionChange = (selection) => {
  selectedStudents.value = selection;
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

// 导出当前页面学生
const handleExportCurrent = async () => {
  try {
    if (studentList.value.length === 0) {
      ElMessage.warning('当前页面没有学生数据');
      return;
    }

    const studentData = studentList.value;
    await exportStudentData(studentData, '当前页面学生名单.xlsx');
  } catch (error) {
    console.error('导出当前页面失败:', error);
    ElMessage.error('导出失败，请联系管理员');
  }
};

// 导出全部学生
const handleExportAll = async () => {
  try {
    // 构建查询所有学生的参数
    const allQuery = {
      keyword: query.keyword,
      departmentId: query.departmentId
    };

    const res = await fetchAllStudentUsers(allQuery);
    const studentData = res.data || [];

    if (studentData.length === 0) {
      ElMessage.warning('没有学生数据可导出');
      return;
    }

    await exportStudentData(studentData, '全部学生名单.xlsx');
  } catch (error) {
    console.error('导出全部学生失败:', error);
    ElMessage.error('导出失败，请联系管理员');
  }
};

// 导出选中学生
const handleExportSelected = async () => {
  try {
    if (selectedStudents.value.length === 0) {
      ElMessage.warning('请先选择要导出的学生');
      return;
    }

    await exportStudentData(selectedStudents.value, '选中学生名单.xlsx');
  } catch (error) {
    console.error('导出选中学生失败:', error);
    ElMessage.error('导出失败，请联系管理员');
  }
};

// 通用导出学生数据方法
const exportStudentData = async (studentData, filename) => {
  // 数据转换：departmentId→系别名称
  const formattedData = studentData.map((student) => ({
    ...student,
    departmentName: getDepartmentName(student.departmentId),
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
    ["ID", "用户名", "姓名", "学号", "邮箱", "手机号", "系别"], // 表头
    ...formattedData.map((student) =>
        columns.map((col) => student[col] || "-"),
    ), // 数据行，空值显示为'-'
  ];

  // 生成Excel
  const worksheet = utils.aoa_to_sheet(exportData);
  const workbook = utils.book_new();
  utils.book_append_sheet(workbook, worksheet, "学生名单");
  writeFile(workbook, filename);

  ElMessage.success(`导出成功，共导出 ${formattedData.length} 条记录`);
};

// 兼容旧的导出方法（保持向后兼容）
const handleExport = () => {
  handleExportCurrent();
};

// 新增：下载模板方法
const handleDownloadTemplate = () => {
  const url = "/public/StudentDownload.xlsx";
  const link = document.createElement("a");
  link.href = url;
  link.download = "学生导入模板.xlsx";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};
// 页面加载时获取数据
onMounted(() => {
  getDepartmentList();
  getStudentList();
});
</script>

<style scoped>
.student-user {
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

.button-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.search-item {
  margin-bottom: 10px;
}

/* 响应式调整 */
@media screen and (max-width: 768px) {
  .button-group {
    justify-content: flex-start;
  }

  .el-select {
    width: 100%;
  }
}

@media screen and (max-width: 576px) {
  .button-group {
    justify-content: center;
  }

  .search-item {
    margin-bottom: 15px;
  }
}
</style>
