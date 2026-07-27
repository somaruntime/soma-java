# 实时派工规则引擎应用 Blueprint

类型：应用 Blueprint

状态：当前

Owner：real-time-dispatch-rule-engine

对 SOMA 产品规范性：否

事实范围：应用目标体验、业务旅程、DataFlow 使用方式和非目标

最后审查日期：2026-07-27

## 目标体验

使用者提供版本化配置，Factory 先产生有限、可重放的 detached dispatch horizon：
初始 work/resource snapshot，加上按时间排序的 work-arrival delta。Dispatcher
创建 runtime、重复执行同一规则 Template，并返回 Runtime 关闭后仍可消费的
领域 Result 和独立 diagnostics。

```text
versioned properties
  -> detached DispatchScenario
  -> WorkState / ResourceState runtime
  -> reusable DispatchRule Template
  -> one Invocation per cycle
  -> detached DispatchCommand
  -> application-owned commit
  -> detached DispatchOutcome
```

普通调用明确拥有 DataFlow execution context：

```java
DispatchConfig config = new DispatchConfigLoader().load("default");
DispatchScenario scenario =
    new SyntheticDispatchScenarioFactory().create(config);
ExecutionBudget budget = ExecutionBudget.defaults()
    .toBuilder()
    .maximumWorkers(config.workers())
    .build();
DataFlowContext context = DataFlowContext.managedParallel(
    config.workers(),
    ExecutionPolicy.adaptiveParallel()
        .withMinimumParallelCardinality(
            config.parallelMinimumCardinality()),
    budget);
try {
  DispatchOutcome outcome =
      new SomaDispatcher().dispatch(scenario, context);
  DispatchResult result = outcome.result();
} finally {
  context.close();
}
```

调用者也可以选择 sequential context，或显式借用自己的 executor；SOMA 只关闭
managed executor，不关闭 borrowed executor，也不隐式使用 common pool。

## Runtime state 与规则

`WorkState` 使用 stable `WorkId` 和 `by_status` exact group；
`ResourceState` 使用 stable `ResourceId`。初始 snapshot 通过 Batch 投影，后续
arrival 通过 keyed Delta 在 Work aggregate safe point 应用。输入 Factory 不被
Runtime 或 rule 反向调用。

规则计划只构造一次，不保存 live Table：

```text
pending work
  -> filter(release <= current minute)
  -> groupBy(capability).counts
  -> innerJoin(available resources, capability)
  -> stable business order
  -> joined current-Index result
```

每次 Invocation 显式绑定当前两张 Table 和 `currentMinute`。应用只在来源未修改的
同步只读批次中消费 joined Index，立即复制为 immutable command；Index、binding、
ColumnView 和 Cursor 不进入下一周期或 Result。

## 提交和输出

DataFlow 负责批量候选推导，不获得跨 Table transaction 能力。应用先用 stable
key 对整批 command 完成 identity、version、capability、availability 和算术预检，
再按确定性顺序修改 Work 与 Resource 两个 root。权威 mutation 之后的意外失败使
本次 horizon fail-stop，不发布 partial success。

`DispatchResult` 只拥有 command、业务计数和稳定 checksum；
`DispatchDiagnostics` 单独拥有 Definition/Template identity、group/join 统计、
worker/task 和 runtime identity。两者都是 detached output，diagnostics 不参与
业务 checksum。

## 成功标准与非目标

- 同一 scenario 在 sequential、managed 和 borrowed context 下结果一致；
- plain-Java reference evaluator 独立验证命令、计数和 checksum；
- 配置、输入生成、runtime、rule、commit、Result 和 evidence 各自有唯一责任；
- default、large、long-run 可以改变规模而不改变 production 分支；
- budget、cancellation、executor ownership 和 fail-stop 边界可见且可验证；
- 不实现数据库/MES 同步、JDBC/CDC、retry、checkpoint、事务或分布式执行。
