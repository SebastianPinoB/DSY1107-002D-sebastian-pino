package com.example.javiiland.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "equipos")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    /** null = sin límite por usos */
    @Column(name = "limite_usos")
    private Integer limiteUsos;

    /** null = sin límite por horas */
    @Column(name = "limite_horas")
    private Double limiteHoras;

    @Column(name = "usos_acumulados", nullable = false)
    @Builder.Default
    private int usosAcumulados = 0;

    @Column(name = "horas_acumuladas", nullable = false)
    @Builder.Default
    private double horasAcumuladas = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoEquipo estado = EstadoEquipo.DISPONIBLE;

    @Column(name = "mantencion_desde")
    private LocalDate mantencionDesde;

    /** Bloqueo optimista: protege los contadores ante actualizaciones concurrentes. */
    @Version
    private Long version;

    public boolean estaBloqueadoEn(LocalDate fecha) {
        return estado == EstadoEquipo.EN_MANTENCION
                && (mantencionDesde == null || !fecha.isBefore(mantencionDesde));
    }
}