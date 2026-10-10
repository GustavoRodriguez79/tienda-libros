package utn.tienda_libros.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.math.BigDecimal;

/**
 * Entidad que representa un libro del catálogo de la tienda.
 * <p>
 * Reglas que se aplican también a nivel base de datos:
 * </p>
 * <ul>
 *     <li>{@code nombreLibro} y {@code autor} son obligatorios y tienen un largo máximo de
 *     {@link #LONGITUD_MAXIMA_TEXTO} caracteres.</li>
 *     <li>No puede haber dos libros con el mismo nombre y el mismo autor
 *     (restricción única {@code uk_libro_nombre_autor}).</li>
 *     <li>El precio se guarda como {@code DECIMAL(10,2)}.</li>
 * </ul>
 */
@Entity
@Table(name = "libro",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_libro_nombre_autor",
                columnNames = {"nombre_libro", "autor"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Libro {

    /** Largo máximo permitido para el nombre del libro y para el autor. */
    public static final int LONGITUD_MAXIMA_TEXTO = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer idLibro;

    // El nombre de columna se indica de forma explícita para que la restricción
    // única de arriba no dependa de la estrategia de nombres de Hibernate.
    @Column(name = "nombre_libro", nullable = false, length = LONGITUD_MAXIMA_TEXTO)
    String nombreLibro;

    @Column(nullable = false, length = LONGITUD_MAXIMA_TEXTO)
    String autor;

    @Column(precision = 10, scale = 2)
    BigDecimal precio;

    Integer existencias;
}
