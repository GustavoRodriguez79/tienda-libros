package utn.tienda_libros.vista;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import utn.tienda_libros.modelo.Libro;
import utn.tienda_libros.servicio.LibroServicio;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formulario principal de la aplicación "Tienda de Libros".
 * <p>
 * Permite listar, agregar, modificar y eliminar libros, usando {@link LibroServicio}
 * como intermediario con la base de datos. La tabla ({@link #tablaLibros}) funciona
 * tanto para mostrar los registros como para seleccionar cuál se va a modificar
 * o eliminar: al hacer clic en una fila, sus datos se cargan automáticamente en
 * los campos de texto del formulario.
 * </p>
 * <p>
 * Características de robustez y usabilidad:
 * </p>
 * <ul>
 *     <li>Solo se puede seleccionar <b>una</b> fila a la vez ({@link ListSelectionModel#SINGLE_SELECTION}).</li>
 *     <li>Las celdas de la tabla no son editables: los cambios se hacen desde los campos de texto.</li>
 *     <li>Validación de los datos ingresados (campos vacíos, números inválidos, valores negativos).</li>
 *     <li>Confirmación antes de eliminar un libro y antes de salir de la aplicación.</li>
 *     <li>Los botones Modificar y Eliminar solo se habilitan cuando hay un libro seleccionado.</li>
 * </ul>
 */
@Component
public class LibroFrom extends JFrame {

    /** Precio máximo permitido: la columna en MySQL es DECIMAL(10,2). */
    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("99999999.99");

    LibroServicio libroServicio;
    private JPanel panel;
    private JTable tablaLibros;
    private JTextField libroTexto;
    private JTextField autorTexto;
    private JTextField precioTexto;
    private JTextField existenciasTexto;
    private JButton agregarButton;
    private JButton modificarButton;
    private JButton eliminarButton;
    private JButton limpiarButton;
    private JButton salirButton;
    private DefaultTableModel tablaModeloLibros;

    /**
     * Id del libro actualmente seleccionado en la tabla.
     * <p>
     * Es {@code null} cuando no hay ninguna fila seleccionada (por ejemplo, justo
     * después de agregar un libro nuevo o de limpiar el formulario). Los botones
     * "Modificar" y "Eliminar" dependen de este valor para saber sobre qué
     * registro de la base de datos deben operar.
     * </p>
     */
    private Integer idSeleccionado;


    /**
     * Crea el formulario e inyecta el servicio de libros a través de Spring.
     * Acá también se conectan los botones con su lógica correspondiente.
     *
     * @param libroServicio servicio inyectado por Spring, con la lógica de negocio sobre Libro
     */
    @Autowired
    public LibroFrom(LibroServicio libroServicio){
        this.libroServicio = libroServicio;
        iniciarForma();
        agregarButton.addActionListener(e -> agregarLibro());
        modificarButton.addActionListener(e -> modificarLibro());
        eliminarButton.addActionListener(e -> eliminarLibro());
        limpiarButton.addActionListener(e -> nuevoRegistro());
        salirButton.addActionListener(e -> salir());

        // La "X" de la ventana pasa por el mismo flujo (con confirmación) que el botón Salir
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                salir();
            }
        });
        actualizarEstadoBotones();
    }

    /**
     * Configura la ventana: título, tamaño, comportamiento al cerrar, y la centra
     * en la pantalla del usuario.
     */
    private void iniciarForma(){
        setContentPane(panel);
        setTitle("Tienda de Libros");
        // El cierre lo maneja windowClosing(), para poder pedir confirmación
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null); // centrada en la pantalla
        setVisible(true);
    }

    /**
     * Crea un libro nuevo a partir de los datos del formulario y lo guarda en la base.
     * <p>
     * Como el libro es nuevo, se pasa {@code null} como id al construirlo — JPA
     * interpreta eso como "insertar un registro nuevo" y genera el id automáticamente.
     * </p>
     */
    private void agregarLibro(){
        var libro = leerLibroDelFormulario(null);
        if(libro == null){
            return; // los datos no eran válidos; el mensaje ya se mostró
        }
        try {
            this.libroServicio.guardarLibro(libro);
            listarLibros();
            nuevoRegistro();
            mostrarMensaje("Se agregó el libro correctamente");
        } catch (Exception ex) {
            mostrarError("No se pudo agregar el libro.", ex);
        }
    }

    /**
     * Actualiza el libro seleccionado en la tabla con los datos actuales del formulario.
     * <p>
     * A diferencia de {@link #agregarLibro()}, acá se reutiliza {@link #idSeleccionado}
     * (el id real que ya existe en la base) al construir el objeto {@link Libro}.
     * Como ese id ya existe, {@code libroServicio.guardarLibro(libro)} hace un
     * UPDATE en vez de un INSERT — es el mismo método de servicio para ambos casos,
     * la diferencia está únicamente en si el id es nulo o no.
     * </p>
     */
    private void modificarLibro(){
        if(idSeleccionado == null){
            mostrarMensaje("Seleccione un libro de la tabla para modificar");
            return;
        }
        // Reutilizamos el id ya existente para que JPA haga UPDATE en vez de INSERT
        var libro = leerLibroDelFormulario(idSeleccionado);
        if(libro == null){
            return;
        }
        try {
            this.libroServicio.guardarLibro(libro);
            listarLibros();
            nuevoRegistro();
            mostrarMensaje("Libro modificado correctamente");
        } catch (Exception ex) {
            mostrarError("No se pudo modificar el libro.", ex);
        }
    }

    /**
     * Elimina de la base de datos el libro actualmente seleccionado en la tabla,
     * previa confirmación del usuario.
     * <p>
     * Como {@code libroServicio.eliminarLibro()} necesita el objeto {@link Libro}
     * completo (no solo el id), primero se busca el libro con
     * {@code buscarLibrosPorId(idSeleccionado)} antes de borrarlo.
     * </p>
     */
    private void eliminarLibro(){
        if(idSeleccionado == null){
            mostrarMensaje("Seleccione un libro de la tabla para eliminar");
            return;
        }
        try {
            var libro = libroServicio.buscarLibrosPorId(idSeleccionado);
            if(libro == null){
                mostrarMensaje("No se encontró el libro a eliminar");
                listarLibros();
                nuevoRegistro();
                return;
            }
            int respuesta = JOptionPane.showConfirmDialog(this,
                    "¿Seguro que desea eliminar el libro \"" + libro.getNombreLibro() + "\"?\n"
                            + "Esta acción no se puede deshacer.",
                    "Confirmar eliminación",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if(respuesta != JOptionPane.YES_OPTION){
                return;
            }
            this.libroServicio.eliminarLibro(libro);
            listarLibros();
            nuevoRegistro();
            mostrarMensaje("Libro eliminado correctamente");
        } catch (Exception ex) {
            mostrarError("No se pudo eliminar el libro.", ex);
        }
    }

    /**
     * Cierra la aplicación, previa confirmación del usuario.
     * Lo usan tanto el botón "Salir" como la "X" de la ventana.
     */
    private void salir(){
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Desea salir de la aplicación?",
                "Salir",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if(respuesta == JOptionPane.YES_OPTION){
            dispose();
            System.exit(0);
        }
    }

    /**
     * Lee y valida los 4 campos del formulario y arma el objeto {@link Libro}.
     * <p>
     * Validaciones: nombre y autor no vacíos; precio numérico, mayor a cero y que
     * entre en DECIMAL(10,2); existencias entero y no negativo. Acepta coma o punto
     * como separador decimal. Ante el primer dato inválido muestra un aviso, deja
     * el cursor en ese campo y devuelve {@code null}.
     * </p>
     *
     * @param id id del libro (o {@code null} si es uno nuevo)
     * @return el libro listo para guardar, o {@code null} si hubo algún dato inválido
     */
    private Libro leerLibroDelFormulario(Integer id){
        String nombre = libroTexto.getText().trim();
        String autor = autorTexto.getText().trim();
        String precioStr = precioTexto.getText().trim().replace(',', '.');
        String existenciasStr = existenciasTexto.getText().trim();

        if(nombre.isEmpty()){
            advertir("Ingrese el nombre del libro", libroTexto);
            return null;
        }
        if(autor.isEmpty()){
            advertir("Ingrese el autor del libro", autorTexto);
            return null;
        }
        if(precioStr.isEmpty()){
            advertir("Ingrese el precio del libro", precioTexto);
            return null;
        }

        BigDecimal precio;
        try {
            precio = new BigDecimal(precioStr).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            advertir("El precio debe ser un número válido (ejemplo: 1500.50)", precioTexto);
            return null;
        }
        if(precio.signum() <= 0){
            advertir("El precio debe ser mayor a cero", precioTexto);
            return null;
        }
        if(precio.compareTo(PRECIO_MAXIMO) > 0){
            advertir("El precio es demasiado grande (máximo " + PRECIO_MAXIMO + ")", precioTexto);
            return null;
        }

        if(existenciasStr.isEmpty()){
            advertir("Ingrese las existencias del libro", existenciasTexto);
            return null;
        }
        int existencias;
        try {
            existencias = Integer.parseInt(existenciasStr);
        } catch (NumberFormatException ex) {
            advertir("Las existencias deben ser un número entero (ejemplo: 25)", existenciasTexto);
            return null;
        }
        if(existencias < 0){
            advertir("Las existencias no pueden ser negativas", existenciasTexto);
            return null;
        }

        return new Libro(id, nombre, autor, precio, existencias);
    }

    /**
     * Deja el formulario listo para cargar un libro nuevo: vacía los campos,
     * quita la selección de la tabla y deshabilita Modificar/Eliminar.
     */
    private void nuevoRegistro(){
        limpiarFormulario();
        idSeleccionado = null;
        tablaLibros.clearSelection();
        actualizarEstadoBotones();
        libroTexto.requestFocusInWindow();
    }

    /**
     * Vacía los 4 campos de texto del formulario.
     * No toca {@link #idSeleccionado} a propósito — eso lo maneja {@link #nuevoRegistro()}.
     */
    private void limpiarFormulario(){
        libroTexto.setText("");
        autorTexto.setText("");
        precioTexto.setText("");
        existenciasTexto.setText("");
    }

    /**
     * Habilita "Modificar" y "Eliminar" solo si hay un libro seleccionado, y hace que
     * la tecla Enter active el botón que corresponde (Modificar si hay selección,
     * Agregar en caso contrario).
     */
    private void actualizarEstadoBotones(){
        // Puede llamarse antes de que los botones existan (durante la construcción)
        if(modificarButton == null || eliminarButton == null){
            return;
        }
        boolean haySeleccion = idSeleccionado != null;
        modificarButton.setEnabled(haySeleccion);
        eliminarButton.setEnabled(haySeleccion);
        getRootPane().setDefaultButton(haySeleccion ? modificarButton : agregarButton);
    }

    /**
     * Muestra un mensaje informativo al usuario.
     *
     * @param mensaje texto a mostrar en el diálogo
     */
    private void mostrarMensaje(String mensaje){
        JOptionPane.showMessageDialog(this, mensaje, "Tienda de Libros", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Muestra un aviso de dato inválido y deja el cursor, con el texto seleccionado,
     * en el campo que hay que corregir.
     *
     * @param mensaje explicación del problema
     * @param campo   campo de texto a enfocar
     */
    private void advertir(String mensaje, JTextField campo){
        JOptionPane.showMessageDialog(this, mensaje, "Dato inválido", JOptionPane.WARNING_MESSAGE);
        campo.requestFocusInWindow();
        campo.selectAll();
    }

    /**
     * Muestra un error inesperado (por ejemplo, la base de datos no responde).
     *
     * @param mensaje descripción de lo que se intentaba hacer
     * @param ex      excepción original, de la que se muestra el detalle
     */
    private void mostrarError(String mensaje, Exception ex){
        JOptionPane.showMessageDialog(this,
                mensaje + "\nDetalle: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Método especial reconocido por el GUI Designer de IntelliJ: se ejecuta
     * antes de que el formulario termine de construirse, y es donde se
     * instancian a mano los componentes que necesitan configuración extra
     * (acá, el {@link JTable} con su modelo, anchos de columna, alineación
     * y el listener de selección de fila).
     */
    private void createUIComponents() {
        // Modelo con celdas NO editables: evita que un doble clic deje una celda en modo edición
        this.tablaModeloLibros = new DefaultTableModel(0, 5) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
        String[] cabecera = {"Id", "Libro", "Autor", "Precio", "Existencias"};
        this.tablaModeloLibros.setColumnIdentifiers(cabecera);
        //Instanciar el objeto de JTable
        this.tablaLibros = new JTable(tablaModeloLibros);

        // Solo una fila a la vez: Modificar/Eliminar trabajan sobre un único libro
        tablaLibros.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Evita que el usuario reordene las columnas arrastrando el encabezado
        tablaLibros.getTableHeader().setReorderingAllowed(false);

        // Ajuste el ancho de cada columna
        tablaLibros.getColumnModel().getColumn(0).setPreferredWidth(40);   // Id
        tablaLibros.getColumnModel().getColumn(1).setPreferredWidth(180);  // Libro
        tablaLibros.getColumnModel().getColumn(2).setPreferredWidth(150);  // Autor
        tablaLibros.getColumnModel().getColumn(3).setPreferredWidth(70);   // Precio
        tablaLibros.getColumnModel().getColumn(4).setPreferredWidth(80);   // Existencias

        // Renderer para centrar: Id, Precio, Existencias
        DefaultTableCellRenderer centrado = new DefaultTableCellRenderer();
        centrado.setHorizontalAlignment(SwingConstants.CENTER);
        tablaLibros.getColumnModel().getColumn(0).setCellRenderer(centrado); // Id
        tablaLibros.getColumnModel().getColumn(3).setCellRenderer(centrado); // Precio
        tablaLibros.getColumnModel().getColumn(4).setCellRenderer(centrado); // Existencias

        // Al hacer clic en una fila, copiamos sus datos a los campos de texto
        // y guardamos el id en idSeleccionado, para que Modificar/Eliminar sepan
        // sobre qué libro trabajar.
        tablaLibros.getSelectionModel().addListSelectionListener(e -> {
            // getValueIsAdjusting() evita que esto se dispare varias veces mientras
            // el usuario todavía está arrastrando el mouse sobre las filas.
            if(!e.getValueIsAdjusting() && tablaLibros.getSelectedRow() != -1){
                int fila = tablaLibros.getSelectedRow();
                idSeleccionado = (Integer) tablaModeloLibros.getValueAt(fila, 0);
                libroTexto.setText(tablaModeloLibros.getValueAt(fila, 1).toString());
                autorTexto.setText(tablaModeloLibros.getValueAt(fila, 2).toString());
                precioTexto.setText(tablaModeloLibros.getValueAt(fila, 3).toString());
                existenciasTexto.setText(tablaModeloLibros.getValueAt(fila, 4).toString());
                actualizarEstadoBotones();
            }
        });

        listarLibros();
    }

    /**
     * Recarga la tabla con el listado completo de libros desde la base de datos.
     * Se llama al iniciar el formulario y después de cualquier operación que
     * cambie los datos (agregar, modificar, eliminar), para que la tabla
     * siempre refleje el estado actual de la base.
     */
    private void listarLibros(){
        //Limpiar la tabla
        tablaModeloLibros.setRowCount(0);
        //Obrener los libros de la BD
        var libros = libroServicio.listarLibros();
        //Iteramos cada libro
        libros.forEach((libro) ->{//Función Lambda
            // Creamos cada registro para agregarlos a la tabla
            Object [] renglonLibro = {
                    libro.getIdLibro(),
                    libro.getNombreLibro(),
                    libro.getAutor(),
                    libro.getPrecio(),
                    libro.getExistencias(),
            };
            this.tablaModeloLibros.addRow(renglonLibro);
        });

    }
}
