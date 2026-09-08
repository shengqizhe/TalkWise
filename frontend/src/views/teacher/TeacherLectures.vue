<template>
  <div class="teacher-lectures">
    <div class="container">
      <div class="page-head">
        <h1>我的讲座管理</h1>
        <button class="btn-add" @click="createLecture">+ 发布新讲座</button>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>讲座名称</th>
              <th>时间</th>
              <th>地点</th>
              <th>最大人数</th>
              <th>当前预约</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in lectures" :key="row.id">
              <td class="name-cell">{{ row.title }}</td>
              <td>{{ formatDate(row.lectureTime) }}</td>
              <td>{{ row.extendData?.locationName || row.locationId || '未设置' }}</td>
              <td>{{ row.capacity }}</td>
              <td>{{ row.registrations ? row.registrations.length : 0 }}</td>
              <td>
                <span v-if="row.status === 3 || row.status === 4" class="tag-off">已结束</span>
                <span v-else-if="row.status === 2" class="tag-run">进行中</span>
                <span v-else-if="row.publishStatus === 1" class="tag-publish">已发布</span>
                <span v-else class="tag-draft">草稿</span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="btn-edit" @click="editLecture(row)">编辑</button>
                  <button class="btn-edit" @click="viewRegistrations(row)">名单</button>
                  <button
                    v-if="row.publishStatus === 0 && new Date(row.lectureTime) > new Date() && row.status !== 3 && row.status !== 4"
                    class="btn-on"
                    @click="togglePublishStatus(row, 1)"
                  >
                    发布
                  </button>
                  <button
                    v-if="row.publishStatus === 1 && row.status !== 2 && row.status !== 3"
                    class="btn-off"
                    @click="togglePublishStatus(row, 0)"
                  >
                    下架
                  </button>
                  <button class="btn-off" @click="deleteLecture(row)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!loading && lectures.length === 0" class="empty-tip">
          还没有讲座，点击右上角发布新讲座
        </p>
      </div>
    </div>

    <!-- 创建/编辑讲座对话框 -->
    <el-dialog 
      v-model="lectureDialogVisible" 
      :title="dialogType === 'create' ? '创建讲座' : '编辑讲座'" 
      width="600px"
    >
      <el-form ref="lectureFormRef" :model="lectureForm" :rules="lectureRules" label-width="100px">
        <el-form-item label="讲座标题" prop="title">
          <el-input v-model="lectureForm.title" placeholder="请输入讲座标题" />
        </el-form-item>
        <el-form-item label="讲座分类" prop="categoryId">
          <el-select v-model="lectureForm.categoryId" placeholder="请选择讲座分类" clearable>
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.categoryName"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="讲座摘要" prop="summary">
          <el-input 
            v-model="lectureForm.summary" 
            type="textarea" 
            :rows="3"
            placeholder="请输入讲座摘要" 
          />
        </el-form-item>
        <el-form-item label="讲座内容" prop="content">
          <el-input 
            v-model="lectureForm.content" 
            type="textarea" 
            :rows="4"
            placeholder="请输入讲座内容" 
          />
        </el-form-item>
        <el-form-item label="主讲人" prop="speaker">
          <el-input v-model="lectureForm.speaker" placeholder="请输入主讲人姓名" />
        </el-form-item>
        <el-form-item label="地点" prop="locationId">
          <el-select v-model="lectureForm.locationId" placeholder="请选择讲座地点" clearable>
            <el-option
              v-for="location in locations"
              :key="location.id"
              :label="location.name"
              :value="location.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="讲座时间" prop="lectureTime">
          <el-date-picker
            v-model="lectureForm.lectureTime"
            type="datetime"
            placeholder="选择讲座时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disablePastDates"
            :disabled-time="disablePastTime"
          />
        </el-form-item>
        <el-form-item label="最大人数" prop="capacity">
          <el-input-number 
            v-model="lectureForm.capacity" 
            :min="1" 
            :max="500"
            placeholder="请输入最大人数" 
          />
        </el-form-item>
        <el-form-item label="发布状态" v-if="dialogType === 'edit'">
          <el-tooltip
            :content="lectureForm.status === 3 ? '已结束的讲座必须保持已发布状态' : ''"
            :disabled="lectureForm.status !== 3"
            placement="top"
          >
            <el-switch
              v-model="lectureForm.publishStatus"
              :active-value="1"
              :inactive-value="0"
              active-text="已发布"
              inactive-text="未发布"
              :disabled="lectureForm.status === 3"
            />
          </el-tooltip>
        </el-form-item>
        
        <!-- 宣讲图片部分 -->
        <el-divider content-position="left">宣讲图片</el-divider>
        
        <el-form-item label="宣讲图片">
          <el-upload
            class="upload-demo"
            action=""
            :http-request="customUpload"
            :on-preview="handlePreview"
            :on-remove="handleRemove"
            :before-upload="beforeUpload"
            :file-list="fileList"
            list-type="picture"
            :limit="5"
            :on-exceed="handleExceed"
          >
            <el-button size="small" type="primary">点击上传</el-button>
            <template #tip>
              <div class="el-upload__tip">
                支持jpg/png文件，且不超过2MB，最多上传5张图片
              </div>
            </template>
          </el-upload>
          
          <!-- 上传进度条 -->
          <div v-if="uploadProgress.show" style="margin-top: 10px;">
            <div style="margin-bottom: 5px; font-size: 14px; color: #606266;">
              正在处理: {{ uploadProgress.fileName }}
            </div>
            <el-progress 
              :percentage="uploadProgress.percentage" 
              :status="uploadProgress.status"
              :stroke-width="8"
            >
              <template #default="{ percentage }">
                <span style="font-size: 12px;">{{ percentage }}%</span>
              </template>
            </el-progress>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="display: flex; justify-content: space-between; width: 100%;">
          <div v-if="dialogType === 'edit' && lectureForm.status">
            <el-tooltip
              :content="lectureForm.publishStatus !== 1 ? '未发布的讲座不能开始' : (new Date(lectureForm.lectureTime) <= new Date() ? '讲座开始时间必须在当前时间之后才能开始讲座' : (lectureForm.status !== 1 ? '只有未开始状态的讲座可以开始' : ''))"
              :disabled="lectureForm.status === 1 && lectureForm.publishStatus === 1 && new Date(lectureForm.lectureTime) > new Date()"
              placement="top"
            >
              <el-button 
                type="success" 
                :disabled="lectureForm.status !== 1 || new Date(lectureForm.lectureTime) <= new Date() || lectureForm.publishStatus !== 1"
                @click="startLecture"
              >
                开始讲座
              </el-button>
            </el-tooltip>
            <el-button 
              type="warning" 
              :disabled="lectureForm.status !== 2"
              @click="endLecture"
            >
              结束讲座
            </el-button>
            <el-button 
              type="primary" 
              :disabled="(lectureForm.status !== 3 && lectureForm.status !== 4) || new Date(lectureForm.lectureTime) <= new Date()"
              :title="new Date(lectureForm.lectureTime) <= new Date() ? '讲座开始时间必须在当前时间之后才能恢复讲座' : '点击将讲座恢复到未开始未发布状态'"
              @click="restoreLecture"
            >
              恢复讲座
            </el-button>
          </div>
          <div style="margin-left: auto;">
            <el-button @click="lectureDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="lectureLoading" @click="submitLecture">
              确定
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed, nextTick, onMounted, onUnmounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {ElLoading, ElMessage, ElMessageBox} from 'element-plus';
import {Refresh, Search} from '@element-plus/icons-vue';
import {useUserStore} from '../../stores/user';
import {
  cancelLecture,
  getLecturePage,
  publishLecture,
  updateLecture,
  updateLecturePublishStatus,
  updateLectureStatus
} from '../../api/lecture';
import { uploadLectureImage } from '../../api/file';
import {getAllCategories} from '../../api/category';
import {getLectureRegistrations} from '../../api/registration';
import {getAllLocations} from '../../api/location';
import {useStudentRegistrationNotification, useWebSocket} from '../../composables/useWebSocket';

