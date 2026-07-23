# 工业动态调度验证

类型：应用验证

状态：candidate

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

事实范围：config、generator、bootstrap、oracle、invariant、long-run和performance evidence

最后审查日期：2026-07-23

受版本控制的 `correctness/default/large/long-run` 配置分别验证输入重放、完整旅程、规模与长期 mutation。性能测量排除配置解析、problem generation和bootstrap，并默认 `claimAllowed=false`。
