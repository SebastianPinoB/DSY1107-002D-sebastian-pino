package com.example.javiiland.repository;

import com.example.javiiland.model.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {
   boolean existsByNombreIgnoreCase(String nombre);
}