const userStore = useUserStore();
const router = useRouter();



// 监听学生报名通知
useStudentRegistrationNotification((message) => {
  console.log('收到学生报名通知:', message);
  // 自动刷新讲座列表
  loadLectures();
});

// 搜索和分页
const searchKeyword = ref('');
const currentPage = ref(1);
const pageSize = ref(100);
const totalLectures = ref(0);
const loading = ref(false);

// 讲座对话框
const lectureDialogVisible = ref(false);
const dialogType = ref('create');
const lectureLoading = ref(false);
const lectureFormRef = ref(null);

// 讲座分类数据
const categories = ref([]);

// 地点数据
const locations = ref([]);

// 加载讲座分类数据
async function loadCategories() {
  try {
    const response = await getAllCategories();
    if (response.code === 200) {
      categories.value = response.data || [];
    }
  } catch (error) {
    console.error('加载讲座分类数据失败:', error);
    // 错误信息已由响应拦截器统一处理，这里不再重复显示
  }
}

// 加载地点数据
async function loadLocations() {
  try {
    const response = await getAllLocations();
    if (response.code === 200) {
      locations.value = response.data || [];
    }
  } catch (error) {
    console.error('加载地点数据失败:', error);
    // 错误信息已由响应拦截器统一处理，这里不再重复显示
  }
}

