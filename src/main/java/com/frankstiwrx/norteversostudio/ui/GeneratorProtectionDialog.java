package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PesDatabase.RosterEntry;
import com.frankstiwrx.norteversostudio.generator.IdentityGenerator;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;

/**
 * Fase 1 do gerador: protege jogadores e produz uma PREVIA de identidades.
 * Aplicar muda SOMENTE Name e opcionalmente Shirt, nunca IDs, clubes ou aparencias.
 */
public final class GeneratorProtectionDialog extends JDialog {
    private static final Color DARK = new Color(21, 41, 63);
    private static final Color GREEN = new Color(30, 128, 93);
    private final PesDatabase db;
    private final String teamId;
    private final ProtectionRegistry registry;
    private final IdentityGenerator generator;
    private final Runnable afterApply;
    private final List<RosterEntry> entries;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel(" ");
    private final JLabel counts = new JLabel(" ");
    private final JComboBox<Origin> origin = new JComboBox<>();
    private final JTextField seed = new JTextField("20261017", 12);
    private final JCheckBox onlySelected = new JCheckBox("Gerar somente selecionados", false);
    private final JCheckBox updateShirt = new JCheckBox("Atualizar nome da camisa (Shirt)", true);
    private final JCheckBox protectGenerated = new JCheckBox("Proteger jogadores após aplicar", false);
    private final JButton apply = new JButton("APLICAR PRÉVIA");
    private List<IdentityGenerator.Proposal> preview = List.of();

    private record Origin(String label, String code) {
        @Override public String toString() { return label; }
    }

