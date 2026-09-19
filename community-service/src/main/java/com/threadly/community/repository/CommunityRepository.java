package com.threadly.community.repository;

import com.threadly.community.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityRepository extends JpaRepository<Community, UUID> {

    boolean existsByName(String name);

    Optional<Community> findByName(String name);
}