// 讲座表单
const lectureForm = reactive({
  id: undefined,
  title: '',
  summary: '',
  content: '',
  speaker: '',
  locationId: undefined,
  lectureTime: '',
  capacity: 100,
  categoryId: undefined,
  status: undefined,
  publishStatus: 0, // 默认未发布
  promotionContent: '' // 简化为字符串格式
});

// 表单验证规则
const lectureRules = {
  title: [{ required: true, message: '请输入讲座标题', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入讲座摘要', trigger: 'blur' }],
  content: [{ required: true, message: '请输入讲座内容', trigger: 'blur' }],
  speaker: [{ required: true, message: '请输入主讲人姓名', trigger: 'blur' }],
  locationId: [{ required: true, message: '请选择讲座地点', trigger: 'change' }],
  lectureTime: [
    { required: true, message: '请选择讲座时间', trigger: 'change' },
    { 
      validator: (rule, value, callback) => {
        if (value) {
          const selectedTime = new Date(value);
          const currentTime = new Date();
          if (selectedTime <= currentTime) {
            callback(new Error('讲座开始时间必须在当前时间之后'));
          } else {
            callback();
          }
        } else {
          callback();
        }
      }, 
      trigger: 'change' 
    }
  ],
  capacity: [{ required: true, message: '请输入最大人数', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择讲座分类', trigger: 'change' }]
};

// 讲座数据
const lectures = ref([]);

// 表格刷新key
const tableKey = ref(Date.now());

// 文件上传相关
const fileList = ref([]);
const uploadProgress = ref({
  show: false,
  percentage: 0,
  status: '',
  fileName: ''
});

// 分页信息
const paginationInfo = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value + 1;
  const end = Math.min(start + pageSize.value - 1, totalLectures.value);
  return `${start} - ${end} 条，共 ${totalLectures.value} 条`;
});

// 格式化日期
function formatDate(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN');
}

// 禁用过去的日期
function disablePastDates(time) {
  const currentDate = new Date();
  currentDate.setHours(0, 0, 0, 0);
  return time.getTime() < currentDate.getTime();
}

// 禁用过去的时间
function disablePastTime(time) {
  const currentDate = new Date();
  const hours = currentDate.getHours();
  const minutes = currentDate.getMinutes();
  const seconds = currentDate.getSeconds();
  
  // 如果选择的是当天，则禁用过去的时间
  const selectedDate = new Date(lectureForm.lectureTime);
  if (selectedDate && selectedDate.toDateString() === currentDate.toDateString()) {
    return {
      hours: (h) => h < hours,
      minutes: (m, h) => h === hours && m < minutes,
      seconds: (s, m, h) => h === hours && m === minutes && s < seconds
    };
  }
  
  return {
    hours: () => false,
    minutes: () => false,
    seconds: () => false
  };
}

// 获取状态类型
function getStatusType(status) {
  const statusMap = {
    1: 'success',   // 未开始
    2: 'warning',   // 进行中
    3: 'info',      // 已结束
    4: 'danger'     // 已取消
  };
  return statusMap[status] || 'info';
}

// 获取状态文本
function getStatusText(status) {
  const statusMap = {
    1: '未开始',
    2: '进行中',
    3: '已结束',
    4: '已取消'
  };
  return statusMap[status] || '未知';
}

// 切换发布状态
async function togglePublishStatus(lecture, newStatus) {
  try {
    // 如果是发布操作，进行检查
    if (newStatus === 1) {
      // 检查讲座状态，如果是已结束(3)或已取消(4)状态，不允许发布
      if (lecture.status === 3 || lecture.status === 4) {
        ElMessage.error('无法发布：已结束或已取消的讲座不能发布，请先通过编辑对话框点击恢复讲座按钮将讲座恢复到未开始未发布状态');
        return;
      }
      
      // 检查讲座开始时间是否在当前时间之后
      const lectureTime = new Date(lecture.lectureTime);
      const currentTime = new Date();
      
      if (lectureTime <= currentTime) {
        ElMessage.error('无法发布：讲座开始时间必须在当前时间之后');
        return;
      }
    }
    
    const statusText = newStatus === 1 ? '发布' : '取消发布';
    await updateLecturePublishStatus(lecture.id, newStatus);
    ElMessage.success(`讲座${statusText}成功`);
    loadLectures();
  } catch (error) {
    console.error(`讲座${newStatus === 1 ? '发布' : '取消发布'}失败:`, error);
    // 错误信息已由响应拦截器统一处理，这里不再重复显示
  }
}

// 加载讲座数据
async function loadLectures() {
  try {
    loading.value = true;
    
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      organizerId: userStore.userInfo?.id
    };
    
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value;
    }
    
    const response = await getLecturePage(params);
    
    if (response.code === 200) {
      // 先清空数组，触发视图更新
      lectures.value = [];
      
      // 使用nextTick确保DOM更新后再设置新数据
      await nextTick();
      lectures.value = (response.data.records || []).map(lecture => {
        // 确保 promotionContent 是字符串格式，但保留原有内容
        if (typeof lecture.promotionContent !== 'string') {
          lecture.promotionContent = '';
        }
        return lecture;
      });
      totalLectures.value = response.data.total || 0;

      // 更新时间戳
      lastDataTimestamp.value = Date.now();
      
      // 获取每个讲座的报名列表
      await Promise.all(lectures.value.map(async (lecture) => {
        try {
          const registrationsRes = await getLectureRegistrations(lecture.id);
          if (registrationsRes.code === 200) {
            // 只计算状态为1（已报名）的记录
            lecture.registrations = registrationsRes.data.filter(reg => reg.status === 1) || [];
          }
        } catch (err) {
          console.error(`获取讲座 ${lecture.id} 的报名列表失败:`, err);
          // 不显示错误消息，避免多个错误提示
        }
      }));
      
      // 强制更新表格
      tableKey.value = Date.now();
    }
  } catch (error) {
    console.error('加载讲座数据失败:', error);
    // 错误信息已由响应拦截器统一处理，这里不再重复显示
  } finally {
    loading.value = false;
  }
}

