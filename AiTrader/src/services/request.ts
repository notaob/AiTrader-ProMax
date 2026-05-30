// 基础请求封装
const BASE_URL = '/api'; // 对应 vite.config.ts 中的 proxy

interface RequestOptions extends RequestInit {
  params?: Record<string, string>;
}

interface ApiResponse<T> {
  code: number;
  msg: string | null;
  data: T;
}

export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const { params, headers, ...rest } = options;
  
  // 处理查询参数
  let fullUrl = `${BASE_URL}${url}`;
  if (params) {
    const searchParams = new URLSearchParams(params);
    fullUrl += `?${searchParams.toString()}`;
  }

  // 默认 headers
  const defaultHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  // 如果 body 是 FormData，删除 Content-Type，让浏览器自动设置 boundary
  if (rest.body instanceof FormData) {
    delete defaultHeaders['Content-Type'];
  }

  const token = localStorage.getItem('token');
  if (token) {
    defaultHeaders['Authorization'] = `Bearer ${token}`;
  }

  const config = {
    headers: {
      ...defaultHeaders,
      ...headers,
    },
    ...rest,
  };

  try {
    const response = await fetch(fullUrl, config);
    
    if (!response.ok) {
      const errorText = await response.text().catch(() => '');
      let errorMessage = `请求失败: ${response.status}`;
      try {
        const errorData = JSON.parse(errorText);
        errorMessage = errorData.message || errorData.msg || errorMessage;
      } catch {
        if (errorText) errorMessage += ` - ${errorText}`;
      }
      throw new Error(errorMessage);
    }

    const resData: ApiResponse<T> = await response.json();
    
    // 根据后端约定的 code 判断业务逻辑是否成功
    // 假设 code === 1 表示成功 (根据你提供的 JSON)
    if (resData.code !== 1) {
       throw new Error(resData.msg || '业务处理失败');
    }

    return resData.data;
  } catch (error) {
    console.error('API Request Error:', error);
    throw error;
  }
}
