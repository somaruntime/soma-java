package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.examples.vrp.generated.CustomerAssignmentBatch;
import com.hgtech.soma.examples.vrp.generated.CustomerAssignmentTable;
import com.hgtech.soma.examples.vrp.generated.CustomerDefinitionBatch;
import com.hgtech.soma.examples.vrp.generated.CustomerDefinitionTable;
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowBatch;
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowTable;
import com.hgtech.soma.examples.vrp.generated.RouteBatch;
import com.hgtech.soma.examples.vrp.generated.RouteTable;
import com.hgtech.soma.examples.vrp.generated.RouteVisitRowBatch;
import com.hgtech.soma.examples.vrp.generated.RouteVisitRowTable;
import com.hgtech.soma.examples.vrp.generated.TravelCostBatch;
import com.hgtech.soma.examples.vrp.generated.TravelCostTable;
import com.hgtech.soma.examples.vrp.generated.UnassignedCustomerRowBatch;
import com.hgtech.soma.examples.vrp.generated.UnassignedCustomerRowTable;
import com.hgtech.soma.examples.vrp.generated.VehicleDefinitionBatch;
import com.hgtech.soma.examples.vrp.generated.VehicleDefinitionTable;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.SomaRuntimeException;

/** Canonical CVRPTW data roles、dense candidate workspace 与 route rewrite 场景。 */
public final class VrpScenario {
  private VrpScenario() {
  }

  public static ScenarioResult run() {
    Fixture input = Fixture.teaching();
    input.validate();
    expectInputFailure(input.withoutLastTravelArc());

    CustomerDefinitionTable customers = CustomerDefinitionTable.create();
    CustomerAssignmentTable assignments = CustomerAssignmentTable.create();
    VehicleDefinitionTable vehicles = VehicleDefinitionTable.create();
    RouteTable routes = RouteTable.create();
    TravelCostTable travel = TravelCostTable.create();
    UnassignedCustomerRowTable unassigned =
      UnassignedCustomerRowTable.create();
    InsertionCandidateRowTable candidates =
      InsertionCandidateRowTable.create();
    CandidateBuilder candidateBuilder = new CandidateBuilder(8);
    RouteProjectionWorkspace routeWorkspace =
      new RouteProjectionWorkspace(8);
    CustomerAssignmentBatch assignmentBatch =
      new CustomerAssignmentBatch(1);
    try {
      importInput(input, customers, assignments, vehicles, routes,
        travel, unassigned);

      InsertionCandidateRowBatch staged = candidateBuilder.buildAllFeasible(
        customers, assignments, vehicles, routes, travel, unassigned);
      require(staged.size() == 4,
        "empty and non-empty routes enumerate every insertion ordinal");
      candidates.replaceAll(staged);
      require(candidates.filter(row -> row.routeIdValue() == input.emptyRoute.value
          && row.insertionOrdinal() == 0).count() == 1L,
        "empty route has exactly ordinal zero");

      ChosenInsertion chosen = select(candidates);
      require(chosen.customerId.equals(input.thirdCustomer)
          && chosen.routeId.equals(input.activeRoute)
          && chosen.insertionOrdinal == 1,
        "total comparator selects the cheapest feasible insertion");
      ChosenInsertion stale = chosen.withRouteVersion(
        Math.addExact(chosen.routeVersion, 1L));
      require(commit(stale, customers, assignments, vehicles, routes,
          travel, unassigned, candidates, routeWorkspace,
          assignmentBatch) == CommitResult.STALE,
        "stale route version performs no authoritative write");
      require(commit(chosen, customers, assignments, vehicles, routes,
          travel, unassigned, candidates, routeWorkspace,
          assignmentBatch) == CommitResult.COMMITTED,
        "current candidate commits once");

      RouteVisitRowTable liveVisits = routes.visits(input.activeRoute);
      IndexSnapshot visitRows = liveVisits.rows().sorted((left, right) ->
        Integer.compare(left.position(), right.position())).rowIndexes();
      LongColumnView visitCustomers = liveVisits.customerIdValueColumn();
      LongColumnView visitLocations = liveVisits.locationIdValueColumn();
      IntColumnView positions = liveVisits.positionColumn();
      LongColumnView arrivals = liveVisits.arrivalSecondColumn();
      LongColumnView departures = liveVisits.departureSecondColumn();
      IntColumnView loads = liveVisits.loadAfterVisitColumn();
      try {
        require(visitRows.size() == 3,
          "non-empty insertion rewrites the shifted route segment");
        int first = visitRows.indexAt(0);
        int inserted = visitRows.indexAt(1);
        int last = visitRows.indexAt(2);
        require(positions.getInt(first) == 0
            && positions.getInt(inserted) == 1
            && positions.getInt(last) == 2
            && visitCustomers.getLong(inserted) == input.thirdCustomer.value
            && visitLocations.getLong(inserted) == input.thirdLocation.value
            && arrivals.getLong(inserted) == 160L
            && departures.getLong(inserted) == 170L
            && loads.getInt(last) == 4,
          "route rewrite publishes location, seconds and load propagation");
      } finally {
        loads.close();
        departures.close();
        arrivals.close();
        positions.close();
        visitLocations.close();
        visitCustomers.close();
      }

      Route exported = routes.fetch(input.activeRoute);
      require(exported.visits.size() == 3 && exported.routeVersion == 1L
          && exported.totalDistanceMeters == 3000L
          && exported.totalDurationSeconds == 330L,
        "route current solution is the exported result Owner");
      require(assignments.size() == 3
          && assignments.fetch(input.thirdCustomer).routeId
          .equals(input.activeRoute)
          && unassigned.size() == 0 && candidates.size() == 0,
        "assignment is authoritative and derived workspaces retire");
      require(routes.findByVehicle(input.activeVehicle).count() == 1L
          && routes.findByVehicle(input.emptyVehicle).count() == 1L,
        "one-active-route model uses secondary unique vehicle access");
      expectCode("missing_key", () -> travel.fetch(new LocationPairKey(
        input.depot, new LocationId(999L))));

      long reads = Math.addExact(visitRows.size(), exported.visits.size());
      return new ScenarioResult(exported.visits.size(),
        routes.runtimePlan().schemaHash(),
        routes.statsSnapshot().childInstanceCount(), liveVisits.size(), 40,
        (long) liveVisits.capacity() * 40L, reads, 5L,
        exported.visits.size());
    } finally {
      candidates.release();
      unassigned.release();
      travel.release();
      routes.release();
      vehicles.release();
      assignments.release();
      customers.release();
    }
  }

