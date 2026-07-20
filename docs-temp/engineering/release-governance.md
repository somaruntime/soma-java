# Release 治理

类型：Engineering

状态：候选

Owner：SOMA Java release 过程

事实范围：release candidate 的证据类别、授权边界和声明规则

非事实范围：当前 readiness、真实账户/联系人和产品语义

最后审查日期：2026-07-19

## 1. Release 与功能完成分离

功能、性能和 package mechanics 通过，不等于可以公开发布。Release candidate 必须来自 clean immutable commit，并在同一 candidate 上重放获批 Gate。

## 2. 必需证据类别

- 版本、coordinates、SCM/project/issue metadata；
- LICENSE/NOTICE 与最终授权；
- maintainer/support/private-security contact 和 ownership policy；
- source/binary/javadoc package、checksum、reproducibility；
- dependency/SBOM/vulnerability/license scan；
- external consumer；
- approved JDK/OS/architecture support matrix；
- signing/OIDC、publishing account/endpoint 和 provenance；
- clean public history 与 release sign-off。

本地 placeholder、dirty package 或同一机器两个 JDK vendor 不替代真实 Owner 和支持矩阵。

## 3. 授权

Push、tag、publish、签名和公开 readiness 声明属于外部状态变更，必须得到明确授权。缺少真实发布事实时状态保持 blocked，不创建虚假 URL、联系人、账户或 approval。

## 4. 输出

Release Report 必须区分：当前已实现能力、本机 mechanics、已验证支持、未满足前置条件和禁止声明。正式支持只能来自已批准且重放通过的 matrix。
