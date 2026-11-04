package com.shiptrack.tracking.repository;

import com.shiptrack.tracking.entity.Location;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface LocationRepository extends CrudRepository<Location, Long> {

    /** Readings for a shipment; sort and limit come from the Pageable (newest first in practice). */
    List<Location> findByShipmentId(Long shipmentId, Pageable pageable);
}
