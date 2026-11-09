package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    List<Voucher> findByCompraIdOrderByIdAsc(Long compraId);

    long countByCompraId(Long compraId);
}
