# versions/26.2 —— 新世代（Modern）bootstrap 构建

这是独立的 Gradle 构建（自带 Gradle 9.5.1 wrapper），与根仓 Legacy 构建互不相干：
根仓照旧 `gradlew build/runGametest/runClient`（JDK 21），本目录走新世代工具链（JDK 25）。

## 本机怎么跑（验收口径）

**IDEA**：文件 → 打开 → 选 `versions/26.2` 目录（新窗口）→ 设置 → 构建工具 → Gradle →
Gradle JVM 选 **25** → sync。

**命令行**（PowerShell，在本目录下）：命令行不走 IDEA 的 Gradle JVM 设置，默认 JDK 是 21
会报「不支持发行版本 25」——先把 `gradle.properties` 里那行 `org.gradle.java.home` 的注释
解开并按本机路径改（模板验证时用过同一招；这是本机配置，别提交）。然后：

```
.\gradlew build        # 出 jar 到 build\libs\sdzjz-0.1.NNN+mc26.2.jar
.\gradlew runClient    # 进 26.2 开发客户端，日志应见一行：
                       # [sdzjz] 26.2 新世代 bootstrap 在岗：Common 层已挂载（...）
```

## 这个构建现在是什么、不是什么

- **是**：工具链骨架 + Common 层（`common/src/main/java`，与根仓共享同一份源码）在
  26.2 下的真编译验证。装进游戏只打一行日志，没有任何玩法。
- **不是**：可玩的 26.2 版模组。机器/存储/画布等全部功能在 Legacy 侧，迁移按
  Phase 2 顺位逐刀走（ModernRecipeAccess 等适配器分域实现，Common 一行不动）。

## 坐标出处（改前先看）

`gradle.properties` 四个坐标 = FabricMC/fabric-example-mod 分支 26.2 原文；
loom 钉 1.17.19（作者本地模板实测解析版，防 SNAPSHOT 漂移，m325 同款规矩）。
升坐标请对 https://fabricmc.net/develop 并在 DEVLOG 记一笔。