// 搜索处理
function handleSearch() {
  currentPage.value = 1;
  loadLectures();
}

// 分页处理
function handleSizeChange(val) {
  pageSize.value = val;
  currentPage.value = 1;
  loadLectures();
}

function handleCurrentChange(val) {
  currentPage.value = val;
  loadLectures();
}

// 创建讲座
function createLecture() {
  dialogType.value = 'create';
  resetForm();
  lectureDialogVisible.value = true;
}

// 编辑讲座
function editLecture(lecture) {
  dialogType.value = 'edit';
  Object.assign(lectureForm, lecture);
  
  // 确保 promotionContent 是字符串格式，但保留原有内容
  if (typeof lectureForm.promotionContent !== 'string') {
    lectureForm.promotionContent = '';
  }
  
  // 从promotionContent中恢复图片文件列表
  fileList.value = [];
  if (lectureForm.promotionContent) {
    // promotionContent现在存储的是逗号分隔的URL字符串
    const imageUrls = lectureForm.promotionContent.split(',').filter(url => url.trim());
    fileList.value = imageUrls.map((url, index) => ({
      uid: `existing-${index}`,
      name: `宣讲图片${index + 1}`,
      status: 'success',
      url: url.trim(),
      response: {
        code: 200,
        data: {
          url: url.trim(),
          fileName: `宣讲图片${index + 1}`
        }
      }
    }));
  }
  
  // 检查讲座是否已经开始或已经结束
  const lectureTime = new Date(lecture.lectureTime);
  const currentTime = new Date();
  
  // 如果讲座时间已过，提示用户可以修改时间以重新发布
  if (lectureTime <= currentTime) {
    // 显示提示信息
    setTimeout(() => {
      ElMessage.info('讲座已开始或已结束，您可以修改讲座时间以便重新发布');
    }, 0);
  }
  
  lectureDialogVisible.value = true;
}

