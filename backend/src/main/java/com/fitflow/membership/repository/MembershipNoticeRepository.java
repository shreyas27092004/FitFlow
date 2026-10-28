package com.fitflow.membership.repository;

import com.fitflow.membership.entity.MembershipNotice;
import com.fitflow.membership.entity.MembershipNoticeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipNoticeRepository extends JpaRepository<MembershipNotice, MembershipNoticeId> {
}
