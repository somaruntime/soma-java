# I4 point mutation qualification

状态：I4_SCOPE_PASS（bounded point-remove slice）；不是完整 I4/G5 或性能声明。

./scripts/check-i4.sh 通过，并覆盖：

- typed RemoveResult carrier 与 generated remove(Key)；
- missing remove 返回 removed=0、不改变 size；
- 命中删除后的 packed logical order/compaction（中间行、跨 chunk、全字段 survivor、Index 查询）；
- null Key 在 admission 前稳定返回 `INVALID_ARGUMENT`，不改变 Group 状态；
- candidate StateRoot 单次 publication 与 stateVersion 增长；
- I2/I3 已验证的 no-op update、callback failure、checked overflow 与 cleanup。

Selection update/remove、global memory admission、GC retained accounting、fault-injection matrix、
large journal path 和百万行 mutation performance仍待后续实现。正式 Blueprint/Design 未修改。