// 查看报名（跳转预约名单页并选中该讲座）
function viewRegistrations(lecture) {
  router.push({ path: '/teacher/apply-list', query: { lectureId: lecture.id } });
}

// 删除讲座
function deleteLecture(lecture) {
  ElMessageBox.confirm(
    `确定要删除讲座"${lecture.title}"吗？`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await cancelLecture(lecture.id, '教师主动删除');
      ElMessage.success('删除成功');
      loadLectures();
    } catch (error) {
      console.error('删除讲座失败:', error);
      // 错误信息已由响应拦截器统一处理，这里不再重复显示
    }
  }).catch(() => {
    // 用户取消操作
  });
}

// 取消讲座
function cancelLectureAction(lecture) {
  ElMessageBox.confirm(
    `确定要取消讲座"${lecture.title}"吗？`,
    '确认取消',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await cancelLecture(lecture.id, '教师主动取消');
      ElMessage.success('讲座已取消');
      loadLectures();
    } catch (error) {
      console.error('取消讲座失败:', error);
      // 错误信息已由响应拦截器统一处理，这里不再重复显示
    }
  }).catch(() => {
    // 用户取消操作
  });
}

// 提交讲座
async function submitLecture() {
  if (!lectureFormRef.value) return;
  
  await lectureFormRef.value.validate(async (valid) => {
    if (valid) {
      // 额外验证讲座时间是否在当前时间之后
      const selectedTime = new Date(lectureForm.lectureTime);
      const currentTime = new Date();
      
      // 只在创建新讲座时，或者编辑讲座但原讲座尚未开始时，验证时间
      if (dialogType.value === 'create' && selectedTime <= currentTime) {
        ElMessage.error('讲座开始时间必须在当前时间之后');
        return;
      }
      
      // 如果是编辑模式，无论原讲座是否已开始，都要确保新选择的时间在当前时间之后
      if (dialogType.value === 'edit') {
        if (selectedTime <= currentTime) {
          ElMessage.error('讲座开始时间必须在当前时间之后');
          return;
        }
      }
      
      // 处理宣讲图片：将fileList中的图片URL直接存储到promotionContent
      const imageUrls = fileList.value
        .filter(file => file.status === 'success' && file.response?.data?.url)
        .map(file => file.response.data.url);
      
      // 将图片URL数组转换为逗号分隔的字符串存储
      const formData = {
        ...lectureForm,
        promotionContent: imageUrls.length > 0 ? imageUrls.join(',') : ''
      };
      
      lectureLoading.value = true;
      try {
        if (dialogType.value === 'create') {
          // 创建新讲座，默认为未发布状态
          await publishLecture({
            ...formData,
            organizerId: userStore.userInfo?.id,
            publishStatus: 0 // 默认未发布
          });
          ElMessage.success('创建成功');
        } else {
          // 更新讲座
          // 确保已结束的讲座必须是已发布状态
          if (formData.status === 3 && formData.publishStatus !== 1) {
            formData.publishStatus = 1;
            ElMessage.info('已结束的讲座必须保持已发布状态');
          }
          await updateLecture(formData);
          ElMessage.success('更新成功');
        }
        lectureDialogVisible.value = false;
        loadLectures();
      } catch (error) {
        console.error('提交讲座失败:', error);
        // 错误信息已由响应拦截器统一处理，这里不再重复显示
      } finally {
        lectureLoading.value = false;
      }
    }
  });
}

