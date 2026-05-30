import { request } from './request';
import type { User } from '../types';

interface LoginResponse extends User {
  token: string;
}

export const authService = {
  // 发送验证码
  sendCode: (phone: string) => {
    return request<string>('/user/code', {
      method: 'POST',
      params: { phone },
    });
  },

  // 验证码登录
  loginByCode: (phone: string, code: string) => {
    return request<LoginResponse>('/user/login/sms', {
      method: 'POST',
      body: JSON.stringify({ 
        phone, // 明确只传 phone
        code 
      }),
    });
  },

  // 密码登录
  loginByPassword: (phone: string, password: string) => {
    return request<LoginResponse>('/user/login/password', {
      method: 'POST',
      body: JSON.stringify({ 
        phone, // 统一改为使用 phone
        password 
      }),
    });
  },
  
  // 注册用户
  // 注意参数顺序调整为与 Register.tsx 调用一致：(phone, code, password, nickName)
  register: (phone: string, code: string, password: string, nickName: string) => {
    return request<LoginResponse>('/user/register', {
      method: 'POST',
      body: JSON.stringify({ 
        phone, 
        password,
        code,
        nickName
      }),
    });
  },

  // 重置密码
  resetPassword: (phone: string, code: string, password: string) => {
    return request<void>('/user/resetPassword', {
      method: 'POST',
      body: JSON.stringify({
        phone,
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
