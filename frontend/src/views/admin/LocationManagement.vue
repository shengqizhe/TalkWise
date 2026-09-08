<template>
  <div class="location-management">
    <!-- 地点管理欢迎卡片 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="welcome-card">
          <div class="welcome-content">
            <div class="welcome-text">
              <h2>地点管理</h2>
              <p>这里可以管理所有讲座地点的信息</p>
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
          placeholder="请输入地点名称搜索"
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
        <el-button type="primary" @click="openAddDialog">新增地点</el-button>
      </el-col>
    </el-row>

    <!-- 地点表格 -->
    <el-table :data="locationList" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="locationName" label="地点名称" width="150" />
      <el-table-column prop="address" label="地址" min-width="200" />
      <el-table-column label="位置坐标" width="180">
        <template #default="scope">
          <span v-if="scope.row.longitude && scope.row.latitude" class="coordinate-text">
            {{ scope.row.longitude.toFixed(6) }}, {{ scope.row.latitude.toFixed(6) }}
          </span>
          <el-tag v-else type="info" size="small">未设置</el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="150" />
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

    <!-- 新增/编辑地点弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="900px" :close-on-click-modal="false">
      <div class="location-dialog-content">
        <!-- 左侧表单 -->
        <div class="form-section">
          <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
            <el-form-item label="地点名称" prop="locationName">
              <el-input v-model="form.locationName" placeholder="请输入地点名称" />
            </el-form-item>
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" placeholder="请选择地址" readonly>
                <template #append>
                  <el-button @click="clearSelectedLocation">清除</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="经纬度" v-if="form.longitude && form.latitude">
              <el-input :value="`${form.longitude}, ${form.latitude}`" readonly />
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
        </div>
        
        <!-- 右侧地图 -->
        <div class="map-section">
          <div class="map-header">
            <h4>在地图上选择地点位置</h4>
            <el-input
              v-model="mapSearchKeyword"
              placeholder="搜索校园地点"
              size="small"
              style="width: 200px;"
              @keyup.enter="searchMapPOI"
            >
              <template #append>
                <el-button @click="searchMapPOI" size="small">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>
          <div id="dialogMapContainer" class="dialog-map"></div>
          <div class="map-tips">
            <el-alert
              title="提示：点击地图上的地点标记或直接点击地图来选择位置"
              type="info"
              :closable="false"
              show-icon
            />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :disabled="!form.address">确定</el-button>
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
import { ref, reactive, onMounted, watch, nextTick } from "vue";
// 引入Element Plus消息组件和图标
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Location } from '@element-plus/icons-vue';
// 引入用户存储
import { useUserStore } from "../../stores/user";
// 引入地点API
import { getAllLocations, createLocation, updateLocation, deleteLocation } from "../../api/location";

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

// 地点列表数据
const locationList = ref([]); // 地点列表
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
  locationName: "",
  address: "",
  longitude: null,
  latitude: null,
  description: "",
});
const formRef = ref(null);

// 地图相关变量
let dialogMap = null; // 弹窗中的地图实例
let dialogMarkers = []; // 弹窗地图标记点
let selectedMarker = null; // 选中的标记点
const mapSearchKeyword = ref(''); // 地图搜索关键词

// 校园地点数据
const campusLocations = [
  { name: '教学楼A1', position: [121.53451, 38.888243], type: 'building' },
  { name: '教学楼A2', position: [121.535113, 38.888948], type: 'building' },
  { name: '教学楼A3', position: [121.536149, 38.88881], type: 'building' },
  { name: '教学楼A5', position: [121.535118, 38.890396], type: 'building' },
  { name: '教学楼A6', position: [121.535784, 38.891845], type: 'building' },
  { name: '教学楼A7', position: [121.536912, 38.892601], type: 'building' },
  { name: '教学楼A8', position: [121.535605, 38.892693], type: 'building' },
  { name: '教学楼A9', position: [121.536648, 38.890839], type: 'building' },
  { name: '教学楼A10', position: [121.536493, 38.893558], type: 'building' },
  { name: '教学楼A11', position: [121.534939, 38.896431], type: 'building' },
  { name: '教学楼A12', position: [121.533964, 38.896267], type: 'building' },
];

