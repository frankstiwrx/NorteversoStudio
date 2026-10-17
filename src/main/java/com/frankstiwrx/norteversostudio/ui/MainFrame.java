package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.csv.Csv;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/** Primeira interface: navegação, prévia CSV somente leitura e cópia segura. */
public final class MainFrame extends JFrame {
    private static final Color AZUL = new Color(18, 35, 57);
    private static final Color VERDE = new Color(24, 117, 105);
    private static final Color FUNDO = new Color(246, 248, 251);
    private static final String[] MODULOS = {
            "Jogadores", "Clubes", "CSV", "Gerador", "Banco de nomes", "Ferramentas"
    };

    private final CardLayout cards = new CardLayout();
    private final JPanel painelCentral = new JPanel(cards);
    private final JLabel barraStatus = new JLabel("Pronto. Você pode carregar a base do PES na seção Clubes.");
    private final JLabel arquivoAtual = new JLabel("Nenhum arquivo carregado");
    private final JLabel infoTabela = new JLabel("0 registros • 0 colunas");
    private final JTextField busca = new JTextField(22);
    private final JButton btnAbrir = new JButton("Abrir CSV...");
    private final JButton btnExportar = new JButton("Salvar cópia...");
    private final CsvTableModel modelo = new CsvTableModel();
    private final JTable tabela = new JTable(modelo);
    private final TableRowSorter<CsvTableModel> ordenacao = new TableRowSorter<>(modelo);
    private final ElencosPanel editorElencos = new ElencosPanel(barraStatus::setText);
    private Path caminhoCsv;
    private int totalRegistros;

