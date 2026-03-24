package com.urlshortener.u_r_l__service3.repository;

import com.urlshortener.u_r_l__service3.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, String> {
}
