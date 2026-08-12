# SOMA Production Core 质量检查方法可行性探索

类型：Conformance / Exploratory Method Evidence

状态：`PASS / METHOD_EVALUATED / ROUTINE_ADOPTION_REJECTED / TEMPORARY_CLOSED`

日期：2026-08-13

Owner：本次Xlint、PMD、JaCoCo有界实验的输入、原始结果、成本、采用裁决与claim boundary

## 1. 最终结论

本次实验已经回答“是否值得把通用静态检查与覆盖率报告纳入SOMA日常开发流程”：

> 当前Evidence不支持建立新的日常质量流程。`javac -Xlint:all`、精选PMD和JaCoCo没有发现
> 既有Design、tests与Qualification之外的高价值production缺陷；信号价值不足以覆盖规则维护、
> triage、POM集成和错误激励成本。三项工具均不晋升为稳定入口、CI或质量门。

这不是“工具永远无用”的结论。遇到具体compiler warning、资源关闭疑点或test-JVM路径问题时，
可以把相应工具作为一次性诊断手段；但不得把本记录解释为永久配置、统一阈值或每个Slice固定动作。

最终Method Decision：

```text
OVERALL                  REJECT_ROUTINE_ADOPTION
JAVAC_XLINT_ALL          REJECT_ROUTINE_ADOPTION
PMD_ALLOWLIST            REJECT_ROUTINE_ADOPTION
JACOCO_REPORT            REJECT_ROUTINE_ADOPTION / ON_DEMAND_DIAGNOSTIC_ONLY
CI / QUALITY_GATE        NOT_ESTABLISHED
STABLE SCRIPT            NOT_CREATED
PRODUCTION FIX           NONE_REQUIRED
```

## 2. 实验身份

| 项目 | 冻结事实 |
|---|---|
| Candidate commit | `de55c50`，其后的dirty scope仅包含trial POM/config/runner |
| Branch | `develop` |
| JDK | Amazon Corretto `1.8.0_502`，Java 8 |
| Maven | `3.9.16` |
| PMD | Maven PMD Plugin `3.28.0` + PMD Core/Java `7.26.0` |
| JaCoCo | Maven Plugin/Agent `0.8.15` |
| Scope | `soma-runtime`、`soma-processor` production source；tests仅由Xlint/JaCoCo观察 |
| Reports | ignored `target/` output；manifest与SHA-256在实验期间验证，不作为交付artifact提交 |

工具事实于2026-08-13按官方资料和实际Java 8执行复核：PMD plugin 3.28.0与PMD 7.x需要Java 8，
PMD采用BSD-style license，Maven plugin采用Apache-2.0；JaCoCo 0.8.15支持当前Java 8 bytecode并采用
EPL-2.0。OSV对三个精确Maven坐标/版本的查询在该时点均返回空结果。该查询只表示当时数据库
没有匹配项，不构成永久无漏洞声明。

## 3. Q0 工具与集成可行性

### 3.1 精确规则

PMD baseline使用16条逐项allowlist规则，来自`errorprone`、`bestpractices`和`multithreading`。
dry-run发现三条初始候选规则已被PMD标为待移除；它们在baseline冻结前按设计允许的唯一一次
规则调整删除。未启用category、CPD、style、design或复杂度阈值。

### 3.2 JaCoCo与processor JVM

第一次profile-only smoke证明：若late property没有profile-local空默认值，Surefire会把
`@{soma.jacoco.argLine}`作为literal JVM参数并失败。trial配置随后加入空默认值；复验同时证明：

- profile激活但agent未注入时，两module测试正常启动；
- agent注入时，runtime与processor分别生成非空`jacoco.exec`和XML report；
- processor最终JVM参数同时保留`tools.jar` bootclasspath；
- forked `javac`、independent consumer和qualification子进程不被Surefire agent观察。

这是工具集成成本，不是SOMA production缺陷。

### 3.3 普通构建与artifact边界

- profile关闭的`./scripts/check.sh`与完整non-publishing qualification通过；
- profile关闭/打开执行相同package后，runtime/processor的binary、source与javadoc六项JAR
  SHA-256逐项相同；
- 两module runtime dependency tree在profile关闭/打开时逐项相同；
- PMD、JaCoCo只属于temporary Maven plugin surface，没有进入production dependency或artifact。

结论：H3成立；工具可以可信运行，但需要中等POM/runner集成成本。

