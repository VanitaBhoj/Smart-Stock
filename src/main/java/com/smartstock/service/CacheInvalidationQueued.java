package com.smartstock.service;

import com.smartstock.entity.CacheInvalidation;

public record CacheInvalidationQueued(CacheInvalidation invalidation) {
}
