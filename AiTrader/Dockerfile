# 使用 Nginx 作为基础镜像
FROM nginx:alpine

# 复制构建产物到 Nginx 默认目录
COPY dist /usr/share/nginx/html

# 复制自定义 Nginx 配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露 80 端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
