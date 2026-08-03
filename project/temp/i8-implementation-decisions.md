# I8 implementation decisions (temporary supplemental note)

状态：I8 boundary sub-slice closed；I8 product qualification remains open。

I8 不因为已有 I1-I7 fixture 就伪造三场景、百万行性能或完整 G9/G10。当前只准入可观察的
package/security boundary evidence：两个 production artifact、Java 8 manifest、license/NOTICE、
SBOM/provenance、javadoc internal exclusion、two-module topology、静态 trust-boundary scan 与
可复现 jar。真实案例需要 Join、完整 relation execution、compression 和 profile harness，只有
这些 capability 与 evidence 出现后才能继续。

该文件只记录本次 bounded implementation scope，不替代正式 Blueprint/Design 或 Conformance
current fact。
