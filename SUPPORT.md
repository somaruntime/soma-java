# Support and qualification matrix

SOMA 当前是尚未发布的 V1 implementation candidate，不提供 production SLA，也没有公开 Maven
坐标或兼容性承诺。下表描述本仓库 qualification 的边界，不等同于正式 release support。

| 维度 | 当前 qualification baseline |
|---|---|
| Source / bytecode | Java 8 source，class major version 52 |
| Build | Java 8 JDK；Maven 3.9.x |
| Runtime model | 单 JVM、on-heap、单 Group 外部串行、显式 bounded parallel |
| Production artifacts | `soma-runtime`、`soma-processor`，恰好两项 |
| Local scale evidence | Narrow、Medium、Reference-mixed 百万行 workload；16 core / 32 GB envelope 内 |
| Persistence / distributed | 不支持 |
| Off-heap / mmap / spill | V1 不支持；架构保留后续内部扩展空间 |
| Remote publication | 未授权、未执行 |

Java 9+ JVM、其他 GC、其他 OS/architecture 和更大规模可能能够工作，但在取得对应证据前不作为
当前兼容性声明。安全报告方式见 [SECURITY.md](SECURITY.md)。
