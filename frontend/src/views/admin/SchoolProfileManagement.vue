<template>
  <div class="school-profile">
    <!-- 欢迎卡片 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="welcome-card">
          <div class="welcome-content">
            <div class="welcome-text">
              <h2>学校画像</h2>
              <p>维护学校办学定位、优势学科与学生群体特征，供 AI 容量评估判断“校本契合度”</p>
            </div>
            <div class="welcome-avatar">
              <el-avatar :size="80" :src="userStore.userInfo?.avatar">
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
          placeholder="请输入学校名称搜索"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
      </el-col>
      <el-col :span="6">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="openAddDialog">新增学校画像</el-button>
      </el-col>
    </el-row>

    <!-- 画像表格 -->
    <el-table :data="profileList" v-loading="loading" border>
      <el-table-column prop="schoolName" label="学校名称" width="200" />
      <el-table-column label="画像内容" min-width="320">
        <template #default="scope">
          <span v-if="scope.row.profileContent">{{ scope.row.profileContent }}</span>
          <el-tag v-else type="info" size="small">未维护（校本契合度将按中性处理）</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="教室容量范围" width="150">
        <template #default="scope">
          <span v-if="scope.row.maxRoomCapacity > 0">
            {{ scope.row.minRoomCapacity }} ~ {{ scope.row.maxRoomCapacity }} 人
          </span>
          <el-tag v-else type="info" size="small">未维护</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="200">
        <template #default="scope">
          <el-button size="small" type="primary" @click="openEditDialog(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="学校名称" prop="schoolName">
          <el-input v-model="form.schoolName" placeholder="请输入学校名称" />
        </el-form-item>
        <el-form-item label="画像内容" prop="profileContent">
          <el-input
            v-model="form.profileContent"
            type="textarea"
            :rows="8"
            placeholder="例如：学校以工科、软件工程、人工智能和数字媒体等方向为主，学生群体以本科生和研究生为主，关注就业实践、前沿技术、科研创新和产业发展。"
          />
          <div class="field-hint">
            这段文字会作为 AI 判断“校本契合度”的依据：与优势学科相关的讲座会适当上调建议容量，关联较弱的主题不会被社会热点过度放大。留空则该维度按中性处理。
          </div>
        </el-form-item>
        <el-form-item v-if="!isAdd" label="教室容量">
          <span class="readonly-value">
            <template v-if="form.maxRoomCapacity > 0">
              {{ form.minRoomCapacity }} ~ {{ form.maxRoomCapacity }} 人
            </template>
            <template v-else>未维护</template>
          </span>
          <div class="field-hint">由地点管理中的教室容量自动汇总，不在此处修改。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useUserStore } from "../../stores/user";
import {
  getAllSchoolProfiles,
  createSchoolProfile,
  updateSchoolProfile,
  deleteSchoolProfile,
} from "../../api/schoolProfile";

const userStore = useUserStore();

const loading = ref(false);
const allProfiles = ref([]); // 全量数据（前端过滤 + 分页）
const profileList = ref([]); // 当前页数据
const total = ref(0);
const query = reactive({
  keyword: "",
  page: 1,
  size: 10,
});

const dialogVisible = ref(false);
const dialogTitle = ref("");
const isAdd = ref(true);
const form = reactive({
  id: null,
  schoolName: "",
  profileContent: "",
  minRoomCapacity: 0,
  maxRoomCapacity: 0,
});
const formRef = ref(null);

const rules = {
  schoolName: [{ required: true, message: "请输入学校名称", trigger: "blur" }],
};

// 拉取全量画像，前端按关键字过滤后分页
const getProfileList = async () => {
  loading.value = true;
  try {
    const res = await getAllSchoolProfiles();
    allProfiles.value = res.data || [];
    applyFilterAndPage();
  } catch (error) {
    console.error("获取学校画像失败:", error);
    ElMessage.error("获取学校画像失败");
  } finally {
    loading.value = false;
  }
};

const applyFilterAndPage = () => {
  const keyword = query.keyword.trim().toLowerCase();
  const filtered = keyword
    ? allProfiles.value.filter((item) =>
        (item.schoolName || "").toLowerCase().includes(keyword)
      )
    : allProfiles.value;

  total.value = filtered.length;
  const start = (query.page - 1) * query.size;
  profileList.value = filtered.slice(start, start + query.size);
};

const handleSearch = () => {
  query.page = 1;
  applyFilterAndPage();
};

const handleReset = () => {
  query.keyword = "";
  query.page = 1;
  applyFilterAndPage();
};

const handleCurrentChange = (page) => {
  query.page = page;
  applyFilterAndPage();
};

const openAddDialog = () => {
  dialogTitle.value = "新增学校画像";
  isAdd.value = true;
  Object.assign(form, {
    id: null,
    schoolName: "",
    profileContent: "",
    minRoomCapacity: 0,
    maxRoomCapacity: 0,
  });
  dialogVisible.value = true;
};

const openEditDialog = (row) => {
  dialogTitle.value = "编辑学校画像";
  isAdd.value = false;
  Object.assign(form, {
    id: row.id,
    schoolName: row.schoolName,
    profileContent: row.profileContent,
    minRoomCapacity: row.minRoomCapacity,
    maxRoomCapacity: row.maxRoomCapacity,
  });
  dialogVisible.value = true;
};

const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    try {
      if (isAdd.value) {
        await createSchoolProfile({
          schoolName: form.schoolName.trim(),
          profileContent: form.profileContent,
        });
        ElMessage.success("新增学校画像成功");
      } else {
        await updateSchoolProfile(form.id, {
          schoolName: form.schoolName.trim(),
          profileContent: form.profileContent,
        });
        ElMessage.success("修改学校画像成功");
      }
      dialogVisible.value = false;
      await getProfileList();
    } catch (error) {
      console.error("操作失败:", error);
      ElMessage.error(error.response?.data?.message || "操作失败");
    }
  });
};

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除【${row.schoolName}】的学校画像吗？删除后容量评估的“校本契合度”将按中性处理。`,
    "提示",
    { type: "warning" }
  ).then(async () => {
    try {
      await deleteSchoolProfile(row.id);
      ElMessage.success("删除成功");
      await getProfileList();
    } catch (error) {
      console.error("删除失败:", error);
      ElMessage.error(error.response?.data?.message || "删除失败");
    }
  });
};

onMounted(() => {
  getProfileList();
});
</script>

<style scoped>
.school-profile {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  color: #333;
}

.welcome-card {
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
}

.welcome-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome-text h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 800;
  color: #222;
}

.welcome-text p {
  margin: 0;
  font-size: 14px;
  color: #888;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}

.readonly-value {
  color: #555;
}
</style>
