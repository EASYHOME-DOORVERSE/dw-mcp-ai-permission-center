<template>
  <div class="dashboard-container">
    <!-- 数据补跑按钮（仅管理员） -->
    <div v-if="isAdmin" class="aggregate-bar">
      <el-button type="warning" size="small" @click="showAggregateDialog = true">
        <el-icon><Refresh /></el-icon>&nbsp;数据补跑
      </el-button>
    </div>

    <!-- 概览卡片 -->
    <el-row :gutter="16" class="overview-row">
      <el-col :span="isAdmin ? 6 : 8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background:#409eff"><el-icon :size="24"><SetUp /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ overview.toolEnabled }}<span v-if="isAdmin" class="stat-sub"> / {{ overview.toolTotal }}</span></div>
            <div class="stat-label">{{ isAdmin ? '启用工具 / 总数' : '启用工具' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="isAdmin ? 6 : 8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><Key /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ overview.apiKeyEnabled ?? 0 }} <span class="stat-sub">/ {{ overview.apiKeyTotal }}</span> <span v-if="overview.apiKeyExpiring > 0" class="stat-warn">({{ overview.apiKeyExpiring }}即将过期)</span></div>
            <div class="stat-label">启用 Key / 总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col v-if="isAdmin" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><User /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ overview.userActiveToday }} <span class="stat-sub">/ {{ overview.userTotal }}</span></div>
            <div class="stat-label">今日活跃 / 用户总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="isAdmin ? 6 : 8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><PhoneFilled /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ overview.callToday }}</div>
            <div class="stat-label">今日调用 <span :class="deltaClass">{{ deltaText }}</span></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：趋势 + 成功率 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>调用趋势</span>
              <el-radio-group v-model="trendDays" size="small" @change="loadStats">
                <el-radio-button :value="7">近7天</el-radio-button>
                <el-radio-button :value="30">近30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-box" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="pie-card">
          <template #header><span>调用成功率</span></template>
          <div ref="pieChartRef" class="chart-box" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：热门工具 + 耗时分布 / 角色分布 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>近30天热门工具 Top 10</span>
              <span class="hint-text">点击工具行可筛选下方日志（近30天）</span>
            </div>
          </template>
          <el-table :data="topTools" size="small" stripe highlight-current-row
            @row-click="onTopToolClick" style="cursor:pointer">
            <el-table-column type="index" width="50" label="#" />
            <el-table-column prop="displayName" label="工具名称" min-width="140">
              <template #default="{ row }">{{ row.displayName || row.toolName }}</template>
            </el-table-column>
            <el-table-column prop="callCount" label="调用次数" width="100" sortable />
            <el-table-column prop="successRate" label="成功率" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.successRate >= 95 ? 'success' : row.successRate >= 80 ? 'warning' : 'danger'" size="small">
                  {{ row.successRate }}%
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="avgDurationMs" label="平均耗时" width="110">
              <template #default="{ row }">{{ formatMs(row.avgDurationMs) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <!-- 管理员：角色权限分布 -->
        <el-card v-if="isAdmin" shadow="never">
          <template #header><span>角色权限分布</span></template>
          <div ref="roleChartRef" class="chart-box" />
        </el-card>
        <!-- 普通用户：我的权限摘要 -->
        <el-card v-else shadow="never">
          <template #header><span>我的工具权限</span></template>
          <div class="perm-summary">
            <div class="perm-total">{{ myPermission.toolCount }} 个工具</div>
            <el-descriptions :column="1" border size="small" v-if="myPermission.categories?.length">
              <el-descriptions-item v-for="cat in myPermission.categories" :key="cat.categoryName" :label="cat.categoryName">
                {{ cat.count }} 个
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 耗时分布 -->
    <el-card shadow="never" style="margin-top:16px">
      <template #header><span>耗时分布（近7天）</span></template>
      <div ref="durationChartRef" style="height:200px" />
    </el-card>

    <!-- 日志查询区 -->
    <el-card shadow="never" style="margin-top:16px">
      <template #header>
        <div class="card-header">
          <span>调用日志</span>
          <el-button text type="primary" @click="clearLogFilter">清除筛选</el-button>
        </div>
      </template>
      <el-form :model="logQuery" inline size="small" class="log-filter">
        <el-form-item label="工具">
          <el-select v-model="logQuery.toolId" placeholder="全部" clearable filterable style="width:180px">
            <el-option v-for="t in topTools" :key="t.toolId" :label="t.displayName || t.toolName" :value="t.toolId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="logQuery.success" placeholder="全部" clearable style="width:100px">
            <el-option label="成功" :value="true" />
            <el-option label="失败" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin" label="用户">
          <el-input v-model="logQuery.username" placeholder="用户名" clearable style="width:120px" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="logDateRange" type="daterange" range-separator="~" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width:240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadLogs">查询</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="logData" v-loading="logLoading" stripe border size="small" max-height="400">
        <el-table-column type="index" label="序号" width="50" />
        <el-table-column prop="callAt" label="调用时间" width="170" />
        <el-table-column prop="displayName" label="工具" min-width="140">
          <template #default="{ row }">{{ row.displayName || row.toolName }}</template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column v-if="isAdmin" prop="username" label="用户" width="100" />
        <el-table-column prop="durationMs" label="耗时" width="90" align="right">
          <template #default="{ row }">{{ formatMs(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column prop="success" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="denyReason" label="拒绝原因" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-pagination class="table-pagination" background layout="total, sizes, prev, pager, next"
        :total="logTotal" v-model:current-page="logQuery.current" v-model:page-size="logQuery.size"
        :page-sizes="[10,20,50]" @change="loadLogs" />
    </el-card>
    <!-- 数据补跑弹窗 -->
    <el-dialog v-model="showAggregateDialog" title="数据补跑" width="440" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="日期范围">
          <el-date-picker v-model="aggregateDateRange" type="daterange" range-separator="~" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item v-if="aggregateResult" label="执行结果">
          <div class="aggregate-result">
            <div>总天数: <strong>{{ aggregateResult.totalDays }}</strong></div>
            <div>成功: <strong style="color:#67c23a">{{ aggregateResult.successDays }}</strong></div>
            <div>失败: <strong :style="{ color: aggregateResult.failedDays > 0 ? '#f56c6c' : '#67c23a' }">{{ aggregateResult.failedDays }}</strong></div>
            <div>耗时: <strong>{{ (aggregateResult.costMs / 1000).toFixed(1) }}s</strong></div>
            <div v-if="aggregateResult.failedDays > 0" style="margin-top:8px;font-size:12px;color:#909399">失败详情请查看服务端日志</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAggregateDialog = false">关闭</el-button>
        <el-button type="primary" :loading="aggregateLoading" :disabled="!aggregateDateRange || aggregateDateRange.length !== 2" @click="runAggregate">
          {{ aggregateLoading ? '执行中...' : '确认补跑' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { getDashboardStats, queryCallLogs, manualAggregateStats } from '../../api'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { init as echartsInit } from 'echarts/core'

use([CanvasRenderer, LineChart, BarChart, PieChart, TooltipComponent, LegendComponent, GridComponent])

// ===== 角色判断 =====
const isAdmin = (() => {
  try { return JSON.parse(localStorage.getItem('admin_roles') || '[]').includes('ADMIN') } catch { return false }
})()

// ===== 数据状态 =====
const stats = ref({})
const overview = computed(() => stats.value.overview || {})
const topTools = computed(() => stats.value.topTools || [])
const myPermission = computed(() => stats.value.myPermissionSummary || { toolCount: 0, categories: [] })

const trendDays = ref(7)
const trendChartRef = ref()
const pieChartRef = ref()
const roleChartRef = ref()
const durationChartRef = ref()
let trendChart, pieChart, roleChart, durationChart

// ===== 数据补跑 =====
const showAggregateDialog = ref(false)
const aggregateDateRange = ref(null)
const aggregateLoading = ref(false)
const aggregateResult = ref(null)

async function runAggregate() {
  if (!aggregateDateRange.value || aggregateDateRange.value.length !== 2) return
  aggregateLoading.value = true
  aggregateResult.value = null
  try {
    const res = await manualAggregateStats(aggregateDateRange.value[0], aggregateDateRange.value[1])
    aggregateResult.value = res.data
    if (res.data?.failedDays === 0) {
      ElMessage.success(`汇总完成，${res.data.totalDays}天全部成功`)
    } else if (res.data?.failedDays > 0) {
      ElMessage.warning(`汇总完成，${res.data.failedDays}天失败`)
    }
    // 刷新看板
    loadStats()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || '汇总执行失败')
  } finally {
    aggregateLoading.value = false
  }
}

// ===== 概览增减百分比 =====
const deltaClass = computed(() => {
  const d = overview.value.callDeltaPct
  if (d > 0) return 'delta-up'
  if (d < 0) return 'delta-down'
  return ''
})
const deltaText = computed(() => {
  const d = overview.value.callDeltaPct
  if (d > 0) return `↑${d}%`
  if (d < 0) return `↓${Math.abs(d)}%`
  return '-'
})

// ===== 加载看板数据 =====
async function loadStats() {
  const res = await getDashboardStats({ days: trendDays.value })
  stats.value = res.data
  await nextTick()
  renderTrendChart()
  renderPieChart()
  renderDurationChart()
  if (isAdmin) renderRoleChart()
}

// ===== 趋势图 =====
function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echartsInit(trendChartRef.value)
  const data = stats.value.callTrend || []
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['总调用', '失败', '拒绝'], top: 0 },
    grid: { left: 50, right: 20, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: data.map(d => d.date?.slice(5)), boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '总调用', type: 'line', data: data.map(d => d.total), smooth: true, areaStyle: { opacity: 0.15 }, itemStyle: { color: '#409eff' } },
      { name: '失败', type: 'line', data: data.map(d => d.failed), smooth: true, itemStyle: { color: '#f56c6c' } },
      { name: '拒绝', type: 'line', data: data.map(d => d.denied), smooth: true, itemStyle: { color: '#e6a23c' } }
    ]
  })
}

