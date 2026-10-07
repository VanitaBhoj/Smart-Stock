package com.smartstock.repository;

import com.smartstock.entity.Inventory;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProduct_Id(Long productId);

    boolean existsByProduct_Id(Long productId);

    List<Inventory> findAllByProduct_IdIn(Collection<Long> productIds);
}
