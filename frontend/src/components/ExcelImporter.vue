<template>
  <div class="excel-importer">
    <div class="upload-area">
      <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :before-upload="handleBeforeUpload"
          :on-change="handleFileChange"
          :on-remove="handleRemove"
          :show-file-list="true"
          accept=".xlsx,.xls"
          action=""
          drag
      >
        <el-icon class="el-icon--upload">
          <upload-filled/>
        </el-icon>
        <div class="el-upload__text">
          将Excel文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            <p>请上传Excel文件(.xlsx或.xls)，文件大小不超过10MB</p>
            <p>必需表头: 姓名、用户名、手机号、邮箱</p>
            <p>系别信息: 可使用"系别"或"系别ID"字段（至少需要一个）</p>
            <p>可选表头: 密码（默认为123456）</p>
          </div>
        </template>
      </el-upload>
    </div>

    <div v-if="previewData.length > 0" class="preview-container">
      <h3>数据预览 (最多显示10条)</h3>
      <el-alert
          :closable="false"
          :title="missingHeaders.length > 0 ? '表头错误' : '请确认以下数据是否正确'"
          :type="missingHeaders.length > 0 ? 'error' : 'info'"
          style="margin-bottom: 15px;"
      >
        <div>
          <p><strong>必需表头:</strong> 姓名、用户名、手机号、邮箱、系别或系别ID</p>
          <p><strong>可选表头:</strong> 密码 (如不提供或为空，默认为123456)</p>
          <p v-if="missingHeaders.length > 0" style="color: #F56C6C; margin-top: 5px;">
            <strong>缺少必需表头:</strong> {{ missingHeaders.join(', ') }}
          </p>
        </div>
      </el-alert>
      <el-table :data="previewData.slice(0, 10)" border stripe style="width: 100%">
        <el-table-column v-for="(col, index) in previewColumns" :key="index" :label="col" :prop="col">
          <template #default="scope">
            <div v-if="col === '手机号' && scope.row._phoneError" class="error-cell">
              <el-tooltip :content="scope.row._phoneErrorMessage" effect="light" placement="top">
                <span style="color: #F56C6C;">{{ scope.row[col] }}</span>
              </el-tooltip>
            </div>
            <div v-else-if="col === '邮箱' && scope.row._emailError" class="error-cell">
              <el-tooltip :content="scope.row._emailErrorMessage" effect="light" placement="top">
                <span style="color: #F56C6C;">{{ scope.row[col] }}</span>
              </el-tooltip>
            </div>
            <span v-else>{{ scope.row[col] }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="preview-info">
        <p>共 {{ previewData.length }} 条数据</p>
        <el-button :disabled="!hasData || missingHeaders.length > 0" type="primary" @click="confirmImport">确认导入
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue';
import {ElLoading, ElMessage} from 'element-plus';
import {UploadFilled} from '@element-plus/icons-vue';
import * as XLSX from 'xlsx';
import request from '../utils/request';

const props = defineProps({
  importUrl: {
    type: String,
    required: true
  },
  templateFields: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['import-success', 'import-error', 'import-possible-success']);

const uploadRef = ref(null);
const previewData = ref([]);
const previewColumns = ref([]);
const parsedData = ref([]);
const invalidPhoneNumbers = ref([]);

// 计算属性：是否有数据可以导入
const hasData = computed(() => previewData.value.length > 0);

// 文件状态改变时的处理函数
const handleFileChange = (file) => {
  console.log('文件状态改变:', file);

  if (file.status === 'ready') {
    const isExcel = file.name.endsWith('.xlsx') || file.name.endsWith('.xls');
    if (!isExcel) {
      ElMessage.warning('请选择Excel文件(.xlsx或.xls)');
      // 移除非Excel文件
      uploadRef.value.handleRemove(file);
      return;
    }

    // 读取Excel文件内容
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const data = e.target.result;

        // 增强错误处理，检查文件内容是否有效
        if (!data || data.byteLength === 0) {
          throw new Error('文件内容为空');
        }

        console.log('开始解析Excel文件，文件大小:', data.byteLength, '字节');

        // 尝试读取Excel文件
        let workbook;
        try {
          workbook = XLSX.read(data, { type: 'array' });
        } catch (readError) {
          console.error('Excel读取错误:', readError);
          throw new Error('无法读取Excel文件，可能是文件格式不正确或已损坏');
        }

        // 检查是否有工作表
        if (!workbook.SheetNames || workbook.SheetNames.length === 0) {
          throw new Error('Excel文件中没有工作表');
        }

        const firstSheetName = workbook.SheetNames[0];
        const worksheet = workbook.Sheets[firstSheetName];

        // 打印工作表信息，便于调试
        console.log('工作表信息:', {
          sheetName: firstSheetName,
          sheetCount: workbook.SheetNames.length,
          ref: worksheet['!ref'] // 工作表范围
        });

        // 转换为JSON，增加配置选项
        const jsonData = XLSX.utils.sheet_to_json(worksheet, {
          header: 1,
          defval: null, // 空单元格使用null值
          blankrows: false // 跳过空行
        });

        // 打印原始JSON数据，便于调试
        console.log('原始Excel数据行数:', jsonData.length);
        if (jsonData.length > 0) {
          console.log('第一行数据:', jsonData[0]);
        }

        // 提取表头和数据
        if (jsonData.length > 0) {
          const headers = jsonData[0];

          // 验证表头是否有效
          if (!headers || headers.length === 0 || headers.every(h => !h)) {
            throw new Error('Excel文件表头无效或为空');
          }

          previewColumns.value = headers;

          // 打印表头信息，便于调试
          console.log('Excel表头:', headers);

          // 转换为对象数组，增强数据处理
          const rows = [];
          for (let i = 1; i < jsonData.length; i++) {
            // 跳过空行
            if (!jsonData[i] || jsonData[i].length === 0 || jsonData[i].every(cell => cell === null || cell === undefined)) {
              console.log(`跳过第${i+1}行：空行`);
              continue;
            }

            const row = {};
            let hasData = false; // 检查行是否有实际数据

            for (let j = 0; j < headers.length; j++) {
              if (headers[j]) { // 只处理有效的表头
                const value = jsonData[i][j];
                row[headers[j]] = value;
                if (value !== null && value !== undefined && value !== '') {
                  hasData = true;
                }
              }
            }

            // 只添加有数据的行
            if (hasData) {
              rows.push(row);
            } else {
              console.log(`跳过第${i+1}行：无有效数据`);
            }
          }

          if (rows.length === 0) {
            ElMessage.warning('Excel文件中没有有效数据行');
            uploadRef.value.handleRemove(file);
            return;
          }

          previewData.value = rows;
          parsedData.value = rows;
          console.log('解析的有效数据行数:', rows.length);
          console.log('第一条解析数据:', rows[0]);

          // 清空之前的错误记录
          invalidPhoneNumbers.value = [];

          // 立即检查手机号格式
          rows.forEach(item => {
            // 验证手机号格式
            if (item.手机号) {
              const phoneStr = item.手机号.toString().trim();
              if (!/^1[3-9]\d{9}$/.test(phoneStr)) {
                console.warn(`【数据预处理】手机号格式不正确的数据:`, item);
                item._phoneError = true; // 标记手机号错误
                item._phoneErrorMessage = `手机号格式不正确：${phoneStr}`; // 记录错误信息

                // 收集错误信息
                invalidPhoneNumbers.value.push({
                  name: item.姓名 || '未知',
                  phone: phoneStr,
                  message: item._phoneErrorMessage
                });
              }
            }

            // 验证邮箱格式
            if (item.邮箱) {
              const emailStr = item.邮箱.toString().trim();
              if (!/^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(emailStr)) {
                item._emailError = true; // 标记邮箱错误
                item._emailErrorMessage = `邮箱格式不正确：${emailStr}`; // 记录错误信息
              }
            }
          });

          // 如果有手机号格式错误，显示提示
          if (invalidPhoneNumbers.value.length > 0) {
            console.warn(`发现${invalidPhoneNumbers.value.length}个手机号格式错误`);
          }
        } else {
          ElMessage.warning('Excel文件中没有数据');
          uploadRef.value.handleRemove(file);
        }
      } catch (error) {
        console.error('解析Excel文件失败:', error);
        ElMessage.error(error.message || '解析Excel文件失败，请检查文件格式');
        uploadRef.value.handleRemove(file);
      }
    };
    reader.readAsArrayBuffer(file.raw);
  }
};

