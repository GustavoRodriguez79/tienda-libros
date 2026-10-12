package utn.tienda_libros.servicio;

import utn.tienda_libros.modelo.Libro;

import java.math.BigDecimal;
import java.util.List;

public interface ILibroServicio {

    public List<Libro> listarLibros();

    public Libro buscarLibrosPorId(Integer idLibro);

    /**
     * Valida, normaliza y guarda el libro (INSERT si no tiene id, UPDATE si lo tiene).
     *
     * @throws utn.tienda_libros.excepcion.LibroInvalidoException  si algún dato no cumple las reglas
     * @throws utn.tienda_libros.excepcion.LibroDuplicadoException si ya existe otro libro con el mismo nombre y autor
     */
    public void guardarLibro(Libro libro);

    public void eliminarLibro(Libro libro);

    /**
     * Busca libros cuyo nombre o autor contengan el texto indicado.
     * Si el texto está vacío devuelve todos los libros.
     */
    public List<Libro> buscarLibros(String texto);

    /** @return el libro con ese nombre y autor (sin distinguir mayúsculas), o {@code null} si no existe */
    public Libro buscarPorNombreYAutor(String nombreLibro, String autor);

    /** Suma {@code cantidad} unidades a las existencias del libro. La cantidad debe ser mayor a cero. */
    public void sumarExistencias(Integer idLibro, int cantidad);

    /** Cambia el precio del libro, aplicando las mismas reglas que al guardar. */
    public void actualizarPrecio(Integer idLibro, BigDecimal nuevoPrecio);

    /** @return la suma de precio por existencias de los libros indicados */
    public BigDecimal calcularValorInventario(List<Libro> libros);

}
