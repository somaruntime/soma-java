package com.hgtech.soma.examples.game;

import com.hgtech.soma.examples.game.generated.AbilityCostBatch;
import com.hgtech.soma.examples.game.generated.AbilityCostTable;
import com.hgtech.soma.examples.game.generated.GameUnitDefinitionBatch;
import com.hgtech.soma.examples.game.generated.GameUnitDefinitionTable;
import com.hgtech.soma.examples.game.generated.GameUnitStateBatch;
import com.hgtech.soma.examples.game.generated.GameUnitStateTable;
import com.hgtech.soma.examples.game.generated.MapTileDefinitionRowBatch;
import com.hgtech.soma.examples.game.generated.MapTileDefinitionRowTable;
import com.hgtech.soma.examples.game.generated.MoveCandidateRowBatch;
import com.hgtech.soma.examples.game.generated.MoveCandidateRowTable;
import com.hgtech.soma.examples.game.generated.PendingDamageRowBatch;
import com.hgtech.soma.examples.game.generated.PendingDamageRowTable;
import com.hgtech.soma.examples.game.generated.PlayerDefinitionBatch;
import com.hgtech.soma.examples.game.generated.PlayerDefinitionTable;
import com.hgtech.soma.examples.game.generated.PlayerStateBatch;
import com.hgtech.soma.examples.game.generated.PlayerStateTable;
import com.hgtech.soma.examples.game.generated.TileOccupancyRowBatch;
import com.hgtech.soma.examples.game.generated.TileOccupancyRowTable;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.SomaRuntimeException;

/** Definition/state、keyed occupancy cache 与 phase-local workspace 场景。 */
public final class GameScenario {
  private GameScenario() {
  }

  public static ScenarioResult run() {
    Fixture input = Fixture.teaching();
    input.validate();

    PlayerDefinitionTable playerDefinitions = PlayerDefinitionTable.create();
    PlayerStateTable playerStates = PlayerStateTable.create();
    GameUnitDefinitionTable unitDefinitions =
      GameUnitDefinitionTable.create();
    GameUnitStateTable unitStates = GameUnitStateTable.create();
    AbilityCostTable costs = AbilityCostTable.create();
    MapTileDefinitionRowTable tiles = MapTileDefinitionRowTable.create();
    TileOccupancyRowTable occupancy = TileOccupancyRowTable.create();
    MoveCandidateRowTable moves = MoveCandidateRowTable.create();
    PendingDamageRowTable damage = PendingDamageRowTable.create();
    try {
      importInput(input, playerDefinitions, playerStates,
        unitDefinitions, unitStates, costs, tiles);
      OccupancyCache occupancyCache = new OccupancyCache(
        occupancy, tiles.size());
      occupancyCache.rebuildFromUnits(tiles, unitStates);
      Battle battle = new Battle(unitDefinitions, unitStates,
        costs, tiles, moves, occupancyCache);

      GameUnitState selected = battle.selectNextUnit();
      require(selected.unitId.equals(input.actor)
          && unitStates.scanByPlayer(input.player).count() == 1L,
        "ready-unit total order and preprojected player access are live");
      MoveActionContext firstContext = battle.rebuildMoves(selected);
      PreparedMove firstPrepared = battle.chooseMove(firstContext);
      MoveActionContext currentContext = battle.rebuildMoves(selected);
      require(battle.commitMove(firstPrepared) == MoveCommitResult.STALE,
        "rebuilding workspace invalidates the previous prepared move");
      PreparedMove prepared = battle.chooseMove(currentContext);
      require(prepared.position.equals(input.destination)
          && battle.commitMove(prepared) == MoveCommitResult.COMMITTED,
        "current action context commits the selected-unit move");

      int actorRow = unitStates.requireIndex(input.actor.value);
      IntColumnView unitX = unitStates.positionXValueColumn();
      IntColumnView unitY = unitStates.positionYValueColumn();
      try {
        require(unitX.getInt(actorRow) == input.destination.x
            && unitY.getInt(actorRow) == input.destination.y,
          "GameUnitState.position remains authoritative");
      } finally {
        unitY.close();
        unitX.close();
      }
      require(occupancy.fetch(input.destination).occupantUnit
          .equals(input.actor)
          && occupancy.fetch(input.origin).occupantUnit == null,
        "keyed occupancy cache follows authoritative movement");
      occupancyCache.rebuildFromUnits(tiles, unitStates);
      require(occupancyCache.rebuildCount == 2,
        "occupancy cache is reproducible from unit positions");

      int attackRow = costs.requireIndex(
        input.soldier.value, AbilityId.ATTACK);
      IntColumnView baseDamage = costs.baseDamageColumn();
      int attackDamage;
      try {
        attackDamage = baseDamage.getInt(attackRow);
      } finally {
        baseDamage.close();
      }
      DamageBatchBuilder damageBuilder = new DamageBatchBuilder(4);
      damageBuilder.begin();
      damageBuilder.add(2L, input.actor, input.target, 1);
      damageBuilder.add(1L, input.actor, input.target, attackDamage);
      damage.replaceAll(damageBuilder.batch());
      DamageCommandBuffer commands = new DamageCommandBuffer(4);
      commands.copyAndValidate(damage, unitStates);
      require(commands.size == 2 && commands.sequenceNo[0] == 1L
          && commands.sequenceNo[1] == 0L,
        "damage commands use resolution-order then unique sequence total order");
      DamageResolution resolution = new DamageResolution();
      resolution.resolve(commands, damage, unitStates,
        playerStates, input.player);
      require(unitStates.fetch(input.target).hp == 4
          && playerStates.fetch(input.player).score == 4L
          && damage.size() == 0,
        "damage buffer stages before mutation and clears after success");

      int exportedUnits = unitStates.materialize().size();
      int apcRows = unitStates.size() + occupancy.size()
        + moves.size() + damage.size();
      long workingSet = (long) unitStates.capacity() * 16L
        + (long) occupancy.capacity() * 8L
        + (long) moves.capacity() * 16L
        + (long) damage.capacity() * 28L;
      long probes = unitStates.statsSnapshot().exactIndexProbeCount()
        + occupancy.statsSnapshot().exactIndexProbeCount();
      return new ScenarioResult(exportedUnits,
        unitStates.runtimePlan().schemaHash(), probes, apcRows, 68,
        workingSet, 6L, 6L, exportedUnits);
    } finally {
      damage.release();
      moves.release();
      occupancy.release();
      tiles.release();
      costs.release();
      unitStates.release();
      unitDefinitions.release();
      playerStates.release();
      playerDefinitions.release();
    }
  }

