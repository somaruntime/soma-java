# Lifecycle、Resource、性能与排障

适用 SOMA 版本：`1.0.x`

正式 Owner：

- [Runtime Plan 与可观测性](../../../../docs/design/runtime-plan-and-observability.md)
- [Ownership 与 lifecycle](../../../../docs/design/ownership-and-lifecycle.md)
- [性能模型](../../../../docs/design/performance-model.md)
- [Correctness 与 failure](../../../../docs/design/correctness-and-failure.md)
- [Result Delivery 与 Materialization](../../../../docs/design/materialization-boundary.md)

## Lifecycle 与资源

- Descriptor/Metadata 描述事实；Plan Builder 只在 freeze/create 前可变；
- create/bind/execute 前给出 planning/maximum rows、materialization 和 execution
  预算，不用缺省猜测替代必需事实；
- Table/Group 拥有 live state；Batch、cursor、view、Invocation、callback 和
  detached result各有不同有效范围；
- one-shot 对象完成后不复用，callback-scoped 对象不逃逸；
- release 必须由明确 Owner 在成功和失败路径都执行；
- structured error、compatibility identity 和 observation字段用于判断，不解析
  message 文本作为 contract。

SOMA 是同步、进程内 library，不是 sandbox、database 或 transaction manager。
application 负责 authentication、authorization、tenant isolation、I/O、persistence、
domain validation、外部副作用和恢复。

## Result Delivery

默认选择 Eager Detached result。只有当前 generated/executable surface 明确支持，
且 consumer 能在同一同步调用栈完成只读消费时，才使用 callback delivery。

禁止用普通 Iterator、closeable pull cursor、Publisher、async push、无限 stream
或保存 callback 参数来模拟 lazy output。若 detached result 超过预算，重新选择
更窄 projection/aggregation、显式 callback path或 application-owned分批边界，
不要绕过 admission。

## 性能坏味道

发现以下形状时先回到 Access/Plan/Schema，而不是先调 JVM 参数：

- hot path materialize schema object、DTO 或 `List<Row>`；
- reflection、metadata interpreter、Java Stream、boxing collection；
- per-row polymorphic dispatch 或临时 tuple；
- exact read 时隐藏全表 rebuild/sort；
- candidate terminal 扩回全表；
- current Index 跨 mutation 保存；
- 无界 scratch、result 或 String reachable bytes；
- 为局部 microbenchmark 牺牲 ownership、failure、determinism 或 public contract。

性能结论必须同时有 correctness guard、记录环境、固定 workload、multi-fork 或正式
qualification方法。单次最好值、smoke 或另一机器/JDK 的历史数字不能成为当前
支持声明。

## 排障顺序

1. 记录固定 SOMA ref/version、Corretto/Maven/OS/architecture和完整命令；
2. 从第一次失败开始分类：toolchain、schema diagnostic、generated surface、
   lifecycle/compatibility、resource、correctness、performance；
3. 检查 generated source/class 和 structured error，不猜 API、不解析 message；
4. 构造最小独立 consumer，保留相同 Schema、Plan、输入和失败边界；
5. 先运行最窄 deterministic check；只有输入、假设或证据目标发生变化时才重复
   高成本 benchmark/qualification；
6. 修复后复验直接 failure，再运行与变更 surface 相称的 consumer journey。

如果问题要求修改 SOMA Design、public/generated contract 或 runtime internals，
停止 consumer Skill 路径，转交相应 SOMA Owner；不要在 consumer 项目建立
reflection adapter、伪 generated API 或长期 fork。
