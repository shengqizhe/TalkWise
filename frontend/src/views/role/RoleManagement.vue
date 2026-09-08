<template>
  <div class="role-container">
    <div class="adm-title-row">
      <h1 class="adm-page-h1">角色权限配置</h1>
    </div>

    <div class="toolbar">
      <button class="adm-btn adm-btn-primary" @click="handleAdd">+ 新增角色</button>
    </div>

    <div class="adm-table-block">
      <table class="adm-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>角色名称</th>
            <th>角色描述</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in roleList" :key="row.id">
            <td>{{ row.id }}</td>
            <td>{{ row.roleName }}</td>
            <td>{{ row.roleDescription }}</td>
            <td>
              <button class="adm-btn" @click="handleEdit(row)">编辑</button>
              <button class="adm-btn" @click="handlePermission(row)">分配权限</button>
              <button class="adm-btn adm-btn-red" @click="handleDelete(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="roleList.length === 0" class="adm-empty">暂无角色数据</p>
    </div>

    <!-- 角色表单对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增角色' : '编辑角色'"
      width="500px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色描述" prop="roleDescription">
          <el-input
            v-model="form.roleDescription"
            type="textarea"
            placeholder="请输入角色描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permissionDialogVisible" title="分配权限" width="500px">
      <el-form label-width="80px">
        <el-form-item label="权限">
          <el-checkbox-group v-model="selectedPermissions">
            <el-checkbox
              v-for="permission in permissionList"
              :key="permission.id"
              :label="permission.id"
            >
              {{ permission.permissionName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="permissionLoading"
          @click="handlePermissionSubmit"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { getRoleList } from "../../api/role";
import { getPermissionList } from "../../api/permission";
import { getRolePermissions, assignRolePermissions } from "../../api/permission";
import { ElMessage, ElMessageBox } from "element-plus";

// 角色列表
const roleList = ref([]);

// 权限列表
const permissionList = ref([]);

// 表单对话框
const dialogVisible = ref(false);
const dialogType = ref("add");
const loading = ref(false);
const formRef = ref(null);
const form = reactive({
  id: undefined,
  roleName: "",
  roleDescription: "",
});

// 权限对话框
const permissionDialogVisible = ref(false);
const permissionLoading = ref(false);
const selectedPermissions = ref([]);
const currentRoleId = ref(null);

// 表单验证规则
const rules = {
  roleName: [{ required: true, message: "请输入角色名称", trigger: "blur" }],
  roleDescription: [
    { required: true, message: "请输入角色描述", trigger: "blur" },
  ],
};

// 获取角色列表
async function fetchRoleList() {
  try {
    const res = await getRoleList();
    roleList.value = res.data;
  } catch (error) {
    console.error("获取角色列表失败:", error);
  }
}

// 获取权限列表
async function fetchPermissionList() {
  try {
    const res = await getPermissionList();
    permissionList.value = res.data;
  } catch (error) {
    console.error("获取权限列表失败:", error);
  }
}

// 新增角色
function handleAdd() {
  dialogType.value = "add";
  form.id = undefined;
  form.roleName = "";
  form.roleDescription = "";
  dialogVisible.value = true;
}

// 编辑角色
function handleEdit(row) {
  dialogType.value = "edit";
  form.id = row.id;
  form.roleName = row.roleName;
  form.roleDescription = row.roleDescription;
  dialogVisible.value = true;
}

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        // TODO: 实现角色新增和编辑接口
        ElMessage.success(dialogType.value === "add" ? "新增成功" : "编辑成功");
        dialogVisible.value = false;
        fetchRoleList();
      } finally {
        loading.value = false;
      }
    }
  });
}

// 删除角色
function handleDelete(row) {
  ElMessageBox.confirm("确定要删除该角色吗？", "提示", {
    type: "warning",
  }).then(async () => {
    try {
      // TODO: 实现角色删除接口
      ElMessage.success("删除成功");
      fetchRoleList();
    } catch (error) {
      console.error("删除角色失败:", error);
    }
  });
}

// 分配权限
async function handlePermission(row) {
  currentRoleId.value = row.id;
  try {
    const res = await getRolePermissions(row.id);
    selectedPermissions.value = res.data.map((permission) => permission.id);
    permissionDialogVisible.value = true;
  } catch (error) {
    console.error("获取角色权限失败:", error);
  }
}

// 提交权限分配
async function handlePermissionSubmit() {
  permissionLoading.value = true;
  try {
    await assignRolePermissions(currentRoleId.value, selectedPermissions.value);
    ElMessage.success("分配权限成功");
    permissionDialogVisible.value = false;
  } finally {
    permissionLoading.value = false;
  }
}

onMounted(() => {
  fetchRoleList();
  fetchPermissionList();
});
</script>

<style scoped>
.role-container {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.toolbar {
  margin-bottom: 20px;
}
</style>
