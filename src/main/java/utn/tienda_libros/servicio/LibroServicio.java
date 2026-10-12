package utn.tienda_libros.servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utn.tienda_libros.excepcion.LibroDuplicadoException;
import utn.tienda_libros.excepcion.LibroInvalidoException;
import utn.tienda_libros.modelo.Libro;
import utn.tienda_libros.repositorio.LibroRepositorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Lógica de negocio sobre los libros.
 * <p>
 * Acá viven las reglas (validaciones, detección de duplicados, cálculos) para que
 * no dependan de la pantalla: cualquier otra interfaz que se agregue en el futuro
 * las respeta igual, y se pueden probar sin abrir ninguna ventana.
 * </p>
 */
@Service
public class LibroServicio implements ILibroServicio {

    /** Precio mínimo aceptado (un centavo). */
    public static final BigDecimal PRECIO_MINIMO = new BigDecimal("0.01");

    /** Precio máximo aceptado: es lo que entra en la columna DECIMAL(10,2). */
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("99999999.99");

    private final LibroRepositorio libroRepositorio;

    @Autowired
    public LibroServicio(LibroRepositorio libroRepositorio) {
        this.libroRepositorio = libroRepositorio;
    }

    @Override
    public List<Libro> listarLibros() {
        return libroRepositorio.findAll();
    }

    @Override
    public Libro buscarLibrosPorId(Integer idLibro) {
        Libro libro = libroRepositorio.findById(idLibro).orElse(null);
        return libro;
    }

    @Override
    public void guardarLibro(Libro libro) {
        validarYNormalizar(libro);
        verificarQueNoEsteDuplicado(libro);
        try {
            libroRepositorio.save(libro);
        } catch (DataIntegrityViolationException ex) {
            // Red de seguridad: si dos personas guardan el mismo libro al mismo tiempo,
            // la restricción única de la base de datos lo frena y lo traducimos acá.
            Libro existente = buscarPorNombreYAutor(libro.getNombreLibro(), libro.getAutor());
            if (existente != null && !Objects.equals(existente.getIdLibro(), libro.getIdLibro())) {
                throw new LibroDuplicadoException(existente);
            }
            throw ex;
        }
    }

    @Override
    public void eliminarLibro(Libro libro) {
        libroRepositorio.delete(libro);
    }

    @Override
    public List<Libro> buscarLibros(String texto) {
        if (texto == null || texto.isBlank()) {
            return listarLibros();
        }
        String buscado = texto.trim();
        return libroRepositorio.findByNombreLibroContainingIgnoreCaseOrAutorContainingIgnoreCase(buscado, buscado);
    }

    @Override
    public Libro buscarPorNombreYAutor(String nombreLibro, String autor) {
        List<Libro> encontrados = libroRepositorio.findByNombreLibroIgnoreCaseAndAutorIgnoreCase(nombreLibro, autor);
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    @Override
    @Transactional
    public void sumarExistencias(Integer idLibro, int cantidad) {
        if (cantidad <= 0) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_EXISTENCIAS,
                    "La cantidad a sumar debe ser mayor a cero");
        }
        Libro libro = obtenerExistente(idLibro);
        long actuales = libro.getExistencias() == null ? 0 : libro.getExistencias();
        long total = actuales + cantidad;
        if (total > Integer.MAX_VALUE) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_EXISTENCIAS,
                    "La cantidad total de existencias es demasiado grande");
        }
        libro.setExistencias((int) total);
        libroRepositorio.save(libro);
    }

    @Override
    @Transactional
    public void actualizarPrecio(Integer idLibro, BigDecimal nuevoPrecio) {
        BigDecimal precio = validarPrecio(nuevoPrecio);
        Libro libro = obtenerExistente(idLibro);
        libro.setPrecio(precio);
        libroRepositorio.save(libro);
    }

    @Override
    public BigDecimal calcularValorInventario(List<Libro> libros) {
        BigDecimal total = BigDecimal.ZERO;
        for (Libro libro : libros) {
            if (libro.getPrecio() != null && libro.getExistencias() != null) {
                total = total.add(libro.getPrecio().multiply(BigDecimal.valueOf(libro.getExistencias())));
            }
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Validaciones internas
    // ------------------------------------------------------------------

    private Libro obtenerExistente(Integer idLibro) {
        if (idLibro == null) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_GENERAL,
                    "Seleccione un libro de la tabla");
        }
        return libroRepositorio.findById(idLibro).orElseThrow(() ->
                new LibroInvalidoException(LibroInvalidoException.CAMPO_GENERAL,
                        "El libro ya no existe en la base de datos"));
    }

    /**
     * Revisa todos los campos y deja el libro "limpio": textos sin espacios de más
     * y precio con exactamente 2 decimales.
     */
    private void validarYNormalizar(Libro libro) {
        if (libro == null) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_GENERAL,
                    "No hay datos del libro para guardar");
        }
        libro.setNombreLibro(validarTexto(libro.getNombreLibro(), "nombre del libro",
                LibroInvalidoException.CAMPO_NOMBRE));
        libro.setAutor(validarTexto(libro.getAutor(), "autor",
                LibroInvalidoException.CAMPO_AUTOR));
        libro.setPrecio(validarPrecio(libro.getPrecio()));
        libro.setExistencias(validarExistencias(libro.getExistencias()));
    }

    private String validarTexto(String valor, String descripcion, String campo) {
        // trim() saca espacios de los extremos y replaceAll junta los espacios repetidos del medio
        String limpio = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            throw new LibroInvalidoException(campo, "Ingrese el " + descripcion);
        }
        if (limpio.length() > Libro.LONGITUD_MAXIMA_TEXTO) {
            throw new LibroInvalidoException(campo, "El " + descripcion + " no puede superar los "
                    + Libro.LONGITUD_MAXIMA_TEXTO + " caracteres");
        }
        return limpio;
    }

    private BigDecimal validarPrecio(BigDecimal precio) {
        if (precio == null) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_PRECIO,
                    "Ingrese el precio del libro");
        }
        // Se compara antes de redondear para no trabajar con números gigantes o diminutos
        if (precio.compareTo(PRECIO_MINIMO) < 0) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_PRECIO,
                    "El precio debe ser mayor a cero (mínimo " + PRECIO_MINIMO + ")");
        }
        if (precio.compareTo(PRECIO_MAXIMO) > 0) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_PRECIO,
                    "El precio es demasiado grande (máximo " + PRECIO_MAXIMO + ")");
        }
        return precio.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer validarExistencias(Integer existencias) {
        if (existencias == null) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_EXISTENCIAS,
                    "Ingrese las existencias del libro");
        }
        if (existencias < 0) {
            throw new LibroInvalidoException(LibroInvalidoException.CAMPO_EXISTENCIAS,
                    "Las existencias no pueden ser negativas");
        }
        return existencias;
    }

    /**
     * Falla si ya hay OTRO libro con el mismo nombre y autor. Si el libro que se guarda
     * es el mismo registro (modificación), no cuenta como duplicado.
     */
    private void verificarQueNoEsteDuplicado(Libro libro) {
        List<Libro> iguales = libroRepositorio.findByNombreLibroIgnoreCaseAndAutorIgnoreCase(
                libro.getNombreLibro(), libro.getAutor());
        for (Libro otro : iguales) {
            if (!Objects.equals(otro.getIdLibro(), libro.getIdLibro())) {
                throw new LibroDuplicadoException(otro);
            }
        }
    }
}
