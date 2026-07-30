---
name: use-soma-java
description: Use when building, reviewing, or troubleshooting a Java 8 application that consumes SOMA Java, including Maven setup, schema modeling, generated API discovery, Access or DataFlow routing, lifecycle, resource, performance, and real consumer verification. Do not use for unrelated Java or database work, SOMA repository governance, or implementation of SOMA internals.
---

# Use SOMA Java

## 目标与权威

帮助 AI coding tool 从普通 Java 8 Maven consumer 正确使用 SOMA，并以真实编译和
运行结果收口。适用系列为 SOMA Java `1.0.x`；实际工作必须固定 consumer 使用的
release tag 或 immutable commit，并从该版本的 POM、generated source/class 与
consumer evidence 获取精确 surface。

本 Skill 只拥有 AI consumer workflow，不拥有 SOMA 语义。发生冲突时按以下顺序
裁决：

1. 固定 SOMA source ref 中的
   `project/blueprint/soma-java-product-blueprint.md` 与 `project/design/README.md`
   决定产品语义和边界；
2. 当前版本的源码、POM、generated source/class、golden 与 external consumer
   决定精确 API；
3. 同一 source ref 的 `docs/getting-started/java-v1-install-and-consumer-guide.md` 决定安装和
   普通 Maven consumer 流程；
4. 本 Skill 只负责把这些事实转化为可靠的 consumer 工作步骤。

上述跨仓库路径始终相对于安装时固定的 SOMA source tree，不是 consumer repository
路径。若工具无法读取该 source ref，先请求访问权或 source location，不把
consumer 中同名文件当作 SOMA Owner。

如果任务是修改 SOMA processor/runtime、治理 Design、执行 release 或处理无关的
Java Collection、ORM、SQL、数据库问题，不要触发本 Skill。

## 工作流

### 1. 固定输入与边界

- 确认这是 SOMA consumer，而不是 SOMA 内部实现任务；
- 记录 SOMA 固定 ref、Maven version、consumer repository、目标 JDK 与完成标准；
- 使用该版本要求的 Amazon Corretto full JDK 8；不要用新 JDK 的
  `--release 8`、Zulu 或其他 vendor 冒充受支持 compiler；
- 若官方 source、版本或访问权不明确，先停止安装或改码，不猜测依赖坐标和 API。

### 2. 只加载需要的参考

- 建模、identity、root/child、Group：
  [modeling-and-ownership.md](references/modeling-and-ownership.md)；
- POM、Schema、编译生成与精确 API：
  [generated-api-workflow.md](references/generated-api-workflow.md)；
- Point/Candidate/Column/Key/Bulk/Ownership 与 DataFlow 选择：
  [access-and-dataflow-routing.md](references/access-and-dataflow-routing.md)；
- lifecycle、resource、结果交付、性能与排障：
  [lifecycle-performance-troubleshooting.md](references/lifecycle-performance-troubleshooting.md)。

不要为了回答局部问题一次加载并复述全部参考。

### 3. 先建模，再写调用

依次回答：

1. 数据是 input fact、working state、frontier/workspace 还是 result fact？
2. 一行是否有跨结构变化仍稳定的业务 identity？
3. 它是 root，还是严格属于某个 parent row 的 child？
4. 哪些 equality access 稳定且高频，值得声明 Key、Unique 或 Index？
5. 多个 root 是否真的需要共同 composition/resource/lifecycle Owner？

任何普通 object、array、DTO、`List`、`Map` graph 都不能成为 live storage。
Schema carrier 只描述逻辑形态和 detached materialization，不是 live row。

### 4. 编译生成后再实现

先配置四个 artifact、声明 `@SomaSchema` / `@SomaTable` / `@SomaValue`，执行
Corretto 8 Maven compile。然后检查 `target/generated-sources/annotations`、
已生成 class 或当前版本的 external consumer，确认真实类型与方法。

严禁根据命名习惯猜 generated method。若目标调用不存在，回到 Schema、processor
diagnostic 和 generated output；不要用 reflection、metadata interpreter 或通用
wrapper 绕开 compiler contract。

### 5. 选择最窄的能力路径

- 已知 Key、Unique 或立即消费的 current Index：Point；
- 已有 exact candidate source 后筛选、排序、聚合：Candidate；
- 单列连续读取：Column；
- logical key 连续读取：Key；
- 批量导入或 mutation staging：Bulk；
- parent-owned child：Ownership；
- 重复执行、typed、多 source 的规则：DataFlow；
- 一次局部 direct access 不要为形式统一强行包装为 DataFlow。

保留 application-owned transaction、event loop、I/O、恢复与业务顺序。

### 6. 闭合 lifecycle、resource 与 failure

在 create/bind/execute 前完成 Plan 和预算；明确 Table、Group、View、Context、
Invocation、callback 和 detached result 的 Owner。current Index 不是稳定
identity；callback-scoped 和 one-shot 对象不得逃逸。默认使用 Eager Detached
result，只有正式 generated surface 明确提供时才使用同步 read-only callback
delivery。

SOMA 不提供 concurrent Table API、跨 Table transaction、持久化、普通
Iterator、Publisher 或 async lazy result。遇到这类需求，应保留 application
responsibility 或重新建模，不要捏造兼容层。

### 7. 用真实 consumer 验证

完成前至少：

1. 在独立于 SOMA reactor 的普通 Maven project 中 clean compile；
2. 确认 processor 只在 build path，runtime graph 只有需要的 production artifact；
3. 运行最小 journey，覆盖 Schema 生成、写入、所选 Access/DataFlow、mutation、
   读取和 release；
4. 对 ownership、current Index、budget、one-shot 或 callback 边界增加对应的
   negative/diagnostic case；
5. 报告固定 ref、JDK/Maven/OS/architecture、命令、结果和仍未验证的边界。

文本看起来合理、IDE 没有报错或只通过单元 mock 都不是完成证据。

## 必须纠正的反模式

- 猜测不存在的 generated type/method；
- 把 Schema object、DTO 或 Java Collection graph 当 live row/storage；
- 把 current Index、物理顺序或 materialized row 当稳定 identity；
- 假设跨 Table transaction、concurrent Table 或隐藏 recovery；
- 在 hot path 引入 reflection、Java Stream、boxing collection、per-row
  polymorphism 或 metadata interpretation；
- 把 callback、cursor、view、Invocation 或 one-shot result 保存到允许范围之外；
- 用 Iterator、pull cursor、Publisher、async lazy result 或无限 stream 替代
  V1 Result Delivery；
- 省略 budget、failure、release 或 consumer compile/run evidence。

发现反模式时先解释违反的 Owner/生命周期/证据边界，再给出符合当前 Design 的
替代路径；不要仅做表面语法替换。