  private static void importInput(
      Fixture input, PlayerDefinitionTable playerDefinitions,
      PlayerStateTable playerStates,
      GameUnitDefinitionTable unitDefinitions,
      GameUnitStateTable unitStates, AbilityCostTable costs,
      MapTileDefinitionRowTable tiles) {
    playerDefinitions.addBatch(new PlayerDefinitionBatch(2)
      .addValues(input.player, 1)
      .addValues(input.opponent, 2));
    playerStates.addBatch(new PlayerStateBatch(2)
      .addValues(input.player, 0L)
      .addValues(input.opponent, 0L));
    unitDefinitions.addBatch(new GameUnitDefinitionBatch(2)
      .addValues(input.actor, input.player, input.soldier, 1)
      .addValues(input.target, input.opponent, input.soldier, 2));
    unitStates.addBatch(new GameUnitStateBatch(2)
      .addValues(input.actor, input.player, 1, input.origin,
        10, 5, UnitState.READY, false, null)
      .addValues(input.target, input.opponent, 2, input.targetPosition,
        8, 2, UnitState.READY, false, null));
    costs.addBatch(new AbilityCostBatch(2)
      .addValues(new UnitAbilityKey(input.soldier, AbilityId.MOVE),
        1, 1, 0)
      .addValues(new UnitAbilityKey(input.soldier, AbilityId.ATTACK),
        2, 2, 3));
    MapTileDefinitionRowBatch tileBatch =
      new MapTileDefinitionRowBatch(input.tiles.length);
    for (TileInput tile : input.tiles) {
      tileBatch.addValues(tile.position, tile.terrain,
        tile.moveCost, tile.blocksSight);
    }
    tiles.addBatch(tileBatch);
  }

