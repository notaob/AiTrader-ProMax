import { request } from './request';
import type { User } from '../types';

interface LoginResponse extends User {
  token: string;
}

export const authService = {
  // 发送验证码到邮箱
  sendCode: (email: string) => {
    return request<string>('/user/code', {
      method: 'POST',
      params: { email },
    });
  },

  // 验证码登录（邮箱+验证码）
  loginByCode: (email: string, code: string) => {
    return request<LoginResponse>('/user/login/code', {
      method: 'POST',
      body: JSON.stringify({
        email,
        code
      }),
    });
  },

  // 密码登录（邮箱+密码）
  loginByPassword: (email: string, password: string) => {
    return request<LoginResponse>('/user/login/password', {
      method: 'POST',
      body: JSON.stringify({
        email,
        password
      }),
    });
  },

  // 注册（邮箱+验证码+密码+昵称）
  register: (email: string, code: string, password: string, nickName: string) => {
    return request<LoginResponse>('/user/register', {
      method: 'POST',
      body: JSON.stringify({
        email,
        password,
        code,
        nickName
      }),
    });
  },

  // 重置密码
  resetPassword: (email: string, code: string, password: string) => {
    return request<void>('/user/resetPassword', {
      method: 'POST',
      body: JSON.stringify({
        email,
        code,
        password
      }),
    });
  },

  // 获取当前用户信息
  getCurrentUser: () => {
    return request<User>('/user/me');
  },

  // 退出登录
  logout: () => {
    return request<string>('/user/logout', {
      method: 'POST'
    });
  },

  // 更新用户信息
  updateUserInfo: (nickName: string) => {
    return request<User>('/user/update', {
      method: 'POST',
      body: JSON.stringify({ nickName }),
    });
  },

  // 上传头像
  uploadAvatar: (formData: FormData) => {
    return request<string>('/user/upload', {
      method: 'POST',
      body: formData,
    });
  },

  // 修改密码
  changePassword: (oldPassword: string, newPassword: string) => {
    return request<void>('/user/password/change', {
      method: 'POST',
      body: JSON.stringify({ oldPassword, newPassword }),
    });
  },

};