    public GeneratorProtectionDialog(Window owner, PesDatabase db, String teamId,
                                     ProtectionRegistry registry, NameCatalog catalog,
                                     Runnable afterApply) {
        super(owner, "Norteverso Studio — Gerador e protetor", ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.teamId = teamId;
        this.registry = registry;
        this.generator = new IdentityGenerator(catalog, registry);
        this.afterApply = afterApply;
        this.entries = new ArrayList<>(db.roster(teamId));
        build(catalog);
        refreshCounts();
        setMinimumSize(new Dimension(890, 550));
        setSize(1130, 690);
        setLocationRelativeTo(owner);
    }

    private void build(NameCatalog catalog) {
        JPanel outer = new JPanel(new BorderLayout(0, 12));
        outer.setBorder(BorderFactory.createEmptyBorder(15, 17, 13, 17));
        outer.setBackground(new Color(246, 248, 250));
        setContentPane(outer);
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Gerador de identidades  +  Proteção de jogadores");
        title.setForeground(DARK);
        title.setFont(new Font("SansSerif", Font.BOLD, 21));
        title.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        JLabel warning = new JLabel("Protegidos ficam intactos. Modo automático pula países sem nomes cadastrados.");
        warning.setAlignmentX(LEFT_ALIGNMENT);
        header.add(warning);
        header.add(Box.createVerticalStrut(5));
        JLabel unchanged = new JLabel("Preservados: IDs, aparências, faces, nacionalidade, idade, atributos e clube.");
        unchanged.setForeground(GREEN);
        unchanged.setAlignmentX(LEFT_ALIGNMENT);
        header.add(unchanged);
        header.add(Box.createVerticalStrut(9));
        counts.setAlignmentX(LEFT_ALIGNMENT);
        header.add(counts);
        header.add(Box.createVerticalStrut(8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("Origem do nome:"));
        origin.addItem(new Origin("Automático (manter origem do país)", "AUTO"));
        origin.addItem(new Origin("Misto Norteverso (65% FI)", "MIX"));
        origin.addItem(new Origin("Finlândia (FI)", "FI"));
        origin.addItem(new Origin("Países nórdicos", "NORDIC"));
        origin.addItem(new Origin("Países bálticos", "BALTIC"));
        origin.addItem(new Origin("Inglês / América do Norte", "ANGLO"));
        origin.addItem(new Origin("Europa Central / Oriental", "EAST"));
        origin.addItem(new Origin("Todos os países", "ALL"));
        for (String code : catalog.countries()) origin.addItem(new Origin("País: " + code, code));
        controls.add(origin);
        controls.add(new JLabel("Seed:"));
        controls.add(seed);
        controls.setAlignmentX(LEFT_ALIGNMENT);
        header.add(controls);
        JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        choices.setOpaque(false);
        choices.add(onlySelected);
        choices.add(updateShirt);
        choices.add(protectGenerated);
        choices.setAlignmentX(LEFT_ALIGNMENT);
        header.add(choices);
        outer.add(header, BorderLayout.NORTH);

        table.setRowHeight(27);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {105, 75, 190, 75, 85, 195, 160, 90};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Elenco — selecione jogadores com Ctrl/Shift para aplicar em parte do clube"));
        outer.add(tableScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        JPanel protections = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        protections.setOpaque(false);
        JButton protect = new JButton("Proteger selecionados");
        JButton unprotect = new JButton("Liberar selecionados");
        JButton protectAll = new JButton("Proteger elenco inteiro");
        JButton unprotectAll = new JButton("Liberar elenco inteiro");
        protections.add(protect);
        protections.add(unprotect);
        protections.add(protectAll);
        protections.add(unprotectAll);
        protect.addActionListener(e -> changeProtection(true, false));
        unprotect.addActionListener(e -> changeProtection(false, false));
        protectAll.addActionListener(e -> changeProtection(true, true));
        unprotectAll.addActionListener(e -> changeProtection(false, true));
        bottom.add(protections);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        actions.setOpaque(false);
        JButton generate = new JButton("Gerar prévia");
        generate.setBackground(GREEN);
        generate.setForeground(Color.WHITE);
        apply.setEnabled(false);
        apply.setBackground(DARK);
        apply.setForeground(Color.WHITE);
        JButton clear = new JButton("Limpar prévia");
        JButton close = new JButton("Fechar");
        actions.add(generate);
        actions.add(apply);
        actions.add(clear);
        actions.add(close);
        generate.addActionListener(e -> generatePreview());
        apply.addActionListener(e -> applyPreview());
        clear.addActionListener(e -> invalidatePreview("Prévia descartada."));
        close.addActionListener(e -> dispose());
        bottom.add(actions);
        JLabel fileLabel = new JLabel("Proteção persistente: " + registry.file());
        fileLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        fileLabel.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(fileLabel);
        status.setForeground(DARK);
        status.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(Box.createVerticalStrut(5));
        bottom.add(status);
        outer.add(bottom, BorderLayout.SOUTH);
    }

    private List<String> selectedIds(boolean allRows) {
        List<String> ids = new ArrayList<>();
        if (allRows) {
            for (RosterEntry entry : entries) ids.add(entry.player().id());
        } else {
            int[] indices = table.getSelectedRows();
            for (int viewIndex : indices) {
                ids.add(entries.get(table.convertRowIndexToModel(viewIndex)).player().id());
            }
        }
        return ids;
    }

    private void changeProtection(boolean protect, boolean all) {
        List<String> ids = selectedIds(all);
        if (ids.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecione um ou mais jogadores primeiro.");
            return;
        }
        if (all && !protect && JOptionPane.showConfirmDialog(this,
                "Liberar todos os " + ids.size() + " jogadores deste elenco para geracao?",
                "Confirmar liberacao", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Map<String, Boolean> original = new HashMap<>();
        for (String id : ids) {
            original.put(id, registry.isProtected(id));
            registry.setProtected(id, db.player(id).name(), protect);
        }
        try {
            registry.save();
            invalidatePreview("Protecao " + (protect ? "ativada" : "desativada") + " em " + ids.size() + " jogador(es).");
            refreshCounts();
        } catch (IOException ex) {
            for (String id : ids) registry.setProtected(id, db.player(id).name(), original.get(id));
            JOptionPane.showMessageDialog(this, "Nao foi possivel salvar a lista de protecao:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generatePreview() {
        try {
            long value = Long.parseLong(seed.getText().strip());
            Origin selected = (Origin) origin.getSelectedItem();
            List<String> ids = selectedIds(!onlySelected.isSelected());
            if (ids.isEmpty()) throw new IllegalArgumentException("Selecione jogadores ou desmarque o filtro.");
            preview = generator.preview(db, ids, selected.code(), value);
            if (preview.isEmpty()) {
                invalidatePreview("Nenhum jogador elegivel. Libere jogadores protegidos ou mude a selecao.");
                return;
            }
            apply.setEnabled(true);
            model.fireTableDataChanged();
            status.setText("Prévia: " + preview.size() + " novo(s) nome(s), "
                    + (ids.size() - preview.size()) + " ignorado(s) (proteção / país sem biblioteca). Nenhum CSV modificado.");
        } catch (IllegalArgumentException ex) {
            invalidatePreview("Falha ao gerar prévia.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Verifique as opções", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void applyPreview() {
        if (preview.isEmpty()) return;
        if (JOptionPane.showConfirmDialog(this,
                "Aplicar " + preview.size() + " nomes?\n"
                + "Somente Name" + (updateShirt.isSelected() ? " e Shirt" : "") + " serão modificados.\n"
                + "As alterações ficam na memória até EXPORTAR OS CSVs.\n"
                + "ID, aparência, idade, posição, nacionalidade e clube permanecem intactos.",
                "Confirmar geração", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            int amount = generator.apply(db, preview, updateShirt.isSelected());
            String protectionWarning = "";
            if (protectGenerated.isSelected()) {
                List<IdentityGenerator.Proposal> applied = preview;
                for (var p : applied) registry.setProtected(p.playerId(), p.after(), true);
                try {
                    registry.save();
                } catch (IOException e) {
                    for (var p : applied) registry.setProtected(p.playerId(), p.after(), false);
                    protectionWarning = " ATENCAO: nao foi possivel salvar protecao: " + e.getMessage();
                    JOptionPane.showMessageDialog(this, protectionWarning,
                            "Nomes aplicados, mas protecao nao salva", JOptionPane.WARNING_MESSAGE);
                }
            }
            invalidatePreview("Aplicados " + amount + " nomes à memória. Use Exportar 3 CSVs na tela de times." + protectionWarning);
            model.fireTableDataChanged();
            refreshCounts();
            afterApply.run();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Não foi possível aplicar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void invalidatePreview(String message) {
        preview = List.of();
        apply.setEnabled(false);
        model.fireTableDataChanged();
        status.setText(message);
    }

    private void refreshCounts() {
        long protectedHere = entries.stream().filter(e -> registry.isProtected(e.player().id())).count();
        counts.setText("Elenco: " + entries.size() + " • protegidos aqui: " + protectedHere
                + " • livres: " + (entries.size() - protectedHere)
                + " • protegidos na biblioteca: " + registry.size());
    }

    private final class Model extends AbstractTableModel {
        private final String[] cols = {"Proteção", "ID", "Nome atual", "País", "Posição",
                "Novo nome (prévia)", "Camisa (prévia)", "Origem"};
        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Object getValueAt(int row, int col) {
            var p = entries.get(row).player();
            IdentityGenerator.Proposal proposal = preview.stream()
                    .filter(s -> s.playerId().equals(p.id())).findFirst().orElse(null);
            return switch (col) {
                case 0 -> registry.isProtected(p.id()) ? "PROTEGIDO" : "Livre";
                case 1 -> p.id();
                case 2 -> db.player(p.id()).name();
                case 3 -> p.country();
                case 4 -> p.position();
                case 5 -> proposal == null ? "—" : proposal.after();
                case 6 -> proposal == null ? "—" : proposal.shirt();
                case 7 -> proposal == null ? "—" : proposal.country();
                default -> "";
            };
        }
    }
}
