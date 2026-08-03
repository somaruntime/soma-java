# I5 implementation decisions (temporary)

状态：ACTIVE DURING I5；本文件只记录 bounded slice 的实施边界，不修改正式 Blueprint/Design。

## Scope

本 slice 先闭合一个可验证的 GroupBy vertical slice：generated scalar Table 的所有类型上可
keyable 的 `int` endpoint（不要求该 Field 已声明为 Key/Index）
支持 `groupBy(...).count()` 与 `groupBy(...).sum(intField)`，返回 detached、encounter-ordered
`IntGroupedLongResult`。结果通过 primitive callback、detached Entry list/array 消费，不返回
`Map<Object,Object>`，不暴露 physical column。

## Deliberate boundary

这是 I5 的 bounded GroupBy slice，不宣称完整 GroupBy capability matrix，也不宣称 Equality/Cross
Join、reference/Enum/Value key、all numeric aggregate、hash/sort optimizer、resource budget或
parallel GroupBy 已完成。当前 runtime 使用确定性的 small-reference grouping path，优先证明 key
首次出现顺序、two-limb checked aggregate、detached result 和 callback failure；bounded path允许
每个 group 使用一个内部 accumulator scratch object，不将其暴露为结果；性能优化和完整 Join
planner 留在后续 slice。

## Evidence

`./scripts/check-i5.sh` 在 Java 8 independent consumer、I1-I4 regression 与 generated `javap`
surface 下通过。PASS 只代表本 bounded GroupBy slice。
