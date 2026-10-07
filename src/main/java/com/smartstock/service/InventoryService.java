package com.smartstock.service;

import com.smartstock.dto.AdjustInventoryRequest;
import com.smartstock.dto.CreateInventoryRequest;
import com.smartstock.dto.InventoryResponse;
import com.smartstock.entity.Inventory;
import com.smartstock.entity.Product;
import com.smartstock.exception.DuplicateResourceException;
import com.smartstock.exception.InsufficientStockException;
import com.smartstock.exception.ResourceNotFoundException;
import com.smartstock.repository.InventoryRepository;
import com.smartstock.repository.ProductRepository;
import java.math.BigInteger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public InventoryService(
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            CacheInvalidationService cacheInvalidationService
    ) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional
    public InventoryResponse create(CreateInventoryRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + request.productId()
                ));

        if (inventoryRepository.existsByProduct_Id(request.productId())) {
            throw new DuplicateResourceException(
                    "Inventory already exists for product: " + request.productId()
            );
        }

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setAvailableQuantity(request.availableQuantity());
        inventory.setReservedQuantity(0);

        try {
            InventoryResponse response = toResponse(inventoryRepository.saveAndFlush(inventory));
            cacheInvalidationService.enqueue("inventoryByProductId", request.productId());
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Inventory already exists for product: " + request.productId()
            );
        }
    }

    @Cacheable(cacheNames = "inventoryByProductId", key = "#productId", unless = "#result == null")
    public InventoryResponse findByProductId(Long productId) {
        return toResponse(getInventory(productId));
    }

    @Transactional
    public InventoryResponse adjust(Long productId, AdjustInventoryRequest request) {
        Inventory inventory = getInventory(productId);
        long adjustedQuantity;
        try {
            adjustedQuantity = Math.addExact(inventory.getAvailableQuantity(), request.quantity());
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Inventory quantity exceeds the supported range");
        }
        ensureAvailable(inventory, adjustedQuantity, request.quantity());
        inventory.setAvailableQuantity(adjustedQuantity);
        InventoryResponse response = toResponse(inventoryRepository.saveAndFlush(inventory));
        cacheInvalidationService.enqueue("inventoryByProductId", productId);
        return response;
    }

    @Transactional
    public InventoryResponse addStock(Long productId, AdjustInventoryRequest request) {
        requirePositiveQuantity(request.quantity());
        Inventory inventory = getInventory(productId);
        long updatedQuantity;
        try {
            updatedQuantity = Math.addExact(inventory.getAvailableQuantity(), request.quantity());
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Inventory quantity exceeds the supported range");
        }
        inventory.setAvailableQuantity(updatedQuantity);
        InventoryResponse response = toResponse(inventoryRepository.saveAndFlush(inventory));
        cacheInvalidationService.enqueue("inventoryByProductId", productId);
        return response;
    }

    @Transactional
    public InventoryResponse removeStock(Long productId, AdjustInventoryRequest request) {
        requirePositiveQuantity(request.quantity());
        Inventory inventory = getInventory(productId);
        long updatedQuantity = inventory.getAvailableQuantity() - request.quantity();
        ensureAvailable(inventory, updatedQuantity, -request.quantity());
        inventory.setAvailableQuantity(updatedQuantity);
        InventoryResponse response = toResponse(inventoryRepository.saveAndFlush(inventory));
        cacheInvalidationService.enqueue("inventoryByProductId", productId);
        return response;
    }

    private Inventory getInventory(Long productId) {
        return inventoryRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for product: " + productId
                ));
    }

    private void ensureAvailable(Inventory inventory, long adjustedQuantity, long delta) {
        if (adjustedQuantity < 0) {
            throw new InsufficientStockException(
                    "Insufficient available stock for product " + inventory.getProductId()
                            + ": available " + inventory.getAvailableQuantity()
                            + ", requested removal " + BigInteger.valueOf(delta).abs()
            );
        }
    }

    private void requirePositiveQuantity(Long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getVersion(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt()
        );
    }
}
