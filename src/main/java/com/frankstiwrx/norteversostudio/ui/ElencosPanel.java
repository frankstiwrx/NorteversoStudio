package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PesDatabase.RosterEntry;
import com.frankstiwrx.norteversostudio.data.PesDatabase.Team;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/** Explora e edita jogadores/times em memoria, sem tocar nos CSVs originais. */
public final class ElencosPanel extends JPanel {
    private static final Color AZUL = new Color(18, 35, 57);
    private static final Color VERDE = new Color(24, 117, 105);
    private static final Color FUNDO = new Color(246, 248, 251);

    private final Consumer<String> globalStatus;
    private final JButton loadButton = new JButton("Carregar jogadores + times + atribuições...");
    private final JLabel summary = new JLabel("Nenhuma base carregada.");
    private final JLabel selectedTeam = new JLabel("Selecione um clube para visualizar o elenco.");
    private final JLabel playerDetails = new JLabel(" ");
    private final JLabel unsaved = new JLabel("Sem alteracoes pendentes.");
    private final JButton editPlayerButton = new JButton("Editar jogador...");
    private final JButton editTeamButton = new JButton("Editar time...");
    private final JButton assignmentButton = new JButton("Camisa / capitão...");
    private final JButton transferButton = new JButton("Transferir...");
    private final JButton formationButton = new JButton("Formação...");
    private final JButton exportButton = new JButton("Exportar 3 CSVs...");
    private final JTextField teamSearch = new JTextField();
    private final JTextField playerSearch = new JTextField();
    private final JCheckBox includeNational = new JCheckBox("Incluir seleções", false);
    private final JComboBox<String> origin = new JComboBox<>(new String[] {
            "Todos os países", "Base Dinamarca (203)", "Base Escócia (232)", "Base Rússia (230)"
    });
    private final DefaultListModel<Team> teamListModel = new DefaultListModel<>();
    private final JList<Team> teamList = new JList<>(teamListModel);
    private final RosterTableModel rosterTableModel = new RosterTableModel();
    private final JTable rosterTable = new JTable(rosterTableModel);
    private final TableRowSorter<RosterTableModel> rosterSorter = new TableRowSorter<>(rosterTableModel);
    private PesDatabase database;
    private File lastDirectory;

    public ElencosPanel(Consumer<String> globalStatus) {
        this.globalStatus = globalStatus;
        setLayout(new BorderLayout(0, 14));
        setBackground(FUNDO);
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        add(createHeader(), BorderLayout.NORTH);
        add(createExplorer(), BorderLayout.CENTER);
        add(createDetails(), BorderLayout.SOUTH);
        registerListeners();
        editPlayerButton.setEnabled(false);
        editTeamButton.setEnabled(false);
        assignmentButton.setEnabled(false);
        transferButton.setEnabled(false);
        formationButton.setEnabled(false);
        exportButton.setEnabled(false);
    }