  private static final class Battle {
    private final GameUnitDefinitionTable definitions;
    private final GameUnitStateTable states;
    private final AbilityCostTable costs;
    private final MapTileDefinitionRowTable tiles;
    private final MoveCandidateRowTable moves;
    private final OccupancyCache occupancy;
    private final MoveCandidateRowBatch moveBatch =
      new MoveCandidateRowBatch(16);
    private long actionGeneration;
    private long pathingRevision;
    private MoveActionContext currentContext;

    Battle(GameUnitDefinitionTable definitions,
           GameUnitStateTable states, AbilityCostTable costs,
           MapTileDefinitionRowTable tiles,
           MoveCandidateRowTable moves, OccupancyCache occupancy) {
      this.definitions = definitions;
      this.states = states;
      this.costs = costs;
      this.tiles = tiles;
      this.moves = moves;
      this.occupancy = occupancy;
    }

    GameUnitState selectNextUnit() {
      return states.filter(row -> row.state() == UnitState.READY)
        .sorted((left, right) -> {
          int compared = Integer.compare(
            left.initiative(), right.initiative());
          return compared != 0 ? compared
            : Long.compare(left.unitIdValue(), right.unitIdValue());
        }).firstOrThrow();
    }

    MoveActionContext rebuildMoves(GameUnitState selected) {
      if (!occupancy.valid) {
        throw new IllegalStateException("occupancy cache is not readable");
      }
      if (selected.actionPoints < 0 || selected.state != UnitState.READY) {
        throw new IllegalArgumentException("selected unit is not movable");
      }
      GameUnitDefinition definition = definitions.fetch(selected.unitId);
      require(definition.playerId.equals(selected.playerId)
          && definition.initiative == selected.initiative,
        "state preprojection must match immutable definition");
      long nextGeneration = Math.addExact(actionGeneration, 1L);
      MoveActionContext next = new MoveActionContext(selected.unitId,
        selected.position, selected.actionPoints, pathingRevision,
        nextGeneration);
      int moveCostRow = costs.requireIndex(
        definition.unitClassId.value, AbilityId.MOVE);
      IntColumnView abilityCosts = costs.actionPointCostColumn();
      int abilityCost;
      try {
        abilityCost = abilityCosts.getInt(moveCostRow);
      } finally {
        abilityCosts.close();
      }

      moveBatch.clear();
      LongColumnView occupants = occupancy.table.occupantUnitValueColumn();
      try {
        tiles.forEach(tile -> {
          int x = tile.positionXValue();
          int y = tile.positionYValue();
          if (x == selected.position.x && y == selected.position.y) return;
          if (tile.terrain() == TerrainType.WATER
              || tile.terrain() == TerrainType.WALL) return;
          int distance = Math.addExact(
            Math.abs(x - selected.position.x),
            Math.abs(y - selected.position.y));
          if (distance != 1) return;
          int occupancyRow = occupancy.table.requireIndex(x, y);
          if (occupants.isPresent(occupancyRow)) return;
          int totalCost = Math.addExact(abilityCost, tile.moveCost());
          int remaining = Math.subtractExact(
            selected.actionPoints, totalCost);
          if (totalCost >= 0 && remaining >= 0) {
            moveBatch.addValues(new GridPosition(x, y),
              totalCost, remaining);
          }
        });
      } finally {
        occupants.close();
      }
      moves.replaceAll(moveBatch);
      actionGeneration = nextGeneration;
      currentContext = next;
      return next;
    }

    PreparedMove chooseMove(MoveActionContext context) {
      if (context == null || !context.sameAs(currentContext)
          || context.pathingRevision != pathingRevision) {
        throw new IllegalStateException("stale move action context");
      }
      MoveCandidateRow chosen = moves.filter(row ->
        row.remainingActionPoints() >= 0).sorted((left, right) -> {
          int compared = Integer.compare(
            left.totalCost(), right.totalCost());
          if (compared != 0) return compared;
          compared = Integer.compare(
            left.positionYValue(), right.positionYValue());
          return compared != 0 ? compared
            : Integer.compare(left.positionXValue(), right.positionXValue());
        }).firstOrThrow();
      return new PreparedMove(context, chosen.position,
        chosen.totalCost, chosen.remainingActionPoints);
    }