  private static void importInput(
      Fixture input, CustomerDefinitionTable customers,
      CustomerAssignmentTable assignments,
      VehicleDefinitionTable vehicles, RouteTable routes,
      TravelCostTable travel, UnassignedCustomerRowTable unassigned) {
    customers.addBatch(new CustomerDefinitionBatch(3)
      .addValues(input.firstCustomer, 1L, input.firstLocation,
        2, 0L, 1000L, 10L)
      .addValues(input.secondCustomer, 2L, input.secondLocation,
        1, 0L, 1000L, 10L)
      .addValues(input.thirdCustomer, 3L, input.thirdLocation,
        1, 0L, 1000L, 10L));
    assignments.addBatch(new CustomerAssignmentBatch(2)
      .addValues(input.firstCustomer, input.activeRoute)
      .addValues(input.secondCustomer, input.activeRoute));
    vehicles.addBatch(new VehicleDefinitionBatch(2)
      .addValues(input.activeVehicle, 10, input.depot, input.depot, 0L)
      .addValues(input.emptyVehicle, 10, input.depot, input.depot, 0L));

    RouteVisitRowBatch currentVisits = new RouteVisitRowBatch(2)
      .addValues(0, input.firstCustomer, input.firstLocation,
        100L, 110L, 2)
      .addValues(1, input.secondCustomer, input.secondLocation,
        210L, 220L, 3);
    routes.addBatch(new RouteBatch(2)
      .addValues(input.activeRoute, input.activeVehicle, 3,
        3000L, 320L, 0L, false, currentVisits)
      .addValues(input.emptyRoute, input.emptyVehicle, 0,
        0L, 0L, 0L, false, new RouteVisitRowBatch(0)));

    TravelCostBatch costs = new TravelCostBatch(input.travelArcs.length);
    for (TravelArc arc : input.travelArcs) {
      costs.addValues(new LocationPairKey(new LocationId(arc.from),
        new LocationId(arc.to)), arc.distanceMeters, arc.travelSeconds);
    }
    travel.addBatch(costs);
    unassigned.addBatch(new UnassignedCustomerRowBatch(1)
      .addValues(input.thirdCustomer, 1, 1000L, 3L));
  }

