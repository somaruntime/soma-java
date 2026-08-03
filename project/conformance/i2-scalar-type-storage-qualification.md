# I2 scalar type/storage qualification

状态：`I2_SCOPE_PASS`（bounded slice）；不是完整 I2 plan 或性能/release声明。

## Scope

本报告只覆盖一张 direct scalar Table 的 I2 纵向切片：Java 8 八种 primitive、String、Enum、
nullable reference payload、zero/one Key、多个 exact Index、paged chunk allocation、
reserve/growth、typed expression filter、point update、detached read、duplicate-key failure、no-op
update 与 checked overflow failure。

正式 Blueprint/Design 未修改。I2 未关闭 `@SomaValue` flattening、nested endpoint、keyless/multi-
table composition、remove/compaction、physical Index sidecar、Join、GroupBy、parallel、
compression、metadata 或百万行性能资格线；这些仍由后续 slice 负责。

## Reproduction

在仓库根目录、Java 8 与当前两 artifact build 下执行：

```text
./scripts/check-i2.sh
./scripts/check-i1.sh
./scripts/check-i0.sh
git diff --check
mvn -B -q test
```

结果：

- `I2 scalar type/storage breadth qualification: PASS`；
- I1 qualification PASS；
- I0 processor harness PASS；
- Maven test PASS；
- `git diff --check` PASS。

脚本证明：

```text
java8-generated-surface
all-primitive-fields
enum-reference
nullable-string
multi-index
null-index
typed-filter
canonical-float-double
point-update
duplicate-key
detached-fetch
reserve-growth
default-group-identity
no-op-root-publication
string-content-equality
checked-overflow-structured-failure
no-reflection-static-scan
```

## Implementation evidence

- `ScalarTableRuntime` 的 field storage 使用 primitive/reference typed arrays；
- ChunkDirectory 使用 page → chunk 两级结构；
- Append 与 point update 在 Group guard 内准备 candidate StateRoot 后发布；
- 无变化 point update 复用原 StateRoot，不递增版本也不发布新根；String content-equal update 有
  直接 regression probe；极值 reserve 在分配目录前以 checked arithmetic fail closed；
- generated source 只通过 processor 生成，consumer 使用 runtime/processor path 分离；
- generated API 为 schema field exact Java type；primitive endpoint 的 generic marker 使用对应
  wrapper 仅承担 Java type token，不改变 storage representation；
- String/Enum/reference 通过普通 reference leaf 保存，nullable selection 具有独立测试；
- I2 script 与 I1/I0 scripts 均是仓库内可重放入口，target 产物不纳入 Git。

## Limitations and next owner

I2_SCOPE_PASS 不得升级为完整 I2 PASS。下一项应补齐 `@SomaValue`、nested logical path、keyless
Table 与 multi-table Group，并在其后由 I3 承接完整 Stream/Selection/IR surface。physical Index
sidecar 与 lookup performance 需要独立证据，不能从本次 exact semantic scan 外推。
