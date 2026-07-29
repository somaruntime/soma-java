# Java-only SOMA V1 support matrix report

类型：Report / Support Matrix

状态：macOS/Linux build/contract passed；selected private-source release blocked

Owner：SOMA Java G6 support matrix

受众：SOMA maintainer、private repository consumer与platform support reviewer

适用版本：`0.2.0-SNAPSHOT`

输入事实源：exact Corretto JDK 8本机验证、GitHub Actions run
`30410355517`与历史Zulu evidence

事实范围：当前JDK authority、private-source checkout/build/contract与记录环境性能

非事实范围：public/Maven release、production SLA或未列平台

最后审查日期：2026-07-29

Gate：G6 selected `private-github-source`

## 1. JDK authority

唯一compiler与validation authority是Amazon Corretto full JDK 8：

- Corretto 8.502.07.1；
- OpenJDK runtime `1.8.0_502-b07`，VM `25.502-b07`；
- full `javac 1.8.0_502`；
- Maven Wrapper / Apache Maven 3.9.16。

Root Maven Enforcer验证Java 8与vendor；`scripts/lib/supported-jdk.sh`进一步验证
java/runtime/javac/javap精确版本。旧Zulu和其他distribution均不属于当前支持
矩阵；Zulu只用于迁移时的negative probe与历史evidence归因。

## 2. Selected matrix

| OS / architecture | Build与contract | 性能/规模 | 当前证据 |
|---|---|---|---|
| macOS 26.5.2 / Darwin 25.5.0, arm64/aarch64 | passed | Access/DataFlow component与九application profile passed；runtime-scale blocked | 本机Corretto Full、component baseline、application 5-fork calibration与3-fork回放 |
| Ubuntu 24.04, Linux x86_64/amd64 | passed | 无Linux性能claim；runtime-scale blocked | SHA-256校验的Corretto 8.502.07.1 archive经`jdkfile`安装；commit `dddf62b`的CI Full run `30410355517`在7分46秒通过 |

2026-07-28的Zulu/macOS runtime-scale和Zulu/Linux CI仍是历史candidate事实，但
不能进入当前Corretto passed矩阵。Codex Cloud为development
`candidate / qualification-blocked`，不进入支持矩阵。

## 3. 支持含义

当前build/contract `passed`表示在上述精确组合上可以：

- 使用Maven Wrapper构建全部production module；
- 运行Corretto 8 javac plugin/processor并生成Java 8 classfile；
- 执行public/generated contract、external Maven consumer、runtime/DataFlow、
  component与三个reference application Gate。macOS行还拥有表中单独列出的
  component/application性能evidence；Linux没有。

它不表示snapshot artifact已公开发布，不提供production SLA，也不承诺当前
Corretto下的100M qualification、任意schema/String profile或Linux性能/规模。

## 4. 未列环境

Windows、其他macOS/Linux版本、其他architecture、其他Corretto update、其他JDK
vendor、JDK 9+、ECJ、IDE内置compiler和非Maven build均为
`unsupported/untested`。扩大矩阵必须选择真实目标，在同一immutable candidate上
执行适用Gate；不能用理论兼容、旧vendor结果或`--release 8`替代Corretto 8
javac authority。

## 5. 关闭条件与scope non-regression

Selected private-source release只有在同一最终clean commit完成manual package/
security qualification、checksum与sign-off后才能关闭。macOS/Linux runtime-scale
只有在原十lane qualification按相同schema/validator/预算于各自选择环境重放后
才能改为passed。

本矩阵没有降低Small/Medium、single/double100M、String、Metadata、parallel、
Result Delivery和三个Example目标；重型性能继续由预注册、高内存、人工监管
qualification拥有。