  private static ChosenInsertion select(InsertionCandidateRowTable candidates) {
    IndexSnapshot selected = candidates.rows().sorted((left, right) -> {
      int compared = Long.compare(
        left.deltaDistanceMeters(), right.deltaDistanceMeters());
      if (compared != 0) return compared;
      compared = Long.compare(
        left.projectedArrivalSecond(), right.projectedArrivalSecond());
      if (compared != 0) return compared;
      compared = Long.compare(left.routeIdValue(), right.routeIdValue());
      if (compared != 0) return compared;
      compared = Long.compare(left.customerIdValue(), right.customerIdValue());
      if (compared != 0) return compared;
      compared = Integer.compare(
        left.insertionOrdinal(), right.insertionOrdinal());
      if (compared != 0) return compared;
      return Long.compare(left.routeVersion(), right.routeVersion());
    }).limit(1).rowIndexes();
    if (selected.size() != 1) {
      throw new VrpInfeasibleException(
        "unassigned customers remain but no feasible insertion exists");
    }
    int row = selected.indexAt(0);
    LongColumnView routeIds = candidates.routeIdValueColumn();
    LongColumnView customerIds = candidates.customerIdValueColumn();
    IntColumnView ordinals = candidates.insertionOrdinalColumn();
    LongColumnView versions = candidates.routeVersionColumn();
    LongColumnView deltas = candidates.deltaDistanceMetersColumn();
    LongColumnView arrivals = candidates.projectedArrivalSecondColumn();
    IntColumnView loads = candidates.projectedLoadColumn();
    LongColumnView durations =
      candidates.projectedTotalDurationSecondsColumn();
    try {
      return new ChosenInsertion(new RouteId(routeIds.getLong(row)),
        new CustomerId(customerIds.getLong(row)), ordinals.getInt(row),
        versions.getLong(row), deltas.getLong(row), arrivals.getLong(row),
        loads.getInt(row), durations.getLong(row));
    } finally {
      durations.close();
      loads.close();
      arrivals.close();
      deltas.close();
      versions.close();
      ordinals.close();
      customerIds.close();
      routeIds.close();
    }
  }

  private static CommitResult commit(
      ChosenInsertion chosen, CustomerDefinitionTable customers,
      CustomerAssignmentTable assignments,
      VehicleDefinitionTable vehicles, RouteTable routes,
      TravelCostTable travel, UnassignedCustomerRowTable unassigned,
      InsertionCandidateRowTable candidates,
      RouteProjectionWorkspace workspace,
      CustomerAssignmentBatch assignmentBatch) {
    int routeRow = routes.rowIndexOf(chosen.routeId.value);
    LongColumnView versions = routes.routeVersionColumn();
    long currentVersion;
    try {
      currentVersion = versions.getLong(routeRow);
    } finally {
      versions.close();
    }
    if (currentVersion != chosen.routeVersion
        || assignments.containsKey(chosen.customerId)) {
      return CommitResult.STALE;
    }

    workspace.loadRoute(routes, chosen.routeId, customers);
    workspace.project(chosen.insertionOrdinal, chosen.customerId,
      customers, vehicles, routes, travel, true);
    require(workspace.feasible
        && workspace.insertedArrivalSecond == chosen.projectedArrivalSecond
        && workspace.projectedLoad == chosen.projectedLoad
        && workspace.totalDurationSeconds
        == chosen.projectedTotalDurationSeconds,
      "commit projection must match selected candidate");
    LongColumnView distances = routes.totalDistanceMetersColumn();
    long currentDistance;
    try {
      currentDistance = distances.getLong(routeRow);
    } finally {
      distances.close();
    }
    long nextDistance = Math.addExact(
      currentDistance, chosen.deltaDistanceMeters);
    require(nextDistance >= 0L && nextDistance == workspace.totalDistanceMeters,
      "candidate delta must match complete route rewrite");
    long nextVersion = Math.addExact(currentVersion, 1L);

    assignmentBatch.clear();
    assignmentBatch.addValues(chosen.customerId, chosen.routeId);
    // 从第一次authoritative write开始，任一失败都使当前solve instance fail-stop。
    assignments.addBatch(assignmentBatch);
    routes.replaceVisits(chosen.routeId, workspace.rewritten);
    routes.mutate(chosen.routeId)
      .setLoad(chosen.projectedLoad)
      .setTotalDistanceMeters(nextDistance)
      .setTotalDurationSeconds(chosen.projectedTotalDurationSeconds)
      .setRouteVersion(nextVersion).commit();

    // 以下两张表都是derived workspace；authoritative facts已经完整提交。
    unassigned.filter(row ->
      row.customerIdValue() == chosen.customerId.value).remove();
    candidates.clear();
    return CommitResult.COMMITTED;
  }

