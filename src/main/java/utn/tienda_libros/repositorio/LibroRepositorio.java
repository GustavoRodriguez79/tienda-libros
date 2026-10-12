package utn.tienda_libros.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import utn.tienda_libros.modelo.Libro;

import java.util.List;

public interface LibroRepositorio extends JpaRepository<Libro, Integer> {

    /**
     * Busca libros con el mismo nombre y autor, sin distinguir mayúsculas de minúsculas.
     * Devuelve una lista (y no un solo libro) a propósito: si la base ya tuviera
     * repetidos de antes, una búsqueda de un único resultado fallaría.
     */
    List<Libro> findByNombreLibroIgnoreCaseAndAutorIgnoreCase(String nombreLibro, String autor);

    /**
     * Busca libros cuyo nombre o autor contengan el texto indicado,
     * sin distinguir mayúsculas de minúsculas.
     */
    List<Libro> findByNombreLibroContainingIgnoreCaseOrAutorContainingIgnoreCase(String nombreLibro, String autor);
}
