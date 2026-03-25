package com.urlshortener.u_r_l__service4.repository;

import com.urlshortener.u_r_l__service4.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, String> {
}
