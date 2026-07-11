# Java-only SOMA V1 support matrix report

状态：blocked（observed local validation 已记录，正式支持承诺未批准）
日期：2026-07-11
Owner：root
Gate：G6

## 1. Observed local validation

| Compiler/runtime | Vendor build | Maven | OS/architecture | 结果 |
|---|---|---|---|---|
| full JDK 8 `javac 1.8.0_492` | Azul Zulu 8.94.0.17, OpenJDK `1.8.0_492-b09`, VM `25.492-b09` | Wrapper 3.9.16 | macOS 26.5.2 / Darwin 25.5.0, arm64/aarch64 | full `./scripts/check.sh`: passed；post-fix benchmark: passed |
| full JDK 8 `javac 1.8.0_492` | Amazon Corretto 8.492.09.2, OpenJDK `1.8.0_492-b09`, VM `25.492-b09` | Wrapper 3.9.16 | macOS 26.5.2 / Darwin 25.5.0, arm64/aarch64 | full `./scripts/check.sh`: passed；post-fix benchmark: passed |

Corretto完整命令：

```text
env JAVA_HOME=/tmp/corretto8-soma/Contents/Home PATH=/tmp/corretto8-soma/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin ./scripts/check.sh
env JAVA_HOME=/tmp/corretto8-soma/Contents/Home PATH=/tmp/corretto8-soma/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin ./scripts/check-benchmark-smoke.sh
```

Zulu完整命令：

```text
./scripts/check.sh
./scripts/check-benchmark-smoke.sh
```

## 2. Claim boundary

上述结果只证明同一台macOS arm64机器、同一JDK 8 update/build的两个vendor distribution。Windows、Linux、x86_64、其他arm64 OS、其他JDK 8 update/vendor、JDK 9+、ECJ、IDE compiler和非Maven consumer均未进入正式支持矩阵。

正式发布主体尚未批准支持范围，也没有跨目标环境验证基础设施，因此G6 support matrix保持`blocked`。未列组合是unsupported/untested，不能称为理论支持。

## 3. V1 scope non-regression

支持矩阵仍是`V1-RELEASE-EVIDENCE`和G6 required evidence，没有从V1删除、降级或用本机smoke替代。后续是additive environment validation，不改变public/generated API、runtime hot path或consumer contract。
