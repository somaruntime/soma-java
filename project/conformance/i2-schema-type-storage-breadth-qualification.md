# SOMA Java I2 Schema、Type 与 Storage Breadth Qualification

类型：Conformance / Implementation Slice Qualification

状态：`PASS`

Slice：`I2 COMPLETED`

Gate disposition：`G1 PASS`（regression）；`G2-G3 I2_SCOPE_PASS / IN_PROGRESS`；
`G4 I1_I2_DIRECT_SOURCE_SCOPE_PASS / IN_PROGRESS`；`G5 I1_SCOPE_PASS / IN_PROGRESS`；
`G10 I0_SCOPE_PASS / IN_PROGRESS`；`G6-G9 NOT_RUN`

正式事实源：是（I2 schema/type/storage/Key/Index breadth与资格证据）

Owner：SOMA Java I2 current executable fact

资格日期：2026-08-04；实现提交与最终记录日期：2026-08-08

## 1. 结论

I2 在I1纵向闭环上建立了正式V1 schema、type、storage、Key与Index breadth：

```text
complete composition model
    -> deterministic generated Java 8 surface
        -> flattened logical Field layout
            -> paged primitive/reference PLAIN storage
                -> optional Key + multiple Index sidecars
                    -> add/find/get/remove/update + direct-source selection
                        -> atomic StateRoot publication
```

