# 个体生态仿真应用 Blueprint

类型：应用 Blueprint

状态：candidate

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：应用目标体验、模型边界和成功标准

最后审查日期：2026-07-23

目标是通过版本化配置生成可重放的 grass field 与 grasser population，装载独立 runtime aggregate，按显式 system 顺序执行 headless ticks，并以 AoS oracle、invariant 和稳定 checksum 验证结果。

应用借鉴 grasser–grass 领域目标，但不依赖 Artemis-odb，也不把 SOMA 扩展成 ECS。
