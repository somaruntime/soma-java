# I8 Boundary Qualification

状态：`I8_BOUNDARY_PASS`；I8 产品资格与 G9/G10 尚未完成。

本次只关闭一个可独立复现的 I8 子范围：在 I7 regression 基础上，对当前两个 production
artifact 做 Java 8 package smoke、LICENSE/NOTICE/SBOM/provenance、public javadoc 的 internal
边界、exactly-two production modules、无 tracked build/legacy surface、reflective entry-point 与
privileged runtime 静态边界（含 singular/plural reflective entry-point negative fixture；仅允许
mapped-array 的 `java.lang.reflect.Array` reification）与
双次 clean package reproducibility 检查。

## Evidence

`./scripts/check-i8-boundary.sh` PASS。

这证明当前 source/repository/package baseline 没有越过 delivery boundary；不证明 SOMA 已经
完成三条真实案例、百万行性能资格、压缩、Join 或完整 G9/G10。

## Remaining I8 work

- 调度、仿真、实时派工三个 public API scenario correctness；
- million-row profile、allocation/GC/managed-memory 与 baseline comparison；
- compression/Join 参与的 runtime qualification；
-完整 G9/G10、selected delivery profile、CI/release workflow 与 Owner sign-off。

GitHub Release、Package、签名和正式发布仍需要独立授权。

正式 Blueprint/Design 未修改。
