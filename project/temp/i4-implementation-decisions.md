# I4 implementation decisions (temporary)

状态：ACTIVE DURING I4；只记录本次 bounded mutation slice，不改变正式 Blueprint/Design。

## Scope

本 slice 交付 point remove：generated Table 的 typed remove(Key)、RemoveResult、missing normal
zero、paged directory compaction、single candidate StateRoot publication 与 version invariant。
I4 也复用 I2/I3 的 point update no-op、callback failure、checked arithmetic 与 scope evidence。

## Deliberate boundary

Selection update/remove、global retained/temporary memory manager、PhantomReference accounting、
fault-injection matrix 与 large candidate/journal strategy 尚未具备完整证据，因此本 slice 不生成
Selection mutation terminal，也不把 I4 报告升级为完整 G5。删除不隐式 shrink capacity；被删行之后
的 logical rows 保持 canonical packed order。

Point remove 的当前 reference implementation 通过 candidate directory 重建并在最终 CAS 前完成
所有可恢复 work；它优先证明 zero publication 与 order，而不是宣称百万行 remove 性能。

## Evidence

./scripts/check-i4.sh 在 Java 8 independent consumer、I3 query regression 和 direct runtime probe
下通过。PASS 只代表 point-remove bounded slice。
