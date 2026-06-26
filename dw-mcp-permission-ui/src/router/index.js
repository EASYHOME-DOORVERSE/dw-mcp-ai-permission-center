import { createRouter, createWebHistory } from 'vue-router'

/**
 * 判断当前用户是否为 ADMIN
 */
function isAdmin() {
  try {
    const roles = JSON.parse(localStorage.getItem('admin_roles') || '[]')
    return roles.includes('ADMIN')
  } catch {
    return false
  }
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('../views/login/ForgotPassword.vue'),
    meta: { title: '忘记密码' }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'user', name: 'User', component: () => import('../views/user/UserList.vue'), meta: { title: '用户管理', requiresAdmin: true } },
      { path: 'role', name: 'Role', component: () => import('../views/role/RoleList.vue'), meta: { title: '角色管理', requiresAdmin: true } },
      { path: 'apikey', name: 'ApiKey', component: () => import('../views/apikey/ApiKeyList.vue'), meta: { title: 'API Key 管理' } },
      { path: 'tool', name: 'Tool', component: () => import('../views/tool/ToolList.vue'), meta: { title: 'MCP 工具' } },
      { path: 'tool-category', name: 'ToolCategory', component: () => import('../views/category/ToolCategoryList.vue'), meta: { title: '工具分类', requiresAdmin: true } },
      { path: 'datasource', name: 'Datasource', component: () => import('../views/datasource/DatasourceList.vue'), meta: { title: '数据源管理', requiresAdmin: true } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - MCP AuthProxy` : 'MCP AuthProxy'
  if (to.path !== '/login' && to.path !== '/forgot-password' && !localStorage.getItem('admin_token')) {
    next('/login')
  } else if (to.meta.requiresAdmin && !isAdmin()) {
    // 非 admin 用户尝试访问 admin 专属页面，重定向到 API Key 页面
    next('/apikey')
  } else {
    next()
  }
})

export default router