  private enum CommitResult {
    COMMITTED,
    STALE
  }

  private static final class CandidateBuilder {
    private final InsertionCandidateRowBatch batch;
    private final RouteProjectionWorkspace workspace;

    CandidateBuilder(int maximumVisits) {
      batch = new InsertionCandidateRowBatch(maximumVisits * 2);
      workspace = new RouteProjectionWorkspace(maximumVisits);
    }

    InsertionCandidateRowBatch buildAllFeasible(
        CustomerDefinitionTable customers,
        CustomerAssignmentTable assignments,
        VehicleDefinitionTable vehicles, RouteTable routes,
        TravelCostTable travel, UnassignedCustomerRowTable unassigned) {
      batch.clear();
      IndexSnapshot customerRows = unassigned.rows().sorted((left, right) -> {
        int compared = Long.compare(left.dueSecond(), right.dueSecond());
        if (compared != 0) return compared;
        compared = Long.compare(left.inputOrder(), right.inputOrder());
        return compared != 0 ? compared
          : Long.compare(left.customerIdValue(), right.customerIdValue());
      }).rowIndexes();
      IndexSnapshot routeRows = routes.rows().sorted((left, right) ->
        Long.compare(left.routeIdValue(), right.routeIdValue())).rowIndexes();
      LongColumnView unassignedIds = unassigned.customerIdValueColumn();
      LongColumnView routeIds = routes.routeIdValueColumn();
      LongColumnView versions = routes.routeVersionColumn();
      LongColumnView distances = routes.totalDistanceMetersColumn();
      IntColumnView routeLoads = routes.loadColumn();
      try {
        for (int customerPosition = 0;
             customerPosition < customerRows.size(); customerPosition++) {
          CustomerId customerId = new CustomerId(unassignedIds.getLong(
            customerRows.indexAt(customerPosition)));
          require(!assignments.containsKey(customerId),
            "unassigned workspace cannot contain assigned customer");
          for (int routePosition = 0;
               routePosition < routeRows.size(); routePosition++) {
            int routeRow = routeRows.indexAt(routePosition);
            RouteId routeId = new RouteId(routeIds.getLong(routeRow));
            workspace.loadRoute(routes, routeId, customers);
            int ordinalCount = Math.addExact(workspace.visitCount, 1);
            for (int ordinal = 0; ordinal < ordinalCount; ordinal++) {
              workspace.project(ordinal, customerId, customers,
                vehicles, routes, travel, false);
              if (!workspace.feasible) continue;
              require(workspace.projectedLoad >= routeLoads.getInt(routeRow),
                "insertion cannot reduce route load");
              long delta = Math.subtractExact(
                workspace.totalDistanceMeters, distances.getLong(routeRow));
              batch.addValues(routeId, customerId, ordinal,
                versions.getLong(routeRow), delta,
                workspace.insertedArrivalSecond,
                workspace.projectedLoad,
                workspace.totalDurationSeconds);
            }
          }
        }
        return batch;
      } finally {
        routeLoads.close();
        distances.close();
        versions.close();
        routeIds.close();
        unassignedIds.close();
      }
    }
  }

