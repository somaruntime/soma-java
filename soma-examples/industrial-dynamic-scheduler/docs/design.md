# 工业动态调度应用 Design

类型：应用 Design

状态：candidate

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

事实范围：配置、detached problem、runtime aggregate、调度循环和失败边界

最后审查日期：2026-07-23

依赖方向固定为 `properties -> config -> detached generator/problem -> validated bootstrap -> runtime`。Generator 不访问 SOMA runtime；runtime loop 不反向调用 generator。Event queue、machine heap、calendar evaluator和跨 Table 提交由应用拥有。

详细候选设计在 active Temporary 专题中维护，专题完成时将稳定事实一次性固化至本应用。
