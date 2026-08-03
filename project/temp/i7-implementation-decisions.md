# I7 implementation decisions (temporary)

状态：ACTIVE DURING I7；本文件只记录 bounded slice 的实施边界，不修改正式 Blueprint/Design。

## Scope

本 slice 固定四个 immutable carrier 的第一版可验证 topology：SomaMetadata、
GroupMetadata、TableMetadata、FieldMetadata，并在 I2 generated consumer 中生成
Soma._metadata()、SomaGroup._metadata()、Table._metadata() 与每个 direct Field
_metadata()。

_metadata() 读取当前 immutable StateRoot/configuration projection，不创建 default Group、不
freeze configuration（已冻结运行中的普通 metadata 仍只观察），并返回 detached carrier。未冻结
时 SomaMetadata 使用明确的 `UNFROZEN / -1 / null` sentinel（状态、budget、compression），后续
仍可正常 configure。Table
统计以 checked plain-equivalent byte estimate 表达当前 logical payload 与已分配 capacity；
ordinary reference body 不计入。Field 只暴露 logical path/type/nullability/key/index role。

当前 physical representation 仍是 PLAIN，encodedRepresentation() 恒为 false。AUTO/OFF 的
有效策略可以被观察，但本 slice 不宣称已经实现 codec、encoded kernel、overlay、rebuild、
compression-aware admission 或完整 _explain()。这保留 Chunk representation seam，同时避免把
配置枚举误报成压缩能力。

## Evidence

./scripts/check-i7.sh 重放 Java 8 generated surface、Soma/Group/Table/Field carrier、默认组身份、
size/capacity/version、plain byte estimate、field role 与 I1-I6 regression。PASS 只代表 metadata/
plain baseline scope，不代表完整 G8。
