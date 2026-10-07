package com.smartstock.service;

import com.smartstock.dto.CreateProductRequest;
import com.smartstock.dto.ProductResponse;
import com.smartstock.dto.UpdateProductRequest;
import com.smartstock.entity.Product;
import com.smartstock.exception.DuplicateResourceException;
import com.smartstock.exception.ResourceNotFoundException;
import com.smartstock.repository.ProductRepository;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    @Caching(put = {
            @CachePut(cacheNames = "productById", key = "#result.id"),
            @CachePut(cacheNames = "productBySku", key = "#result.sku")
    })
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

    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
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
    @Caching(put = @CachePut(cacheNames = "productById", key = "#id"),
            evict = @CacheEvict(cacheNames = "productBySku", allEntries = true))
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = getProduct(id);
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

        return toResponse(productRepository.save(product));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "productById", key = "#id"),
            @CacheEvict(cacheNames = "productBySku", allEntries = true)
    })
    public void delete(Long id) {
        Product product = getProduct(id);
        productRepository.delete(product);
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
