# versions/1.20.1 —— 旧世代（Retro）bootstrap 构建

独立 Gradle 构建（Gradle 8.10 wrapper，与根仓同线），工具链=经典 fabric-loom 1.7.4 + mojmap +
release 17。现阶段=骨架+Common 真编译+在岗日志，无玩法。P-B 第二段起移植：存储网络域 →
Xfer120/TagItemData/Net120 对位实现 → **Create 传送带↔数据线互通（本格验收线，作者承诺项）**。
坐标出处见 gradle.properties 头注释（web 实查非记忆）。本机跑法：`gradlew build`（Gradle JVM≥17）。