// 开始讲座
async function startLecture() {
  try {
    // 检查讲座状态
    if (lectureForm.status !== 1) {
      ElMessage.warning('只有未开始状态的讲座可以开始');
      return;
    }
    
    // 检查讲座发布状态
    if (lectureForm.publishStatus !== 1) {
      ElMessage.warning('未发布的讲座不能开始');
      return;
    }
    
    // 移除对讲座开始时间的检查，允许在计划时间当天或之后开始讲座
    
    // 更新讲座状态为进行中，后端会自动确保发布状态为已发布
    await updateLectureStatus(lectureForm.id, 2); // 2表示进行中
    
    // 前端直接更新状态，不再单独调用更新发布状态的API
    lectureForm.status = 2;
    lectureForm.publishStatus = 1;
    
    ElMessage.success('讲座已开始');
    lectureDialogVisible.value = false;
    loadLectures();
  } catch (error) {
    console.error('开始讲座失败:', error);
    // 错误信息已由响应拦截器统一处理，这里不再重复显示
  }
}

// 结束讲座
async function endLecture() {
  try {
    // 更新讲座状态为已结束，后端会自动确保发布状态为已发布
    await updateLectureStatus(lectureForm.id, 3); // 3表示已结束
    
    // 前端直接更新状态，不再单独调用更新发布状态的API
    lectureForm.status = 3;
    lectureForm.publishStatus = 1;
    
    ElMessage.success('讲座已结束');
    lectureDialogVisible.value = false;
    loadLectures();
  } catch (error) {
    console.error('结束讲座失败:', error);
    // 错误信息已由响应拦截器统一处理，这里不再重复显示
  }
}

// 恢复讲座
async function restoreLecture() {
  try {
    // 检查讲座开始时间是否在当前时间之后
    const lectureTime = new Date(lectureForm.lectureTime);
    const currentTime = new Date();
    
    if (lectureTime <= currentTime) {
      ElMessage.error('无法恢复：讲座开始时间必须在当前时间之后');
      return;
    }
    
    // 先将讲座状态设置为未开始
    await updateLectureStatus(lectureForm.id, 1); // 1表示未开始
    
    // 然后将讲座发布状态设置为未发布
    await updateLecturePublishStatus(lectureForm.id, 0); // 0表示未发布
    
    ElMessage.success('讲座已恢复到未开始未发布状态');
    lectureForm.status = 1;
    lectureForm.publishStatus = 0;
    lectureDialogVisible.value = false;
    loadLectures();
  } catch (error) {
    console.error('恢复讲座失败:', error);
    // 错误信息已由响应拦截器统一处理，这里不再重复显示
  }
}

// 重置表单
function resetForm() {
  lectureForm.id = undefined;
  lectureForm.title = '';
  lectureForm.summary = '';
  lectureForm.content = '';
  lectureForm.speaker = '';
  lectureForm.locationId = undefined;
  lectureForm.lectureTime = '';
  lectureForm.capacity = 100;
  lectureForm.categoryId = undefined;
  lectureForm.status = undefined;
  lectureForm.publishStatus = 0; // 默认未发布
  lectureForm.promotionContent = ''; // 简化为字符串格式
  fileList.value = [];
  uploadProgress.value = {
    show: false,
    percentage: 0,
    status: '',
    fileName: ''
  };
}