// 文件移除时的处理函数
const handleRemove = () => {
  console.log('文件被移除');
  previewData.value = [];
  previewColumns.value = [];
  parsedData.value = [];
  invalidPhoneNumbers.value = []; // 清空手机号错误记录
};

// 上传前的处理函数
const handleBeforeUpload = (file) => {
  console.log('上传前验证文件:', file);

  // 检查文件类型
  const isExcel = file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
      file.type === 'application/vnd.ms-excel' ||
      file.name.endsWith('.xlsx') ||
      file.name.endsWith('.xls');

  if (!isExcel) {
    ElMessage({
      type: 'warning',
      dangerouslyUseHTMLString: true,
      message: `<strong>文件类型错误</strong><br/><div style="margin-top: 5px;">只能上传Excel文件(.xlsx或.xls)，当前文件类型: ${file.type || '未知'}</div>`,
      duration: 5000,
      showClose: true
    });
    return false;
  }

  // 检查文件大小（小于10MB）
  const isLt10M = file.size / 1024 / 1024 < 10;
  if (!isLt10M) {
    ElMessage({
      type: 'warning',
      dangerouslyUseHTMLString: true,
      message: `<strong>文件大小超限</strong><br/><div style="margin-top: 5px;">文件大小不能超过10MB，当前文件大小: ${(file.size / 1024 / 1024).toFixed(2)}MB</div>`,
      duration: 5000,
      showClose: true
    });
    return false;
  }

  return true;
};

