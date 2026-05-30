# 今日学习总结与笔记

## 1. React 组件化开发
- **组件提取**：学会了将重复的 UI 逻辑（如头像）提取为独立的 `src/components/Avatar.tsx` 组件。
- **Props 设计**：掌握了如何通过解构赋值和默认参数来定义组件接口，提升代码可读性。
- **条件渲染**：在 `src/components/BottomNav.tsx` 中根据当前路径决定是否显示导航栏。

## 2. 状态管理 (Context API)
- **全局状态**：创建了 `src/context/MomentsContext.tsx`，解决了页面跳转后数据丢失的问题（持久化思想）。
- **Provider 模式**：理解了如何在 `src/App.tsx` 中包裹 Provider，让所有子组件都能共享数据。
- **工程化细节**：处理了 Fast Refresh 引起的警告，学会了使用 `// eslint-disable-next-line`。

## 3. 路由与安全 (Routing & Security)
- **动态导航**：使用 `useNavigate` 进行页面跳转，使用 `useLocation` 获取当前路径。
- **路由守卫 (Route Guard)**：实现了 `src/components/ProtectedRoute.tsx`，拦截未登录用户的访问。
- **就地登录 UI**：优化了用户体验，当访问受限页面时直接在当前页显示登录提示，而不是生硬地跳转。

## 4. CSS 与 UI/UX 优化
- **CSS Modules**：使用 `*.module.css` 实现样式隔离，避免全局命名冲突。
- **盒模型**：深入理解了 `padding`（内边距）和 `margin`（外边距）的区别及缩写语法。
- **交互细节**：移除了默认的紫色 `focus` 边框，优化了按钮的悬浮 (`:hover`) 效果。

## 5. 调试与错误修复
- **Linter 修复**：学会了识别并清理未使用的变量、修复缺失的 import。
- **语法排查**：解决了一些常见的 JSX 闭合标签错误和变量定义域问题。

---

**下一步建议：**
1. **安全中心页面 (Security)**：继续完善 `Me` 页面的子菜单功能。
2. **资产管理 Context**：将用户的余额 (Balance) 也存入 Context，实现跨页面的实时更新。
