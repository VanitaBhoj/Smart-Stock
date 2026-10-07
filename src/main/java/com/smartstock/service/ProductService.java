package com.smartstock.service;

import com.smartstock.dto.CreateProductRequest;
import com.smartstock.dto.ProductResponse;
import com.smartstock.dto.UpdateProductRequest;
import com.smartstock.entity.Product;
import com.smartstock.exception.DuplicateResourceException;
import com.smartstock.exception.ResourceNotFoundException;
import com.smartstock.repository.ProductRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public ProductService(ProductRepository productRepository, CacheInvalidationService cacheInvalidationService) {
        this.productRepository = productRepository;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = request.sku().trim();
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("SKU already exists: " + sku);
        }

        Product product = new Product();
        product.setName(request.name().trim());
        product.setSku(sku);
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setActive(request.active() == null || request.active());

        return toResponse(productRepository.save(product));
    }

    public List<ProductResponse> findAll(int page, int size) {
        validatePage(page, size);
        return productRepository.findAll(PageRequest.of(page, size, Sort.by("id").ascending())).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
    }

    @Cacheable(cacheNames = "productById", key = "#id", unless = "#result == null")
    public ProductResponse findById(Long id) {
        return toResponse(getProduct(id));
    }

    @Cacheable(cacheNames = "productBySku", key = "#sku", unless = "#result == null")
    public ProductResponse findBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with sku: " + sku));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = getProduct(id);
        String oldSku = product.getSku();
        String sku = request.sku().trim();

        if (productRepository.existsBySkuAndIdNot(sku, id)) {
            throw new DuplicateResourceException("SKU already exists: " + sku);
        }

        product.setName(request.name().trim());
        product.setSku(sku);
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setActive(request.active());

        ProductResponse response = toResponse(productRepository.save(product));
        cacheInvalidationService.enqueue("productById", id);
        cacheInvalidationService.enqueue("productBySku", oldSku);
        cacheInvalidationService.enqueue("productBySku", sku);
        return response;
    }

    @Transactional
    public void delete(Long id) {
        Product product = getProduct(id);
        String sku = product.getSku();
        productRepository.delete(product);
        cacheInvalidationService.enqueue("productById", id);
        cacheInvalidationService.enqueue("productBySku", sku);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
