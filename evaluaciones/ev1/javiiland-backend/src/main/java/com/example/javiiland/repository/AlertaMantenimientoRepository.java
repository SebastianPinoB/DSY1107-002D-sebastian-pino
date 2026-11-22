package com.example.javiiland.repository;

import com.example.javiiland.model.AlertaMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertaMantenimientoRepository extends JpaRepository<AlertaMantenimiento, Long> {
   List<AlertaMantenimiento> findByResueltaOrderByCreadaEnDesc(boolean resuelta);

   List<AlertaMantenimiento> findByEquipoIdAndResuelta(Long equipoId, boolean resuelta);
}