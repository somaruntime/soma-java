package com.hgtech.soma.examples.game;

import com.hgtech.soma.examples.game.generated.AbilityCostBatch;
import com.hgtech.soma.examples.game.generated.AbilityCostTable;
import com.hgtech.soma.examples.game.generated.GameUnitBatch;
import com.hgtech.soma.examples.game.generated.GameUnitTable;
import com.hgtech.soma.examples.game.generated.MapTileRowBatch;
import com.hgtech.soma.examples.game.generated.MapTileRowTable;
import com.hgtech.soma.examples.game.generated.MoveCandidateRowBatch;
import com.hgtech.soma.examples.game.generated.MoveCandidateRowTable;
import com.hgtech.soma.examples.game.generated.PendingDamageRowBatch;
import com.hgtech.soma.examples.game.generated.PendingDamageRowTable;
import com.hgtech.soma.examples.game.generated.PlayerBatch;
import com.hgtech.soma.examples.game.generated.PlayerTable;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.UpdateResult;

/** Unit.position为事实源、occupancy为可重建cache的正式game-loop场景。 */
public final class GameScenario {
    private GameScenario() { }

    public static ScenarioResult run() {
        PlayerId player = new PlayerId(1L);
        PlayerId opponent = new PlayerId(2L);
        UnitId actor = new UnitId(10L);
        UnitId target = new UnitId(20L);
        UnitClassId soldier = new UnitClassId(100L);
        GridPosition origin = new GridPosition(0, 0);
        GridPosition destination = new GridPosition(1, 0);

        PlayerTable players = PlayerTable.create();
        GameUnitTable units = GameUnitTable.create();
        AbilityCostTable costs = AbilityCostTable.create();
        MapTileRowTable map = MapTileRowTable.create();
        MoveCandidateRowTable moves = MoveCandidateRowTable.create();
        PendingDamageRowTable damage = PendingDamageRowTable.create();
        try {
            players.addBatch(new PlayerBatch(2)
                    .addValues(player, 1, 0L).addValues(opponent, 2, 0L));
            units.addBatch(new GameUnitBatch(2)
                    .addValues(actor, player, soldier, origin, 10, 3, 1,
                            UnitState.READY, false, null)
                    .addValues(target, opponent, soldier, new GridPosition(2, 0),
                            8, 2, 2, UnitState.READY, false, null));
            costs.addBatch(new AbilityCostBatch(2)
                    .addValues(new UnitAbilityKey(soldier, AbilityId.MOVE), 1, 1, 0)
                    .addValues(new UnitAbilityKey(soldier, AbilityId.ATTACK), 2, 2, 3));
            map.addBatch(new MapTileRowBatch(3)
                    .addValues(origin, TerrainType.PLAIN, 1, false, true, actor)
                    .addValues(destination, TerrainType.FOREST, 2, false, false, null)
                    .addValues(new GridPosition(2, 0), TerrainType.PLAIN,
                            1, false, true, target));

            IndexSnapshot nextRows = units.rows().sorted((left, right) -> {
                int compared = Integer.compare(left.initiative(), right.initiative());
                return compared != 0 ? compared
                        : Long.compare(left.unitIdValue(), right.unitIdValue());
            }).limit(1).rowIndexes();
            LongColumnView unitIds = units.unitIdValueColumn();
            try {
                require(nextRows.size() == 1
                                && unitIds.getLong(nextRows.indexAt(0)) == actor.value
                                && units.findByPlayer(player).count() == 1L,
                    "ordered and grouped unit access");
            } finally {
                unitIds.close();
            }
            int moveCostRow = costs.rowIndexOf(soldier.value, AbilityId.MOVE);
            IntColumnView actionPointCosts = costs.actionPointCostColumn();
            int moveCost;
            try {
                moveCost = actionPointCosts.getInt(moveCostRow);
            } finally {
                actionPointCosts.close();
            }
            moves.replaceAll(new MoveCandidateRowBatch(2)
                    .addValues(actor, destination, moveCost + 1, 2)
                    .addValues(actor, new GridPosition(0, 1), moveCost + 3, 1));
            IndexSnapshot selectedRows = moves.rows().sorted((left, right) -> {
                int compared = Integer.compare(left.totalCost(), right.totalCost());
                if (compared != 0) return compared;
                compared = Integer.compare(left.positionYValue(), right.positionYValue());
                return compared != 0 ? compared
                        : Integer.compare(left.positionXValue(), right.positionXValue());
            }).limit(1).rowIndexes();
            require(selectedRows.size() == 1, "move order requires one candidate");
            int selectedRow = selectedRows.indexAt(0);
            IntColumnView moveX = moves.positionXValueColumn();
            IntColumnView moveY = moves.positionYValueColumn();
            IntColumnView remainingPoints = moves.remainingActionPointsColumn();
            GridPosition selectedPosition;
            int selectedActionPoints;
            try {
                selectedPosition = new GridPosition(
                        moveX.getInt(selectedRow), moveY.getInt(selectedRow));
                selectedActionPoints = remainingPoints.getInt(selectedRow);
            } finally {
                remainingPoints.close();
                moveY.close();
                moveX.close();
            }
            require(selectedPosition.equals(destination),
                    "selected-unit workspace chooses the maintained minimum cost");

            units.mutate(actor).setPosition(selectedPosition)
                    .setActionPoints(selectedActionPoints)
                    .setState(UnitState.MOVED).commit();
            // Unit.position先提交，occupancy cache随后同步；失败时由game loop重建cache。
            UpdateResult occupancyUpdate = map.update(row -> {
                if (row.positionXValue() == origin.x && row.positionYValue() == origin.y) {
                    row.clearOccupantUnit();
                }
                if (row.positionXValue() == destination.x
                        && row.positionYValue() == destination.y) {
                    row.setOccupantUnit(actor);
                }
            });
            int actorRow = units.rowIndexOf(actor.value);
            IntColumnView unitX = units.positionXValueColumn();
            IntColumnView unitY = units.positionYValueColumn();
            try {
                require(unitX.getInt(actorRow) == destination.x
                                && unitY.getInt(actorRow) == destination.y,
                        "unit position remains authoritative");
            } finally {
                unitY.close();
                unitX.close();
            }
            require(map.filter(row -> row.positionXValue() == destination.x
                            && row.positionYValue() == destination.y
                            && row.occupantUnitPresent()
                            && row.occupantUnitValue() == actor.value).count() == 1L,
                    "occupancy cache follows the authoritative move");

            int attackCostRow = costs.rowIndexOf(soldier.value, AbilityId.ATTACK);
            IntColumnView baseDamage = costs.baseDamageColumn();
            int attackDamage;
            try {
                attackDamage = baseDamage.getInt(attackCostRow);
            } finally {
                baseDamage.close();
            }
            damage.addBatch(new PendingDamageRowBatch(1)
                    .addValues(1L, actor, target, attackDamage));
            IndexSnapshot pendingRows = damage.rows().sorted((left, right) -> {
                int compared = Long.compare(left.resolutionOrder(), right.resolutionOrder());
                return compared != 0 ? compared
                        : Long.compare(left.targetUnitValue(), right.targetUnitValue());
            }).limit(1).rowIndexes();
            require(pendingRows.size() == 1, "damage order requires one event");
            int pendingRow = pendingRows.indexAt(0);
            LongColumnView damageTargets = damage.targetUnitValueColumn();
            IntColumnView damageValues = damage.damageColumn();
            UnitId pendingTarget;
            int pendingDamage;
            try {
                pendingTarget = new UnitId(damageTargets.getLong(pendingRow));
                pendingDamage = damageValues.getInt(pendingRow);
            } finally {
                damageValues.close();
                damageTargets.close();
            }
            int targetRow = units.rowIndexOf(pendingTarget.value);
            IntColumnView hitPoints = units.hpColumn();
            EnumColumnView<UnitState> states = units.stateColumn();
            int remaining;
            UnitState beforeState;
            try {
                remaining = hitPoints.getInt(targetRow) - pendingDamage;
                beforeState = states.get(targetRow);
            } finally {
                states.close();
                hitPoints.close();
            }
            units.mutate(pendingTarget).setHp(remaining)
                    .setState(remaining <= 0 ? UnitState.DEAD : beforeState).commit();
            players.mutate(player).setScore(3L).commit();
            damage.clear();
            IntColumnView finalHp = units.hpColumn();
            LongColumnView scores = players.scoreColumn();
            try {
                require(finalHp.getInt(units.rowIndexOf(target.value)) == 5
                                && damage.size() == 0
                                && scores.getLong(players.rowIndexOf(player.value)) == 3L,
                        "pending damage is a phase buffer, not history storage");
            } finally {
                scores.close();
                finalHp.close();
            }

            int exportedUnits = units.materialize().size();
            long hotLeafWorkingSetBytes = (long) units.capacity() * 12L
                    + (long) map.capacity() * 8L
                    + (long) moves.capacity() * 4L
                    + (long) damage.capacity() * 8L;
            return new ScenarioResult(exportedUnits,
                    units.runtimePlan().schemaHash(), units.statsSnapshot().exactIndexProbeCount(),
                    units.size() + map.size() + moves.size() + damage.size(), 32,
                    hotLeafWorkingSetBytes,
                    occupancyUpdate.scanned() + exportedUnits,
                    occupancyUpdate.changed(), exportedUnits);
        } finally {
            damage.release();
            moves.release();
            map.release();
            costs.release();
            units.release();
            players.release();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class ScenarioResult {
        public final int units;
        public final String schemaHash;
        public final long exactIndexProbeCount;
        public final int apcRows;
        public final int aggregateHotLeafWidths;
        public final long hotLeafWorkingSetBytes;
        public final long reads;
        public final long mutations;
        public final int exports;
        ScenarioResult(int units, String schemaHash, long exactIndexProbeCount,
                       int apcRows, int aggregateHotLeafWidths,
                       long hotLeafWorkingSetBytes, long reads, long mutations,
                       int exports) {
            this.units = units;
            this.schemaHash = schemaHash;
            this.exactIndexProbeCount = exactIndexProbeCount;
            this.apcRows = apcRows;
            this.aggregateHotLeafWidths = aggregateHotLeafWidths;
            this.hotLeafWorkingSetBytes = hotLeafWorkingSetBytes;
            this.reads = reads;
            this.mutations = mutations;
            this.exports = exports;
        }
    }
}
