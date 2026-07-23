# 个体生态仿真应用 Design

类型：应用 Design

状态：candidate

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：配置、detached initial state、runtime aggregate、system顺序、随机与失败边界

最后审查日期：2026-07-23

## 责任边界

依赖方向固定为：

```text
config/       strict properties、typed effective config
model/        detached initial state 与 generator
state/        annotation schema
runtime/      bootstrap、aggregate、ordered systems、primitive staging
validation/   independent AoS oracle 与 invariant
evidence/     verification、allocation/GC benchmark
```

`config/model/support` 不 import SOMA runtime 或 generated package；runtime 不调用
`InitialStateGenerator`。Grass grid、cell scratch、random function 和 system order
由应用拥有。

## Authoritative state

- `GrasserStateTable` 是 live individual state，`GrasserId` 是跨 operation identity；
- `TraceSampleTable` 只保存低频 summary，不参与 system 决策；
- `double[] grass` 是二维 world 的 row-major authoritative field；
- `Index` 只在一个同步只读批次中立即消费；长期引用必须使用 key；
- 所有 Table 与 scratch 由单个 `SimulationRuntime` session 拥有并统一 release。

删除使用 swap-remove，不承诺 packed 遍历顺序。结果 checksum 先按 stable ID
canonicalize 个体，再编码 grass 的固定 cell 顺序。

## Mutation 与失败

单次 SOMA operation 保持其既有失败原子性；应用不声称跨 Table/grass transaction。
两阶段系统遵循：

1. 只读扫描形成可丢弃 primitive decision；
2. 完成 finite、bounds 和 cell budget 校验；
3. 提交可能失败的 SOMA operation；
4. 执行不抛异常的 grass primitive publish；
5. authoritative publish 后的意外异常使 session fail-stop。

结构 mutation 不发生在 borrowed/update callback 中。繁殖按稳定 ID 暂存 parent
事实，构建 offspring Batch，原子更新 parent energy 后批量 append；若跨 operation
后续失败，不继续复用该 session。

## 成本边界

- growth：`O(world cells)`；
- metabolism、grassing、searching：`O(current population)`；
- death：candidate scan + swap-remove；
- reproduction：candidate filter，选中 parent 的 stable sort 与 batch append；
- mode access：`@SomaIndex` exact group；
- trace/result：低频 stable sort、Column/IndexSnapshot gather 或 materialization。

配置在装载时一次解析为 typed primitive 字段，hot loop 不重复解析字符串。系统
loop 不使用 Java Stream，也不逐 tick materialize object graph。

详细迁移与切换裁决仍由 active Temporary 专题拥有；本应用文档不重新定义 SOMA
Access Model 或 runtime 语义。
