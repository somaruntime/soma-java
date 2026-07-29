# SOMA Java V1 Goal execution status

类型：Report / Status

状态：G0–G5 passed；G6 blocked

Owner：SOMA Java Goal execution

受众：SOMA maintainer、Gate owner、release owner

适用版本：`0.2.0-SNAPSHOT` 2026-07-29 logical/execution candidate

输入事实源：正式Blueprint/Design/Conformance/Engineering、production source、
generated/public consumers、component/application baseline、runtime-scale
qualification、private SCM与CI配置

事实范围：当前G0–G6状态、JDK authority、有效evidence与claim boundary

非事实范围：public release授权、未执行环境支持或任意workload性能承诺

最后审查日期：2026-07-29

## 1. 当前结论

SOMA Java V1保持完整产品目标：

- Java 8、Schema-Defined、Compiler-Specialized、JVM Heap-Resident；
- `State / Owner + Capability + Plan / Lifecycle`核心抽象；
- Metadata/Group、four-kind storage type、closed logical type capability；
- String reference backend、packed storage、exact Access、typed Transformation；
- numeric closed kernel、formula-bound Bitmap、primitive Join runtime filter；
- Eager Detached默认、callback-scoped Result Delivery；
- bounded adaptive morsel scheduler与三类reference application；
- Small/Medium、单1M、双1M、String、Expansion、Delivery、Soak正式qualification。

10M/100M没有被删除；它们被修正为非阻塞research/stress，不再把V1 readiness绑定
到超出务实产品保证的row count，也不能反向外推为任意Schema SLA。

唯一compiler/validation authority为：

```text
Amazon Corretto 8.502.07.1
java 1.8.0_502-b07
javac 1.8.0_502
Maven 3.9.16
```

当前Corretto/macOS/aarch64 candidate已通过compiler/codegen、public/generated、
external consumer、runtime/DataFlow contract、reference differential、两个
component、三个application及8-lane runtime-scale qualification。Corretto
Ubuntu x64 build/contract evidence继续有效，但不外推为Linux性能baseline。

## 2. G0–G6

| Gate | 状态 | 当前直接证据与边界 |
|---|---|---|
| G0 | passed | Java 8产品边界、Blueprint/Design Owner、Capability与claim boundary稳定；SQL只作设计启发 |
| G1 | passed | four-kind storage classifier、logical type catalog、String、Metadata hierarchy、compiler diagnostics及schema/hash repeat通过 |
| G2 | passed | Corretto full JDK 8 integration、logical generated facade、TIME validation、golden、negative compile、external consumer与Java major 52通过 |
| G3 | passed | Group/lifecycle、storage/access、Bitmap、closed kernel、Join filter、DataFlow、scheduler、delivery、failure/observation通过contract与differential |
| G4 | passed | dense/keyed/access/child/breadth普通external Maven consumer能够clean generate/compile/run |
| G5 | passed | component与九application profile通过；Corretto v2的Small、Medium、单1M、双1M、String、Expansion、Delivery、Soak全部required lane通过 |
| G6 | blocked | selected `private-github-source`尚缺同一最终candidate的clean package/security provenance、support-matrix sign-off与manual release qualification |

G5 qualification identity：

- ID：`runtime-scale-qualification-20260729-d90e8499d51f`；
- source：
  `content-sha256:d90e8499d51f7477db3959033895853e223bd692794e25eb8bdf234492e3c2ba`；
- artifact：
  `sha256:4bdc5b51407aaec838af0a95de81249c717e8beab9fea78e1cbf4db8a4abbbef`。

## 3. Conformance

当前开放差距：

- `CF-005`：性能与规模只适用于记录的Corretto/macOS/aarch64环境/profile；
- `CF-006`：selected private-source G6 release qualification与sign-off；
- `CF-017`：Codex Cloud fresh-container development qualification。

本轮关闭：

- `CF-016`：Corretto runtime-scale v2完整required artifact；
- `CF-018`：logical type与physical capability clean production cutover。

历史Zulu 100M与release evidence仍可解释相应历史candidate，但不再承担当前
authority的passed依据。

## 4. V1 RC 关系

当前candidate已经具备进入V1 Release Candidate评估的G0–G5技术基础，但尚未获得
G6 release sign-off。准确表述是：

> SOMA Java V1的产品设计、production implementation与当前Corretto G5
> qualification已闭合；selected private-source release仍blocked。

允许陈述：

- 当前Corretto/macOS/aarch64记录profile的Small/Medium、单1M、双1M、String、
  Expansion、Delivery、Soak通过；
- 三个reference application是独立Java 8 consumer并通过当前evidence；
- 10M/100M存在optional research/stress入口。

不允许陈述：

- 任意Schema、任意String profile或任意relation都保证1M/100M；
- Linux、其他CPU/JDK build或Cloud已经具有性能/规模支持；
- production-ready、public RC、Maven Central ready或G6 passed。

## 5. 下一 Gate

进入真正release candidate前，只执行窄G6工作：

1. 固定同一最终candidate和selected `private-github-source` profile；
2. 运行clean package/security/provenance qualification；
3. 核对SCM、support/security contact与Support Matrix真实值；
4. 由release owner完成manual sign-off。

Public repository、Maven Central、Cloud development与10M/100M research都不能替代
这四项，也不应混入本轮G5结论。
