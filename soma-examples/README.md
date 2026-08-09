# SOMA Reference Applications

这里不是片段式 demo，而是三个可独立编译、运行和剖析的 reference application：

- `scheduling`：Job、Machine、ProcessingOption 关系与补偿式发布；
- `simulation`：显式事件顺序、detached decision 与分步状态迁移；
- `real-time-dispatch`：Index 缩窄、typed Join、显式并行和外部派工边界。

三个项目只使用 SOMA 的 public/generated Java 8 API。它们不依赖 runtime internal package，
不拥有新的产品语义，也不进入 SOMA 的两个 production artifact。每个项目包含：

```text
schema/        编译期 Table 声明
application/   场景编排与业务补偿
Main           确定性 public-API journey
src/test/      非发布的 million-row profile harness
```

从仓库根目录先安装当前 checkout 的两项 production artifact，再以真正的下游 consumer 构建：

```sh
mvn clean install -Dmaven.install.skip=false -DskipTests
mvn -f soma-examples/pom.xml clean package
```

正式重放入口、JVM/heap 与 evidence 边界由 `scripts/qualify-i8.sh` 和
`scripts/benchmark-i8.sh` 统一管理。