// 校园中心点和半径
const campusCenter = [121.536985, 38.892765]; // 大连东软信息学院软件园校区中心点
const campusRadius = 0.006; // 约660米半径（经纬度单位）

// 校验规则
const rules = {
  locationName: [{ required: true, message: "请输入地点名称", trigger: "blur" }],
  address: [{ required: true, message: "请输入地址", trigger: "blur" }],
};

// 获取地点列表
const getLocationList = async () => {
  loading.value = true;
  try {
    const res = await getAllLocations();
    console.log('后端返回的地点数据:', res.data); // 调试日志
    
    // 处理后端返回的数据，将name映射为locationName
    locationList.value = res.data.map(item => ({
      id: item.id, // 确保ID正确传递
      locationName: item.name,
      address: item.address || '',
      longitude: parseFloat(item.longitude), // 确保经度是数值类型
      latitude: parseFloat(item.latitude),   // 确保纬度是数值类型
      description: item.type || '',
      createdTime: item.createdTime
    }));
    
    console.log('处理后的地点数据:', locationList.value); // 调试日志
    
    // 按照id排序
    locationList.value.sort((a, b) => {
      // 确保使用数值比较
      const idA = Number(a.id);
      const idB = Number(b.id);
      return idA - idB;
    });
    
    // 如果有关键字搜索，进行前端过滤
    if (query.keyword) {
      locationList.value = locationList.value.filter(item => 
        (item.locationName && item.locationName.toLowerCase().includes(query.keyword.toLowerCase())) ||
        (item.address && item.address.toLowerCase().includes(query.keyword.toLowerCase()))
      );
    }
    
    // 计算总数和分页
    total.value = locationList.value.length;
    
    // 前端分页
    const start = (query.page - 1) * query.size;
    const end = start + query.size;
    locationList.value = locationList.value.slice(start, end);
    
    loading.value = false;
  } catch (error) {
    console.error("获取地点列表失败:", error);
    ElMessage.error("获取地点列表失败");
    loading.value = false;
  }
};

// 搜索处理
const handleSearch = () => {
  query.page = 1;
  getLocationList();
};

// 重置搜索
const handleReset = () => {
  query.keyword = "";
  query.page = 1;
  getLocationList();
};

// 分页处理
const handleCurrentChange = (page) => {
  query.page = page;
  getLocationList();
};

// 打开新增弹窗
const openAddDialog = () => {
  dialogTitle.value = "新增地点";
  isAdd.value = true;
  Object.assign(form, {
    id: null,
    locationName: "",
    address: "",
    longitude: null,
    latitude: null,
    description: "",
  });
  dialogVisible.value = true;
  // 延迟初始化地图
  nextTick(() => {
    initDialogMap();
  });
};

// 打开编辑弹窗
const openEditDialog = (row) => {
  dialogTitle.value = "编辑地点";
  isAdd.value = false;
  
  // 确保ID被正确传递
  console.log('编辑行数据:', row); // 调试日志
  
  // 重置表单后再赋值，避免残留旧数据
  Object.assign(form, {
    id: null,
    locationName: "",
    address: "",
    longitude: null,
    latitude: null,
    description: "",
  });
  
  // 将行数据赋值给表单
  Object.assign(form, { ...row });
  console.log('表单数据:', form); // 调试日志
  
  dialogVisible.value = true;
  // 延迟初始化地图
  nextTick(() => {
    initDialogMap();
    // 如果有经纬度信息，在地图上显示
    if (row.longitude && row.latitude) {
      selectLocationOnMap([row.longitude, row.latitude], row.address);
    }
  });
};

// 初始化弹窗地图
const initDialogMap = () => {
  if (dialogMap) {
    dialogMap.destroy();
    dialogMap = null;
  }
  
  // 高德地图API密钥
  window._AMapSecurityConfig = {
    securityJsCode: '',
  };

  // 检查是否已加载高德地图API
  if (typeof AMap !== 'undefined') {
    createDialogMap();
  } else {
    // 异步加载高德地图API
    const script = document.createElement('script');
    script.src = 'https://webapi.amap.com/maps?v=2.0&key=66c8d1c1ec025968df446cdddd1cacdc&plugin=AMap.Geolocation,AMap.PlaceSearch,AMap.ToolBar';
    script.onload = () => {
      createDialogMap();
    };
    document.head.appendChild(script);
  }
};

