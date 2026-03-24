package com.urlshortener.u_r_l__service1.repository;

import com.urlshortener.u_r_l__service1.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, String> {
}