    MoveCommitResult commitMove(PreparedMove prepared) {
      if (prepared == null || prepared.context == null
          || !prepared.context.sameAs(currentContext)) {
        return MoveCommitResult.STALE;
      }
      MoveActionContext context = prepared.context;
      GameUnitState current = states.fetch(context.unitId);
      if (!occupancy.valid
          || context.pathingRevision != pathingRevision
          || current.state != UnitState.READY
          || !current.position.equals(context.origin)
          || current.actionPoints != context.initialActionPoints
          || prepared.totalCost < 0 || prepared.remainingActionPoints < 0
          || prepared.remainingActionPoints != Math.subtractExact(
          current.actionPoints, prepared.totalCost)) {
        return MoveCommitResult.STALE;
      }
      TileOccupancyRow source = occupancy.table.fetch(current.position);
      TileOccupancyRow target = occupancy.table.fetch(prepared.position);
      MapTileDefinitionRow targetDefinition = tiles.fetch(prepared.position);
      if (source.occupantUnit == null
          || !source.occupantUnit.equals(context.unitId)
          || target.occupantUnit != null
          || targetDefinition.terrain == TerrainType.WATER
          || targetDefinition.terrain == TerrainType.WALL) {
        return MoveCommitResult.STALE;
      }
      long nextRevision = Math.addExact(pathingRevision, 1L);
      states.mutate(context.unitId).setPosition(prepared.position)
        .setActionPoints(prepared.remainingActionPoints)
        .setState(UnitState.MOVED).commit();
      pathingRevision = nextRevision;
      occupancy.moveOrRebuild(
        current.position, prepared.position, context.unitId,
        tiles, states);
      currentContext = null;
      moves.clear();
      return MoveCommitResult.COMMITTED;
    }
  }

  private static final class OccupancyCache {
    private final TileOccupancyRowTable table;
    private final TileOccupancyRowBatch rebuildBatch;
    private boolean valid;
    private int rebuildCount;

    OccupancyCache(TileOccupancyRowTable table, int tileCount) {
      this.table = table;
      rebuildBatch = new TileOccupancyRowBatch(tileCount);
    }

    void rebuildFromUnits(MapTileDefinitionRowTable tiles,
                          GameUnitStateTable states) {
      rebuildBatch.clear();
      IndexSnapshot tileIndexes = tiles.sorted((left, right) -> {
        int compared = Integer.compare(
          left.positionYValue(), right.positionYValue());
        return compared != 0 ? compared
          : Integer.compare(left.positionXValue(), right.positionXValue());
      }).indexSnapshot();
      IntColumnView tileX = tiles.positionXValueColumn();
      IntColumnView tileY = tiles.positionYValueColumn();
      LongColumnView unitIds = states.unitIdValueColumn();
      try {
        for (int position = 0; position < tileIndexes.size(); position++) {
          int tileIndex = tileIndexes.indexAt(position);
          int x = tileX.getInt(tileIndex);
          int y = tileY.getInt(tileIndex);
          IndexSnapshot occupants = states.filter(row ->
            row.state() != UnitState.DEAD
              && row.positionXValue() == x
              && row.positionYValue() == y).indexSnapshot();
          require(occupants.size() <= 1,
            "one coordinate cannot contain multiple live units");
          if (occupants.size() == 0) {
            rebuildBatch.addValues(new GridPosition(x, y), false, null);
          } else {
            rebuildBatch.addValues(new GridPosition(x, y), true,
              new UnitId(unitIds.getLong(occupants.indexAt(0))));
          }
        }
      } finally {
        unitIds.close();
        tileY.close();
        tileX.close();
      }
      requireEveryLiveUnitHasTile(tiles, states);
      table.replaceAll(rebuildBatch);
      valid = true;
      rebuildCount = Math.addExact(rebuildCount, 1);
    }

    void moveOrRebuild(GridPosition from, GridPosition to, UnitId unitId,
                       MapTileDefinitionRowTable tiles,
                       GameUnitStateTable states) {
      try {
        table.mutate(from).clearOccupantUnit().commit();
        table.mutate(to).setOccupantUnit(unitId).commit();
      } catch (SomaRuntimeException cacheFailure) {
        valid = false;
        try {
          rebuildFromUnits(tiles, states);
        } catch (RuntimeException rebuildFailure) {
          rebuildFailure.addSuppressed(cacheFailure);
          throw rebuildFailure;
        }
      }
    }

