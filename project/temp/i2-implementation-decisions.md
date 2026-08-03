# I2 implementation decisions (temporary)

状态：ACTIVE DURING I2；只记录实现切片范围，不改变正式 Blueprint/Design。

## 1. 本切片的可交付边界

I2 先交付一个可独立验证的 scalar-field vertical slice：一个 composition 中的一张 direct
scalar Table，覆盖八种 Java primitive、`String`、Enum、nullable reference payload、zero/one
Key、多个 exact Index、paged chunk allocation、reserve/growth、detached point read、typed
expression filter 与 point update。I1 的单 `long` key/`long` payload facade 继续由 I1 renderer
负责，避免两套 renderer 同时声明同一能力。

`@SomaValue`、nested logical endpoint、keyless multi-table composition、remove/compaction、
physical Index sidecar、Join/Group/parallel/compression 不在这个 bounded renderer 中伪装为已完成；
processor 对不属于本切片的 composition 仍只生成 composition carrier，后续 slice 负责其
surface admission。正式 Design 仍是唯一语义 Owner。

## 2. Storage implementation

`ScalarTableRuntime` 使用 `FieldSpec` 描述每个 logical field 的 primitive/reference kind、Key
与 Index role。每个 Chunk 为各字段保留 exact primitive array 或 reference array；ChunkDirectory
使用 page -> chunk 两级结构，StateRoot 以单个 `AtomicReference` 发布。Append 与 point update
先在 operation guard 中准备 candidate directory，再发布完整 root；View/Query/Editor 仍是
thread/callback-scoped borrowed state。

I2 的 exact Index selection 先以 StateRoot snapshot 上的语义精确扫描作为 reference baseline；
Index sidecar 与 lookup kernel不在此切片声明性能合同，不能据此外推 I2 performance qualification。

## 3. Type semantics

- primitive leaf 不可为 null，默认 Java zero；
- String/Enum/reference payload 可为 null；
- float/double comparison 使用 `Float.compare`/`Double.compare`，因此 NaN 与 signed zero 遵循
  既定 canonical semantics；
- String 的 Key/更新相等性使用内容相等；Enum 与普通 Java reference 仍使用 identity 相等性；
- Key 不接受 null；Editor 不生成 Key setter；
- ordinary Object、`@SomaValue`、nested endpoint、structural remove 与 key/index capability
  在 I2 scalar renderer 中不生成 API。

## 4. Evidence boundary

`scripts/check-i2.sh` 是可重放 qualification entry，覆盖 Java 8 independent consumer、generated
surface、all primitive fields、Enum/String/null、multiple Index、typed filter、point update、
duplicate key、reserve/growth、detached result、default Group identity、no-op StateRoot publication、
String content equality、checked overflow failure 与 no-reflection source scan。该脚本的 PASS 只
代表本 bounded I2 scope；不覆盖 I2 全部 plan bullets，也不代表 G3/G4/G5 或百万行 performance
已通过。

该记录在 I2 exit evidence 写入后归档；不得把它当成新的长期 Design Owner。