// 创建弹窗地图
const createDialogMap = () => {
  dialogMap = new AMap.Map('dialogMapContainer', {
    zoom: 16,
    center: campusCenter,
    viewMode: '3D',
  });

  // 添加工具条控件
  dialogMap.addControl(new AMap.ToolBar());

  // 绘制校园边界
  const campusBoundary = new AMap.Circle({
    center: campusCenter,
    radius: campusRadius * 111000,
    strokeColor: '#3366FF',
    strokeWeight: 3,
    strokeOpacity: 0.8,
    fillColor: '#99CCFF',
    fillOpacity: 0.3,
    zIndex: 50,
  });
  campusBoundary.setMap(dialogMap);

  // 添加校园地点标记
  addDialogMapMarkers();

  // 添加地图点击事件
  dialogMap.on('click', (e) => {
    const position = [e.lnglat.lng, e.lnglat.lat];
    // 检查是否在校园范围内
    if (isInCampus(position)) {
      selectLocationOnMap(position, `自定义位置 (${position[0].toFixed(6)}, ${position[1].toFixed(6)})`);
    } else {
      ElMessage.warning('请选择校园范围内的位置');
    }
  });
};

// 添加弹窗地图标记
const addDialogMapMarkers = () => {
  // 清除之前的标记
  dialogMarkers.forEach(marker => marker.setMap(null));
  dialogMarkers = [];

  campusLocations.forEach(location => {
    const marker = new AMap.Marker({
      position: location.position,
      title: location.name,
      clickable: true,
    });

    // 使用自定义内容作为标记
    const content = document.createElement('div');
    content.className = 'marker-content';
    content.innerHTML = `<div class="marker-icon" style="background-color: #000">${location.name.substring(0, 1)}</div>`;
    marker.setContent(content);

    // 点击标记时选择该位置
    marker.on('click', () => {
      selectLocationOnMap(location.position, location.name);
    });

    marker.setMap(dialogMap);
    dialogMarkers.push(marker);
  });
};

// 在地图上选择位置
const selectLocationOnMap = (position, address) => {
  // 清除之前选中的标记
  if (selectedMarker) {
    selectedMarker.setMap(null);
  }

  // 创建选中标记
  selectedMarker = new AMap.Marker({
    position: position,
    icon: new AMap.Icon({
      image: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png',
      size: new AMap.Size(25, 34),
      imageSize: new AMap.Size(25, 34),
      anchor: new AMap.Pixel(12.5, 17)
    }),
    zIndex: 100,
    title: '选中位置',
    animation: 'AMAP_ANIMATION_BOUNCE'
  });
  selectedMarker.setMap(dialogMap);

  // 更新表单数据
  form.address = address;
  form.longitude = position[0];
  form.latitude = position[1];

  // 地图中心移动到选中位置
  dialogMap.setCenter(position);

  ElMessage.success(`已选择位置: ${address}`);
};

// 清除选中的位置
const clearSelectedLocation = () => {
  if (selectedMarker) {
    selectedMarker.setMap(null);
    selectedMarker = null;
  }
  form.address = '';
  form.longitude = null;
  form.latitude = null;
  ElMessage.info('已清除选中位置');
};

// 判断点是否在校园范围内
const isInCampus = (position) => {
  if (!position) return false;
  const distance = AMap.GeometryUtil.distance(position, campusCenter);
  return distance <= campusRadius * 111000;
};

