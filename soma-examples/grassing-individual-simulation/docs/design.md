# 个体生态仿真应用 Design

类型：应用 Design

状态：candidate

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：配置、detached initial state、runtime aggregate、system顺序、随机与失败边界

最后审查日期：2026-07-23

依赖方向固定为 `properties -> config -> detached initial-state generator -> validated bootstrap -> runtime`。Grass grid 和 random function由应用拥有；随机值由 seed、tick、stable id、process和draw派生，不能依赖 packed order。

详细候选设计在 active Temporary 专题中维护，专题完成时将稳定事实一次性固化至本应用。
