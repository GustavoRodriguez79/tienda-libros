package utn.tienda_libros.excepcion;

/**
 * Se lanza cuando los datos de un libro no cumplen las reglas de negocio
 * (campo vacío, número fuera de rango, texto demasiado largo, etc.).
 * <p>
 * El mensaje ({@link #getMessage()}) está pensado para mostrarse tal cual al usuario,
 * y {@link #getCampo()} indica qué campo del formulario hay que corregir.
 * </p>
 */
public class LibroInvalidoException extends RuntimeException {

    public static final String CAMPO_NOMBRE = "nombreLibro";
    public static final String CAMPO_AUTOR = "autor";
    public static final String CAMPO_PRECIO = "precio";
    public static final String CAMPO_EXISTENCIAS = "existencias";
    public static final String CAMPO_GENERAL = "libro";

    private final String campo;

    public LibroInvalidoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    /** @return identificador del campo con el problema (una de las constantes {@code CAMPO_*}) */
    public String getCampo() {
        return campo;
    }
}
