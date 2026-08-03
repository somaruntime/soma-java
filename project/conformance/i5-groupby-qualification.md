# I5 GroupBy qualification

状态：I5_SCOPE_PASS（bounded integer-key GroupBy slice）；不是完整 I5/G6。

覆盖：

- generated `groupBy(intField).count()` 与 `.sum(intField)` 精确 Java 8 surface；
- 类型可用但未声明 `@SomaKey/@SomaIndex` 的普通 `int` Field 也可作为 GroupBy key；
- key 首次出现顺序与重复 key 聚合；
- two-limb checked aggregate semantics（共享 I2 arithmetic evidence，含抵消与最终溢出证据）；
- detached typed result、Entry list/array 与 primitive consumer；
- null consumer、callback failure、foreign endpoint 与 GroupBy one-shot 的 structured failure；
- I1-I4 regression、full generated source 与 Java 8 independent consumer。

未覆盖且不作声明：

- reference/Enum/@SomaValue key、其他 aggregate family、multi-aggregate；
- Equality/Cross Join、optimizer/hash/sort plan、resource budget、parallel execution；
- million-row GroupBy performance、完整 G6 differential 与 metadata/explain。

正式 Blueprint/Design 未修改。
