package utn.tienda_libros.vista;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import utn.tienda_libros.excepcion.LibroDuplicadoException;
import utn.tienda_libros.excepcion.LibroInvalidoException;
import utn.tienda_libros.modelo.Libro;
import utn.tienda_libros.servicio.ILibroServicio;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Formulario principal de la aplicación "Tienda de Libros".
 * <p>
 * Permite listar, agregar, modificar y eliminar libros, usando {@link ILibroServicio}
 * como intermediario con la base de datos. La tabla ({@link #tablaLibros}) funciona
 * tanto para mostrar los registros como para seleccionar cuál se va a modificar
 * o eliminar: al hacer clic en una fila, sus datos se cargan automáticamente en
 * los campos de texto del formulario.
 * </p>
 * <p>
 * Esta clase solo se ocupa de la pantalla: lee lo que escribe el usuario, llama al
 * servicio y muestra el resultado. Las reglas de negocio (validaciones, duplicados,
 * cálculos) están en {@code LibroServicio}.
 * </p>
 * <ul>
 *     <li>Solo se puede seleccionar <b>una</b> fila a la vez y las celdas no son editables.</li>
 *     <li>Si se intenta agregar un libro que ya existe, se ofrece sumar existencias o cambiar el precio.</li>
 *     <li>Confirmación antes de eliminar y antes de salir.</li>
 *     <li>Los errores inesperados se registran en el log y se muestran con un mensaje claro.</li>
 *     <li>Buscador por título o autor, resaltado de stock bajo y resumen del inventario.</li>
 * </ul>
 */
@Component
@Lazy // se crea recién cuando main() la pide, ya dentro del hilo de Swing (EDT)
public class LibroFrom extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(LibroFrom.class);

    /** Los libros con menos existencias que este valor se resaltan en rojo. */
    private static final int STOCK_BAJO = 5;

    private final ILibroServicio libroServicio;
    private final ApplicationContext contexto;
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
    private JTextField buscarTexto;
    private JLabel resumenLabel;
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
     * @param contexto      contexto de Spring, necesario para cerrar la aplicación de forma ordenada
     */
    @Autowired
    public LibroFrom(ILibroServicio libroServicio, ApplicationContext contexto){
        this.libroServicio = libroServicio;
        this.contexto = contexto;
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

        // Cada vez que cambia el texto del buscador se vuelve a filtrar la tabla
        buscarTexto.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                alCambiarBusqueda();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                alCambiarBusqueda();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                alCambiarBusqueda();
            }
        });

        // La tabla se carga acá (y no en createUIComponents) para que el formulario
        // ya esté completamente armado cuando se consulta la base de datos.
        listarLibros();
        actualizarEstadoBotones();
    }

    /**
     * Configura la ventana: título, tamaño, comportamiento al cerrar, y la centra
     * en la pantalla del usuario. No la muestra: de eso se encarga
     * {@code TiendaLibrosApplication} cuando la tabla ya está cargada.
     */
    private void iniciarForma(){
        setContentPane(panel);
        setTitle("Tienda de Libros");
        // El cierre lo maneja windowClosing(), para poder pedir confirmación
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null); // centrada en la pantalla
    }

    /**
     * Crea un libro nuevo a partir de los datos del formulario y lo guarda en la base.
     * <p>
     * Como el libro es nuevo, se pasa {@code null} como id al construirlo — JPA
     * interpreta eso como "insertar un registro nuevo" y genera el id automáticamente.
     * Si el servicio avisa que ese libro ya existe, se llama a {@link #resolverDuplicado(Libro)}.
     * </p>
     */
    private void agregarLibro(){
        Libro libro = leerLibroDelFormulario(null);
        if(libro == null){
            return; // el dato no era un número válido; el aviso ya se mostró
        }
        try {
            this.libroServicio.guardarLibro(libro);
            listarLibros();
            nuevoRegistro();
            mostrarMensaje("Se agregó el libro correctamente");
        } catch (LibroDuplicadoException ex) {
            resolverDuplicado(ex.getLibroExistente());
        } catch (LibroInvalidoException ex) {
            advertir(ex.getMessage(), campoPara(ex.getCampo()));
        } catch (Exception ex) {
            mostrarError("No se pudo agregar el libro.", ex);
        }
    }

    /**
     * Actualiza el libro seleccionado en la tabla con los datos actuales del formulario.
     * <p>
     * A diferencia de {@link #agregarLibro()}, acá se reutiliza {@link #idSeleccionado}
     * (el id real que ya existe en la base) al construir el objeto {@link Libro}.
     * Como ese id ya existe, {@code guardarLibro(libro)} hace un UPDATE en vez de un
     * INSERT — es el mismo método de servicio para ambos casos, la diferencia está
     * únicamente en si el id es nulo o no.
     * </p>
     */
    private void modificarLibro(){
        if(idSeleccionado == null){
            mostrarMensaje("Seleccione un libro de la tabla para modificar");
            return;
        }
        Libro libro = leerLibroDelFormulario(idSeleccionado);
        if(libro == null){
            return;
        }
        try {
            this.libroServicio.guardarLibro(libro);
            listarLibros();
            nuevoRegistro();
            mostrarMensaje("Libro modificado correctamente");
        } catch (LibroDuplicadoException ex) {
            advertir(ex.getMessage() + ". Cambie el nombre o el autor.", libroTexto);
        } catch (LibroInvalidoException ex) {
            advertir(ex.getMessage(), campoPara(ex.getCampo()));
        } catch (Exception ex) {
            mostrarError("No se pudo modificar el libro.", ex);
        }
    }

    /**
     * Elimina de la base de datos el libro actualmente seleccionado en la tabla,
     * previa confirmación del usuario.
     * <p>
     * Como {@code eliminarLibro()} necesita el objeto {@link Libro} completo
     * (no solo el id), primero se busca el libro con
     * {@code buscarLibrosPorId(idSeleccionado)} antes de borrarlo.
     * </p>
     */
    private void eliminarLibro(){
        if(idSeleccionado == null){
            mostrarMensaje("Seleccione un libro de la tabla para eliminar");
            return;
        }
        try {
            Libro libro = libroServicio.buscarLibrosPorId(idSeleccionado);
            if(libro == null){
                mostrarMensaje("No se encontró el libro a eliminar. Quizás ya lo borró otra persona.");
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
     * Se usa cuando el usuario intenta agregar un libro que ya existe.
     * Muestra los datos actuales y ofrece sumar existencias, cambiar el precio o cancelar.
     *
     * @param existente el libro que ya estaba cargado en la base
     */
    private void resolverDuplicado(Libro existente){
        String[] opciones = {"Sumar existencias", "Cambiar precio", "Cancelar"};
        int opcion = JOptionPane.showOptionDialog(this,
                "El libro \"" + existente.getNombreLibro() + "\" de " + existente.getAutor() + " ya existe.\n\n"
                        + "Existencias actuales: " + existente.getExistencias() + "\n"
                        + "Precio actual: " + existente.getPrecio() + "\n\n"
                        + "¿Qué desea hacer?",
                "Libro existente",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, opciones, opciones[2]);
        if(opcion == 0){
            sumarExistenciasAlExistente(existente);
        } else if(opcion == 1){
            cambiarPrecioDelExistente(existente);
        }
        // Cancelar, o cerrar el diálogo con la X: no se hace nada
    }

    /**
     * Pide cuántas unidades sumar (propone las que se escribieron en el campo
     * Existencia) y las suma al libro que ya existía.
     */
    private void sumarExistenciasAlExistente(Libro existente){
        Object entrada = JOptionPane.showInputDialog(this,
                "¿Cuántas unidades desea sumar a \"" + existente.getNombreLibro() + "\"?",
                "Sumar existencias", JOptionPane.QUESTION_MESSAGE,
                null, null, existenciasTexto.getText().trim());
        if(entrada == null){
            return; // canceló
        }
        try {
            int cantidad = Integer.parseInt(entrada.toString().trim());
            libroServicio.sumarExistencias(existente.getIdLibro(), cantidad);
            listarLibros();
            nuevoRegistro();
            mostrarMensaje("Se sumaron " + cantidad + " unidades a \"" + existente.getNombreLibro() + "\"");
        } catch (NumberFormatException ex) {
            mostrarAdvertencia("La cantidad debe ser un número entero (ejemplo: 10)");
        } catch (LibroInvalidoException ex) {
            mostrarAdvertencia(ex.getMessage());
        } catch (Exception ex) {
            mostrarError("No se pudieron sumar las existencias.", ex);
        }
    }

    /**
     * Pide el precio nuevo (propone el que se escribió en el campo Precio)
     * y se lo cambia al libro que ya existía.
     */
    private void cambiarPrecioDelExistente(Libro existente){
        Object entrada = JOptionPane.showInputDialog(this,
                "Nuevo precio de \"" + existente.getNombreLibro() + "\":",
                "Cambiar precio", JOptionPane.QUESTION_MESSAGE,
                null, null, precioTexto.getText().trim());
        if(entrada == null){
            return; // canceló
        }
        try {
            BigDecimal precio = new BigDecimal(entrada.toString().trim().replace(',', '.'));
            libroServicio.actualizarPrecio(existente.getIdLibro(), precio);
            listarLibros();
            nuevoRegistro();
            mostrarMensaje("Se actualizó el precio de \"" + existente.getNombreLibro() + "\"");
        } catch (NumberFormatException ex) {
            mostrarAdvertencia("El precio debe ser un número válido (ejemplo: 1500.50)");
        } catch (LibroInvalidoException ex) {
            mostrarAdvertencia(ex.getMessage());
        } catch (Exception ex) {
            mostrarError("No se pudo cambiar el precio.", ex);
        }
    }

    /**
     * Cierra la aplicación, previa confirmación del usuario.
     * Lo usan tanto el botón "Salir" como la "X" de la ventana.
     * <p>
     * {@link SpringApplication#exit(ApplicationContext, org.springframework.boot.ExitCodeGenerator...)}
     * cierra el contexto de Spring (y con él las conexiones a la base de datos)
     * antes de terminar el programa.
     * </p>
     */
    private void salir(){
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Desea salir de la aplicación?",
                "Salir",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if(respuesta == JOptionPane.YES_OPTION){
            dispose();
            System.exit(SpringApplication.exit(contexto));
        }
    }

    /**
     * Lee los 4 campos del formulario y arma el objeto {@link Libro}.
     * <p>
     * Acá solo se convierten los textos a números (precio y existencias). Si no se puede
     * convertir, se avisa y se devuelve {@code null}. Todo lo demás (campos vacíos,
     * rangos, largos, duplicados) lo valida {@code LibroServicio}. Un campo numérico vacío
     * se envía como {@code null} y el servicio responde con el aviso correspondiente.
     * </p>
     *
     * @param id id del libro (o {@code null} si es uno nuevo)
     * @return el libro armado, o {@code null} si el precio o las existencias no eran números
     */
    private Libro leerLibroDelFormulario(Integer id){
        BigDecimal precio = null;
        String textoPrecio = precioTexto.getText().trim().replace(',', '.');
        if(!textoPrecio.isEmpty()){
            try {
                precio = new BigDecimal(textoPrecio);
            } catch (NumberFormatException ex) {
                advertir("El precio debe ser un número válido (ejemplo: 1500.50)", precioTexto);
                return null;
            }
        }

        Integer existencias = null;
        String textoExistencias = existenciasTexto.getText().trim();
        if(!textoExistencias.isEmpty()){
            try {
                existencias = Integer.valueOf(textoExistencias);
            } catch (NumberFormatException ex) {
                advertir("Las existencias deben ser un número entero (ejemplo: 25)", existenciasTexto);
                return null;
            }
        }

        return new Libro(id, libroTexto.getText(), autorTexto.getText(), precio, existencias);
    }

    /**
     * Traduce el nombre de campo que informa el servicio al componente de la pantalla.
     *
     * @return el campo de texto correspondiente, o {@code null} si el error no es de un campo puntual
     */
    private JTextField campoPara(String campo){
        if(campo == null){
            return null;
        }
        return switch (campo) {
            case LibroInvalidoException.CAMPO_NOMBRE -> libroTexto;
            case LibroInvalidoException.CAMPO_AUTOR -> autorTexto;
            case LibroInvalidoException.CAMPO_PRECIO -> precioTexto;
            case LibroInvalidoException.CAMPO_EXISTENCIAS -> existenciasTexto;
            default -> null;
        };
    }

    /**
     * Deja el formulario listo para cargar un libro nuevo: vacía los campos,
     * quita la selección de la tabla, deshabilita Modificar/Eliminar y
     * deja el cursor en el campo Libro.
     */
    private void nuevoRegistro(){
        deseleccionar();
        libroTexto.requestFocusInWindow();
    }

    /**
     * Vacía el formulario y quita la selección de la tabla, sin mover el cursor.
     */
    private void deseleccionar(){
        limpiarFormulario();
        idSeleccionado = null;
        tablaLibros.clearSelection();
        actualizarEstadoBotones();
    }

    /**
     * Vacía los 4 campos de texto del formulario.
     * No toca {@link #idSeleccionado} a propósito — eso lo maneja {@link #deseleccionar()}.
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
     * Muestra un aviso de advertencia (sin mover el cursor).
     */
    private void mostrarAdvertencia(String mensaje){
        JOptionPane.showMessageDialog(this, mensaje, "Dato inválido", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Muestra un aviso de dato inválido y deja el cursor, con el texto seleccionado,
     * en el campo que hay que corregir.
     *
     * @param mensaje explicación del problema
     * @param campo   campo de texto a enfocar (puede ser {@code null})
     */
    private void advertir(String mensaje, JTextField campo){
        mostrarAdvertencia(mensaje);
        if(campo != null){
            campo.requestFocusInWindow();
            campo.selectAll();
        }
    }

    /**
     * Muestra un error inesperado (por ejemplo, la base de datos no responde).
     * El detalle técnico no se le muestra al usuario: se guarda en el log.
     *
     * @param mensaje descripción de lo que se intentaba hacer
     * @param ex      excepción original, que se registra en el log
     */
    private void mostrarError(String mensaje, Exception ex){
        log.error(mensaje, ex);
        JOptionPane.showMessageDialog(this,
                mensaje + "\nRevise la conexión con la base de datos e intente nuevamente.",
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

        // Existencias: centradas, y en rojo y negrita cuando el stock es bajo
        DefaultTableCellRenderer stock = new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable tabla, Object valor,
                    boolean seleccionada, boolean conFoco, int fila, int columna) {
                java.awt.Component celda = super.getTableCellRendererComponent(
                        tabla, valor, seleccionada, conFoco, fila, columna);
                boolean bajo = valor instanceof Integer cantidad && cantidad < STOCK_BAJO;
                // Swing reutiliza el mismo renderer para todas las filas: hay que dejar
                // SIEMPRE el estilo normal y recien despues pintar de rojo.
                celda.setFont(tabla.getFont());
                if(!seleccionada){
                    celda.setForeground(tabla.getForeground());
                }
                if(bajo){
                    celda.setFont(tabla.getFont().deriveFont(Font.BOLD));
                    if(!seleccionada){
                        celda.setForeground(Color.RED);
                    }
                }
                return celda;
            }
        };
        stock.setHorizontalAlignment(SwingConstants.CENTER);
        tablaLibros.getColumnModel().getColumn(4).setCellRenderer(stock); // Existencias

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
    }

    /**
     * Cada vez que cambia el texto del buscador: si había un libro seleccionado se
     * deselecciona (porque la tabla va a cambiar) y se vuelve a cargar la lista filtrada.
     */
    private void alCambiarBusqueda(){
        if(idSeleccionado != null){
            deseleccionar();
        }
        listarLibros();
    }

    /**
     * Recarga la tabla desde la base de datos, aplicando el filtro del buscador
     * (por título o autor) si hay algún texto escrito. Se llama al iniciar el formulario,
     * al cambiar el buscador y después de cualquier operación que cambie los datos
     * (agregar, modificar, eliminar), para que la tabla siempre refleje el estado actual.
     */
    private void listarLibros(){
        try {
            //Limpiar la tabla
            tablaModeloLibros.setRowCount(0);
            //Obrener los libros de la BD
            List<Libro> todos = libroServicio.listarLibros();
            String filtro = buscarTexto == null ? "" : buscarTexto.getText().trim();
            List<Libro> mostrados = filtro.isEmpty() ? todos : libroServicio.buscarLibros(filtro);
            //Iteramos cada libro
            mostrados.forEach(this::agregarFila);
            actualizarResumen(todos, mostrados.size());
        } catch (Exception ex) {
            mostrarError("No se pudo cargar la lista de libros.", ex);
        }
    }

    /**
     * Actualiza el texto de abajo con la cantidad de libros, cuántos tienen stock bajo
     * y el valor total del inventario (precio por existencias de todos los libros).
     *
     * @param todos     todos los libros de la base (el valor del inventario no depende del filtro)
     * @param mostrados cuántos libros se están viendo en la tabla
     */
    private void actualizarResumen(List<Libro> todos, int mostrados){
        if(resumenLabel == null){
            return; // el formulario todavía se está armando
        }
        long stockBajo = todos.stream()
                .filter(libro -> libro.getExistencias() != null && libro.getExistencias() < STOCK_BAJO)
                .count();
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "AR"));
        String valorTotal = moneda.format(libroServicio.calcularValorInventario(todos));
        resumenLabel.setText("Mostrando " + mostrados + " de " + todos.size() + " libros   |   "
                + "Stock bajo (menos de " + STOCK_BAJO + "): " + stockBajo + "   |   "
                + "Valor total del inventario: " + valorTotal);
    }

    /**
     * Agrega un libro como una fila nueva de la tabla.
     */
    private void agregarFila(Libro libro){
        // Creamos cada registro para agregarlos a la tabla
        Object [] renglonLibro = {
                libro.getIdLibro(),
                libro.getNombreLibro(),
                libro.getAutor(),
                libro.getPrecio(),
                libro.getExistencias(),
        };
        this.tablaModeloLibros.addRow(renglonLibro);
    }
}