// 定时器引用，用于自动刷新数据
let autoRefreshTimer = null;

// 自动刷新数据的时间间隔（毫秒）
const AUTO_REFRESH_INTERVAL = 30000; // 30秒刷新一次，更及时地检测讲座状态变化

// 存储上次数据更新的时间戳
const lastDataTimestamp = ref(Date.now());

/**
 * 自动刷新数据
 * 定期检查和更新讲座列表，确保讲座状态保持最新
 * 只在数据有变化时才更新界面
 */
async function autoRefreshData() {
  try {
    // 记录上次更新时间戳
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      organizerId: userStore.userInfo?.id,
      checkDataChange: true,
      lastUpdatedTimestamp: lastDataTimestamp.value
    };

    if (searchKeyword.value) {
      params.keyword = searchKeyword.value;
    }

    // 发送请求检查数据是否有变化
    const response = await getLecturePage(params);

    if (response.code === 200) {
      // 检查返回的数据是否有变化
      const dataChanged = response.data.records.length > 0 ?
          response.data.records[0].extendData?.dataChanged : false;

      if (dataChanged) {
        console.log('检测到数据变化，更新界面');
        // 数据有变化，更新界面
        // 先清空数组，触发视图更新
        lectures.value = [];

        // 使用nextTick确保DOM更新后再设置新数据
        await nextTick();
        lectures.value = (response.data.records || []).map(lecture => {
          // 确保 promotionContent 是字符串格式
          if (!lecture.promotionContent || typeof lecture.promotionContent !== 'string') {
            lecture.promotionContent = '';
          }
          return lecture;
        });
        totalLectures.value = response.data.total || 0;

        // 更新时间戳
        lastDataTimestamp.value = Date.now();

        // 获取每个讲座的报名列表
        await Promise.all(lectures.value.map(async (lecture) => {
          try {
            const registrationsRes = await getLectureRegistrations(lecture.id);
            if (registrationsRes.code === 200) {
              // 只计算状态为1（已报名）的记录
              lecture.registrations = registrationsRes.data.filter(reg => reg.status === 1) || [];
            }
          } catch (err) {
            console.error(`获取讲座 ${lecture.id} 的报名列表失败:`, err);
            // 不显示错误消息，避免多个错误提示
          }
        }));

        // 强制更新表格
        tableKey.value = Date.now();
      } else {
        console.log('数据无变化，不更新界面');
        // 数据无变化时，仅更新时间戳，避免频繁请求
        lastDataTimestamp.value = Date.now();
      }
    }
  } catch (error) {
    console.error('自动刷新讲座数据失败:', error);
  }
}

/**
 * 手动刷新数据
 * 用户点击刷新按钮时调用，显示加载指示器并刷新数据
 */
async function manualRefreshData() {
  // 显示全局加载指示器
  const loadingInstance = ElLoading.service({
    lock: true,
    text: '正在刷新数据...',
    background: 'rgba(0, 0, 0, 0.7)'
  });

  try {
    // 调用自动刷新函数刷新数据
    await autoRefreshData();

    // 显示成功消息
    ElMessage({
      message: '数据刷新成功',
      type: 'success'
    });
  } catch (error) {
    ElMessage.error('数据刷新失败，请稍后重试');
  } finally {
    // 关闭加载指示器
    loadingInstance.close();
  }
}



// 文件上传处理函数
function handlePreview(file) {
  // 预览图片
  if (file.url) {
    window.open(file.url, '_blank');
  }
}

function handleRemove(file, uploadFileList) {
  // 从fileList中移除文件
  const index = fileList.value.findIndex(f => f.uid === file.uid);
  if (index > -1) {
    fileList.value.splice(index, 1);
  }
}

function beforeUpload(file) {
  const isJPG = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/gif';
  const isLt2M = file.size / 1024 / 1024 < 2;

  if (!isJPG) {
    ElMessage.error('上传图片只能是 JPG/PNG/GIF 格式!');
    return false;
  }
  if (!isLt2M) {
    ElMessage.error('上传图片大小不能超过 2MB!');
    return false;
  }
  
  return true; // 允许上传
}

