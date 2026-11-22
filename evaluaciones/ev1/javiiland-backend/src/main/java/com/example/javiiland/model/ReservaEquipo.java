package com.example.javiiland.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reserva_equipos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaEquipo {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "reserva_id")
   private Reserva reserva;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "equipo_id")
   private Equipo equipo;

   @Column(nullable = false)
   private double horas;
}