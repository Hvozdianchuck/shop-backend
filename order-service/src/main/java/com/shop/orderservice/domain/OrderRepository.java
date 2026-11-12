package com.shop.orderservice.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findWithLinesById(UUID id);

    /**
     * Two queries whatever the page size. Fetching lines per order would be one query per order (N+1),
     * and join fetch with a limit makes Hibernate load all rows and paginate in memory.
     */
    default Slice<Order> findPageWithLines(Pageable pageable) {
        var ids = findIds(pageable);
        Map<UUID, Order> byId = findWithLinesByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return ids.map(byId::get);
    }

    @Query("select o.id from Order o")
    Slice<UUID> findIds(Pageable pageable);

    @Query("select o from Order o left join fetch o.lines where o.id in :ids")
    List<Order> findWithLinesByIdIn(Collection<UUID> ids);
}