// ===== 成功率饼图 =====
function renderPieChart() {
  if (!pieChartRef.value) return
  if (!pieChart) pieChart = echartsInit(pieChartRef.value)
  const rate = stats.value.successRate || {}
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '45%'],
      label: { show: true, formatter: '{d}%' },
      data: [
        { name: '成功', value: rate.success || 0, itemStyle: { color: '#67c23a' } },
        { name: '失败', value: rate.failed || 0, itemStyle: { color: '#f56c6c' } },
        { name: '权限拒绝', value: rate.denied || 0, itemStyle: { color: '#e6a23c' } }
      ]
    }]
  })
}

// ===== 耗时分布 =====
function renderDurationChart() {
  if (!durationChartRef.value) return
  if (!durationChart) durationChart = echartsInit(durationChartRef.value)
  const buckets = stats.value.durationDistribution || []
  durationChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: buckets.map(b => b.range) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar', data: buckets.map(b => b.count), barWidth: '50%',
      itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] }
    }]
  })
}

// ===== 角色分布 =====
function renderRoleChart() {
  if (!roleChartRef.value) return
  if (!roleChart) roleChart = echartsInit(roleChartRef.value)
  const roles = stats.value.roleDistribution || []
  roleChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 80, right: 20, top: 20, bottom: 20 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: roles.map(r => r.roleName) },
    series: [{
      type: 'bar', data: roles.map(r => r.toolCount), barWidth: 20,
      itemStyle: { color: '#409eff', borderRadius: [0, 4, 4, 0] }
    }]
  })
}

