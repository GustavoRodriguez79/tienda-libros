package utn.tienda_libros.excepcion;

import utn.tienda_libros.modelo.Libro;

/**
 * Se lanza cuando se intenta guardar un libro con el mismo nombre y autor
 * que otro que ya existe. Guarda el libro existente para que la pantalla
 * pueda ofrecer alternativas (por ejemplo, sumar existencias o cambiar el precio).
 */
public class LibroDuplicadoException extends RuntimeException {

    private final transient Libro libroExistente;

    public LibroDuplicadoException(Libro libroExistente) {
        super("El libro \"" + libroExistente.getNombreLibro() + "\" de "
                + libroExistente.getAutor() + " ya existe");
        this.libroExistente = libroExistente;
    }

    /** @return el libro que ya estaba cargado en la base de datos */
    public Libro getLibroExistente() {
        return libroExistente;
    }
}