## 4. Q1 唯一 baseline

### 4.1 运行成本

| 工具 | Wall time | User CPU | System CPU |
|---|---:|---:|---:|
| Xlint + tests | 12.42 s | 47.56 s | 3.41 s |
| PMD | 2.84 s | 15.87 s | 0.92 s |
| JaCoCo + tests/report | 12.73 s | 51.90 s | 3.38 s |
| 合计 | 27.99 s | 115.33 s | 7.71 s |

机器时间很低；主要成本来自Candidate设计、POM/agent集成、规则裁决与信号解释。Goal在Q2开始时
报告`260272` tokens used；该数字包含本Goal至该时点的模型输入、输出和工具上下文，不能按工具
精确拆分，也不能与未来不同宿主直接比较。

### 4.2 Xlint

`-Xlint:all`产生200条warning：

- 199条为`auxiliaryclass`，其中production/test分别约100/99条；
- 1条为test source中的冗余`(int)`cast；
- 没有新的unchecked、resource、serialization或编译正确性缺陷family。

199条warning来自若干source file内共同放置强相关package-private IR/operator helper，再由同package
其他class访问。它是一个可读性/增量编译调查线索，但当前Maven全source编译、Java 8 consumer、
full-regeneration和package资格均已覆盖其真实使用方式。为消除warning拆分大量内部文件不会增加
产品正确性，反而制造高churn。分类为一个`USEFUL_LEAD / LOW_VALUE` family，而不是199个缺陷。

冗余cast属于`LOW_VALUE / TEST_ONLY`，不值得为一次报告数字修改test。

### 4.3 PMD

runtime和processor在16条冻结规则下均为0 violation。工具、ruleset和两module source读取成功，
不存在报告缺失。它没有提供独立新增Evidence。

### 4.4 JaCoCo

| Module | Line covered / total | Branch covered / total |
|---|---:|---:|
| runtime | `7403 / 11058`（约66.9%） | `3487 / 6815`（约51.2%） |
| processor | `2609 / 2713`（约96.2%） | `770 / 943`（约81.7%） |

这些数字只描述instrumented Surefire JVM。runtime缺失行主要分布于operation variant、generated
carrier、representation与failure分支；其中相当部分由forked qualification、independent consumer、
reference differential、profile和scenario拥有。增加测试以提高统一百分比会复制Evidence或冻结
private shape。报告没有证明新的产品缺陷。

## 5. Q2 采用裁决

| 工具 | Unique value | Precision | Integration / maintenance | Decision |
|---|---|---|---|---|
| Xlint all | 1个低价值组织线索，0个高/中价值缺陷 | 199/200为同源噪声family | 命中随internal source organization放大 | Reject routine |
| PMD allowlist | 0 | 无finding，无法证明增量收益 | 版本、规则弃用和dependency tree需持续维护 | Reject routine |
| JaCoCo | test-JVM路径地图，但无缺陷 | 数字可靠、claim范围窄 | agent/POM/fork边界需要维护，易诱发coverage gaming | On-demand only |

H1、H2、H4未得到支持；H3成立；H5通过拒绝机械拆分、coverage补数和报告驱动修改得到保护。

本次没有production source或test修复。原因不是“warning必须保留”，而是没有finding达到既有
Design内高置信、具有真实consumer影响且收益超过churn的实施标准。

## 6. Temporary与trial closure

实验结束后已删除：

- `build-support/quality/` temporary runner与ruleset；
- 两module的default-off quality profile；
- Candidate Temporary章程与设计。

没有创建`scripts/quality.sh`、CI、quality gate、coverage threshold、suppression registry、Skill、
新module或production dependency。稳定事实仅由本Conformance记录拥有。

## 7. Claim boundary

本记录证明：在冻结candidate与当前source上，一次有界Xlint/PMD/JaCoCo实验已经可信执行、完整
裁决并关闭，日常采用缺少净收益。

它不证明：

- SOMA没有任何缺陷；
- 未来版本或不同ruleset仍然没有价值；
- 66.9%/96.2%是质量分数或coverage Gate；
- 本次实验覆盖forked compiler、consumer、Shell/Python、Examples或benchmark；
- qualification授权release或对外质量/性能声明。

需要具体工具回答具体问题时，后续Slice可以重新准入一次性诊断；不得从本记录恢复已删除的
trial配置作为第二套长期流程。
