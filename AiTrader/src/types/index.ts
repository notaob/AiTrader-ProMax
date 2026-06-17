export interface ChartData {
  time: string;
  price: number;
  timestamp: number;
}

export interface User {
  id: string; // 后端返回的是 id (number)，前端用 string 接收也可以，或者改成 number
  nickName: string; // 新增 nickName
  phone: string; // 新增 phone
  icon?: string; // 用户头像
  vipLevel?: number;
  aiChance?:number;//ai咨询次数
  point?:number;//积分
}

export interface MenuItem {
  icon: React.ElementType;
  label: string;
  path: string;
}

export interface Post {
  id: number;
  userName: string;
  userAvatar?: string;
  time: string;
  content: string;
  likes: number;
  comments: number;
  isLiked: boolean;
}

export interface Comment {
  id: number;
  userName: string;
  userAvatar?: string;
  content: string;
  time: string;
}
