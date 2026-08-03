# I3 direct query/reference qualification

状态：I3_SCOPE_PASS（bounded slice）；不是完整 I3/G4 或性能声明。

./scripts/check-i3.sh 在 Java 8 下通过。它复用了 I2 consumer 的 positive/negative、failure、
detached-result 与 runtime probes，并验证 generated source 包含：

- short-circuit findFirst/anyMatch/allMatch/noneMatch；
- modifiable detached record List 与 typed record array；
- reference map 的 List 与 toArray(Class)；
- specialized mapToLong；
- extended-precision long sum 的抵消行为与最终范围检查；
- one-shot pipeline claim 与 borrowed View escape guard。

当前仍未关闭完整 I3 的 sort/distinct/skip/limit/top、所有 primitive mapper、typed logical-plan
normalization、optimized differential、parallel、Join/Group、metadata 与百万行性能证据。正式
Blueprint/Design 未修改；下一项 I4 负责 mutation selection/failure/resource admission。