// ===== 日志查询 =====
const logLoading = ref(false)
const logData = ref([])
const logTotal = ref(0)

// 默认查近 90 天，防止全量查日志表过慢
function todayStr() { return new Date().toISOString().slice(0, 10) }
function daysAgoStr(n) { const d = new Date(); d.setDate(d.getDate() - n); return d.toISOString().slice(0, 10) }
const logDateRange = ref([daysAgoStr(90), todayStr()])
const logQuery = reactive({ current: 1, size: 20, toolId: null, success: null, username: '' })

async function loadLogs() {
  logLoading.value = true
  try {
    const params = { current: logQuery.current, size: logQuery.size }
    if (logQuery.toolId) params.toolId = logQuery.toolId
    if (logQuery.success !== null && logQuery.success !== '') params.success = logQuery.success
    if (logQuery.username && isAdmin) params.username = logQuery.username
    if (logDateRange.value && logDateRange.value.length === 2) {
      params.startTime = logDateRange.value[0]
      params.endTime = logDateRange.value[1]
    }
    const res = await queryCallLogs(params)
    logData.value = res.data.records || []
    logTotal.value = res.data.total || 0
  } finally {
    logLoading.value = false
  }
}

// ===== 图表→日志联动 =====
function onTopToolClick(row) {
  logQuery.toolId = row.toolId
  logDateRange.value = [daysAgoStr(30), todayStr()]
  logQuery.current = 1
  loadLogs()
}

function clearLogFilter() {
  logQuery.toolId = null
  logQuery.success = null
  logQuery.username = ''
  logDateRange.value = [daysAgoStr(90), todayStr()]
  logQuery.current = 1
  loadLogs()
}

// ===== 工具函数 =====
function formatMs(ms) {
  if (ms == null) return '-'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(2) + 's'
}

// ===== 窗口resize =====
function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
  durationChart?.resize()
  roleChart?.resize()
}

// ===== 生命周期 =====
onMounted(async () => {
  await loadStats()
  loadLogs()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
  durationChart?.dispose()
  roleChart?.dispose()
})
</script>

<style scoped>
.dashboard-container { height: 100%; }

/* 数据补跑 */
.aggregate-bar { display: flex; justify-content: flex-end; margin-bottom: 8px; }
.aggregate-result { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.aggregate-errors { margin-top: 8px; max-height: 120px; overflow-y: auto; }
.error-item { font-size: 12px; color: #f56c6c; padding: 2px 0; }

/* 概览卡片 */
.overview-row .stat-card :deep(.el-card__body) { display: flex; align-items: center; gap: 16px; padding: 20px; }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; flex-shrink: 0; }
.stat-value { font-size: 24px; font-weight: 700; color: #303133; line-height: 1.2; }
.stat-sub { font-size: 14px; font-weight: 400; color: #909399; }
.stat-warn { font-size: 12px; color: #e6a23c; font-weight: 400; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.delta-up { color: #f56c6c; font-size: 12px; }
.delta-down { color: #67c23a; font-size: 12px; }

/* 图表 */
.chart-box { height: 320px; }
.pie-card .chart-box { height: 320px; }

/* 卡片头部 */
.card-header { display: flex; align-items: center; justify-content: space-between; }
.hint-text { font-size: 12px; color: #909399; font-weight: normal; }

/* 日志区域 */
.log-filter { margin-bottom: 12px; }
.table-pagination { margin-top: 12px; justify-content: flex-end; }

/* 权限摘要 */
.perm-summary { padding: 8px 0; }
.perm-total { font-size: 20px; font-weight: 600; color: #409eff; margin-bottom: 12px; }
</style>