  /** 复用 primitive route sequence 与 child Batch；不保存 SOMA Index。 */
  private static final class RouteProjectionWorkspace {
    private long[] customerIds;
    private long[] locationIds;
    private final RouteVisitRowBatch rewritten;
    private RouteId routeId;
    private int visitCount;
    private boolean feasible;
    private long insertedArrivalSecond;
    private int projectedLoad;
    private long totalDistanceMeters;
    private long totalDurationSeconds;

    RouteProjectionWorkspace(int initialCapacity) {
      customerIds = new long[initialCapacity];
      locationIds = new long[initialCapacity];
      rewritten = new RouteVisitRowBatch(Math.addExact(initialCapacity, 1));
    }

    void loadRoute(RouteTable routes, RouteId routeId,
                   CustomerDefinitionTable customers) {
      this.routeId = routeId;
      RouteVisitRowTable visits = routes.visits(routeId);
      ensureCapacity(visits.size());
      IndexSnapshot ordered = visits.rows().sorted((left, right) -> {
        int compared = Integer.compare(left.position(), right.position());
        return compared != 0 ? compared
          : Long.compare(left.customerIdValue(), right.customerIdValue());
      }).rowIndexes();
      LongColumnView ids = visits.customerIdValueColumn();
      LongColumnView locations = visits.locationIdValueColumn();
      IntColumnView positions = visits.positionColumn();
      LongColumnView definitionLocations =
        customers.locationIdValueColumn();
      try {
        for (int position = 0; position < ordered.size(); position++) {
          int row = ordered.indexAt(position);
          long customerId = ids.getLong(row);
          long locationId = locations.getLong(row);
          require(positions.getInt(row) == position,
            "route positions must be continuous and unique");
          for (int previous = 0; previous < position; previous++) {
            require(customerIds[previous] != customerId,
              "route cannot visit one customer twice");
          }
          int definitionRow = customers.rowIndexOf(customerId);
          require(definitionLocations.getLong(definitionRow) == locationId,
            "preprojected visit location must match customer definition");
          customerIds[position] = customerId;
          locationIds[position] = locationId;
        }
        visitCount = ordered.size();
      } finally {
        definitionLocations.close();
        positions.close();
        locations.close();
        ids.close();
      }
    }