    public MainFrame() {
        super("Norteverso Studio — Etapa 03");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { tentarSair(); }
        });
        setMinimumSize(new Dimension(850, 550));
        setSize(1370, 820);
        setLocationRelativeTo(null);
        getContentPane().setBackground(FUNDO);
        setLayout(new BorderLayout());
        setJMenuBar(criarMenu());
        add(criarCabecalho(), BorderLayout.NORTH);
        add(criarCorpo(), BorderLayout.CENTER);
        barraStatus.setBorder(BorderFactory.createEmptyBorder(7, 14, 8, 14));
        barraStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(barraStatus, BorderLayout.SOUTH);
        btnExportar.setEnabled(false);
        busca.setEnabled(false);
    }

    private void tentarSair() {
        if (editorElencos.hasPendingExport() && JOptionPane.showConfirmDialog(this,
                "Ha alteracoes de jogadores/times ainda nao exportadas. Sair e descartar essas edicoes?",
                "Alteracoes pendentes", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        dispose();
    }

    private JMenuBar criarMenu() {
        JMenuBar menu = new JMenuBar();
        JMenu arquivo = new JMenu("Arquivo");
        arquivo.add(new JMenuItem(new AbstractAction("Abrir CSV...") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { abrirCsv(); }
        }));
        arquivo.add(new JMenuItem(new AbstractAction("Salvar cópia...") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { salvarCopia(); }
        }));
        arquivo.addSeparator();
        arquivo.add(new JMenuItem(new AbstractAction("Sair") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { tentarSair(); }
        }));
        menu.add(arquivo);
        JMenu ajuda = new JMenu("Ajuda");
        ajuda.add(new JMenuItem(new AbstractAction("Sobre") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                JOptionPane.showMessageDialog(MainFrame.this,
                        "Norteverso Studio — Etapa 03\n" +
                        "Editor de jogadores e times com exportacao segura de CSV.\n" +
                        "Nenhum arquivo original e sobrescrito.",
                        "Sobre", JOptionPane.INFORMATION_MESSAGE);
            }
        }));
        menu.add(ajuda);
        return menu;
    }

    private JPanel criarCabecalho() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AZUL);
        header.setBorder(BorderFactory.createEmptyBorder(17, 24, 17, 24));
        JLabel titulo = new JLabel("NORTEVERSO  STUDIO");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 22));
        titulo.setForeground(Color.WHITE);
        JLabel versao = new JLabel("ETAPA 03  •  EDICAO DE JOGADORES E TIMES");
        versao.setFont(new Font("SansSerif", Font.BOLD, 11));
        versao.setForeground(new Color(178, 209, 226));
        header.add(titulo, BorderLayout.WEST);
        header.add(versao, BorderLayout.EAST);
        return header;
    }

    private JPanel criarCorpo() {
        JPanel corpo = new JPanel(new BorderLayout());
        JList<String> navegacao = new JList<>(MODULOS);
        navegacao.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navegacao.setFont(new Font("SansSerif", Font.PLAIN, 15));
        navegacao.setFixedCellHeight(55);
        navegacao.setBackground(new Color(230, 236, 241));
        navegacao.setSelectionBackground(VERDE);
        navegacao.setSelectionForeground(Color.WHITE);
        navegacao.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,
                    Object value, int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focused);
                label.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 7));
                label.setFont(new Font("SansSerif", selected ? Font.BOLD : Font.PLAIN, 14));
                return label;
            }
        });
        navegacao.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        JPanel esquerda = new JPanel(new BorderLayout());
        esquerda.setPreferredSize(new Dimension(198, 200));
        esquerda.add(navegacao, BorderLayout.CENTER);
        corpo.add(esquerda, BorderLayout.WEST);

        painelCentral.setBackground(FUNDO);
        painelCentral.add(criarJogadores(), "CSV");
        painelCentral.add(editorElencos, "Clubes");
        painelCentral.add(criarAviso("Gerador",
                "Em desenvolvimento: nomes, perfis, atributos e habilidades."), "Gerador");
        painelCentral.add(criarAviso("Banco de nomes",
                "Na próxima etapa, vamos integrar nomes.csv e sobrenomes.csv."), "Banco de nomes");
        painelCentral.add(criarAviso("Ferramentas",
                "Validação detalhada e comparação de CSVs serão integradas aqui."), "Ferramentas");
        navegacao.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String modulo = navegacao.getSelectedValue();
                if (modulo != null) cards.show(painelCentral,
                        modulo.equals("Jogadores") ? "Clubes" : modulo);
            }
        });
        navegacao.setSelectedIndex(0);
        corpo.add(painelCentral, BorderLayout.CENTER);
        return corpo;
    }

    private JPanel criarJogadores() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(FUNDO);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        JPanel topo = new JPanel();
        topo.setBackground(FUNDO);
        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));
        JLabel titulo = new JLabel("Jogadores  /  Pré-visualização CSV");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 21));
        titulo.setForeground(AZUL);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        topo.add(titulo);
        topo.add(Box.createVerticalStrut(5));
        JLabel aviso = new JLabel("Somente leitura — seus arquivos originais não serão modificados.");
        aviso.setForeground(new Color(89, 105, 117));
        aviso.setAlignmentX(Component.LEFT_ALIGNMENT);
        topo.add(aviso);
        topo.add(Box.createVerticalStrut(18));
        JPanel botoes = new JPanel(new BorderLayout(10, 0));
        botoes.setBackground(FUNDO);
        JPanel esquerda = new JPanel(new GridLayout(1, 2, 8, 0));
        btnAbrir.addActionListener(e -> abrirCsv());
        btnExportar.addActionListener(e -> salvarCopia());
        esquerda.add(btnAbrir);
        esquerda.add(btnExportar);
        botoes.add(esquerda, BorderLayout.WEST);
        JPanel filtro = new JPanel(new BorderLayout(6, 0));
        filtro.setBackground(FUNDO);
        filtro.add(new JLabel("Buscar:"), BorderLayout.WEST);
        filtro.add(busca, BorderLayout.CENTER);
        botoes.add(filtro, BorderLayout.EAST);
        botoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        topo.add(botoes);
        topo.add(Box.createVerticalStrut(15));
        arquivoAtual.setForeground(AZUL);
        arquivoAtual.setAlignmentX(Component.LEFT_ALIGNMENT);
        topo.add(arquivoAtual);
        infoTabela.setForeground(new Color(89, 105, 117));
        infoTabela.setAlignmentX(Component.LEFT_ALIGNMENT);
        topo.add(infoTabela);
        panel.add(topo, BorderLayout.NORTH);

        tabela.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabela.setRowHeight(26);
        tabela.setFillsViewportHeight(true);
        tabela.setAutoCreateRowSorter(false);
        tabela.setRowSorter(ordenacao);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(tabela);
        scroll.getViewport().setBackground(Color.WHITE);
        panel.add(scroll, BorderLayout.CENTER);
        busca.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filtrar(); }
            @Override public void removeUpdate(DocumentEvent e) { filtrar(); }
            @Override public void changedUpdate(DocumentEvent e) { filtrar(); }
        });
        return panel;
    }

    private JPanel criarAviso(String titulo, String descricao) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(FUNDO);
        panel.setBorder(BorderFactory.createEmptyBorder(32, 28, 32, 28));
        JPanel texto = new JPanel(new GridLayout(2, 1, 0, 10));
        texto.setBackground(FUNDO);
        JLabel nome = new JLabel(titulo);
        nome.setForeground(AZUL);
        nome.setFont(new Font("SansSerif", Font.BOLD, 24));
        JLabel nota = new JLabel("<html>" + descricao + "</html>");
        nota.setFont(new Font("SansSerif", Font.PLAIN, 15));
        nota.setForeground(new Color(92, 102, 118));
        texto.add(nome);
        texto.add(nota);
        panel.add(texto, BorderLayout.NORTH);
        return panel;
    }

    private void abrirCsv() {
        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Abrir CSV exportado pelo editor do PES");
        seletor.setFileFilter(new FileNameExtensionFilter("Arquivos CSV (*.csv)", "csv"));
        seletor.setAcceptAllFileFilterUsed(true);
        if (seletor.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path escolhido = seletor.getSelectedFile().toPath();
        btnAbrir.setEnabled(false);
        barraStatus.setText("Lendo " + escolhido.getFileName() + "...");
        new SwingWorker<List<List<String>>, Void>() {
            @Override protected List<List<String>> doInBackground() throws Exception {
                return Csv.ler(escolhido);
            }
            @Override protected void done() {
                btnAbrir.setEnabled(true);
                try {
                    List<List<String>> registros = get();
                    if (registros.isEmpty()) throw new IOException("O CSV está vazio.");
                    modelo.carregar(registros);
                    caminhoCsv = escolhido;
                    totalRegistros = registros.size() - 1;
                    busca.setText("");
                    busca.setEnabled(true);
                    btnExportar.setEnabled(true);
                    arquivoAtual.setText("Arquivo: " + escolhido.getFileName());
                    infoTabela.setText(totalRegistros + " registros • " + modelo.getColumnCount() + " colunas");
                    barraStatus.setText("CSV carregado. Prévia somente leitura; nenhum dado foi alterado.");
                } catch (Exception ex) {
                    String mensagem = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    barraStatus.setText("Não foi possível abrir o CSV.");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Falha ao abrir arquivo:\n" + mensagem,
                            "Erro de leitura", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void filtrar() {
        if (!busca.isEnabled()) return;
        String termo = busca.getText();
        ordenacao.setRowFilter(termo.isBlank() ? null :
                RowFilter.regexFilter("(?i)" + Pattern.quote(termo)));
        infoTabela.setText(ordenacao.getViewRowCount() + " de " + totalRegistros +
                " registros • " + modelo.getColumnCount() + " colunas");
    }

    private void salvarCopia() {
        if (caminhoCsv == null) return;
        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Salvar cópia idêntica do CSV original");
        seletor.setSelectedFile(new java.io.File("copia_" + caminhoCsv.getFileName()));
        if (seletor.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destino = seletor.getSelectedFile().toPath();
        try {
            if (Files.isSameFile(caminhoCsv, destino)) {
                JOptionPane.showMessageDialog(this, "Selecione outro nome para a cópia.");
                return;
            }
        } catch (java.nio.file.NoSuchFileException ignored) {
            // Destino ainda não existe: comportamento esperado.
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Erro ao verificar destino: " + ex.getMessage());
            return;
        }
        if (Files.exists(destino) && JOptionPane.showConfirmDialog(this,
                "Substituir o arquivo existente?", "Confirmar",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            Files.copy(caminhoCsv, destino, StandardCopyOption.REPLACE_EXISTING);
            barraStatus.setText("Cópia idêntica salva: " + destino.getFileName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Não foi possível salvar:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static final class CsvTableModel extends AbstractTableModel {
        private List<String> cabecalho = List.of();
        private List<List<String>> linhas = List.of();

        void carregar(List<List<String>> csv) {
            cabecalho = List.copyOf(csv.get(0));
            linhas = new ArrayList<>(csv.subList(1, csv.size()));
            fireTableStructureChanged();
        }

        @Override public int getRowCount() { return linhas.size(); }
        @Override public int getColumnCount() { return cabecalho.size(); }
        @Override public String getColumnName(int coluna) {
            String valor = cabecalho.get(coluna).replace("\ufeff", "").trim();
            return valor.isEmpty() ? "Coluna " + (coluna + 1) : valor;
        }
        @Override public Object getValueAt(int linha, int coluna) {
            List<String> registro = linhas.get(linha);
            return coluna < registro.size() ? registro.get(coluna) : "";
        }
        @Override public boolean isCellEditable(int linha, int coluna) { return false; }
    }
}
