# SOMA Java I3 Query IR 与 Reference Execution Qualification

类型：Conformance / Implementation Slice Qualification

状态：`PASS`

Slice：`I3 COMPLETED`

Gate disposition：`G1 PASS`（regression）；`G2 I3_SCOPE_PASS / IN_PROGRESS`；
`G3 I2_SCOPE_PASS / IN_PROGRESS`；`G4 PASS`；`G5 I3_QUERY_SCOPE_PASS / IN_PROGRESS`；
`G10 I0_SCOPE_PASS / IN_PROGRESS`；`G6-G9 NOT_RUN`

正式事实源：是（I3 direct query、typed IR、reference interpreter与optimized sequential执行）

Owner：SOMA Java I3 current executable fact

资格日期：2026-08-09

## 1. 结论

I3 在I2 schema/storage基础上建立了完整的顺序查询闭环：

```text
generated typed source/expression
    -> immutable logical plan
        -> terminal-start binding
            -> reference interpreter
            -> normalized/optimized sequential physical plan
                -> scalar、numeric、materialized result
```

Table、Index和Field source，typed predicate与opaque callback barrier，filter/map/mapTo、
distinct/sort/top/skip/limit、match/scalar/numeric/materialization及one-shot lifecycle均已有production
实现。Reference interpreter与optimized sequential executor消费同一authoritative logical语义、保持独立
traversal，并通过差分证据关闭Index substitution、顺序、null、numeric与failure边界。

I3不包含Selection mutation、GroupBy/Join、parallel、compression/metadata、场景性能、CI/package或
release qualification；这些仍分别属于I4-I8。

## 2. 冻结输入与环境

| 项目 | 冻结事实 |
|---|---|
| Repository / branch | `somaruntime/soma-java` / `develop` |
| I3 implementation commit | `62ad28c` |
| Canonical qualification | `scripts/check.sh -> scripts/qualify-i3.sh` |
| JDK | Amazon Corretto `1.8.0_502-b07`，class major 52 |
| Maven | Apache Maven `3.9.16` |
| OS / arch | macOS `26.6.1` / `arm64` |
| Production topology | 恰好`soma-runtime`与`soma-processor` |

I3没有新增third-party dependency、第三production artifact、legacy/compatibility surface，也没有修改
Blueprint或Design。

## 3. Canonical qualification结果

最终运行：

```sh
./scripts/qualify-i3.sh
```

结果：

```text
soma-runtime tests:       46 run, 0 failures, 0 errors, 0 skipped
soma-processor tests:     32 run, 0 failures, 0 errors, 0 skipped
Java 8 I3 consumer:       pass
I3 optimizer plan golden: pass
I2 breadth regression:    pass
I2 million-row journey:   pass, fingerprint=de855c5651
compile negatives:        pass
full regeneration:        pass
processor reflection scan:pass
i3-qualification:         ok
git diff --check:         pass
```

百万行结果只证明既有functional/scale journey没有回归，不构成G9吞吐门槛或跨机器性能声明。

## 4. Public surface与generation证据

- Java 8 consumer覆盖Table/Index/Field source、nested Value/Enum Field、typed expression、mapped与
  primitive stream以及terminal组合；
- compile-negative证明I4 Selection mutation、I5+ relation和I6 parallel surface没有被提前发布；
- generated source由golden hash冻结，42个generated public class同时冻结`javap -public`与规范化
  `javap -v -public`；public signature不泄漏internal type；
- shared runtime共73个public class，同样冻结public/verbose ABI；
- generated public digest：`5292251865c02202a840e9c8998c7ad958b90b38134c35d06381359cba7b2343`；
- runtime public digest：`5c2fd280d8ed065d1cdc0bd3092fca8aa3bbe0724a2bb42573a5a873bee5a3e4`；
- full regeneration、input-order determinism与I2 breadth consumer在同一资格入口重放。

## 5. IR、reference与optimizer证据

- `PredicateIr`与`LogicalRowPlan`是data-only logical owner，pipeline构建不执行数据或callback；
- terminal取得Group guard后绑定当前StateRoot；reference与optimized路径不把pipeline创建时状态当
  snapshot；
- adjacent typed filters规范化，opaque callback形成barrier；required leaves、stateless fusion与
  Key/Index `EQ`、nullable Index `IS_NULL` substitution进入可重放plan golden；
- reference interpreter始终按canonical locator顺序解释原始logical plan；optimized executor使用独立
  traversal，不以reference作为失败fallback；
- Index source/substitution保留duplicate、null与canonical encounter order，residual predicate不会被
  静默删除；
- query View与mutation Row分责，borrowed View保持callback-scoped且不向public ABI泄漏internal marker。

## 6. Result、resource与failure边界

- integer aggregate采用signed-128累计并在最终long结果处fail closed；floating aggregate采用固定
  1024-element block与deterministic pairwise strictfp reduction；
- materialization产生detached result；mapped reference array使用`toArray(Class<A>)`；primitive路径
  保持primitive array；
- stateful/materialization/IN temporary在执行callback或数据kernel前进行managed admission，并在
  terminal结束时释放；
- one-shot carrier在成功或terminal失败后永久consumed，参数验证失败保持未claim；
- application callback/Comparator异常统一进入structured `CALLBACK_FAILED`，不泄漏部分SOMA state；
- reference/optimized representative success与failure使用独立Table state进行差分。

## 7. 独立审查与问题闭合

独立surface审查以最终generated/runtime surface为输入，结论为`PASS`，未发现残留P0/P1。更早的
runtime/evidence只读审查识别出public internal marker、validation-before-claim、resource preflight、
IN normalization、Comparator boundary与plan evidence等具体问题；这些问题均在最终资格输入形成前由
production code、targeted test和canonical qualification关闭。

本次收口没有继续把极端非正常调用扩张为新产品合同；只有影响正常使用、正式合同或架构分责的发现
进入实现。

## 8. Gate disposition与下一项

| Gate | I3 disposition | 边界 |
|---|---|---|
| G1 | `PASS` | clean reactor、Java 8、I0-I2 regression与full regeneration通过 |
| G2 | `I3_SCOPE_PASS / IN_PROGRESS` | I3 exact query/generated surface成立；I5-I7 surface尚未出现 |
| G3 | `I2_SCOPE_PASS / IN_PROGRESS` | I3复用当前PLAIN storage；I4 accounting与I7 compression尚未关闭 |
| G4 | `PASS` | direct query、IR、reference、optimized sequential、materialization与差分证据闭合 |
| G5 | `I3_QUERY_SCOPE_PASS / IN_PROGRESS` | query lifecycle/resource/failure成立；Selection mutation与完整accounting属于I4 |
| G6-G9 | `NOT_RUN` | 对应production surface尚未建立 |
| G10 | `I0_SCOPE_PASS / IN_PROGRESS` | I3没有新增dependency/artifact；完整package/release属于I8 |

I3关闭后没有active slice；下一项只允许从I4 Selection mutation、failure与resource admission开始。