    void project(int insertionOrdinal, CustomerId insertedCustomer,
                 CustomerDefinitionTable customers,
                 VehicleDefinitionTable vehicles, RouteTable routes,
                 TravelCostTable travel, boolean writeBatch) {
      if (insertionOrdinal < 0 || insertionOrdinal > visitCount) {
        throw new IllegalArgumentException("insertion ordinal out of range");
      }
      for (int index = 0; index < visitCount; index++) {
        if (customerIds[index] == insertedCustomer.value) {
          throw new IllegalArgumentException("customer already occurs in route");
        }
      }
      int routeRow = routes.rowIndexOf(routeId.value);
      LongColumnView vehicleIds = routes.vehicleIdValueColumn();
      VehicleId vehicleId;
      try {
        vehicleId = new VehicleId(vehicleIds.getLong(routeRow));
      } finally {
        vehicleIds.close();
      }
      int vehicleRow = vehicles.rowIndexOf(vehicleId.value);
      IntColumnView capacities = vehicles.capacityColumn();
      LongColumnView starts = vehicles.startLocationValueColumn();
      LongColumnView ends = vehicles.endLocationValueColumn();
      LongColumnView available = vehicles.availableFromSecondColumn();
      int capacity;
      long startLocation;
      long endLocation;
      long availableSecond;
      try {
        capacity = capacities.getInt(vehicleRow);
        startLocation = starts.getLong(vehicleRow);
        endLocation = ends.getLong(vehicleRow);
        availableSecond = available.getLong(vehicleRow);
      } finally {
        available.close();
        ends.close();
        starts.close();
        capacities.close();
      }
      if (writeBatch) rewritten.clear();
      feasible = true;
      insertedArrivalSecond = -1L;
      projectedLoad = 0;
      totalDistanceMeters = 0L;
      long currentSecond = availableSecond;
      long previousLocation = startLocation;
      int rewrittenCount = Math.addExact(visitCount, 1);
      LongColumnView customerLocations = customers.locationIdValueColumn();
      IntColumnView demands = customers.demandColumn();
      LongColumnView ready = customers.readySecondColumn();
      LongColumnView due = customers.dueSecondColumn();
      LongColumnView service = customers.serviceSecondsColumn();
      LongColumnView travelDistances = travel.distanceMetersColumn();
      LongColumnView travelSeconds = travel.travelSecondsColumn();
      try {
        for (int position = 0; position < rewrittenCount; position++) {
          boolean inserted = position == insertionOrdinal;
          int oldPosition = inserted ? -1
            : position < insertionOrdinal ? position : position - 1;
          long customerId = inserted
            ? insertedCustomer.value : customerIds[oldPosition];
          int customerRow = customers.rowIndexOf(customerId);
          long location = customerLocations.getLong(customerRow);
          int demand = demands.getInt(customerRow);
          long readySecond = ready.getLong(customerRow);
          long dueSecond = due.getLong(customerRow);
          long serviceSeconds = service.getLong(customerRow);
          int arcRow = travel.rowIndexOf(previousLocation, location);
          totalDistanceMeters = Math.addExact(totalDistanceMeters,
            travelDistances.getLong(arcRow));
          long arrival = Math.max(readySecond,
            Math.addExact(currentSecond, travelSeconds.getLong(arcRow)));
          projectedLoad = Math.addExact(projectedLoad, demand);
          if (arrival > dueSecond || projectedLoad > capacity) {
            feasible = false;
            return;
          }
          long departure = Math.addExact(arrival, serviceSeconds);
          if (inserted) insertedArrivalSecond = arrival;
          if (writeBatch) {
            rewritten.addValues(position, new CustomerId(customerId),
              new LocationId(location), arrival, departure, projectedLoad);
          }
          currentSecond = departure;
          previousLocation = location;
        }
        int returnArc = travel.rowIndexOf(previousLocation, endLocation);
        totalDistanceMeters = Math.addExact(totalDistanceMeters,
          travelDistances.getLong(returnArc));
        currentSecond = Math.addExact(currentSecond,
          travelSeconds.getLong(returnArc));
      } finally {
        travelSeconds.close();
        travelDistances.close();
        service.close();
        due.close();
        ready.close();
        demands.close();
        customerLocations.close();
      }
      totalDurationSeconds = Math.subtractExact(
        currentSecond, availableSecond);
      require(totalDistanceMeters >= 0L && totalDurationSeconds >= 0L
          && insertedArrivalSecond >= 0L,
        "route projection must remain non-negative and complete");
    }

    private void ensureCapacity(int required) {
      if (required <= customerIds.length) return;
      int next = Math.max(required, customerIds.length * 2);
      long[] nextCustomers = new long[next];
      long[] nextLocations = new long[next];
      System.arraycopy(customerIds, 0, nextCustomers, 0, visitCount);
      System.arraycopy(locationIds, 0, nextLocations, 0, visitCount);
      customerIds = nextCustomers;
      locationIds = nextLocations;
    }
  }

  private static final class ChosenInsertion {
    final RouteId routeId;
    final CustomerId customerId;
    final int insertionOrdinal;
    final long routeVersion;
    final long deltaDistanceMeters;
    final long projectedArrivalSecond;
    final int projectedLoad;
    final long projectedTotalDurationSeconds;

    ChosenInsertion(RouteId routeId, CustomerId customerId,
                    int insertionOrdinal, long routeVersion,
                    long deltaDistanceMeters, long projectedArrivalSecond,
                    int projectedLoad,
                    long projectedTotalDurationSeconds) {
      this.routeId = routeId;
      this.customerId = customerId;
      this.insertionOrdinal = insertionOrdinal;
      this.routeVersion = routeVersion;
      this.deltaDistanceMeters = deltaDistanceMeters;
      this.projectedArrivalSecond = projectedArrivalSecond;
      this.projectedLoad = projectedLoad;
      this.projectedTotalDurationSeconds =
        projectedTotalDurationSeconds;
    }

    ChosenInsertion withRouteVersion(long version) {
      return new ChosenInsertion(routeId, customerId, insertionOrdinal,
        version, deltaDistanceMeters, projectedArrivalSecond,
        projectedLoad, projectedTotalDurationSeconds);
    }
  }

