# Java-only SOMA V1 support matrix report

类型：Report / Support Matrix

状态：passed for selected private-source build/contract profile；performance受限

Owner：SOMA Java G6 support matrix

受众：SOMA maintainer、private repository consumer 与 platform support reviewer

适用版本：`0.2.0-SNAPSHOT`

输入事实源：exact Zulu JDK 8 本机验证与 GitHub Actions evidence

事实范围：private-source checkout、build、compiler/generated/runtime contract 与
记录环境的性能证据

非事实范围：public/Maven release、production SLA 或未列平台

最后审查日期：2026-07-28

Gate：G6 selected `private-github-source`

## 1. Selected matrix

唯一 JDK authority 是 Azul Zulu full JDK 8：

- Zulu 8.94.0.17；
- OpenJDK runtime `1.8.0_492-b09`，VM `25.492-b09`；
- full `javac 1.8.0_492`；
- Maven Wrapper / Apache Maven 3.9.16。

| OS / architecture | Build与contract support | 性能状态 | 直接 evidence |
|---|---|---|---|
| macOS 26.5.2 / Darwin 25.5.0, arm64/aarch64 | passed | 记录的component、application与runtime-scale profile passed | 本机完整`./scripts/check.sh`、既有十lane qualification与三个Example baseline |
| Ubuntu 24.04, Linux x86_64/amd64 | passed | macOS baseline `not-applicable`；无Linux性能claim | GitHub Actions exact Zulu full check、clean workspace与manual private-source qualification |
Codex Cloud实验没有完成fresh-container full check，当前为
`not-selected / not-ready`，不进入支持矩阵。GitHub hosted CI证明记录的Linux
build/compiler/generated/runtime contract，不把macOS阈值外推到Linux。

## 2. 支持含义

`passed`表示有权限的private repository consumer可以在上述精确组合上：

- 使用Maven Wrapper构建全部production module；
- 运行Zulu 8 javac plugin/processor并生成Java 8 classfile；
- 执行public/generated contract、external Maven consumer和三个reference
  application Gate。

它不表示snapshot artifact已公开发布，不提供production SLA，也不承诺任意schema、
String profile、100M workload或Linux性能。

## 3. 未列环境

Windows、其他macOS/Linux版本、其他architecture、其他Zulu update、其他JDK
vendor、JDK 9+、ECJ、IDE内置compiler和非Maven build均为
`unsupported/untested`。早期Corretto结果只属于历史候选，不进入当前支持矩阵，
也不要求为了“多vendor”重放。

扩大矩阵必须先选择真实目标环境，再在同一immutable candidate上执行适用Gate；
不能用理论兼容或`--release 8`替代Zulu 8 javac authority。

## 4. Scope non-regression

本矩阵只新增Linux build/contract证据，没有重跑或降低Small/Medium、
single/double100M、String、Metadata、parallel、Result Delivery和三个Example的
产品目标。重型性能仍由预注册、高内存、人工监管qualification拥有。