// 搜索地图地点
const searchMapPOI = () => {
  if (!mapSearchKeyword.value) {
    ElMessage.warning('请输入搜索关键词');
    return;
  }

  // 在校园地点中搜索
  const results = campusLocations.filter(location => 
    location.name.includes(mapSearchKeyword.value)
  );

  if (results.length > 0) {
    // 清除之前的搜索结果标记
    addDialogMapMarkers();

    // 高亮搜索结果
    results.forEach(location => {
      const marker = new AMap.Marker({
        position: location.position,
        title: location.name,
        animation: 'AMAP_ANIMATION_BOUNCE',
        clickable: true,
      });

      const content = document.createElement('div');
      content.className = 'marker-content';
      content.innerHTML = `<div class="marker-icon" style="background-color: #F56C6C">${location.name.substring(0, 1)}</div>`;
      marker.setContent(content);

      marker.on('click', () => {
        selectLocationOnMap(location.position, location.name);
      });

      marker.setMap(dialogMap);
      dialogMarkers.push(marker);
    });

    // 调整视野以包含搜索结果
    if (results.length === 1) {
      dialogMap.setCenter(results[0].position);
      dialogMap.setZoom(17);
    } else {
      const markers = results.map(location => {
        const marker = new AMap.Marker({ position: location.position });
        return marker;
      });
      dialogMap.setFitView(markers);
    }

    ElMessage.success(`找到 ${results.length} 个地点`);
  } else {
    ElMessage.warning('未找到匹配的地点');
  }
};

// 监听弹窗关闭，清理地图
watch(dialogVisible, (newVal) => {
  if (!newVal && dialogMap) {
    // 清理地图资源
    if (selectedMarker) {
      selectedMarker.setMap(null);
      selectedMarker = null;
    }
    dialogMarkers.forEach(marker => marker.setMap(null));
    dialogMarkers = [];
    mapSearchKeyword.value = '';
  }
});

// 提交表单
const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return;

    try {
      // 准备提交的数据
      const locationData = {
        id: form.id,
        name: form.locationName,
        address: form.address,
        longitude: form.longitude,
        latitude: form.latitude,
        type: form.description
      };

      console.log('提交的数据:', locationData); // 调试日志

      if (isAdd.value) {
        // 新增地点
        await createLocation(locationData);
        ElMessage.success("新增地点成功");
      } else {
        // 编辑地点
        if (!locationData.id) {
          console.error("编辑时缺少ID");
          ElMessage.error("编辑失败：缺少ID信息");
          return;
        }
        await updateLocation(locationData);
        ElMessage.success("修改地点成功");
      }
      dialogVisible.value = false;
      getLocationList();
    } catch (error) {
      console.error("操作失败:", error);
      ElMessage.error(error.response?.data?.message || "操作失败");
    }
  });
};

// 删除地点
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除地点【${row.locationName}】吗？`,
    "提示",
    { type: "warning" },
  ).then(async () => {
    try {
      await deleteLocation(row.id);
      ElMessage.success("删除地点成功");
      await getLocationList();
    } catch (error) {
      console.error("删除失败:", error);
      ElMessage.error(error.response?.data?.message || "删除失败");
    }
  });
};

// 页面加载时获取数据
onMounted(() => {
  getLocationList();
});
</script>

<style scoped>
.location-management {
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

/* 地点选择弹窗样式 */
.location-dialog-content {
  display: flex;
  gap: 20px;
  height: 500px;
}

.form-section {
  flex: 1;
  min-width: 300px;
}

.map-section {
  flex: 1;
  min-width: 400px;
  display: flex;
  flex-direction: column;
}

.map-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.map-header h4 {
  margin: 0;
  color: #000;
}

.dialog-map {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  min-height: 400px;
}

.map-tips {
  margin-top: 10px;
}

/* 地图标记样式 */
:deep(.marker-content) {
  position: relative;
}

:deep(.marker-icon) {
  width: 30px;
  height: 30px;
  line-height: 30px;
  text-align: center;
  border-radius: 50%;
  color: white;
  font-weight: bold;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  cursor: pointer;
  transition: transform 0.2s;
}

:deep(.marker-icon:hover) {
  transform: scale(1.1);
}

/* 响应式布局 */
@media (max-width: 768px) {
  .location-dialog-content {
    flex-direction: column;
    height: auto;
  }
  
  .map-section {
    min-width: auto;
  }
  
  .dialog-map {
    min-height: 300px;
  }
}

/* 坐标文本样式 */
.coordinate-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  border: 1px solid #e4e7ed;
}
</style>