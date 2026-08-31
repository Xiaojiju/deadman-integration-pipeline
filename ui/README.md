# MTFM Gateway Console

基于 Vite + React + shadcn/ui 的网关配置控制台。

## 开发

```bash
# 终端 1：启动网关（默认 :8080）
./mvnw -pl app spring-boot:run

# 终端 2：启动 UI（:5173，代理 /catalog 到网关）
cd ui && npm install && npm run dev
```

打开 http://localhost:5173

## 功能

- 产品：新建、挂载能力预置功能
- 通道：按 connection schema 新建
- 设备：登记（含端点）、Load/Unload、手动下发指令、删除
- 能力：查看已登记能力与 functionTemplates

## 打包到网关静态资源

```bash
cd ui && npm run build
rm -rf ../app/src/main/resources/static/*
cp -R dist/* ../app/src/main/resources/static/
```

然后访问 http://localhost:8080/