  private static final class Fixture {
    final CustomerId firstCustomer = new CustomerId(1L);
    final CustomerId secondCustomer = new CustomerId(2L);
    final CustomerId thirdCustomer = new CustomerId(3L);
    final RouteId activeRoute = new RouteId(10L);
    final RouteId emptyRoute = new RouteId(20L);
    final VehicleId activeVehicle = new VehicleId(1000L);
    final VehicleId emptyVehicle = new VehicleId(2000L);
    final LocationId depot = new LocationId(100L);
    final LocationId firstLocation = new LocationId(101L);
    final LocationId secondLocation = new LocationId(102L);
    final LocationId thirdLocation = new LocationId(103L);
    final TravelArc[] travelArcs;

    private Fixture(TravelArc[] travelArcs) {
      this.travelArcs = travelArcs;
    }

    static Fixture teaching() {
      long[] locations = {100L, 101L, 102L, 103L};
      TravelArc[] arcs = new TravelArc[locations.length * locations.length];
      int index = 0;
      for (long from : locations) {
        for (long to : locations) {
          long distance = distance(from, to);
          arcs[index++] = new TravelArc(
            from, to, distance, distance / 10L);
        }
      }
      return new Fixture(arcs);
    }

    Fixture withoutLastTravelArc() {
      TravelArc[] incomplete = new TravelArc[travelArcs.length - 1];
      System.arraycopy(travelArcs, 0, incomplete, 0, incomplete.length);
      return new Fixture(incomplete);
    }

    void validate() {
      long[] locations = {depot.value, firstLocation.value,
        secondLocation.value, thirdLocation.value};
      for (long from : locations) {
        for (long to : locations) {
          int matches = 0;
          for (TravelArc arc : travelArcs) {
            requireInput(arc.distanceMeters >= 0L && arc.travelSeconds >= 0L,
              "travel units must be non-negative");
            if (arc.from == from && arc.to == to) matches++;
          }
          requireInput(matches == 1,
            "required directed travel lookup must be complete and unique");
        }
      }
      requireInput(0L <= 1000L && 10 >= 4,
        "time windows and capacity must be valid");
      Math.addExact(320L, 1000L);
    }

    private static long distance(long from, long to) {
      if (from == to) return 0L;
      long low = Math.min(from, to);
      long high = Math.max(from, to);
      if (low == 100L && high == 103L) return 2000L;
      if ((low == 101L || low == 102L) && high == 103L) return 500L;
      return 1000L;
    }
  }

  private static final class TravelArc {
    final long from;
    final long to;
    final long distanceMeters;
    final long travelSeconds;

    TravelArc(long from, long to, long distanceMeters, long travelSeconds) {
      this.from = from;
      this.to = to;
      this.distanceMeters = distanceMeters;
      this.travelSeconds = travelSeconds;
    }
  }

  private static void expectInputFailure(Fixture invalid) {
    try {
      invalid.validate();
      throw new AssertionError("expected VRP input preflight failure");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }
  }

  private static void requireInput(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private static void expectCode(String code, Action action) {
    try {
      action.run();
      throw new AssertionError("expected " + code);
    } catch (SomaRuntimeException failure) {
      require(code.equals(failure.code()),
        "unexpected runtime code " + failure.code());
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private interface Action {
    void run();
  }

  public static final class ScenarioResult {
    public final int visits;
    public final String schemaHash;
    public final long childInstances;
    public final int apcRows;
    public final int aggregateHotLeafWidths;
    public final long hotLeafWorkingSetBytes;
    public final long reads;
    public final long mutations;
    public final int exports;

    ScenarioResult(int visits, String schemaHash, long childInstances,
                   int apcRows, int aggregateHotLeafWidths,
                   long hotLeafWorkingSetBytes, long reads, long mutations,
                   int exports) {
      this.visits = visits;
      this.schemaHash = schemaHash;
      this.childInstances = childInstances;
      this.apcRows = apcRows;
      this.aggregateHotLeafWidths = aggregateHotLeafWidths;
      this.hotLeafWorkingSetBytes = hotLeafWorkingSetBytes;
      this.reads = reads;
      this.mutations = mutations;
      this.exports = exports;
    }
  }
}