    private JPanel createHeader() {
        JPanel top = new JPanel();
        top.setBackground(FUNDO);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Editor de jogadores e times  /  PES");
        title.setForeground(AZUL);
        title.setFont(new Font("SansSerif", Font.BOLD, 21));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(5));
        JLabel help = new JLabel("Edicao local • IDs e aparencias protegidos • Exporte copias antes de importar no Ejogc327");
        help.setForeground(new Color(92, 102, 118));
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(help);
        top.add(Box.createVerticalStrut(13));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        toolbar.setBackground(FUNDO);
        loadButton.addActionListener(e -> chooseFiles());
        editPlayerButton.addActionListener(e -> editSelectedPlayer());
        editTeamButton.addActionListener(e -> editSelectedTeam());
        assignmentButton.addActionListener(e -> editSelectedAssignment());
        transferButton.addActionListener(e -> transferSelected());
        formationButton.addActionListener(e -> editFormation());
        exportButton.addActionListener(e -> exportEdited());
        toolbar.add(loadButton);
        toolbar.add(editPlayerButton);
        toolbar.add(editTeamButton);
        toolbar.add(assignmentButton);
        toolbar.add(transferButton);
        toolbar.add(formationButton);
        toolbar.add(exportButton);
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(toolbar);
        top.add(Box.createVerticalStrut(9));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(summary);
        unsaved.setForeground(VERDE);
        unsaved.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(Box.createVerticalStrut(5));
        top.add(unsaved);
        return top;
    }

    private JSplitPane createExplorer() {
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setBackground(FUNDO);
        JPanel filters = new JPanel();
        filters.setBackground(FUNDO);
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));
        filters.add(new JLabel("Buscar time:"));
        filters.add(Box.createVerticalStrut(4));
        teamSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        filters.add(teamSearch);
        filters.add(Box.createVerticalStrut(8));
        origin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        filters.add(origin);
        includeNational.setBackground(FUNDO);
        filters.add(includeNational);
        left.add(filters, BorderLayout.NORTH);
        teamList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        teamList.setFixedCellHeight(29);
        teamList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        teamList.setSelectionBackground(VERDE);
        teamList.setSelectionForeground(Color.WHITE);
        left.add(new JScrollPane(teamList), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 9));
        right.setBackground(FUNDO);
        selectedTeam.setForeground(AZUL);
        selectedTeam.setFont(new Font("SansSerif", Font.BOLD, 15));
        JPanel above = new JPanel(new BorderLayout(0, 6));
        above.setBackground(FUNDO);
        above.add(selectedTeam, BorderLayout.NORTH);
        JPanel searchBar = new JPanel(new BorderLayout(6, 0));
        searchBar.setBackground(FUNDO);
        searchBar.add(new JLabel("Buscar no elenco:"), BorderLayout.WEST);
        searchBar.add(playerSearch, BorderLayout.CENTER);
        above.add(searchBar, BorderLayout.SOUTH);
        right.add(above, BorderLayout.NORTH);
        rosterTable.setRowHeight(27);
        rosterTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        rosterTable.setFillsViewportHeight(true);
        rosterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rosterTable.setRowSorter(rosterSorter);
        rosterTable.getTableHeader().setReorderingAllowed(false);
        int[] widths = {62, 80, 220, 70, 95, 85, 85, 70, 70, 100};
        for (int i = 0; i < widths.length; i++) {
            rosterTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        right.add(new JScrollPane(rosterTable), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(.29);
        split.setDividerLocation(265);
        split.setBorder(null);
        return split;
    }

    private JPanel createDetails() {
        JPanel detail = new JPanel(new BorderLayout());
        detail.setBackground(new Color(230, 238, 242));
        detail.setBorder(BorderFactory.createEmptyBorder(10, 13, 10, 13));
        playerDetails.setForeground(AZUL);
        detail.add(playerDetails, BorderLayout.CENTER);
        return detail;
    }

    private void registerListeners() {
        addTextListener(teamSearch, this::filterTeams);
        addTextListener(playerSearch, this::filterPlayers);
        origin.addActionListener(e -> filterTeams());
        includeNational.addActionListener(e -> filterTeams());
        teamList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showRoster(teamList.getSelectedValue());
        });
        rosterTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showPlayer();
        });
        rosterTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedPlayer();
            }
        });
        teamList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedTeam();
            }
        });
    }

    private static void addTextListener(JTextField field, Runnable callback) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { callback.run(); }
            @Override public void removeUpdate(DocumentEvent e) { callback.run(); }
            @Override public void changedUpdate(DocumentEvent e) { callback.run(); }
        });
    }

    private void chooseFiles() {
        if (hasPendingExport()
                && JOptionPane.showConfirmDialog(this,
                "Ha alteracoes nao exportadas. Carregar nova base vai descarta-las. Continuar?",
                "Alteracoes pendentes", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Path players = chooseFile("1/3 — Selecione Jogadores - PES 2021 - Bin.csv");
        if (players == null) return;
        Path teams = chooseFile("2/3 — Selecione Times - PES 2021 - Bin.csv");
        if (teams == null) return;
        Path assignments = chooseFile("3/3 — Selecione Atribuição jogadores - PES 2021 - Bin.csv");
        if (assignments == null) return;
        loadButton.setEnabled(false);
        globalStatus.accept("Importando CSVs: leitura e associação de IDs...");
        new SwingWorker<PesDatabase, String>() {
            @Override protected PesDatabase doInBackground() throws Exception {
                return PesDatabase.load(players, teams, assignments, this::publish);
            }
            @Override protected void process(List<String> updates) {
                if (!updates.isEmpty()) globalStatus.accept(updates.get(updates.size() - 1));
            }
            @Override protected void done() {
                loadButton.setEnabled(true);
                try {
                    database = get();
                    summary.setText(String.format("%d jogadores • %d times • %d atribuições",
                            database.totalPlayers(), database.teams().size(), database.totalAssignments()));
                    teamSearch.setText("");
                    playerSearch.setText("");
                    filterTeams();
                    editTeamButton.setEnabled(true);
                    formationButton.setEnabled(true);
                    updateDirty();
                    globalStatus.accept("Base importada; edicoes apenas em memoria ate exportar copias.");
                    if (database.unmatchedPlayers() > 0 || database.unmatchedTeams() > 0) {
                        JOptionPane.showMessageDialog(ElencosPanel.this,
                                "Atribuições com ID de jogador ausente: " + database.unmatchedPlayers()
                                + "\nAtribuições com ID de time ausente: " + database.unmatchedTeams()
                                + "\nEsses vínculos não foram exibidos; confira a consistência da base.",
                                "Avisos de integridade", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    Throwable root = ex.getCause() != null ? ex.getCause() : ex;
                    globalStatus.accept("Falha na importação; o banco anterior foi mantido.");
                    JOptionPane.showMessageDialog(ElencosPanel.this,
                            "Não foi possível carregar os CSVs:\n" + root.getMessage(),
                            "Erro de importação", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private Path chooseFile(String title) {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("Arquivos CSV (*.csv)", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        lastDirectory = chooser.getSelectedFile().getParentFile();
        return chooser.getSelectedFile().toPath();
    }

    private void filterTeams() {
        if (database == null) return;
        String query = teamSearch.getText().strip().toLowerCase(java.util.Locale.ROOT);
        String previous = teamList.getSelectedValue() == null ? null : teamList.getSelectedValue().id();
        String country = switch (origin.getSelectedIndex()) {
            case 1 -> "203";
            case 2 -> "232";
            case 3 -> "230";
            default -> "";
        };
        teamListModel.clear();
        int keep = -1;
        for (Team team : database.teams()) {
            if (!includeNational.isSelected() && team.national()) continue;
            if (!country.isEmpty() && !country.equals(team.country())) continue;
            if (!query.isEmpty() && !team.name().toLowerCase(java.util.Locale.ROOT).contains(query)
                    && !team.id().contains(query)) continue;
            if (team.id().equals(previous)) keep = teamListModel.size();
            teamListModel.addElement(team);
        }
        if (!teamListModel.isEmpty()) teamList.setSelectedIndex(keep < 0 ? 0 : keep);
        else showRoster(null);
    }

    private void showRoster(Team team) {
        if (database == null || team == null) {
            selectedTeam.setText("Nenhum clube selecionado.");
            rosterTableModel.load(List.of());
            playerDetails.setText(" ");
            editPlayerButton.setEnabled(false);
            editTeamButton.setEnabled(false);
            formationButton.setEnabled(false);
            assignmentButton.setEnabled(false);
            transferButton.setEnabled(false);
            return;
        }
        List<RosterEntry> roster = database.roster(team.id());
        rosterTableModel.load(roster);
        editTeamButton.setEnabled(true);
        formationButton.setEnabled(true);
        editPlayerButton.setEnabled(false);
        assignmentButton.setEnabled(false);
        transferButton.setEnabled(false);
        selectedTeam.setText(team.name() + "  •  ID " + team.id() + "  •  "
                + roster.size() + " atribuições");
        filterPlayers();
        playerDetails.setText("Selecione um jogador para consultar seus dados.");
    }

    private void filterPlayers() {
        String query = playerSearch.getText().strip();
        rosterSorter.setRowFilter(query.isEmpty() ? null
                : RowFilter.regexFilter("(?iu)" + Pattern.quote(query), 1, 2, 6));
    }

    private void showPlayer() {
        int selected = rosterTable.getSelectedRow();
        if (selected < 0) {
            editPlayerButton.setEnabled(false);
            assignmentButton.setEnabled(false);
            transferButton.setEnabled(false);
            return;
        }
        boolean club = database != null && teamList.getSelectedValue() != null
                && !teamList.getSelectedValue().national();
        assignmentButton.setEnabled(club);
        transferButton.setEnabled(club);
        editPlayerButton.setEnabled(true);
        RosterEntry entry = rosterTableModel.get(rosterTable.convertRowIndexToModel(selected));
        var p = entry.player();
        playerDetails.setText("<html><b>" + escape(p.name()) + "</b>  •  ID " + escape(p.id())
                + "  •  Idade " + escape(p.age()) + "  •  " + escape(p.height()) + " cm / "
                + escape(p.weight()) + " kg  •  Pé (dado bruto): " + escape(p.foot())
                + "  •  Estilo (código): " + escape(p.playingStyle()) + "</html>");
    }

    public boolean hasPendingExport() {
        return database != null && database.hasUnexportedChanges();
    }

    private void updateDirty() {
        if (database == null) return;
        int players = database.dirtyPlayers();
        int teams = database.dirtyTeams();
        int assignments = database.dirtyAssignments();
        unsaved.setText(players + " jogadores, " + teams + " times e " + assignments
                + " vínculos alterados na memória"
                + (database.hasUnexportedChanges() ? "  •  Exportacao pendente" : "  •  Sem exportacao pendente"));
        exportButton.setEnabled(players + teams + assignments > 0);
    }

    private String selectedPlayerId() {
        int view = rosterTable.getSelectedRow();
        if (view < 0) return null;
        return rosterTableModel.get(rosterTable.convertRowIndexToModel(view)).player().id();
    }

    private void editSelectedPlayer() {
        if (database == null) return;
        String id = selectedPlayerId();
        if (id == null) {
            JOptionPane.showMessageDialog(this, "Selecione um jogador na lista do elenco.");
            return;
        }
        new PlayerEditorDialog(SwingUtilities.getWindowAncestor(this), database, id,
                () -> refreshPlayer(id)).setVisible(true);
    }

    private void editSelectedTeam() {
        if (database == null || teamList.getSelectedValue() == null) {
            JOptionPane.showMessageDialog(this, "Selecione um time.");
            return;
        }
        String id = teamList.getSelectedValue().id();
        new TeamEditorDialog(SwingUtilities.getWindowAncestor(this), database, id,
                () -> refreshTeam(id)).setVisible(true);
    }

    private String selectedAssignmentId() {
        int view = rosterTable.getSelectedRow();
        if (view < 0) return null;
        return rosterTableModel.get(rosterTable.convertRowIndexToModel(view)).assignmentId();
    }

    private void editSelectedAssignment() {
        if (database == null || selectedAssignmentId() == null) return;
        String aid = selectedAssignmentId();
        String pid = selectedPlayerId();
        new SquadAssignmentDialog(SwingUtilities.getWindowAncestor(this), database, aid,
                () -> refreshPlayer(pid)).setVisible(true);
    }

    private void transferSelected() {
        if (database == null || selectedAssignmentId() == null) return;
        String aid = selectedAssignmentId();
        new TransferDialog(SwingUtilities.getWindowAncestor(this), database, aid, () -> {
            showRoster(teamList.getSelectedValue());
            updateDirty();
        }).setVisible(true);
    }

    private void editFormation() {
        if (database == null || teamList.getSelectedValue() == null) return;
        Path tactics = chooseFile("Selecione Táticas - PES 2021 - Bin.csv");
        if (tactics == null) return;
        Path formations = chooseFile("Selecione TacticsFormation.bin - PES 2021 - Bin.csv");
        if (formations == null) return;
        try {
            FormationEditorDialog.open(SwingUtilities.getWindowAncestor(this), database,
                    teamList.getSelectedValue().id(), tactics, formations);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Não foi possível abrir as formações:\n" + ex.getMessage(),
                    "Erro de táticas", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshPlayer(String id) {
        Team team = teamList.getSelectedValue();
        if (team == null) return;
        showRoster(team);
        for (int i=0; i<rosterTableModel.getRowCount(); i++) {
            if (rosterTableModel.get(i).player().id().equals(id)) {
                int view = rosterTable.convertRowIndexToView(i);
                if (view >= 0) rosterTable.setRowSelectionInterval(view, view);
                break;
            }
        }
        updateDirty();
    }

    private void refreshTeam(String teamId) {
        filterTeams();
        for (int i=0; i<teamListModel.getSize(); i++) {
            if (teamListModel.get(i).id().equals(teamId)) {
                teamList.setSelectedIndex(i);
                showRoster(teamList.getSelectedValue());
                break;
            }
        }
        updateDirty();
    }

    private void exportEdited() {
        if (database == null || database.dirtyPlayers() + database.dirtyTeams()
                + database.dirtyAssignments() == 0) return;
        JFileChooser picker = new JFileChooser(lastDirectory);
        picker.setDialogTitle("Escolha uma pasta para os novos CSVs (nao altera os arquivos originais)");
        picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (picker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path folder = picker.getSelectedFile().toPath();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path players = folder.resolve("Jogadores_Norteverso_" + timestamp + ".csv");
        Path teams = folder.resolve("Times_Norteverso_" + timestamp + ".csv");
        Path assignments = folder.resolve("Atribuicao_Jogadores_Norteverso_" + timestamp + ".csv");
        if (JOptionPane.showConfirmDialog(this,
                "Exportar três arquivos na pasta selecionada?\n\n" + players.getFileName()
                + "\n" + teams.getFileName() + "\n" + assignments.getFileName()
                + "\n\nNão altera as fontes nem os arquivos de aparências.",
                "Confirmar exportacao", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        exportButton.setEnabled(false);
        editPlayerButton.setEnabled(false);
        editTeamButton.setEnabled(false);
        loadButton.setEnabled(false);
        globalStatus.accept("Gravando CSVs completos em copias separadas...");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                database.exportAll(players, teams, assignments);
                return null;
            }
            @Override protected void done() {
                loadButton.setEnabled(true);
                editTeamButton.setEnabled(teamList.getSelectedValue() != null);
                formationButton.setEnabled(teamList.getSelectedValue() != null);
                editPlayerButton.setEnabled(selectedPlayerId() != null);
                assignmentButton.setEnabled(selectedAssignmentId() != null);
                transferButton.setEnabled(selectedAssignmentId() != null);
                try {
                    get();
                    updateDirty();
                    globalStatus.accept("Exportacao concluida em " + folder);
                    JOptionPane.showMessageDialog(ElencosPanel.this,
                            "Cópias exportadas com sucesso:\n" + players + "\n" + teams + "\n" + assignments
                            + "\n\nAs edicoes continuam em memoria. Reimporte os CSVs gerados no Ejogc327 apenas apos conferir.",
                            "Exportacao concluida", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    Throwable root = ex.getCause() == null ? ex : ex.getCause();
                    updateDirty();
                    globalStatus.accept("Falha na exportacao: " + root.getMessage());
                    JOptionPane.showMessageDialog(ElencosPanel.this,
                            root.getMessage(), "Erro de exportacao", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class RosterTableModel extends AbstractTableModel {
        private static final String[] HEADERS = {
                "Camisa", "ID", "Nome", "Idade", "POS (cód.)", "Overall",
                "País (cód.)", "Altura", "Peso", "Estilo (cód.)"
        };
        private List<RosterEntry> rows = List.of();
        void load(List<RosterEntry> entries) {
            rows = new ArrayList<>(entries);
            fireTableDataChanged();
        }
        RosterEntry get(int index) { return rows.get(index); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return HEADERS.length; }
        @Override public String getColumnName(int col) { return HEADERS[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }
        @Override public Object getValueAt(int row, int col) {
            var e = rows.get(row);
            var p = e.player();
            return switch (col) {
                case 0 -> e.shirtNumber();
                case 1 -> p.id();
                case 2 -> p.name();
                case 3 -> p.age();
                case 4 -> p.position();
                case 5 -> p.overall();
                case 6 -> p.country();
                case 7 -> p.height();
                case 8 -> p.weight();
                case 9 -> p.playingStyle();
                default -> "";
            };
        }
    }
}
