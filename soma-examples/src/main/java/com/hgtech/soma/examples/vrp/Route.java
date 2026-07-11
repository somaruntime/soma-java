package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;
import java.util.List;

@SomaTable(name = "routes", defaultCapacity = 512)
@SomaIndex(name = "by_vehicle", fields = {"vehicleId.value"})
@SomaOrder(name = "by_route_id", by = {@SomaSort("routeId.value")})
public final class Route {
    @SomaKey public RouteId routeId;
    @SomaField public VehicleId vehicleId;
    @SomaField @SomaDefault("0") public int load;
    @SomaField @SomaDefault("0") public long totalDistanceMeters;
    @SomaField @SomaDefault("0") public long totalDurationSeconds;
    @SomaField @SomaDefault("0") public long routeVersion;
    @SomaField @SomaDefault("false") public boolean closed;
    @SomaChild(initialCapacity = 32) public List<RouteVisitRow> visits;
}
