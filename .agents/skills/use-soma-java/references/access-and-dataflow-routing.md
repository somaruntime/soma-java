# Access 与 DataFlow 路由

适用 SOMA 版本：`1.0.x`

正式 Owner：

以下路径相对于安装来源固定的 SOMA source ref：

- `docs/design/access-model-and-candidate-scan.md`
- `docs/design/transformation-model.md`
- `docs/design/dataflow-execution-model.md`
- `docs/design/table-storage-and-access.md`

本参考帮助选择能力族；精确 generated type/method 仍从当前编译结果读取。

## 先选最窄 Access

| 已知输入或目标 | 首选能力 | 不应默认做什么 |
|---|---|---|
| stable Key、Unique 或立即使用的 current Index | Point | 为一次点查构造万能 pipeline |
| exact source 后继续 filter/sort/aggregate | Candidate | terminal 静默回退全表 |
| 连续只读一个 primitive column | Column | 逐 row materialize DTO |
| 连续读取 logical key | Key | 用物理 Index 代替 identity |
| 批量导入、替换或 staged mutation | Bulk | 每行临时 object/隐式部分提交 |
| parent-owned child | Ownership | share/reparent 或保存 `List` live graph |

Candidate stage 只能处理前一 stage 的候选；filter 后的 sort、first、update 或
aggregate 不得重新扩展到全表。Point/Column/Key/Bulk/Ownership 都是正式能力，
Pipeline 不代表整个产品。

## Current Index 边界

- Index 只在当前 Table state 内定位物理 row；
- mutation、compaction、lifecycle 变化后旧 Index 可以失效；
- 跨 operation identity 使用 Key/Value Object；
- 只有正式 API 明确提供且调用点确需批量稀疏读取时才创建 IndexSnapshot；
- View、cursor、callback 与 snapshot 的有效范围必须从当前 generated/runtime
  contract确认，不能凭名称延长。

## 何时使用 DataFlow

使用 typed DataFlow 的条件通常包括：

- 规则会反复执行；
- 输入 Shape、operator、result/effect 能静态表达；
- 需要多 source bind、受控 parallel、统一预算或 Explain；
- Definition/Template 的编译成本值得复用。

一次点查、单列读取或简单 candidate chain 继续用 direct Access。不要为了“架构
统一”把每次操作都包装成 DataFlow。

正确 lifecycle 是：

```text
immutable Definition -> compiled reusable Template
    -> bind current sources/parameters
    -> one-shot Invocation
    -> execute
    -> complete detached result 或同步 callback-scoped delivery
    -> observation/release
```

Invocation 不能复用；parallel 只发生在 application 已独占的执行内部，不把 Table
变成 concurrent API。Effect 只在正式 safe point 发布，application 外部副作用和
跨 Table transaction仍由 application拥有。

## 结果与关系

- 默认结果是完整 Eager Detached；
- callback delivery 仅限正式支持的同步 read-only one-shot 路径；
- 不增加 Iterator、pull cursor、Publisher、async lazy 或 partial result；
- primitive join runtime filter、Bitmap 等是内部策略，不进入 consumer 逻辑；
- String/composite Key 不因内部策略存在而获得未声明的 filter/ordering 保证；
- built-in integral arithmetic固定为fail-closed checked semantics；application
  仍必须显式拥有 comparator、tie-break、unit、业务值域，以及registered
  function/reducer自行声明的value/failure semantics。

路由完成后，用 reference/differential 或小型 oracle 验证结果、顺序、cardinality、
failure 和 lifecycle，而不只验证“能运行”。
