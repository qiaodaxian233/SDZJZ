# versions/26.1 —— Modern 第二靶（单源双靶）

本目录**没有自己的 java 源**：`build.gradle` 的 srcDir 指向 `../26.2/src/main/java`
（Modern 代际一份适配器，26.1/26.2 两个真编译+GameTest 靶互为分叉探测器）。改 Modern
代码去 26.2 那边改，两边 CI 都会判。资源（fabric.mod.json）各自带，锚各自 minecraft 版本。

本机跑法与 JDK 要求同 `versions/26.2/README.md`（Gradle JVM=25）。
坐标出处见本目录 `gradle.properties` 头注释（m432 web 实查，非记忆）。