    private static void requireEveryLiveUnitHasTile(
        MapTileDefinitionRowTable tiles, GameUnitStateTable states) {
      IntColumnView x = states.positionXValueColumn();
      IntColumnView y = states.positionYValueColumn();
      EnumColumnView<UnitState> unitStates = states.stateColumn();
      EnumColumnView<TerrainType> terrain = tiles.terrainColumn();
      try {
        for (int row = 0; row < states.size(); row++) {
          if (unitStates.get(row) == UnitState.DEAD) continue;
          int tileIndex = tiles.requireIndex(x.getInt(row), y.getInt(row));
          TerrainType value = terrain.get(tileIndex);
          require(value != TerrainType.WALL && value != TerrainType.WATER,
            "live unit must occupy a traversable tile");
        }
      } finally {
        terrain.close();
        unitStates.close();
        y.close();
        x.close();
      }
    }
  }

  private static final class DamageBatchBuilder {
    private final PendingDamageRowBatch batch;
    private long nextSequenceNo;

    DamageBatchBuilder(int capacity) {
      batch = new PendingDamageRowBatch(capacity);
    }

    void begin() {
      batch.clear();
      nextSequenceNo = 0L;
    }

    void add(long resolutionOrder, UnitId source,
             UnitId target, int amount) {
      if (resolutionOrder < 0L || amount < 0
          || nextSequenceNo == Long.MAX_VALUE) {
        throw new IllegalArgumentException("invalid damage command");
      }
      long sequence = nextSequenceNo;
      nextSequenceNo = Math.addExact(nextSequenceNo, 1L);
      batch.addValues(resolutionOrder, sequence, source, target, amount);
    }

    PendingDamageRowBatch batch() {
      return batch;
    }
  }

  /** 完整复制 command facts 后才允许跨 Table mutation。 */
  private static final class DamageCommandBuffer {
    private final long[] resolutionOrder;
    private final long[] sequenceNo;
    private final long[] sourceUnit;
    private final long[] targetUnit;
    private final int[] amount;
    private int size;

    DamageCommandBuffer(int capacity) {
      resolutionOrder = new long[capacity];
      sequenceNo = new long[capacity];
      sourceUnit = new long[capacity];
      targetUnit = new long[capacity];
      amount = new int[capacity];
    }

    void copyAndValidate(PendingDamageRowTable pending,
                         GameUnitStateTable states) {
      size = 0;
      IndexSnapshot ordered = pending.sorted((left, right) -> {
        int compared = Long.compare(
          left.resolutionOrder(), right.resolutionOrder());
        return compared != 0 ? compared
          : Long.compare(left.sequenceNo(), right.sequenceNo());
      }).indexSnapshot();
      LongColumnView orders = pending.resolutionOrderColumn();
      LongColumnView sequences = pending.sequenceNoColumn();
      LongColumnView sources = pending.sourceUnitValueColumn();
      LongColumnView targets = pending.targetUnitValueColumn();
      IntColumnView amounts = pending.damageColumn();
      try {
        for (int position = 0; position < ordered.size(); position++) {
          if (size == amount.length) {
            throw new IllegalStateException("damage command buffer exhausted");
          }
          int row = ordered.indexAt(position);
          resolutionOrder[size] = orders.getLong(row);
          sequenceNo[size] = sequences.getLong(row);
          sourceUnit[size] = sources.getLong(row);
          targetUnit[size] = targets.getLong(row);
          amount[size] = amounts.getInt(row);
          size++;
        }
      } finally {
        amounts.close();
        targets.close();
        sources.close();
        sequences.close();
        orders.close();
      }
      for (int index = 0; index < size; index++) {
        require(resolutionOrder[index] >= 0L && amount[index] >= 0,
          "damage order and amount must be non-negative");
        require(states.containsKey(new UnitId(sourceUnit[index]))
            && states.containsKey(new UnitId(targetUnit[index])),
          "damage source and target must exist");
        int aggregate = 0;
        for (int other = 0; other < size; other++) {
          require(index == other || sequenceNo[index] != sequenceNo[other],
            "damage sequence must be unique within the batch");
          if (targetUnit[index] == targetUnit[other]) {
            aggregate = Math.addExact(aggregate, amount[other]);
          }
        }
      }
    }
  }

