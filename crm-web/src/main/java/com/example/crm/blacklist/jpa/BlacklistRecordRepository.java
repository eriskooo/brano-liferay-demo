package com.example.crm.blacklist.jpa;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository – náhrada legacy DAO (ručné SQL) pre agregát záznamu blacklistu. */
public interface BlacklistRecordRepository extends JpaRepository<BlacklistRecordEntity, Long>,
        JpaSpecificationExecutor<BlacklistRecordEntity> {

    /**
     * Záznam so zámkom riadku (SELECT … FOR UPDATE): dve súbežné odstránenia nesmú zmazať oba posledné
     * identifikátory (in-memory: synchronized, legacy JDBC: FOR UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BlacklistRecordEntity r where r.id = :id")
    Optional<BlacklistRecordEntity> findForUpdate(@Param("id") long id);
}
