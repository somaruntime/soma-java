# I3 implementation decisions (temporary)

状态：ACTIVE DURING I3；只记录本次 bounded query slice，不改变正式 Blueprint/Design。

## Scope

在 I2 scalar Table 上补齐一个可重放的 direct query/reference-interpreter slice：

- findFirst、anyMatch、allMatch、noneMatch；
- detached record toList() / typed record toArray()；
- reference map(Function) 的 modifiable toList() 与 toArray(Class)；
- mapToLong(ToLongFunction) 的 checked sum() 与 primitive toArray()；
- long sum 使用固定两个 long limb 的 signed-128 accumulator，只在最终结果超出 long 时失败；
- callback barrier、one-shot claim、query guard cleanup 与 borrowed View map escape failure。

本 slice 仍不宣称 sort/distinct/slice/window、完整四种 primitive mapper、typed optimizer、
parallel differential、Join/Group 或 metadata 已完成；I3 后续 owner 继续补齐这些能力。

## Execution boundary

这些 terminals 直接在绑定的 ScalarTableRuntime.Query snapshot 上按 canonical row order 执行，
因此它们是 I3 的顺序 reference interpreter，而不是把 callback 字节码交给 optimizer。映射结果
使用 detached Java containers；toArray(Class) 只使用 component type 做 JVM array reification。

## Evidence

./scripts/check-i3.sh 在 Java 8 下通过。PASS 只代表该 bounded I3 slice，不升级 G4 为完整 PASS。
