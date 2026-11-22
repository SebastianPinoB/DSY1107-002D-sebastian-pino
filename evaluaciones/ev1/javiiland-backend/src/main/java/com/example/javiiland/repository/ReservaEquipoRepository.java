package com.example.javiiland.repository;

import com.example.javiiland.model.ReservaEquipo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservaEquipoRepository extends JpaRepository<ReservaEquipo, Long> {
   List<ReservaEquipo> findByReservaId(Long reservaId);
}