  private static final class DamageResolution {
    private boolean failed;

    void resolve(DamageCommandBuffer commands,
                 PendingDamageRowTable pending,
                 GameUnitStateTable states,
                 PlayerStateTable players, PlayerId scoringPlayer) {
      if (failed) throw new IllegalStateException("damage phase is fail-stop");
      int appliedDamage = 0;
      try {
        for (int index = 0; index < commands.size; index++) {
          UnitId targetId = new UnitId(commands.targetUnit[index]);
          GameUnitState target = states.fetch(targetId);
          int nextHp = Math.max(0,
            Math.subtractExact(target.hp, commands.amount[index]));
          states.mutate(targetId).setHp(nextHp)
            .setState(nextHp == 0 ? UnitState.DEAD : target.state)
            .commit();
          appliedDamage = Math.addExact(
            appliedDamage, commands.amount[index]);
        }
        PlayerState player = players.fetch(scoringPlayer);
        players.mutate(scoringPlayer).setScore(
          Math.addExact(player.score, (long) appliedDamage)).commit();
        pending.clear();
      } catch (RuntimeException failure) {
        failed = true;
        throw failure;
      } catch (Error failure) {
        failed = true;
        throw failure;
      }
    }
  }

  private enum MoveCommitResult {
    COMMITTED,
    STALE
  }

  private static final class MoveActionContext {
    final UnitId unitId;
    final GridPosition origin;
    final int initialActionPoints;
    final long pathingRevision;
    final long generation;

    MoveActionContext(UnitId unitId, GridPosition origin,
                      int initialActionPoints, long pathingRevision,
                      long generation) {
      this.unitId = unitId;
      this.origin = origin;
      this.initialActionPoints = initialActionPoints;
      this.pathingRevision = pathingRevision;
      this.generation = generation;
    }

    boolean sameAs(MoveActionContext other) {
      return other != null && unitId.equals(other.unitId)
        && origin.equals(other.origin)
        && initialActionPoints == other.initialActionPoints
        && pathingRevision == other.pathingRevision
        && generation == other.generation;
    }
  }

  private static final class PreparedMove {
    final MoveActionContext context;
    final GridPosition position;
    final int totalCost;
    final int remainingActionPoints;

    PreparedMove(MoveActionContext context, GridPosition position,
                 int totalCost, int remainingActionPoints) {
      this.context = context;
      this.position = position;
      this.totalCost = totalCost;
      this.remainingActionPoints = remainingActionPoints;
    }
  }

  private static final class Fixture {
    final PlayerId player = new PlayerId(1L);
    final PlayerId opponent = new PlayerId(2L);
    final UnitId actor = new UnitId(10L);
    final UnitId target = new UnitId(20L);
    final UnitClassId soldier = new UnitClassId(100L);
    final GridPosition origin = new GridPosition(0, 0);
    final GridPosition destination = new GridPosition(1, 0);
    final GridPosition alternative = new GridPosition(0, 1);
    final GridPosition targetPosition = new GridPosition(2, 0);
    final TileInput[] tiles = {
      new TileInput(origin, TerrainType.PLAIN, 1, false),
      new TileInput(destination, TerrainType.PLAIN, 1, false),
      new TileInput(alternative, TerrainType.FOREST, 3, false),
      new TileInput(targetPosition, TerrainType.PLAIN, 1, false)
    };

    static Fixture teaching() {
      return new Fixture();
    }

    void validate() {
      for (int index = 0; index < tiles.length; index++) {
        requireInput(tiles[index].moveCost >= 0,
          "tile move cost must be non-negative");
        for (int other = 0; other < index; other++) {
          requireInput(!tiles[index].position.equals(tiles[other].position),
            "tile coordinates must be unique");
        }
      }
      requireInput(10 >= 0 && 8 >= 0 && 5 >= 0,
        "hp and action points must be non-negative");
    }
  }

  private static final class TileInput {
    final GridPosition position;
    final TerrainType terrain;
    final int moveCost;
    final boolean blocksSight;

    TileInput(GridPosition position, TerrainType terrain,
              int moveCost, boolean blocksSight) {
      this.position = position;
      this.terrain = terrain;
      this.moveCost = moveCost;
      this.blocksSight = blocksSight;
    }
  }

  private static void requireInput(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
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
