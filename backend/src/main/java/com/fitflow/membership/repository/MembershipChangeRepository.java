package com.fitflow.membership.repository;

import com.fitflow.membership.entity.MembershipChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MembershipChangeRepository extends JpaRepository<MembershipChange, Long> {

    List<MembershipChange> findByMembershipId(Long membershipId);
}
