package com.example.javiiland.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alertas_mantenimiento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaMantenimiento {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "equipo_id")
   private Equipo equipo;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   private MotivoAlerta motivo;

   @Column(length = 500)
   private String detalle;

   @Column(name = "creada_en", nullable = false)
   @Builder.Default
   private LocalDateTime creadaEn = LocalDateTime.now();

   @Column(nullable = false)
   @Builder.Default
   private boolean resuelta = false;

   @Column(name = "resuelta_en")
   private LocalDateTime resueltaEn;
}