本slice满足
[Implementation Plan](../engineering/v1-implementation-plan.md#6-i2--schematypestorage-breadth)
的I2 Exit：全部准入Field类别、Value flattening、keyless/keyed Table、multiple Index、reference
null、floating canonical equality、Group identity、long reserve/growth、tail compaction/reference
clearing、full regeneration、1:M relation Table journey与100+ Table generated-surface profile均有
production code和可重放证据。

I2没有外推I3-I8能力：完整Predicate/Logical IR、reference interpreter、optimizer、materialization、
Selection mutation、explicit Group GC accounting、GroupBy/Join、parallel、compression/metadata、三项
场景性能、CI/package/release qualification仍未成立。百万行运行结果只是本机资格观测，不是G9
threshold或产品性能承诺。

## 2. 冻结输入与提交

| 项目 | 冻结事实 |
|---|---|
| Repository / branch | `somaruntime/soma-java` / `develop` |
| I2 implementation commit | `3fa61a0d29d48cf96e66bb18a79739119c9e3d76` |
| Production topology | 恰好`io.github.somaruntime.soma:soma-runtime`与`io.github.somaruntime.soma:soma-processor` |
| Qualified source-set digest | `c7326ba6fa8619c1aeef886583ae28de186c598740d90354892f5ce718ec25b7` |
| Canonical qualification | `scripts/check.sh -> scripts/qualify-i2.sh -> I1 -> I0 regression` |
| Dirty-state policy | 完整文件集在资格前后使用同一digest冻结；2026-08-08提交前重新计算仍逐字节一致 |
| Generated output policy | generated source/class只存在于`target/`或资格临时目录，不提交 |

I2没有新增production artifact、third-party dependency、legacy API或compatibility surface，也没有
修改Blueprint/Design。`@SomaField`与`SomaFieldEndpoint<R,V>`仍是唯一正式命名。

## 3. 资格环境与入口

| 维度 | 值 |
|---|---|
| OS / arch | macOS `26.6.1` / `arm64` |
| Machine continuity | 与I0-I1资格相同的18 logical processor、48 GiB机器 |
| JDK / javac | Amazon Corretto `1.8.0_502-b07` / Java 8 class major version 52 |
| Maven | Apache Maven `3.9.16` |

Canonical command：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
    ./scripts/check.sh
```

最终结果：

```text
soma-runtime tests:          20 run, 0 failures, 0 errors, 0 skipped
soma-processor tests:        29 run, 0 failures, 0 errors, 0 skipped
I0 regression:               ok
I1 regression:               ok
I2 consumer/regeneration:    ok
generated public classes:    155
generated public javap SHA:  fb3aa8ae399f5f7a31d972593aaf7dfd982e422ea3523f6c59d89befbd776c85
i2-qualification:            ok
git diff --check:            pass
```

首次全量重放暴露的两个问题均在同一冻结输入形成前关闭：Unicode schema测试不再继承仅应供
`javap`使用的C locale；统一renderer恢复I0 internal linkage的`schemaPackage()`，processor javadoc
显式使用Java 8 `tools.jar`。随后I0-I2 canonical chain完整通过，没有用跳过或降级测试关闭问题。

## 4. Schema、compiler与generated surface证据

- processor以完整composition聚合Table与Value shape，在任何source/manifest publication前完成
  role、type、accessibility、naming与exact/NFC/case-folded/FQN collision preflight；
- six schema annotations、direct Field role、Value递归flatten、reserved generated member、跨
  composition collision、late-round收集与source-before-manifest failure boundary均有positive/
  negative证据；
- `SourceShapeInspector`按JLS单遍解码Unicode escape，并区分code、comment、string与char；code中的
  bidi control稳定产生`[SOMA-1024]`，合法comment/literal不被误拒绝；
- Java 8 Trees API通过compiler/test JVM的`tools.jar`直接链接；processor bytecode不包含
  `java/lang/reflect`或`Class.forName`；
- full regeneration对真实clean rename/delete证明旧generated source、class和manifest entry全部
  消失；失败不能发布部分composition；
- I2 consumer冻结34个generated source hash；资格脚本从class输出独立发现155个public generated
  class并冻结完整`javap -public`聚合digest；
- 112 Table fixture含448 Field、224 Index；canonical profile为1,298 ms、2,401,595 generated-source
  bytes、1,349 class files / 3,212,929 bytes、单class最多120 methods、1,275 constant-pool entries。

这些结构数据证明当前generated surface未接近JVM method/constant-pool边界，不是processor编译时间
SLA或任意规模承诺。

## 5. Type、storage、Key与Index证据

- primitive scalar使用primitive paged arrays；String、Enum、ordinary/parameterized Object与array
  payload使用reference paged arrays；nested `@SomaValue`递归展开为logical leaf layout；
- primitive Field不可null；reference payload可null；Value的每个reference leaf独立遵守logical
  nullability；tail remove清除全部被删除reference，不保留不可达application object；
- ordinary Object保持identity-only storage，不生成Equality、Key、Index、GroupBy或Join capability；
  forbidden use由compile-negative关闭；
- Key为optional且每Table最多一个；全部准入Key type、zero value、duplicate、missing、floating
  canonical semantics与reference content/identity边界均有type matrix；
- multiple Index与duplicate posting、null、remove compaction、point update重建、actual radix
  `255 -> 256` bucket boundary均通过；1:M使用普通relation Table和双向Index完成，不恢复ChildTable；
- size/capacity/row locator保持long-domain；physical chunk/local bucket使用bounded int；checked
  arithmetic、retained/temporary accounting与allocation ordering在失败时fail closed；
- default/explicit Group隔离；同一Group同类型Table accessor在并发首次访问时只发布一个identity。

## 6. Mutation与failed-state证据

- add、reserve、point update与point remove在payload、Key、每个Index sidecar、allocation及publish前
  注入失败时，旧`StateRoot`、version、size、payload、Key/Index可见性与accounting保持完整；
- publication前完成所有可能失败的验证与分配；最终root publish窗口不发生新分配；
- changed update同时维护全部flattened payload、Key与Index；Key immutable边界保持，改变Key仍需
  remove后add；
- remove采用tail compaction并同步移动payload/Key/Index位置；missing为`removed == 0`；
- nullable reference、duplicate Key/Index、invalid argument、arithmetic/resource/callback failure均映射
  稳定structured failure，不泄漏partial state。

I4仍拥有Selection mutation、explicit Group `PhantomReference/ReferenceQueue` retained accounting、
完整peak admission与exhaustive failure matrix；本记录不提前宣称G5整体完成。

## 7. Consumer、规模与artifact fingerprint

I2 independent consumer覆盖全部type shape、keyless/keyed、default/explicit Group、multiple Index、
nullable Value、reference clearing、remove/update、1:M relation与negative surface。百万行profile在
`-Xmx2g`下完成：

```text
rows=1,000,000
capacity=1,048,576
elapsedSeconds=0.390570458
rowsPerSecond=2,560,357
resultFingerprint=de855c5651
```

该数值只证明同一输入的functional/scale journey闭合，不构成G9、跨机器或稳定吞吐声明。

最终canonical qualification生成的local artifact fingerprint如下；它不是release、签名或remote
publication：

| Artifact | SHA-256 |
|---|---|
| `soma-runtime-1.0.0-SNAPSHOT.jar` | `d6e3d4b01690d757b3ca25934d642af2387e5f1105e432f7b8d77a5c2761c2bb` |
| `soma-runtime-1.0.0-SNAPSHOT-sources.jar` | `e9af76511808a20307a9998e5d61d6ba70309a4b33b3361a658d223d1d88c01b` |
| `soma-runtime-1.0.0-SNAPSHOT-javadoc.jar` | `5fba3f72515d9da804ef0701058b239b9186fbf9c1624c6c2f29976ab4de648d` |
| `soma-processor-1.0.0-SNAPSHOT.jar` | `d2da9d05cc8c9e7351643dfc9d2460905fa0bb1097d8633deedc6d275dad5146` |
| `soma-processor-1.0.0-SNAPSHOT-sources.jar` | `789d6591920daadbd99725c1b811e15941202cb34b8c469abe53b85a00f256fe` |
| `soma-processor-1.0.0-SNAPSHOT-javadoc.jar` | `dba045fe6f6ee94ad6d7c1412d8c82d3ca08f581d39397454a39f24f31272da7` |

## 8. 独立审查

| Reviewer route | Scope | Verdict |
|---|---|---|
| `compiler_audit` | composition model、type/role/naming、Unicode、generation atomicity、Java 8、scale | `PASS`；P0/P1/P2无残留发现 |
| `runtime_audit` | layout/chunk、Key/Index、StateRoot、mutation、resource、failed state | `PASS`；I2范围P0/P1全部关闭 |
| `product_evidence_audit` | I2 Exit、golden、type matrix、boundary、relation、claim boundary | `PASS`；五项先前P1全部关闭 |

三项审查相互独立且均以最终实现为输入。审查明确保留I3+和整体Gate边界，没有把局部PASS外推。

## 9. Gate disposition与下一项

| Gate | I2 disposition | 边界 |
|---|---|---|
| G1 | `PASS` | I0-I1完整regression与two-artifact/full-regeneration spine仍通过 |
| G2 | `I2_SCOPE_PASS / IN_PROGRESS` | I2 schema/type/generated breadth成立；I3+ expression/materialization等surface尚未完成 |
| G3 | `I2_SCOPE_PASS / IN_PROGRESS` | PLAIN storage、全部准入Key/Index breadth成立；compression与完整resource closure待I4/I7 |
| G4 | `I1_I2_DIRECT_SOURCE_SCOPE_PASS / IN_PROGRESS` | Table/Index direct selection carrier成立；完整IR/reference/optimizer属于I3 |
| G5 | `I1_SCOPE_PASS / IN_PROGRESS` | I2扩展了point mutation failed-state evidence，但Selection/resource总体closure属于I4 |
| G6-G9 | `NOT_RUN` | 对应production surface尚未进入active slice |
| G10 | `I0_SCOPE_PASS / IN_PROGRESS` | artifact/dependency baseline未变化；CI/package/release qualification属于I8 |

I2关闭后没有active slice；下一项只允许从I3 direct query、Predicate IR与reference interpreter开始。
