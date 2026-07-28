# Java-only SOMA V1 support matrix report

类型：Report / Support Matrix

状态：blocked（Zulu-only vendor边界已裁决，精确version/OS/architecture与正式支持承诺未批准）

Owner：SOMA Java G6 support matrix

受众：SOMA maintainer、release owner 与 platform support reviewer

适用版本：`0.2.0-SNAPSHOT`

输入事实源：实际 Zulu JDK 8 validation metadata 与 release governance

事实范围：observed local validation 与尚未批准的正式支持边界

最后审查日期：2026-07-28

Gate：G6
首次记录日期：2026-07-11

## 1. Observed local validation

| Compiler/runtime | Vendor build | Maven | OS/architecture | 结果 |
|---|---|---|---|---|
| full JDK 8 `javac 1.8.0_492` | Azul Zulu 8.94.0.17, OpenJDK `1.8.0_492-b09`, VM `25.492-b09` | Wrapper 3.9.16 | macOS 26.5.2 / Darwin 25.5.0, arm64/aarch64 | full `./scripts/check.sh`: passed；post-fix benchmark: passed |

Zulu完整命令：

```text
./scripts/check.sh
./scripts/check-benchmark-smoke.sh
```

## 2. 历史非当前 evidence

2026-07-11曾在Amazon Corretto 8.492.09.2上完成旧候选验证。该事实由当时的G5/Phase 6报告保留，只证明历史候选曾运行，不构成当前候选支持或后续重放要求。

## 3. Claim boundary

当前目标JDK distribution只包括Azul Zulu。上述结果只证明记录版本的Zulu在同一台macOS arm64机器通过；Windows、Linux、x86_64、其他arm64 OS、其他Zulu update、其他JDK vendor、JDK 9+、ECJ、IDE compiler和非Maven consumer均未进入正式支持矩阵。

正式发布主体尚未批准精确Zulu version/build、OS与architecture组合，也没有目标环境验证基础设施，因此G6 support matrix保持`blocked`。这里的关闭条件不是增加其他JDK vendor；未列组合均为unsupported/untested，不能称为理论支持。

## 4. V1 scope non-regression

支持矩阵仍是`V1-RELEASE-EVIDENCE`和G6 required evidence，没有用本机smoke替代。Zulu-only是明确的vendor产品边界，不是要求多vendor后再打折；后续只验证获批的Zulu version/build与目标OS/architecture，不改变public/generated API、runtime hot path或consumer contract。
