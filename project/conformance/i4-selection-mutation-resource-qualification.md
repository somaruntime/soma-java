# SOMA Java I4 Selection Mutation 与 Resource Qualification

类型：Conformance / Implementation Slice Qualification

状态：`PASS`

Slice：`I4 COMPLETED`

Gate disposition：`G1 PASS`（regression）；`G2 I4_SCOPE_PASS / IN_PROGRESS`；
`G3 I4_ACCOUNTING_SCOPE_PASS / IN_PROGRESS`；`G4 PASS`；`G5 PASS`；
`G10 I0_SCOPE_PASS / IN_PROGRESS`；`G6-G9 NOT_RUN`

正式事实源：是（I4 Selection mutation、atomic publication与Group retained accounting）

Owner：SOMA Java I4 current executable fact

资格日期：2026-08-09

## 1. 结论

I4 在I3 authoritative logical plan、terminal-start binding与optimized sequential execution之上建立了
完整的Table-local Selection mutation闭环：

```text
Selection / IndexSelection
    -> terminal bind + conservative admission
        -> freeze canonical locator membership
            -> callback-scoped reusable Editor staging
                -> candidate payload + Key/all Index rebuild
                    -> one StateRoot/accounting publication
```

`Selection.update/remove`与`IndexSelection.update/remove`已经进入generated Java 8 surface。Update
返回`matched/changed`，logical no-op不发布新version；Remove保持capacity并按稳定规则完成dense
compaction。任一callback、resource或recoverable publish failure都不会发布部分payload、Key、Index或
accounting状态。

I4没有加入GroupBy/Join、parallel、compression/metadata、场景benchmark、CI/package或release
surface；这些仍属于I5-I8。

## 2. 冻结输入与环境

| 项目 | 冻结事实 |
|---|---|
| Repository / branch | `somaruntime/soma-java` / `develop` |
| I4 implementation commit | `f93a2c2` |
| Canonical qualification | `scripts/check.sh -> scripts/qualify-i4.sh` |
| JDK | Amazon Corretto `1.8.0_502-b07`，class major 52 |
| Maven | Apache Maven `3.9.16` |
| OS / arch | macOS `26.6.1` / `arm64` |
| Production topology | 恰好`soma-runtime`与`soma-processor` |

I4没有新增third-party dependency、第三production artifact、legacy/compatibility surface，也没有修改
Blueprint或Design。

## 3. Canonical qualification结果

最终运行：

```sh
./scripts/qualify-i4.sh
```

结果：

```text
soma-runtime tests:       50 run, 0 failures, 0 errors, 0 skipped
soma-processor tests:     32 run, 0 failures, 0 errors, 0 skipped
Java 8 I3/I4 consumers:  pass
I3 optimizer plan golden:pass
I2 breadth regression:   pass
I2 million-row journey:  pass, fingerprint=de855c5651
full regeneration/ABI:   pass
processor reflection scan: pass
i4-qualification:        ok
git diff --check:        pass
```

本机百万行repeated-add profile为约`3.17M rows/s`；它只证明I4没有使既有ingestion journey发生
明显回归，不构成G9阈值、跨机器比较或对外性能claim，因此没有触发Loader Temporary。

## 4. Mutation、state与resource证据

- mutation terminal取得同一Group operation guard后绑定当前StateRoot，并在任何opaque predicate或
  Editor callback之前按input upper bound预留selection、candidate、sidecar与scratch峰值；
- final locator membership只求值一次并冻结；sequential Editor在calling thread按该顺序逐项复用，
  每个membership最多执行一次；
- Key没有Editor setter；payload candidate完成后重建全部Index，Key因update locator不变而复用，
  remove则与全部Index一起重建；
- small/large selection统一使用copy-on-write candidate root。正式Design允许journal或candidate；
  V1不为同一语义维护第二条small-journal commit机制；
- changed为零时返回matched结果但不发布version；remove对已排序hole使用tail survivor填充并清理尾部
  reference slot；
- application callback异常稳定映射为外层`CALLBACK_FAILED / UPDATE`，candidate丢弃、old root/version
  与temporary accounting保持不变；
- class-loader-wide memory manager按Group token记账，并以`PhantomReference/ReferenceQueue`同步回收
  explicit Group retained bytes；Table对Group的正常强引用不会形成manager到Group的strong cycle，也无
  background cleaner。

## 5. Generated surface与回归边界

- Java 8 application consumer直接使用IndexSelection update、typed Selection update、no-op update与
  Selection remove；Field/mapped mutation仍不存在；
- 42个generated public class继续由source、`javap -public`与规范化`javap -v -public`冻结，public
  signature不泄漏internal Editor access type；
- generated public digest：`fb6cf44b4b9f57e514b98c9ec6ce0e6cc4bead0d7288396a4504d5f2d6e5ac34`；
- shared runtime public ABI未因I4 internal mechanism扩张；
- I3 query consumer、optimizer golden、I2 full type/storage breadth、million-row journey、full
  regeneration与processor no-reflection evidence均在同一入口重放。

## 6. 审查方法与效率边界

主Agent在实现完成后进行一次detached diff review，只检查atomic publication、Key/Index/accounting
同代、callback-before-admission与Group reachability四项架构不变量。另一个bounded只读reviewer按原
slice合同启动，但超过timebox仍未形成verdict，依据Product Owner关于停止重复审查与控制Token的指令
被中断；本文不将其记为独立Agent PASS。

因此，本slice的独立性证据来自processor生成链、Java 8 application consumer与canonical
qualification的分离执行路径，而不是多Agent重复扫描。后续slice继续采用单主路径、一个最终
qualification、必要时一个严格timebox的reviewer；reviewer无新证据时立即停止。

## 7. Gate disposition与下一项

| Gate | I4 disposition | 边界 |
|---|---|---|
| G1 | `PASS` | clean reactor、Java 8、I0-I3 regression与full regeneration通过 |
| G2 | `I4_SCOPE_PASS / IN_PROGRESS` | Selection/IndexSelection mutation exact surface成立；I5-I7 surface待实现 |
| G3 | `I4_ACCOUNTING_SCOPE_PASS / IN_PROGRESS` | atomic StateRoot与Group retained accounting成立；compression属于I7 |
| G4 | `PASS` | I4复用I3 authoritative logical/optimized execution且query regression通过 |
| G5 | `PASS` | point与Selection mutation、resource、callback failure、GC accounting闭合 |
| G6-G9 | `NOT_RUN` | 对应production surface尚未建立 |
| G10 | `I0_SCOPE_PASS / IN_PROGRESS` | I4没有新增dependency/artifact；完整package/release属于I8 |

I4关闭后没有active slice；下一项只允许从I5 GroupBy与binary Equality/Cross Join开始。
