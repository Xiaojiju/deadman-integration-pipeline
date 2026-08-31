# 代码格式化 — 阿里巴巴 P3C 规范

本目录存放 [alibaba/p3c](https://github.com/alibaba/p3c/tree/master/p3c-formatter) 官方格式化模板，供 IDE 与 Maven 统一使用。

| 文件 | 说明 |
|------|------|
| `eclipse-codestyle.xml` | 代码格式化（Profile：`P3C-CodeStyle`） |
| `eclipse-codetemplate.xml` | 新建类/方法注释模板 |

## Cursor / VS Code

仓库已提交 `.vscode/settings.json`，需安装 **Extension Pack for Java**（`vscjava.vscode-java-pack`）。

保存 Java 文件时将自动：

1. 按 `P3C-CodeStyle` 格式化（通过 `java.settings.url` → `config/formatter/java.prefs`）
2. 整理 import（`[java].editor.codeActionsOnSave` → `source.organizeImports`）

> 新版 Language Support for Java 已移除 `java.format.settings.*`、`java.templates.file` 等旧配置项，统一改用 `java.settings.url` 指向 Eclipse prefs 文件。

若未生效，检查右下角 Java Language Server 已启动，并执行 **Java: Clean Java Language Server Workspace** 后重载窗口。

## IntelliJ IDEA

1. 安装插件 [Alibaba Java Coding Guidelines](https://plugins.jetbrains.com/plugin/10046-alibaba-java-coding-guidelines)（可选，用于规约检查）
2. **Settings → Editor → Code Style → Java → ⚙ → Import Scheme → Eclipse XML Profile**
3. 选择本目录 `eclipse-codestyle.xml`，Profile 选 `P3C-CodeStyle`
4. **Settings → Editor → File and Code Templates**，可导入 `eclipse-codetemplate.xml`（部分字段需手工对照）

## 命令行（可选）

根目录执行，批量格式化所有 Java 源文件：

```bash
./mvnw spotless:apply
```

检查格式（CI 可用）：

```bash
./mvnw spotless:check
```
