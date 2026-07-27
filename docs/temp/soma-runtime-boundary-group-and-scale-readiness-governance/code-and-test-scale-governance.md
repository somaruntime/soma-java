# 代码与测试规模候选议题转移记录

类型：Temporary

状态：promoted at `d5f713d`；等待 P7–P10 execution evidence

Owner：P6 code/test governance promotion trace

正式事实源：否

事实范围：正式工程 Owner 映射、P7 replacement-closure 执行边界和 provenance

非事实范围：具体删除裁决、当前代码/测试正确性或 Gate 状态

最后审查日期：2026-07-28

原候选正文已经转移为正式工程规则：

- [测试与 evidence](../../engineering/testing-and-evidence.md)拥有 invariant、
  differential、failure、resource、performance 与 replacement closure；
- [文档治理](../../engineering/documentation-governance.md)拥有 design-first
  promotion、Temporary 退役与事实原子性；
- [Validation Gate](../../engineering/validation-gates.md)拥有 fresh candidate Gate；
- [Benchmark 治理](../../engineering/benchmark-governance.md)拥有 production-scale
  qualification。

P7 必须对 production、generated/public surface、test、benchmark、script、Guide、
Report 和三个 Example 建立逐项 disposition。删除只能在以下链条完整时执行：

```text
Design intent
  -> current responsibility and consumer
  -> replacement owner
  -> invariant/evidence migration
  -> old reference closure
  -> narrow validation
  -> phase Gate
```

LOC、单实现、单调用者、静态“unused”或测试重复外观都不是独立删除理由。最终裁决
记录在 P7 production disposition；原候选正文可从 `aea5cc0` 及
更早 Git history 审计；本文件随 P11 删除。
