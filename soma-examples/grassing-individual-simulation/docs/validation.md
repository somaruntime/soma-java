# 个体生态仿真验证

类型：应用验证

状态：candidate

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：config、generator、bootstrap、AoS oracle、long-run和performance evidence

最后审查日期：2026-07-23

受版本控制的 `correctness/default/large/long-run` 配置分别验证逐 tick 等价、完整旅程、规模与长期 birth/death churn。性能测量排除配置解析、initial-state generation和bootstrap，并默认 `claimAllowed=false`。
