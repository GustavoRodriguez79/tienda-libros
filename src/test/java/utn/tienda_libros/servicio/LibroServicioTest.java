package utn.tienda_libros.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import utn.tienda_libros.excepcion.LibroDuplicadoException;
import utn.tienda_libros.excepcion.LibroInvalidoException;
import utn.tienda_libros.modelo.Libro;
import utn.tienda_libros.repositorio.LibroRepositorio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link LibroServicio}.
 * <p>
 * No necesitan base de datos ni Spring: el repositorio se reemplaza por un "mock"
 * (un doble de prueba) y se comprueba solo la lógica de negocio del servicio.
 * </p>
 */
class LibroServicioTest {

    private LibroRepositorio repositorio;
    private LibroServicio servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(LibroRepositorio.class);
        servicio = new LibroServicio(repositorio);
    }

    private Libro libroValido(Integer id) {
        return new Libro(id, "Rayuela", "Julio Cortázar", new BigDecimal("16750"), 15);
    }

    private LibroInvalidoException guardarYEsperarError(Libro libro) {
        return assertThrows(LibroInvalidoException.class, () -> servicio.guardarLibro(libro));
    }

    // ----------------------------------------------------------------
    // Guardar: datos válidos y normalización
    // ----------------------------------------------------------------

    @Test
    void guardarLibroNuevoValido_lo_guarda() {
        Libro libro = libroValido(null);

        servicio.guardarLibro(libro);

        verify(repositorio).save(libro);
    }

    @Test
    void guardarLibro_quitaEspaciosYDejaPrecioConDosDecimales() {
        Libro libro = new Libro(null, "  Rayuela  ", "  Julio   Cortázar ", new BigDecimal("16750"), 15);

        servicio.guardarLibro(libro);

        assertEquals("Rayuela", libro.getNombreLibro());
        assertEquals("Julio Cortázar", libro.getAutor());
        assertEquals(new BigDecimal("16750.00"), libro.getPrecio());
    }

    @Test
    void guardarLibro_redondeaPrecioHaciaArribaEnLaMitad() {
        Libro libro = libroValido(null);
        libro.setPrecio(new BigDecimal("10.005"));

        servicio.guardarLibro(libro);

        assertEquals(new BigDecimal("10.01"), libro.getPrecio());
    }

    // ----------------------------------------------------------------
    // Guardar: validaciones
    // ----------------------------------------------------------------

    @Test
    void guardarLibro_sinNombre_falla() {
        Libro libro = libroValido(null);
        libro.setNombreLibro("   ");

        LibroInvalidoException ex = guardarYEsperarError(libro);

        assertEquals(LibroInvalidoException.CAMPO_NOMBRE, ex.getCampo());
        verify(repositorio, never()).save(any());
    }

    @Test
    void guardarLibro_sinAutor_falla() {
        Libro libro = libroValido(null);
        libro.setAutor(null);

        assertEquals(LibroInvalidoException.CAMPO_AUTOR, guardarYEsperarError(libro).getCampo());
    }

    @Test
    void guardarLibro_conNombreDemasiadoLargo_falla() {
        Libro libro = libroValido(null);
        libro.setNombreLibro("x".repeat(Libro.LONGITUD_MAXIMA_TEXTO + 1));

        assertEquals(LibroInvalidoException.CAMPO_NOMBRE, guardarYEsperarError(libro).getCampo());
    }

    @Test
    void guardarLibro_conNombreDelLargoMaximo_esValido() {
        Libro libro = libroValido(null);
        libro.setNombreLibro("x".repeat(Libro.LONGITUD_MAXIMA_TEXTO));

        servicio.guardarLibro(libro);

        verify(repositorio).save(libro);
    }

    @Test
    void guardarLibro_sinPrecio_falla() {
        Libro libro = libroValido(null);
        libro.setPrecio(null);

        assertEquals(LibroInvalidoException.CAMPO_PRECIO, guardarYEsperarError(libro).getCampo());
    }

    @Test
    void guardarLibro_conPrecioCeroONegativo_falla() {
        Libro cero = libroValido(null);
        cero.setPrecio(BigDecimal.ZERO);
        Libro negativo = libroValido(null);
        negativo.setPrecio(new BigDecimal("-5"));

        assertEquals(LibroInvalidoException.CAMPO_PRECIO, guardarYEsperarError(cero).getCampo());
        assertEquals(LibroInvalidoException.CAMPO_PRECIO, guardarYEsperarError(negativo).getCampo());
    }

    @Test
    void guardarLibro_conPrecioMayorAlMaximo_falla() {
        Libro libro = libroValido(null);
        libro.setPrecio(new BigDecimal("100000000"));

        assertEquals(LibroInvalidoException.CAMPO_PRECIO, guardarYEsperarError(libro).getCampo());
    }

    @Test
    void guardarLibro_conPrecioExactamenteElMaximo_esValido() {
        Libro libro = libroValido(null);
        libro.setPrecio(LibroServicio.PRECIO_MAXIMO);

        servicio.guardarLibro(libro);

        verify(repositorio).save(libro);
    }

    @Test
    void guardarLibro_sinExistencias_falla() {
        Libro libro = libroValido(null);
        libro.setExistencias(null);

        assertEquals(LibroInvalidoException.CAMPO_EXISTENCIAS, guardarYEsperarError(libro).getCampo());
    }

    @Test
    void guardarLibro_conExistenciasNegativas_falla() {
        Libro libro = libroValido(null);
        libro.setExistencias(-1);

        assertEquals(LibroInvalidoException.CAMPO_EXISTENCIAS, guardarYEsperarError(libro).getCampo());
    }

    @Test
    void guardarLibro_conExistenciasCero_esValido() {
        Libro libro = libroValido(null);
        libro.setExistencias(0);

        servicio.guardarLibro(libro);

        verify(repositorio).save(libro);
    }

    // ----------------------------------------------------------------
    // Duplicados
    // ----------------------------------------------------------------

    @Test
    void agregarLibroQueYaExiste_lanzaDuplicadoYNoGuarda() {
        Libro existente = libroValido(1);
        when(repositorio.findByNombreLibroIgnoreCaseAndAutorIgnoreCase("Rayuela", "Julio Cortázar"))
                .thenReturn(List.of(existente));

        LibroDuplicadoException ex = assertThrows(LibroDuplicadoException.class,
                () -> servicio.guardarLibro(libroValido(null)));

        assertSame(existente, ex.getLibroExistente());
        verify(repositorio, never()).save(any());
    }

    @Test
    void duplicado_seDetectaAunqueSobrenEspacios() {
        Libro existente = libroValido(1);
        // El servicio limpia los espacios ANTES de consultar, por eso el mock espera el texto limpio
        when(repositorio.findByNombreLibroIgnoreCaseAndAutorIgnoreCase("Rayuela", "Julio Cortázar"))
                .thenReturn(List.of(existente));
        Libro conEspacios = new Libro(null, "  Rayuela ", " Julio   Cortázar", new BigDecimal("1"), 1);

        assertThrows(LibroDuplicadoException.class, () -> servicio.guardarLibro(conEspacios));
    }

    @Test
    void modificarElMismoLibro_noEsDuplicado() {
        Libro existente = libroValido(1);
        when(repositorio.findByNombreLibroIgnoreCaseAndAutorIgnoreCase("Rayuela", "Julio Cortázar"))
                .thenReturn(List.of(existente));
        Libro modificado = libroValido(1);
        modificado.setExistencias(99);

        servicio.guardarLibro(modificado);

        verify(repositorio).save(modificado);
    }

    @Test
    void modificarLibroConElNombreDeOtro_esDuplicado() {
        Libro otro = libroValido(1);
        when(repositorio.findByNombreLibroIgnoreCaseAndAutorIgnoreCase("Rayuela", "Julio Cortázar"))
                .thenReturn(List.of(otro));
        Libro modificado = libroValido(2); // otro id, mismo nombre y autor

        assertThrows(LibroDuplicadoException.class, () -> servicio.guardarLibro(modificado));
        verify(repositorio, never()).save(any());
    }

    @Test
    void siLaBaseRechazaPorRestriccionUnica_seTraduceADuplicado() {
        Libro existente = libroValido(7);
        // Primera consulta (antes de guardar): no ve nada. Segunda (tras el error): ya existe.
        when(repositorio.findByNombreLibroIgnoreCaseAndAutorIgnoreCase("Rayuela", "Julio Cortázar"))
                .thenReturn(List.of(), List.of(existente));
        when(repositorio.save(any())).thenThrow(new DataIntegrityViolationException("clave duplicada"));

        LibroDuplicadoException ex = assertThrows(LibroDuplicadoException.class,
                () -> servicio.guardarLibro(libroValido(null)));

        assertSame(existente, ex.getLibroExistente());
    }

    // ----------------------------------------------------------------
    // Sumar existencias y cambiar precio
    // ----------------------------------------------------------------

    @Test
    void sumarExistencias_sumaALasActuales() {
        Libro libro = libroValido(1); // 15 existencias
        when(repositorio.findById(1)).thenReturn(Optional.of(libro));

        servicio.sumarExistencias(1, 10);

        assertEquals(25, libro.getExistencias());
        verify(repositorio).save(libro);
    }

    @Test
    void sumarExistencias_conCantidadCeroONegativa_falla() {
        assertThrows(LibroInvalidoException.class, () -> servicio.sumarExistencias(1, 0));
        assertThrows(LibroInvalidoException.class, () -> servicio.sumarExistencias(1, -3));
    }

    @Test
    void sumarExistencias_deUnLibroInexistente_falla() {
        when(repositorio.findById(99)).thenReturn(Optional.empty());

        assertThrows(LibroInvalidoException.class, () -> servicio.sumarExistencias(99, 1));
    }

    @Test
    void sumarExistencias_siSeDesbordaElEntero_falla() {
        Libro libro = libroValido(1);
        libro.setExistencias(Integer.MAX_VALUE);
        when(repositorio.findById(1)).thenReturn(Optional.of(libro));

        assertThrows(LibroInvalidoException.class, () -> servicio.sumarExistencias(1, 1));
    }

    @Test
    void actualizarPrecio_cambiaElPrecioConDosDecimales() {
        Libro libro = libroValido(1);
        when(repositorio.findById(1)).thenReturn(Optional.of(libro));

        servicio.actualizarPrecio(1, new BigDecimal("20000"));

        assertEquals(new BigDecimal("20000.00"), libro.getPrecio());
        verify(repositorio).save(libro);
    }

    @Test
    void actualizarPrecio_conPrecioInvalido_falla() {
        assertThrows(LibroInvalidoException.class, () -> servicio.actualizarPrecio(1, new BigDecimal("-1")));
        assertThrows(LibroInvalidoException.class, () -> servicio.actualizarPrecio(1, null));
    }

    // ----------------------------------------------------------------
    // Valor del inventario y búsqueda
    // ----------------------------------------------------------------

    @Test
    void calcularValorInventario_sumaPrecioPorExistencias() {
        List<Libro> libros = List.of(
                new Libro(1, "A", "X", new BigDecimal("100.00"), 2),   // 200.00
                new Libro(2, "B", "Y", new BigDecimal("50.50"), 3),    // 151.50
                new Libro(3, "C", "Z", null, 5));                      // se ignora

        BigDecimal total = servicio.calcularValorInventario(libros);

        assertEquals(0, new BigDecimal("351.50").compareTo(total));
    }

    @Test
    void calcularValorInventario_sinLibros_esCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(servicio.calcularValorInventario(List.of())));
    }

    @Test
    void buscarLibros_conTextoVacio_devuelveTodos() {
        List<Libro> todos = List.of(libroValido(1));
        when(repositorio.findAll()).thenReturn(todos);

        assertSame(todos, servicio.buscarLibros("   "));
        assertSame(todos, servicio.buscarLibros(null));
    }

    @Test
    void buscarLibros_conTexto_buscaPorNombreOAutor() {
        List<Libro> encontrados = List.of(libroValido(1));
        when(repositorio.findByNombreLibroContainingIgnoreCaseOrAutorContainingIgnoreCase("rayu", "rayu"))
                .thenReturn(encontrados);

        assertSame(encontrados, servicio.buscarLibros("  rayu "));
    }
}
