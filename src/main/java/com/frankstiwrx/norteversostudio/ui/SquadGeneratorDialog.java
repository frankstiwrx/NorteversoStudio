package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.csv.Csv;
import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PesDatabase.RosterEntry;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService.Config;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService.Division;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService.Item;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService.Preview;
import com.frankstiwrx.norteversostudio.generator.SquadGenerationService.Role;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/** Elenco inteiro, com plano editavel e relatorio de antes/depois para auditoria. */
public final class SquadGeneratorDialog extends JDialog {
    private static final Color DARK = new Color(21, 41, 63);
    private static final Color GREEN = new Color(30, 128, 93);
    private final PesDatabase db;
    private final String teamId;
    private final SquadGenerationService service;
    private final ProtectionRegistry registry;
    private final Runnable onApply;
    private final List<RosterEntry> roster;
    private final int eligible;
    private final JComboBox<Division> division = new JComboBox<>(Division.values());
    private final JComboBox<String> origin = new JComboBox<>(new String[]{
        "Misto Norteverso", "Finlândia", "Países nórdicos", "Países bálticos",
        "Britânicos e norte-americanos", "Europa Central e Oriental", "Todos os países"});
    private final JSpinner baseLevel = new JSpinner(new SpinnerNumberModel(84, 58, 94, 1));
    private final JTextField seed = new JTextField("20261017", 10);
    private final JCheckBox nationality = new JCheckBox("Nacionalidade conforme nome", true);
    private final JCheckBox shirt = new JCheckBox("Atualizar nome na camisa", true);
    private final JCheckBox protectGenerated = new JCheckBox("Proteger gerados depois de aplicar", false);
    private final Map<Role, JSpinner> roleInputs = new EnumMap<>(Role.class);
    private final JLabel counts = new JLabel();
    private final JLabel status = new JLabel("Sugira uma distribuição e gere uma prévia para começar.");
    private final JLabel summary = new JLabel(" ");
    private final RosterModel model = new RosterModel();
    private final JTable table = new JTable(model);
    private final JButton applyButton = new JButton("2. Aplicar prévia à memória");
    private final JButton editButton = new JButton("Editar jogador da prévia...");
    private final JButton reportButton = new JButton("Exportar comparação antes/depois...");
    private Preview preview;
    private List<List<String>> lastAudit;
    private boolean configuring = false;

