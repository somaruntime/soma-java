# I8 Examples Workspace Qualification

类型：Conformance / I8 scenario consumer evidence

状态：`I8_EXAMPLES_WORKSPACE_PASS`；G9 `NOT_RUN`；G10 `NOT_RUN`

日期：2026-08-04

## 1. 目的与边界

本记录证明仓库中的 `soma-examples` 已形成三个可独立编译、生成并运行的 Java 8
application consumer。它不是性能资格、发布资格，也不证明完整 V1 relation surface
已经实现。

Examples 是 Blueprint 定义的 role/scenario projection，不拥有新的产品语义或 Design
合同。生产根 reactor 仍然只有 `soma-runtime` 与 `soma-processor`；`soma-examples` 位于
根 reactor 之外，父 POM 禁止 install/deploy，不产生 SOMA 的第三个发布 artifact。

当前 executable generated surface 是每个 composition 一个 scalar Table。因此三个场景
各使用一个真实的、schema-defined SOMA Table，并明确把业务编排、时间编码、排序策略与
外部副作用留在 application 层；没有用私有 runtime API 或伪造 multi-table Join。

## 2. 场景交付

| 子项目 | SOMA state | 应用层职责 | 运行入口 |
|---|---|---|---|
| `soma-examples/scheduling` | `JobTable`、Key、machine/priority Index、typed filter、point update、read-only parallel count | candidate ordering、状态机、调度决策 | `SchedulingApplication` |
| `soma-examples/simulation` | `SimulationEventTable`、due/entity Index、detached materialization、point update/remove | clock、event transition、entity state | `SimulationApplication` |
| `soma-examples/dispatch` | `PendingDispatchTable`、machine/priority Index、detached projection、point update、read-only parallel count | priority policy、assignment/completion orchestration | `DispatchApplication` |

所有场景都遵守：reserve-before-load、Key immutable、typed expression 优先、View 不跨
callback scope、detached result 才进入 OOP 决策、parallel 显式且共享 application-owned
`ForkJoinPool`。

## 3. 可重放证据

在仓库根目录执行：

```sh
./soma-examples/verify.sh
```

该命令对三个独立 POM 分别执行 `clean verify`，然后以 `-ea` 运行三个 assertion-backed
qualification entry point。2026-08-04 本机 Corretto 8.502 结果：

```text
scheduling qualification PASS: candidates=2
simulation qualification PASS: state=7
dispatch qualification PASS: request=201
```

验证覆盖：processor full-source-set option、Java 8 compile、generated source、Key/Index
lookup、typed filter、detached map、point update/remove，以及受控 parallel count。

## 4. 未声称的内容

- 未运行百万行或更大 profile，因此不关闭 G9；
- 未声明 multi-table generated API、Equality Join、optimizer pushdown 已可用；
- 未创建生产 artifact、package、SBOM、签名、CI workflow 或 GitHub Release；
- `target/` 与 generated source 均为 build output，不提交。

下一步如果要让场景真正覆盖 Job/Machine/Option 等多 Table 关系，必须先在既有 Design
与对应 production implementation slice 中建立公开 generated multi-table/Join surface，
再以本工作区扩展并重新运行场景和性能 Gate；本次不以 Examples 绕过该准入。

## 5. Surface admission

- **Capability / consumer**：为 SOMA library consumer 提供三个可运行的真实场景投影，证明
  generated schema、Index、typed query、detached application object 与 point mutation 能在
  application-shaped workflow 中组合。
- **Owner / lifecycle / failure boundary**：每个子项目拥有自己的 domain/application/schema
  源码与 qualification entry point；workspace 只在本地开发和 I8 scenario qualification
  生命周期内存在，不进入 SOMA 发布面。编译、生成或运行失败直接阻止该场景通过；不由
  Examples 捕获或改写 SOMA structured failure。
- **为什么不放进生产根 reactor**：Examples 是消费方 role projection，不是 runtime 或
  processor capability。将它加入根 reactor 会改变已批准的 two-artifact production
  topology，且会把应用 artifact 与库 artifact 混成一个发布面。
- **Blueprint / Design trace**：BP-1、BP-5、BP-6、BP-9、BP-12、BP-13、BP-15；对应
  Logical API、Generated API Signature、Execution/Concurrency、Implementation Architecture
  与 Core Narratives 的 I8 三场景 fill map。
- **成立所需 evidence**：每个子项目独立 Java 8 full-regeneration compile、deterministic
  positive workflow、generated output inspection、clean workspace check；性能、package
  和 release evidence 仍按 G9/G10 单独准入。