// 确认导入
const confirmImport = async () => {
  // 检查是否有数据可以导入
  if (!parsedData.value || parsedData.value.length === 0) {
    ElMessage.warning('没有有效数据可以导入');
    return;
  }

  // 检查必需表头是否存在
  const requiredHeaders = ['姓名', '用户名', '手机号', '邮箱'];
  const hasRequiredHeaders = requiredHeaders.every(header =>
      previewColumns.value.includes(header)
  );

  // 检查系别相关字段
  const hasDepartmentId = previewColumns.value.includes('系别ID');
  const hasDepartment = previewColumns.value.includes('系别');

  if (!hasRequiredHeaders) {
    ElMessage.error(`缺少必需表头: ${requiredHeaders.filter(h => !previewColumns.value.includes(h)).join(', ')}`);
    return;
  }

  // 检查系别或系别ID至少有一个
  if (!hasDepartmentId && !hasDepartment) {
    ElMessage.error('缺少系别信息: 需要包含"系别"或"系别ID"字段');
    return;
  }

  // 检查是否有不符合格式的手机号
  if (invalidPhoneNumbers.value && invalidPhoneNumbers.value.length > 0) {
    // 构建错误消息
    const maxDisplay = 5; // 最多显示5条错误
    const phoneErrors = invalidPhoneNumbers.value.slice(0, maxDisplay);
    const moreErrors = invalidPhoneNumbers.value.length > maxDisplay ?
        `\n...(还有${invalidPhoneNumbers.value.length - maxDisplay}条错误未显示)` : '';

    // 显示错误提示
    ElMessage({
      type: 'error',
      dangerouslyUseHTMLString: true,
      message: `<strong>导入失败：手机号格式不符合国内要求</strong><br/>` +
          phoneErrors.map((err, index) =>
              `<div style="margin-left: 10px;">${index + 1}. ${err.name}: ${err.phone}</div>`
          ).join('') +
          (moreErrors ? `<div style="margin-top: 5px; color: #909399;">${moreErrors}</div>` : '') +
          `<div style="margin-top: 10px;">请修正以上手机号格式后再导入。</div>`,
      duration: 10000,
      showClose: true
    });
    return;
  }

  // 继续导入流程
  await handleImport();
};

// 定义requiredHeaders为ref，以便在计算属性中使用
const requiredHeaders = ref(["姓名", "用户名", "手机号", "邮箱"]);

const missingHeaders = computed(() => {
  const missing = [];

  // 检查基本必填字段
  for (const header of requiredHeaders.value) {
    if (!previewColumns.value.includes(header)) {
      missing.push(header);
    }
  }

  // 特殊检查：系别和系别ID至少要有一个
  if (!previewColumns.value.includes("系别") && !previewColumns.value.includes("系别ID")) {
    missing.push("系别或系别ID");
  }

  return missing;
});

