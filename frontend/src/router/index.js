import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "../stores/user";

const routes = [
  {
    path: "/",
    redirect: "/home"
  },
  {
    path: "/home",
    name: "Home",
    component: () => import("../views/Home.vue"),
    meta: { requiresAuth: false },
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("../views/Login.vue"),
    meta: { requiresAuth: false },
  },
  {
    path: "/register",
    name: "Register",
    component: () => import("../views/Register.vue"),
    meta: { requiresAuth: false },
  },
  {
    path: "/forgot-password",
    name: "ForgotPassword",
    component: () => import("../views/ForgotPassword.vue"),
    meta: { requiresAuth: false },
  },
  {
    path: "/demo",
    name: "Demo",
    component: () => import("../views/Demo.vue"),
    meta: { requiresAuth: false },
  },
  {
    path: "/",
    name: "Layout",
    component: () => import("../layout/index.vue"),
    meta: { requiresAuth: true },
    children: [
      // 学生路由
      {
        path: "student/dashboard",
        name: "StudentDashboard",
        component: () => import("../views/student/StudentDashboard.vue"),
        meta: { roles: ["student"] },
      },
      {
        path: "student/lectures",
        name: "StudentLectures",
        component: () => import("../views/student/StudentLectures.vue"),
        meta: { roles: ["student"] },
      },
      {
        path: "student/lectures/:id",
        name: "StudentLectureDetail",
        component: () => import("../views/student/StudentLectureDetail.vue"),
        meta: { roles: ["student"] },
      },
      {
        path: "student/registrations",
        name: "StudentRegistrations",
        component: () => import("../views/student/StudentRegistrations.vue"),
        meta: { roles: ["student"] },
      },
      {
        path: "student/profile",
        name: "StudentProfile",
        component: () => import("../views/student/StudentProfile.vue"),
        meta: { roles: ["student"] },
      },
      {
        path: "student/ai",
        name: "StudentAi",
        component: () => import("../views/student/StudentAi.vue"),
        meta: { roles: ["student"] },
      },
      // 教师路由
      {
        path: "teacher/dashboard",
        name: "TeacherDashboard",
        component: () => import("../views/teacher/TeacherDashboard.vue"),
        meta: { roles: ["teacher"] },
      },
      {
        path: "teacher/lectures",
        name: "TeacherLectures",
        component: () => import("../views/teacher/TeacherLectures.vue"),
        meta: { roles: ["teacher"] },
      },
      {
        path: "teacher/apply-list",
        name: "TeacherApplyList",
        component: () => import("../views/teacher/TeacherApplyList.vue"),
        meta: { roles: ["teacher"] },
      },
      {
        path: "teacher/record",
        name: "TeacherRecord",
        component: () => import("../views/teacher/TeacherRecord.vue"),
        meta: { roles: ["teacher"] },
      },
      {
        path: "teacher/profile",
        name: "TeacherProfile",
        component: () => import("../views/teacher/TeacherProfile.vue"),
        meta: { roles: ["teacher"] },
      },
      {
        path: "teacher/lectures/:id/registrations",
        name: "TeacherRegistrations",
        redirect: (to) => ({
          path: "/teacher/apply-list",
          query: { lectureId: to.params.id },
        }),
        meta: { roles: ["teacher"] },
      },
      {
        path: "teacher/evaluations",
        name: "TeacherEvaluations",
        component: () => import("../views/teacher/TeacherEvaluations.vue"),
        meta: { roles: ["teacher"] },
      },

    ],
  },
  {
    path: "/admin",
    name: "AdminLayout",
    component: () => import("../layout/AdminLayout.vue"),
    meta: { requiresAuth: true },
    children: [
      {
        path: "dashboard",
        name: "AdminDashboard",
        component: () => import("../views/admin/AdminDashboard.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "audit",
        name: "AdminAudit",
        component: () => import("../views/admin/AdminAudit.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "publish",
        name: "AdminPublish",
        component: () => import("../views/admin/AdminPublish.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "student-user",
        name: "StudentUser",
        component: () => import("../views/admin/StudentUser.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "teacher-user",
        name: "TeacherUser",
        component: () => import("../views/admin/TeacherUser.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "location",
        name: "LocationManagement",
        component: () => import("../views/admin/LocationManagement.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "department",
        name: "DepartmentManagement",
        component: () => import("../views/admin/DepartmentManagement.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "category",
        name: "AdminCategory",
        component: () => import("../views/admin/AdminCategory.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "report",
        name: "AdminReport",
        component: () => import("../views/admin/AdminReport.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "notice",
        name: "AdminNotice",
        component: () => import("../views/admin/AdminNotice.vue"),
        meta: { roles: ["admin"] },
      },
      {
        path: "permission",
        name: "RoleManagement",
        component: () => import("../views/role/RoleManagement.vue"),
        meta: { roles: ["admin"] },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem("token");

  // 不需要认证的页面直接通过
  if (!to.meta.requiresAuth) {
    next();
    return;
  }

  // 检查是否有token
  if (!token) {
    next("/login");
    return;
  }

  // 获取用户信息
  const userStore = useUserStore();
  if (!userStore.userInfo) {
    try {
      await userStore.getUserInfoAction();
    } catch (error) {
      localStorage.removeItem("token");
      next("/login");
      return;
    }
  }

  // 检查角色权限
  if (to.meta.roles) {
    const userRoles = userStore.userInfo?.roles || [];
    const hasRole = to.meta.roles.some((role) => userRoles.includes(role));

    if (!hasRole) {
      // 没有权限，重定向到对应的仪表板
      if (userRoles.includes("admin")) {
        next("/admin/dashboard");
      } else if (userRoles.includes("teacher")) {
        next("/teacher/dashboard");
      } else if (userRoles.includes("student")) {
        next("/student/dashboard");
      } else {
        next("/");
      }
      return;
    }
  }

  next();
});

export default router;