// 自定义上传方法
function customUpload(options) {
  const { file } = options;
  const formData = new FormData();
  formData.append('file', file);
  
  // 显示进度条
  uploadProgress.value = {
    show: true,
    percentage: 0,
    status: '',
    fileName: file.name
  };
  
  uploadLectureImage(formData)
    .then(response => {
      if (response.code === 200) {
        uploadProgress.value.percentage = 100;
        uploadProgress.value.status = 'success';
        
        const imageUrl = response.data.url;
        
        // 添加到fileList用于显示
        fileList.value.push({
          uid: Date.now() + Math.random(),
          name: file.name,
          status: 'success',
          url: imageUrl,
          response: {
            code: 200,
            data: {
              url: imageUrl,
              fileName: file.name
            }
          }
        });
        
        ElMessage.success('图片上传成功');
        options.onSuccess(response);
      } else {
        uploadProgress.value.status = 'exception';
        ElMessage.error(response.message || '图片上传失败');
        options.onError(new Error(response.message || '图片上传失败'));
      }
    })
    .catch(error => {
      uploadProgress.value.status = 'exception';
      ElMessage.error('图片上传失败');
      options.onError(error);
    })
    .finally(() => {
      // 延迟隐藏进度条
      setTimeout(() => {
        uploadProgress.value.show = false;
      }, 1000);
    });
}

function handleExceed(files, uploadFileList) {
  ElMessage.warning('最多只能上传5张图片');
}

function handleProgress(event, file, fileList) {
  // 处理上传进度（如果需要真实上传时使用）
  if (uploadProgress.value.show) {
    uploadProgress.value.percentage = Math.floor(event.percent);
  }
}

onMounted(() => {
  loadCategories();
  loadLocations();
  loadLectures();

  // 设置定时器，定期刷新数据
  autoRefreshTimer = setInterval(autoRefreshData, AUTO_REFRESH_INTERVAL);
});

// 组件卸载时清除定时器
onUnmounted(() => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
});
</script>

<style scoped>
/* ============ 黑白极简风格（与学生端统一） ============ */

.teacher-lectures {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  line-height: 1.6;
  color: #333;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
}

.page-head h1 {
  font-size: 36px;
  font-weight: 800;
  color: #333;
  margin: 0;
}

.btn-add {
  padding: 10px 24px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  font-family: inherit;
  transition: background 0.2s;
}

.btn-add:hover {
  background: #333;
}

.table-wrap {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 16px 20px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
  color: #444;
}

th {
  background: #fafafa;
  color: #666;
  font-weight: 500;
}

tbody tr:last-child td {
  border-bottom: none;
}

.name-cell {
  font-weight: 600;
  color: #333;
}

/* 状态文字 */
.tag-publish {
  color: #00b42a;
  font-weight: 500;
}

.tag-draft {
  color: #ff7d00;
  font-weight: 500;
}

.tag-run {
  color: #000;
  font-weight: 600;
}

.tag-off {
  color: #999;
}

/* 操作按钮 */
.row-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-edit {
  padding: 6px 14px;
  border: 1px solid #e0e0e0;
  color: #555;
  background: #fff;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-edit:hover {
  border-color: #000;
  color: #000;
}

.btn-on {
  padding: 6px 14px;
  border: 1px solid #000;
  color: #fff;
  background: #000;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.2s;
}

.btn-on:hover {
  background: #333;
}

.btn-off {
  padding: 6px 14px;
  border: 1px solid #ff6b6b;
  color: #ff6b6b;
  background: #fff;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-off:hover {
  background: #fff5f5;
}

.empty-tip {
  text-align: center;
  color: #999;
  font-size: 15px;
  padding: 60px 0;
}

/* 创建/编辑对话框微调（黑白主题） */
:deep(.el-dialog) {
  border-radius: 16px;
}
</style>