const showHeaderWarning = () => {
  // 如果缺少必需表头，显示警告
  if (missingHeaders.value.length > 0) {
    ElMessage({
      type: 'warning',
      dangerouslyUseHTMLString: true,
      message: `<strong>表头错误</strong><br/><div style="margin-top: 5px;">缺少必需表头: <span style="color: #F56C6C;">${missingHeaders.value.join(', ')}</span></div><div style="margin-top: 5px;">必需表头: 姓名、用户名、手机号、邮箱，以及系别或系别ID至少一个</div>`,
      duration: 5000,
      showClose: true
    });
    return true;
  }
  return false;
};

// 处理文件读取
const handleFileRead = (data) => {
  // 设置预览数据
  previewData.value = data;

  // 获取表头
  if (data.length > 0) {
    previewColumns.value = Object.keys(data[0]);
    hasData.value = true;

    // 检查必需表头并显示警告
    showHeaderWarning();
  } else {
    hasData.value = false;
  }
};

const handleImport = async () => {
  if (!hasData.value) {
    ElMessage.warning('没有数据可以导入');
    return;
  }

  // 检查必需表头
  if (missingHeaders.value.length > 0) {
    ElMessage({
      type: 'error',
      dangerouslyUseHTMLString: true,
      message: `<strong>无法导入</strong><br/><div style="margin-top: 5px;">Excel文件缺少必需表头: <span style="color: #F56C6C;">${missingHeaders.value.join(', ')}</span></div><div style="margin-top: 5px;">请确保Excel文件包含所有必需表头: 姓名、用户名、手机号、邮箱，以及系别或系别ID至少一个</div>`,
      duration: 5000,
      showClose: true
    });
    return;
  }

  // 再次检查是否有不符合格式的手机号
  if (invalidPhoneNumbers.value && invalidPhoneNumbers.value.length > 0) {
    ElMessage({
      type: 'error',
      dangerouslyUseHTMLString: true,
      message: `<strong>导入失败</strong><br/><div style="margin-top: 5px;">存在${invalidPhoneNumbers.value.length}个手机号格式不符合国内要求，请修正后再导入</div>`,
      duration: 5000,
      showClose: true
    });
    return;
  }

  // 预处理数据，收集错误信息
  console.log('开始预处理数据，原始数据条数:', previewData.value.length);

  // 不需要重新收集错误信息，使用已经在handleFileChange中收集的错误信息
  console.log(`预处理数据时，已有${invalidPhoneNumbers.value.length}个手机号格式错误`);

  // 确保所有错误标记都已正确设置
  previewData.value.forEach(item => {
    // 检查item是否为有效对象
    if (!item || typeof item !== 'object') {
      return;
    }

    // 确保手机号错误标记已设置
    if (item.手机号 && item._phoneError) {
      console.log(`确认手机号错误标记: ${item.姓名}, ${item.手机号}`);
    }

    // 确保邮箱错误标记已设置
    if (item.邮箱 && item._emailError) {
      console.log(`确认邮箱错误标记: ${item.姓名}, ${item.邮箱}`);
    }
  });

  // 然后处理数据，过滤无效数据
  parsedData.value = previewData.value
      .map(item => {
        // 检查item是否为有效对象
        if (!item || typeof item !== 'object') {
          console.warn('【数据预处理】跳过无效数据项:', item);
          return null; // 跳过无效数据
        }

        // 验证必要的表头是否存在
        for (const header of requiredHeaders.value) {
          if (!(header in item)) {
            console.warn(`【数据预处理】跳过缺少必要表头 ${header} 的数据:`, item);
            return null; // 跳过缺少必要表头的数据
          }
        }

        // 验证所有必填字段是否有值
        let hasEmptyRequiredField = false;

        for (const header of requiredHeaders.value) {
          if (!item[header] || item[header].toString().trim() === '') {
            console.warn(`【数据预处理】字段 ${header} 为空的数据:`, item);
            hasEmptyRequiredField = true;
            // 不立即返回null，继续检查其他字段
          }
        }

        // 单独检查系别字段
        if (!item.系别 && !item.系别ID) {
          console.warn(`【数据预处理】系别/系别ID字段为空的数据:`, item);
          hasEmptyRequiredField = true;
        }

        // 检查是否有格式错误
        if (hasEmptyRequiredField || item._phoneError || item._emailError) {
          return null; // 跳过有错误的数据
        }

        return item;
      })
      .filter(item => item !== null); // 过滤掉无效数据

  console.log(`预处理后有效数据: ${parsedData.value.length}条`);

  // 检查是否有有效数据可以导入
  if (!parsedData.value || parsedData.value.length === 0) {
    ElMessage.warning('没有有效数据可以导入，请检查数据格式是否正确');
    return;
  }

  console.log(`预处理后有效数据: ${parsedData.value.length}条`);

  // 显示加载中
  const loading = ElLoading.service({
    lock: true,
    text: '正在导入数据，请稍候...',
    background: 'rgba(0, 0, 0, 0.7)'
  });

  try {
    // 检查是否有数据可以导入
    if (parsedData.value.length === 0) {
      ElMessage.warning('没有有效数据可以导入');
      loading.close();
      return;
    }

    // 发送请求到后端，确保数据格式正确
    // 增强数据映射逻辑，处理更多可能的字段名称
    console.log('准备发送导入请求到:', props.importUrl);

    // 再次检查是否有有效数据可以导入
    if (!parsedData.value || parsedData.value.length === 0) {
      ElMessage.warning('没有有效数据可以导入');
      loading.close();
      return;
    }

    // 记录开始请求的时间，用于计算请求耗时
    const requestStartTime = new Date().getTime();

    // 再次检查是否有有效数据可以导入
    if (!parsedData.value || parsedData.value.length === 0) {
      ElMessage.warning('没有有效数据可以导入');
      loading.close();
      return;
    }

    const response = await request({
      url: props.importUrl,
      method: 'post',
      timeout: 120000, // 增加超时时间到120秒，处理大量数据可能需要更长时间
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
      },
      data: {
        users: parsedData.value.map(item => {
          try {
            // 打印原始项目数据，便于调试
            console.log('原始项目数据:', item);

            // 创建一个标准化的用户对象
            const standardizedUser = {
              "姓名": null,
              "用户名": null,
              "学号": null,
              "密码": null,
              "手机号": null,
              "邮箱": null,
              "系别": null
            };

            // 数据清理和格式化
            const cleanData = {};
            Object.keys(item).forEach(key => {
              // 去除前后空格
              if (item[key] !== null && item[key] !== undefined) {
                cleanData[key] = typeof item[key] === 'string' ? item[key].trim() : item[key];
              }
            });

            // 严格按照标准表头映射数据
            standardizedUser["姓名"] = cleanData.姓名;
            standardizedUser["用户名"] = cleanData.用户名;
            standardizedUser["学号"] = cleanData.学号 || cleanData.用户名; // 优先使用学号字段，如果没有则使用用户名

            // 处理密码字段 - 如果密码字段存在且有值则使用，否则使用默认密码123456
            if ("密码" in cleanData && cleanData.密码 && cleanData.密码.toString().trim() !== '') {
              standardizedUser["密码"] = cleanData.密码;
            } else {
              standardizedUser["密码"] = '123456'; // 默认密码
              console.log('使用默认密码123456');
            }

            // 处理手机号 - 确保是字符串格式
            if (cleanData.手机号) {
              standardizedUser["手机号"] = cleanData.手机号.toString().trim();
            }

            // 处理邮箱
            if (cleanData.邮箱) {
              standardizedUser["邮箱"] = cleanData.邮箱.toString().trim();
            }

            // 处理系别名称，直接传递给后端处理
            if (cleanData.系别 !== undefined && cleanData.系别 !== null) {
              standardizedUser["系别"] = cleanData.系别;
            }
            // 如果有系别ID字段，将其作为系别名称处理
            else if (cleanData.系别ID !== undefined && cleanData.系别ID !== null) {
              standardizedUser["系别"] = cleanData.系别ID.toString();
            }

            // 最终验证所有必填字段是否有值
            const missingFields = requiredHeaders.value.filter(field => {
              return standardizedUser[field] === null ||
                  standardizedUser[field] === undefined ||
                  (typeof standardizedUser[field] === 'string' && standardizedUser[field].trim() === '');
            });

            // 检查系别信息是否存在
            const hasDepartmentName = standardizedUser["系别"] !== null &&
                standardizedUser["系别"] !== undefined &&
                (typeof standardizedUser["系别"] === 'string' ?
                    standardizedUser["系别"].trim() !== '' : true);

            if (!hasDepartmentName) {
              missingFields.push("系别");
            }

            if (missingFields.length > 0) {
              console.error(`标准化后数据缺少必填字段: ${missingFields.join(', ')}`, standardizedUser);
              return null; // 跳过无效数据
            }

            // 打印映射后的数据，便于调试
            console.log('映射后的用户数据:', standardizedUser);

            return standardizedUser;
          } catch (err) {
            console.error('数据处理过程中出错:', err, item);
            return null; // 处理过程中出错，跳过该条数据
          }
        }).filter(item => item !== null) // 过滤掉无效数据
      }
    });

    // 计算请求耗时
    const requestEndTime = new Date().getTime();
    const requestDuration = (requestEndTime - requestStartTime) / 1000;
    console.log(`导入请求耗时: ${requestDuration}秒`);

    // 检查响应是否为空
    if (!response) {
      console.error('导入响应为空');
      loading.close();
      ElMessage.error('导入失败：服务器未返回数据');
      emit('import-error', new Error('服务器未返回数据'));
      return;
    }

    // 打印响应，便于调试
    console.log('导入响应:', response);

    loading.close();

    // 处理成功响应
    // 检查response是否有效，以及是否包含success字段
    if (response && response.success) {
      // 显示成功导入的消息
      ElMessage.success(`导入成功：成功${response.successCount}条，失败${response.failCount}条`);

      // 如果有失败信息，显示详细信息
      if (response.failCount > 0 && response.failMessages && response.failMessages.length > 0) {
        const maxDisplayErrors = 5; // 最多显示5条错误信息
        const messages = response.failMessages.slice(0, maxDisplayErrors);
        const moreErrorsText = response.failMessages.length > maxDisplayErrors ?
            `\n...(还有${response.failMessages.length - maxDisplayErrors}条错误未显示)` : '';

        // 使用HTML格式显示更详细的错误信息
        ElMessage({
          type: 'warning',
          dangerouslyUseHTMLString: true,
          message: `<strong>导入部分失败</strong><br/>` +
              `<div style="margin-top: 5px; margin-bottom: 5px;">成功导入: <span style="color: #67C23A;">${response.successCount}条</span>, 失败: <span style="color: #F56C6C;">${response.failCount}条</span></div>` +
              `<div style="margin-top: 10px;"><strong>失败原因:</strong></div>` +
              messages.map((msg, index) => `<div style="margin-left: 10px;">${index + 1}. ${msg}</div>`).join('') +
              (moreErrorsText ? `<div style="margin-top: 5px; color: #909399;">${moreErrorsText}</div>` : '') +
              `<div style="margin-top: 10px;">请检查Excel文件格式是否符合要求，确保所有必填字段都已填写。</div>`,
          duration: 15000, // 增加显示时间
          showClose: true,
        });
      } else if (response.successCount > 0) {
        // 全部成功的情况
        ElMessage({
          type: 'success',
          dangerouslyUseHTMLString: true,
          message: `<strong>导入全部成功</strong><br/><div style="margin-top: 5px;">成功导入: <span style="color: #67C23A;">${response.successCount}条</span></div>`,
          duration: 5000,
          showClose: true,
        });
      }

      // 清空数据
      previewData.value = [];
      previewColumns.value = [];
      parsedData.value = [];
      uploadRef.value.clearFiles();

      // 触发成功事件
      emit('import-success', response);
    } else {
      ElMessage.error(response.message || '导入失败');
      emit('import-error', response);
    }
  } catch (error) {
    loading.close();
    console.error('导入错误:', error);

    // 增强错误日志，打印更多信息
    console.error('错误详情:', {
      message: error.message,
      stack: error.stack,
      response: error.response,
      request: error.request
    });

    // 特殊处理：如果是系统错误，但数据可能已经成功插入数据库
    if (error.message === '系统错误') {
      // 尝试显示一个更友好的提示
      ElMessage({
        type: 'warning',
        dangerouslyUseHTMLString: true,
        message: `<strong>注意</strong><br/>
                 <div style="margin-top: 10px;">系统返回了错误，但数据可能已经成功导入。</div>
                 <div style="margin-top: 5px;">建议刷新页面查看最新数据，避免重复导入。</div>`,
        duration: 8000,
        showClose: true
      });

      // 清空数据，避免用户重复提交
      previewData.value = [];
      previewColumns.value = [];
      parsedData.value = [];
      uploadRef.value.clearFiles();

      // 触发一个特殊的事件，通知父组件可能需要刷新数据
      emit('import-possible-success');

      return; // 提前返回，不再显示其他错误消息
    }

    // 尝试提取详细错误信息
    let errorMessage = '导入失败，请检查数据格式或网络连接';
    let errorDetail = '';
    let errorDetails = [];

    // 处理系统错误的特殊情况
    if (error.message === '系统错误') {
      // 检查是否有空数据或格式问题
      if (!parsedData.value || parsedData.value.length === 0) {
        // 没有有效数据可以导入
        ElMessage({
          type: 'error',
          dangerouslyUseHTMLString: true,
          message: `<strong>导入失败</strong><br/>
                   <div style="margin-top: 10px;">没有有效数据可以导入</div>
                   <div style="margin-top: 10px;">建议操作：</div>
                   <div style="margin-left: 10px; margin-top: 5px;">1. 检查Excel文件格式是否正确</div>
                   <div style="margin-left: 10px;">2. 确保Excel文件中包含有效数据</div>
                   <div style="margin-left: 10px;">3. 确保所有必填字段（姓名、用户名、手机号、邮箱）都已正确填写，以及系别或系别ID至少一个</div>`,
          duration: 15000,
          showClose: true
        });
        return;
      } else {
        // 检查数据中是否有格式问题
        const invalidData = parsedData.value.some(item => {
          return !item || typeof item !== 'object' ||
              !item.姓名 || !item.用户名 || !item.手机号 || !item.邮箱 ||
              (!item.系别 && !item.系别ID);
        });

        if (invalidData) {
          // 数据格式有误
          ElMessage({
            type: 'error',
            dangerouslyUseHTMLString: true,
            message: `<strong>导入失败</strong><br/>
                     <div style="margin-top: 10px;">数据格式有误</div>
                     <div style="margin-top: 10px;">建议操作：</div>
                     <div style="margin-left: 10px; margin-top: 5px;">1. 检查Excel文件格式是否正确</div>
                     <div style="margin-left: 10px;">2. 确保所有必填字段（姓名、用户名、手机号、邮箱）都已正确填写，以及系别或系别ID至少一个</div>
                     <div style="margin-left: 10px;">3. 确保手机号和邮箱格式正确</div>`,
            duration: 15000,
            showClose: true
          });
          return;
        } else {
          // 系统处理数据出错
          ElMessage({
            type: 'error',
            dangerouslyUseHTMLString: true,
            message: `<strong>导入失败</strong><br/>
                     <div style="margin-top: 10px;">系统处理数据时出现错误，可能的原因：</div>
                     <div style="margin-left: 10px; margin-top: 5px;">1. 数据格式不符合要求</div>
                     <div style="margin-left: 10px;">2. 数据中包含无效字符</div>
                     <div style="margin-left: 10px;">3. 服务器处理能力暂时受限</div>
                     <div style="margin-left: 10px;">4. 数据中可能存在重复的用户名或学号</div>
                     <div style="margin-top: 10px;">建议操作：</div>
                     <div style="margin-left: 10px; margin-top: 5px;">1. 检查Excel文件格式是否正确</div>
                     <div style="margin-left: 10px;">2. 确保所有必填字段（姓名、用户名、手机号、邮箱）都已正确填写，以及系别或系别ID至少一个</div>
                     <div style="margin-left: 10px;">3. 减少导入数据量，分批导入</div>
                     <div style="margin-left: 10px;">4. 检查是否有重复的用户名或学号</div>
                     <div style="margin-left: 10px;">5. 稍后重试</div>`,
            duration: 15000,
            showClose: true
          });
          return; // 已经显示了详细错误信息，不需要再显示简单错误信息
        }
      }
    } else if (error.response) {
      // 服务器响应了请求，但返回了错误状态码
      console.log('服务器响应错误:', error.response);

      if (error.response.status === 400) {
        errorMessage = '请求数据格式不正确，请检查Excel模板是否匹配';
      } else if (error.response.status === 401) {
        errorMessage = '未授权，请重新登录';
      } else if (error.response.status === 403) {
        errorMessage = '没有权限执行此操作';
      } else if (error.response.status === 404) {
        errorMessage = '请求的资源不存在';
      } else if (error.response.status === 500) {
        errorMessage = '服务器内部错误，请联系管理员';
      }

      // 尝试从响应中提取更详细的错误信息
      if (error.response.data) {
        if (typeof error.response.data === 'string') {
          errorDetail = error.response.data;
        } else if (error.response.data.message) {
          errorDetail = error.response.data.message;
        } else if (error.response.data.error) {
          errorDetail = error.response.data.error;
        }

        // 检查是否有详细错误信息数组
        if (error.response.data.errors && Array.isArray(error.response.data.errors)) {
          errorDetails = error.response.data.errors;
        } else if (error.response.data.failMessages && Array.isArray(error.response.data.failMessages)) {
          errorDetails = error.response.data.failMessages;
        }
      }
    } else if (error.request) {
      // 请求已发送，但没有收到响应
      console.log('未收到响应:', error.request);
      errorMessage = '服务器未响应，请检查网络连接或服务器状态';
    } else if (error.message && error.message.includes('timeout')) {
      // 请求超时
      errorMessage = '请求超时，服务器可能正忙或处理大量数据';
    } else if (error.message) {
      // 其他错误
      errorMessage = error.message;
    }

    // 显示错误消息，如果有详细信息则一并显示
    if (errorDetails.length > 0) {
      // 如果有详细错误信息数组，显示更详细的错误消息
      const maxDisplayErrors = 5; // 最多显示5条错误信息
      const messages = errorDetails.slice(0, maxDisplayErrors);
      const moreErrorsText = errorDetails.length > maxDisplayErrors ?
          `还有${errorDetails.length - maxDisplayErrors}条错误未显示` : '';

      ElMessage({
        type: 'error',
        dangerouslyUseHTMLString: true,
        message: `<strong>${errorMessage}</strong><br/>` +
            `<div style="margin-top: 10px;"><strong>错误详情:</strong></div>` +
            messages.map((msg, index) => `<div style="margin-left: 10px;">${index + 1}. ${msg}</div>`).join('') +
            (moreErrorsText ? `<div style="margin-top: 5px; color: #909399;">${moreErrorsText}</div>` : '') +
            `<div style="margin-top: 10px;">请检查Excel文件格式是否符合要求，确保所有必填字段都已填写。</div>`,
        duration: 15000,
        showClose: true,
      });
    } else if (errorDetail) {
      ElMessage({
        type: 'error',
        dangerouslyUseHTMLString: true,
        message: `<strong>${errorMessage}</strong><br/>${errorDetail}`,
        duration: 10000,
        showClose: true
      });
    } else if (error.message === '系统错误') {
      // 系统错误的特殊处理
      ElMessage({
        type: 'error',
        dangerouslyUseHTMLString: true,
        message: `<strong>导入失败</strong><br/>
                 <div style="margin-top: 10px;">系统处理数据时出现错误，可能的原因：</div>
                 <div style="margin-left: 10px; margin-top: 5px;">1. 数据格式不符合要求</div>
                 <div style="margin-left: 10px;">2. 数据中包含无效字符</div>
                 <div style="margin-left: 10px;">3. 服务器处理能力暂时受限</div>
                 <div style="margin-top: 10px;">建议操作：</div>
                 <div style="margin-left: 10px; margin-top: 5px;">1. 检查Excel文件格式是否正确</div>
                 <div style="margin-left: 10px;">2. 确保所有必填字段（姓名、用户名、手机号、邮箱）都已正确填写，以及系别或系别ID至少一个</div>
                 <div style="margin-left: 10px;">3. 减少导入数据量，分批导入</div>
                 <div style="margin-left: 10px;">4. 稍后重试</div>`,
        duration: 15000,
        showClose: true
      });
    } else {
      ElMessage.error(errorMessage);
    }

    emit('import-error', error);
  }
};
</script>

<style scoped>
.excel-importer {
  margin-bottom: 20px;
}

.preview-container {
  margin-top: 20px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 15px;
  background-color: #f9f9f9;
}

.preview-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 15px;
}

.error-cell {
  position: relative;
}

.error-cell span {
  text-decoration: underline;
  text-decoration-style: wavy;
  text-decoration-color: #F56C6C;
  cursor: pointer;
}
</style>