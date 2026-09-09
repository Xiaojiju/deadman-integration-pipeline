# MTFM Gateway Console

基于 Vite + React + shadcn/ui 的网关配置控制台，供**单独部署**使用。用其它 UI 库对接同一套 `/catalog` 契约时，见 [UI 接入指南](../docs/ui-integration-guide.md)。

## 开发

```bash
# 终端 1：启动网关（默认 :8080）
./mvnw -pl app spring-boot:run

# 终端 2：启动 UI（:5173，代理 /catalog 到网关）
cd ui && npm install && npm run dev
```

打开 http://localhost:5173

## 功能

- 产品类型：CRUD（与南向 capabilityType 分离；创建产品必选）
- 产品：筛选、新建（必选类型，可选 seed 能力）、挂载功能
- 通道：按 connection schema 新建；`probeSupported` 时扫描子设备
- 设备：筛选、登记、编辑、在线/已加载分开展示、单台与批量 Load、Unload、指令、删除
- 能力：查看已登记能力与 functionTemplates

契约细节见 [UI 接入指南](../docs/ui-integration-guide.md)。

## 打包到网关静态资源

```bash
cd ui && npm run build
rm -rf ../app/src/main/resources/static/*
cp -R dist/* ../app/src/main/resources/static/
```

然后访问 http://localhost:8080/