    public SquadGeneratorDialog(Window owner, PesDatabase db, String teamId,
                                NameCatalog catalog, ProtectionRegistry registry, Runnable onApply) {
        super(owner, "Norteverso Studio — Gerador inteligente de elencos", ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.teamId = teamId;
        this.registry = registry;
        this.service = new SquadGenerationService(catalog, registry);
        this.onApply = onApply;
        // O CSV pode ter atribuicoes repetidas de um mesmo jogador no clube.
        // Uma identidade = uma linha de geracao, independentemente das atribuicoes.
        LinkedHashMap<String, RosterEntry> unique = new LinkedHashMap<>();
        for (RosterEntry entry : db.roster(teamId)) unique.putIfAbsent(entry.player().id(), entry);
        this.roster = new ArrayList<>(unique.values());
        this.eligible = service.eligibleCount(db, teamId);
        build();
        String country = db.team(teamId).country();
        if ("232".equals(country)) division.setSelectedItem(Division.LIIGA1);
        else if ("230".equals(country)) division.setSelectedItem(Division.LIIGA2);
        else division.setSelectedItem(Division.SUPERLIIGA);
        applyDivision();
        suggestRoles();
        setMinimumSize(new Dimension(900, 600));
        var screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1320, Math.max(900, screen.width - 60)),
                Math.min(805, Math.max(600, screen.height - 60)));
        setLocationRelativeTo(owner);
    }

    private void build() {
        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        outer.setBackground(new Color(247, 249, 250));
        setContentPane(outer);
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Gerador inteligente — " + db.team(teamId).name());
        title.setFont(new Font("SansSerif", Font.BOLD, 21));
        title.setForeground(DARK);
        title.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        JLabel guide = new JLabel("Usa registros existentes. Preserva IDs, faces, altura/peso e clubes; mantém posições por padrão (edição manual liberada).");
        guide.setAlignmentX(LEFT_ALIGNMENT);
        header.add(guide);
        header.add(Box.createVerticalStrut(4));
        JLabel ovr = new JLabel("IMPORTANTE: Nível de referência ≠ overall do PES. OverallStats original permanece inalterado no CSV.");
        ovr.setForeground(new Color(149, 82, 22));
        ovr.setAlignmentX(LEFT_ALIGNMENT);
        header.add(ovr);
        header.add(Box.createVerticalStrut(8));
        int duplicateAssignments = db.roster(teamId).size() - roster.size();
        counts.setText("Jogadores únicos: " + roster.size() + " | Livres: " + eligible
                + " | Protegidos: " + (roster.size() - eligible)
                + (duplicateAssignments > 0 ? " | Atribuições repetidas ignoradas: " + duplicateAssignments : ""));
        counts.setAlignmentX(LEFT_ALIGNMENT);
        counts.setFont(counts.getFont().deriveFont(Font.BOLD));
        header.add(counts);

        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        settings.setOpaque(false);
        settings.add(new JLabel("Divisão:"));
        settings.add(division);
        settings.add(new JLabel("Nível técnico (NÃO OVR):"));
        settings.add(baseLevel);
        settings.add(new JLabel("Origem dos nomes:"));
        settings.add(origin);
        settings.add(new JLabel("Seed (repete o sorteio):"));
        settings.add(seed);
        settings.setAlignmentX(LEFT_ALIGNMENT);
        header.add(settings);
        JPanel toggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        toggles.setOpaque(false);
        toggles.add(nationality);
        toggles.add(shirt);
        toggles.add(protectGenerated);
        header.add(toggles);
        baseLevel.setToolTipText("Controla a força aproximada dos atributos. NÃO é o overall oficial do PES.");
        division.setToolTipText("Preenche o nível técnico recomendado para a divisão. Você pode alterá-lo.");
        seed.setToolTipText("Mesmo número e mesmas opções reproduzem a mesma geração.");
        nationality.setToolTipText("Altera a nacionalidade esportiva para a origem escolhida; desligue se quiser preservar o país anterior.");
        protectGenerated.setToolTipText("Após aplicar, guarda os IDs para que gerações futuras não os alterem.");

        JPanel distribution = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 3));
        distribution.setOpaque(false);
        distribution.setBorder(BorderFactory.createTitledBorder("Categorias — a soma deve ser igual aos jogadores livres"));
        for (Role role : Role.values()) {
            distribution.add(new JLabel(role + ":"));
            JSpinner count = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
            roleInputs.put(role, count);
            distribution.add(count);
            count.addChangeListener(e -> changedSettings());
        }
        JButton suggest = new JButton("Preencher categorias automaticamente");
        distribution.add(suggest);
        suggest.addActionListener(e -> suggestRoles());
        distribution.setAlignmentX(LEFT_ALIGNMENT);
        header.add(distribution);
        summary.setAlignmentX(LEFT_ALIGNMENT);
        summary.setForeground(DARK);
        header.add(summary);
        JLabel balanceHelp = new JLabel("Balanceamento: evita excesso de notas 95–99 na geração automática. Perfis editados manualmente continuam livres.");
        balanceHelp.setForeground(new Color(30, 105, 90));
        header.add(balanceHelp);
        header.add(Box.createVerticalStrut(5));
        outer.add(header, BorderLayout.NORTH);

        table.setAutoCreateRowSorter(true);
        table.setRowHeight(27);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        int[] width = {88, 180, 85, 100, 85, 190, 90, 75, 70, 80, 95};
        for (int i = 0; i < width.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(width[i]);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Elenco completo. Duplo clique num jogador gerado para personalizar a prévia."));
        outer.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 4));
        buttons.setOpaque(false);
        JButton generate = new JButton("1. Gerar prévia (não aplica)");
        generate.setBackground(GREEN);
        generate.setForeground(Color.WHITE);
        applyButton.setBackground(DARK);
        applyButton.setForeground(Color.WHITE);
        JButton discard = new JButton("Descartar prévia");
        JButton close = new JButton("Fechar");
        buttons.add(generate);
        buttons.add(editButton);
        buttons.add(applyButton);
        buttons.add(reportButton);
        buttons.add(discard);
        buttons.add(close);
        bottom.add(buttons);
        status.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(status);
        outer.add(bottom, BorderLayout.SOUTH);
        applyButton.setEnabled(false);
        editButton.setEnabled(false);
        reportButton.setEnabled(false);

        division.addActionListener(e -> applyDivision());
        origin.addActionListener(e -> changedSettings());
        nationality.addActionListener(e -> changedSettings());
        shirt.addActionListener(e -> changedSettings());
        baseLevel.addChangeListener(e -> changedSettings());
        seed.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() { changedSettings(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        });
        generate.addActionListener(e -> generate());
        editButton.addActionListener(e -> editSelected());
        applyButton.addActionListener(e -> apply());
        reportButton.addActionListener(e -> exportAudit());
        discard.addActionListener(e -> invalidatePreview("Prévia descartada. Nenhuma alteração foi aplicada."));
        close.addActionListener(e -> dispose());
    }

    private void applyDivision() {
        Division choice = (Division) division.getSelectedItem();
        if (choice != null) baseLevel.setValue(choice.defaultLevel());
        changedSettings();
    }

    private void suggestRoles() {
        configuring = true;
        Map<Role, Integer> counts = SquadGenerationService.suggestedDistribution(eligible);
        for (Role role : Role.values()) roleInputs.get(role).setValue(counts.get(role));
        configuring = false;
        changedSettings();
    }

    private void changedSettings() {
        if (configuring) return;
        invalidatePreview("Configuração alterada. Gere nova prévia antes de aplicar.");
        int total = 0;
        for (JSpinner spinner : roleInputs.values()) total += (Integer)spinner.getValue();
        summary.setText("Distribuição: " + total + "/" + eligible
                + " jogadores livres | diferença: " + (eligible - total)
                + " | referência, não OVR oficial.");
    }

    private Config config() {
        long seedValue;
        try { seedValue = Long.parseLong(seed.getText().strip()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Seed deve ser um número inteiro."); }
        EnumMap<Role, Integer> roles = new EnumMap<>(Role.class);
        for (Role role : Role.values()) roles.put(role, (Integer)roleInputs.get(role).getValue());
        String code = switch (origin.getSelectedIndex()) {
            case 1 -> "FI";
            case 2 -> "NORDIC";
            case 3 -> "BALTIC";
            case 4 -> "ANGLO";
            case 5 -> "EAST";
            case 6 -> "ALL";
            default -> "MIX";
        };
        return new Config((Integer)baseLevel.getValue(), code, seedValue,
                nationality.isSelected(), shirt.isSelected(), roles);
    }

    private void generate() {
        try {
            preview = service.preview(db, teamId, config());
            lastAudit = null;
            applyButton.setEnabled(true);
            editButton.setEnabled(true);
            reportButton.setEnabled(true);
            model.fireTableDataChanged();
            status.setText("Prévia: " + preview.items().size() + " jogadores. Confira e edite antes de aplicar. Protegidos intocados.");
        } catch (IllegalArgumentException ex) {
            invalidatePreview("Não foi possível gerar prévia.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Gerador de elenco", JOptionPane.WARNING_MESSAGE);
        }
    }

    private Item selectedItem() {
        if (preview == null || table.getSelectedRow() < 0) return null;
        String id = roster.get(table.convertRowIndexToModel(table.getSelectedRow())).player().id();
        for (Item item : preview.items()) if (item.playerId().equals(id)) return item;
        return null;
    }

    private void editSelected() {
        Item item = selectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Selecione um jogador livre com prévia gerada.");
            return;
        }
        GeneratedPlayerEditorDialog dialog = new GeneratedPlayerEditorDialog(this, db, item.draft());
        dialog.setVisible(true);
        if (dialog.wasSaved()) {
            model.fireTableDataChanged();
            status.setText("Proposta personalizada. Exporte relatório ou aplique o elenco quando estiver satisfeito.");
        }
    }

    private void apply() {
        if (preview == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "Aplicar " + preview.items().size() + " jogadores à memória?\n"
                + "IDs, faces, clubes e jogadores protegidos serão mantidos.\n"
                + "Ainda será necessário exportar os 3 CSVs na tela Clubes.\n"
                + "OverallStats NÃO será recalculado automaticamente.",
                "Aplicar geração", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            // Captura o relatorio ANTES de aplicar, preservando dados originais para comparar.
            List<List<String>> snapshot = SquadGenerationService.auditRows(db, preview);
            int applied = service.apply(db, preview);
            String protectionNote = "";
            if (protectGenerated.isSelected()) {
                Map<String, Boolean> previous = new HashMap<>();
                for (Item item : preview.items()) {
                    previous.put(item.playerId(), registry.isProtected(item.playerId()));
                    registry.setProtected(item.playerId(), db.player(item.playerId()).name(), true);
                }
                try {
                    registry.save();
                } catch (IOException ex) {
                    for (Item item : preview.items()) {
                        if (!previous.get(item.playerId())) registry.setProtected(item.playerId(), "", false);
                    }
                    protectionNote = " Aviso: falhou gravacao do arquivo de protegidos.";
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Protecao nao salva", JOptionPane.WARNING_MESSAGE);
                }
            }
            lastAudit = snapshot;
            preview = null;
            applyButton.setEnabled(false);
            editButton.setEnabled(false);
            reportButton.setEnabled(true);
            model.fireTableDataChanged();
            status.setText("Aplicados " + applied + " perfis na memória. Exporte o relatório aqui e os 3 CSVs na tela Clubes." + protectionNote);
            onApply.run();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Prévia não aplicada", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportAudit() {
        List<List<String>> rows = preview == null ? lastAudit : SquadGenerationService.auditRows(db, preview);
        if (rows == null) return;
        JFileChooser picker = new JFileChooser();
        picker.setDialogTitle("Salvar relatório de auditoria do elenco (CSV)");
        picker.setSelectedFile(new java.io.File("Relatorio_Elenco_" + teamId + ".csv"));
        if (picker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected = picker.getSelectedFile().toPath();
        if (!selected.getFileName().toString().toLowerCase().endsWith(".csv")) {
            selected = selected.resolveSibling(selected.getFileName() + ".csv");
        }
        if (Files.exists(selected) && JOptionPane.showConfirmDialog(this,
                "Esse relatório já existe. Sobrescrever?", "Confirmar", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        try {
            Csv.gravar(selected, rows);
            status.setText("Relatório de " + (rows.size() - 1) + " jogadores salvo: " + selected.getFileName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro ao exportar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void invalidatePreview(String message) {
        preview = null;
        lastAudit = null;
        applyButton.setEnabled(false);
        editButton.setEnabled(false);
        reportButton.setEnabled(false);
        model.fireTableDataChanged();
        status.setText(message);
    }

    private final class RosterModel extends AbstractTableModel {
        private final String[] columns = {"ID", "Nome atual", "Posição", "Categoria", "Nível ref.",
                "Novo nome", "País PES", "Idade", "OVR base", "Média proj.", "Status"};
        @Override public int getRowCount() { return roster.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }
        @Override public Object getValueAt(int row, int col) {
            String id = roster.get(row).player().id();
            var player = db.player(id);
            Item item = null;
            if (preview != null) for (Item proposed : preview.items()) {
                if (proposed.playerId().equals(id)) { item = proposed; break; }
            }
            if (col == 0) return id;
            if (col == 1) return player.name();
            if (col == 2) return PesDatabase.POSITIONS.get(Integer.parseInt(player.position()));
            if (col == 8) return player.overall();
            if (col == 10) return registry.isProtected(id) ? "PROTEGIDO" : (item == null ? "Livre" : "Prévia");
            if (item == null) return "—";
            return switch (col) {
                case 3 -> item.role().toString();
                case 4 -> item.targetLevel();
                case 5 -> item.draft().result(db, "Name");
                case 6 -> item.draft().result(db, "Country");
                case 7 -> item.draft().result(db, "Age");
                case 9 -> SquadGenerationService.averageTechnical(db, id, item.draft(), player.position());
                default -> "";
            };
        }
    }
}
