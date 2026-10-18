package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PesDatabase.RosterEntry;
import com.frankstiwrx.norteversostudio.data.PlayingStyleCatalog;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.IdentityPreservationVerifier;
import com.frankstiwrx.norteversostudio.generator.OverallTargetBalancer;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Draft;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Mode;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Options;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.IOException;
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
import javax.swing.JTextArea;
import javax.swing.JFileChooser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;

/**
 * Gerador + protetor: quatro modos individuais e geracao inteligente por elenco.
 * Trabalha só com jogadores existentes, preservando IDs, rostos e clubes.
 */
public final class GeneratorProtectionDialog extends JDialog {
    private static final Color DARK = new Color(21, 41, 63);
    private static final Color GREEN = new Color(30, 128, 93);
    private final PesDatabase db;
    private final ProtectionRegistry registry;
    private final PlayerGenerationService generator;
    private final Runnable afterApply;
    private final List<RosterEntry> entries;
    private final String selectedTeamId;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel(" ");
    private final JLabel counts = new JLabel(" ");
    private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
    private final JComboBox<Origin> origin = new JComboBox<>();
    private final JTextField seed = new JTextField("20261017", 10);
    private final JSpinner level = new JSpinner(new SpinnerNumberModel(78, 50, 99, 1));
    private final JCheckBox targetOverall = new JCheckBox("Ajustar ao overall desejado", true);
    private final JCheckBox onlySelected = new JCheckBox("Somente selecionados", false);
    private final JCheckBox changeCountry = new JCheckBox("Nacionalidade conforme origem do nome", true);
    private final JCheckBox updateShirt = new JCheckBox("Atualizar nome na camisa", true);
    private final JCheckBox protectGenerated = new JCheckBox("Proteger depois de aplicar", false);
    private final JButton apply = new JButton("APLICAR PRÉVIA");
    private final JButton edit = new JButton("Editar jogador da prévia...");
    private final JButton verifyIdentity = new JButton("Verificar preservação");
    private IdentityPreservationVerifier.Snapshot identitySnapshot;
    private List<Draft> preview = List.of();

    private record Origin(String label, String code) {
        @Override public String toString() { return label; }
    }

    public GeneratorProtectionDialog(Window owner, PesDatabase db, String teamId,
                                     ProtectionRegistry registry, NameCatalog catalog,
                                     Runnable afterApply) {
        super(owner, "Norteverso Studio — Gerador e protetor", ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.selectedTeamId = teamId;
        this.registry = registry;
        this.generator = new PlayerGenerationService(catalog, registry);
        this.afterApply = afterApply;
        this.entries = new ArrayList<>(db.roster(teamId));
        build(catalog);
        refreshCounts();
        setMinimumSize(new Dimension(840, 520));
        var screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1250, Math.max(840, screen.width - 50)),
                Math.min(755, Math.max(520, screen.height - 50)));
        setLocationRelativeTo(owner);
    }

    private void build(NameCatalog catalog) {
        JPanel outer = new JPanel(new BorderLayout(0, 9));
        outer.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        outer.setBackground(new Color(246, 248, 250));
        setContentPane(outer);
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Gerador de jogadores   /   Protetor de elencos");
        title.setForeground(DARK);
        title.setFont(new Font("SansSerif", Font.BOLD, 21));
        title.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        JLabel warning = new JLabel("🔒 Jogadores protegidos ficam intactos. ID, face 3D, miniface, altura/peso e vínculos com clubes nunca são gerados.");
        warning.setAlignmentX(LEFT_ALIGNMENT);
        header.add(warning);
        header.add(Box.createVerticalStrut(5));
        JLabel hint = new JLabel("'Perfil completo' transforma um registro EXISTENTE — não cria novo ID. Com ajuste de OVR ativo, o nível é a nota-alvo.");
        hint.setForeground(GREEN);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        header.add(hint);
        JLabel identityHint = new JLabel("Para manter a força da Série C: selecione 'Só nome e nacionalidade' e clique em 'Verificar preservação'.");
        identityHint.setForeground(DARK);
        identityHint.setAlignmentX(LEFT_ALIGNMENT);
        header.add(identityHint);
        header.add(Box.createVerticalStrut(7));
        counts.setAlignmentX(LEFT_ALIGNMENT);
        header.add(counts);
        header.add(Box.createVerticalStrut(7));

        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        settings.setOpaque(false);
        settings.add(new JLabel("Modo:"));
        settings.add(mode);
        settings.add(new JLabel("Origem do nome:"));
        origin.addItem(new Origin("Automático (país atual)", "AUTO"));
        origin.addItem(new Origin("Misto Norteverso", "MIX"));
        origin.addItem(new Origin("Finlândia (FI)", "FI"));
        origin.addItem(new Origin("Países nórdicos", "NORDIC"));
        origin.addItem(new Origin("Países bálticos", "BALTIC"));
        origin.addItem(new Origin("Inglês / América do Norte", "ANGLO"));
        origin.addItem(new Origin("Europa Central / Oriental", "EAST"));
        origin.addItem(new Origin("Todos os países", "ALL"));
        for (String code : catalog.countries()) origin.addItem(new Origin("País: " + code, code));
        origin.setSelectedIndex(1);
        settings.add(origin);
        settings.add(new JLabel("OVR desejado / nível de atributos:"));
        settings.add(level);
        settings.add(new JLabel("Seed:"));
        settings.add(seed);
        settings.setAlignmentX(LEFT_ALIGNMENT);
        header.add(settings);
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        options.setOpaque(false);
        options.add(targetOverall);
        options.add(onlySelected);
        options.add(changeCountry);
        options.add(updateShirt);
        options.add(protectGenerated);
        options.setAlignmentX(LEFT_ALIGNMENT);
        header.add(options);
        mode.setSelectedItem(Mode.KEEP_POSITION);
        origin.addActionListener(e -> invalidatePreview("Origem alterada. Gere novamente."));
        level.addChangeListener(e -> invalidatePreview("Nível alterado. Gere novamente."));
        targetOverall.addActionListener(e -> invalidatePreview("Modo de cálculo alterado. Gere novamente."));
        targetOverall.setToolTipText("Com esta opção ativa, a nota escolhida passa a ser um overall-alvo para a geração técnica; a prévia mostra o OVR calculado.");
        changeCountry.addActionListener(e -> invalidatePreview("Opções alteradas. Gere novamente."));
        updateShirt.addActionListener(e -> invalidatePreview("Opções alteradas. Gere novamente."));
        onlySelected.addActionListener(e -> invalidatePreview("Seleção do gerador alterada. Gere novamente."));
        seed.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() { invalidatePreview("Seed alterada. Gere novamente."); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        });
        mode.addActionListener(e -> {
            boolean needsIdentity = ((Mode)mode.getSelectedItem()).generatesIdentity();
            origin.setEnabled(needsIdentity);
            changeCountry.setEnabled(needsIdentity);
            updateShirt.setEnabled(needsIdentity);
            targetOverall.setEnabled(((Mode)mode.getSelectedItem()).generatesTechnical());
            // Mudou configuração com prévia existente? Deve regenerar, não aplicar plano antigo.
            invalidatePreview("Modo alterado. Gere uma nova prévia.");
        });
        outer.add(header, BorderLayout.NORTH);

        table.setRowHeight(27);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {100, 80, 170, 70, 70, 190, 75, 75, 80, 190, 85, 85};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Selecione jogadores com Ctrl/Shift. Duplo clique para editar uma proposta já gerada."));
        outer.add(scroll, BorderLayout.CENTER);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        JPanel protections = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 3));
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
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        actions.setOpaque(false);
        JButton generate = new JButton("Gerar prévia");
        generate.setBackground(GREEN);
        generate.setForeground(Color.WHITE);
        apply.setEnabled(false);
        edit.setEnabled(false);
        apply.setBackground(DARK);
        apply.setForeground(Color.WHITE);
        verifyIdentity.setToolTipText("Somente no modo 'Só nome e nacionalidade': compara todos os campos e confirma que nenhuma habilidade, posição ou atributo será alterado.");
        verifyIdentity.setEnabled(false);
        JButton squad = new JButton("Gerar elenco inteligente...");
        JButton clear = new JButton("Descartar prévia");
        JButton close = new JButton("Fechar");
        actions.add(generate);
        actions.add(squad);
        actions.add(verifyIdentity);
        actions.add(edit);
        actions.add(apply);
        actions.add(clear);
        actions.add(close);
        generate.addActionListener(e -> generatePreview());
        verifyIdentity.addActionListener(e -> showVerification());
        squad.addActionListener(e -> {
            if (!preview.isEmpty()) {
                int answer = JOptionPane.showConfirmDialog(this,
                        "A prévia individual atual será descartada. Continuar?",
                        "Gerar elenco", JOptionPane.YES_NO_OPTION);
                if (answer != JOptionPane.YES_OPTION) return;
            }
            invalidatePreview("Abrindo gerador de elencos...");
            SquadGeneratorDialog dialog = new SquadGeneratorDialog(this, db,
                    teamId(), catalog, registry, () -> {
                        model.fireTableDataChanged();
                        refreshCounts();
                        afterApply.run();
                    });
            dialog.setVisible(true);
            model.fireTableDataChanged();
            refreshCounts();
        });
        edit.addActionListener(e -> editSelected());
        apply.addActionListener(e -> applyPreview());
        clear.addActionListener(e -> invalidatePreview("Prévia descartada."));
        close.addActionListener(e -> dispose());
        bottom.add(actions);
        JLabel file = new JLabel("Proteção persistente: " + registry.file());
        file.setFont(new Font("SansSerif", Font.PLAIN, 11));
        file.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(file);
        status.setForeground(DARK);
        status.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(status);
        outer.add(bottom, BorderLayout.SOUTH);
    }

    private String teamId() { return selectedTeamId; }

    private List<String> selectedIds(boolean all) {
        List<String> ids = new ArrayList<>();
        if (all) for (RosterEntry e : entries) ids.add(e.player().id());
        else for (int view : table.getSelectedRows()) {
            ids.add(entries.get(table.convertRowIndexToModel(view)).player().id());
        }
        return ids;
    }

    private void changeProtection(boolean protect, boolean all) {
        List<String> ids = selectedIds(all);
        if (ids.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecione jogadores primeiro.");
            return;
        }
        if (all && JOptionPane.showConfirmDialog(this,
                (protect ? "Proteger" : "Liberar") + " os " + ids.size() + " jogadores deste clube?",
                "Confirmar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Map<String, Boolean> snapshot = new HashMap<>();
        for (String id : ids) {
            snapshot.put(id, registry.isProtected(id));
            registry.setProtected(id, db.player(id).name(), protect);
        }
        try {
            registry.save();
            invalidatePreview("Proteção " + (protect ? "ativada" : "desativada") + " em " + ids.size() + " jogador(es).");
            refreshCounts();
        } catch (IOException ex) {
            for (String id : ids) registry.setProtected(id, db.player(id).name(), snapshot.get(id));
            JOptionPane.showMessageDialog(this, "Não foi possível salvar proteção:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generatePreview() {
        try {
            long seedValue = Long.parseLong(seed.getText().strip());
            Origin selected = (Origin) origin.getSelectedItem();
            List<String> ids = selectedIds(!onlySelected.isSelected());
            if (ids.isEmpty()) throw new IllegalArgumentException("Selecione jogadores ou desmarque 'somente selecionados'.");
            Options options = new Options((Mode) mode.getSelectedItem(), selected.code(), seedValue,
                    (Integer)level.getValue(), changeCountry.isSelected(), updateShirt.isSelected());
            // Fotografa todo o elenco ANTES de gerar a previa para auditar mudancas.
            identitySnapshot = options.mode() == Mode.IDENTITY
                    ? IdentityPreservationVerifier.capture(db, teamId()) : null;
            preview = generator.preview(db, ids, options);
            if (targetOverall.isSelected() && ((Mode) mode.getSelectedItem()).generatesTechnical()) {
                for (Draft draft : preview) {
                    int position = Integer.parseInt(draft.result(db, "POS"));
                    OverallTargetBalancer.tune(draft.edits(), key -> db.playerField(draft.playerId(), key),
                            position, (Integer) level.getValue(), 99);
                }
            }
            if (preview.isEmpty()) {
                invalidatePreview("Nenhum jogador elegível. Verifique proteções e origem selecionada.");
                return;
            }
            apply.setEnabled(true);
            edit.setEnabled(true);
            verifyIdentity.setEnabled(identitySnapshot != null);
            model.fireTableDataChanged();
            long good = preview.stream().filter(d ->
                    Math.abs(db.calculatedOverall(d.playerId(), d.edits()) - (Integer)level.getValue()) <= 1).count();
            status.setText("Prévia: " + preview.size() + " jogador(es); " + (ids.size() - preview.size())
                    + " ignorado(s). " + (targetOverall.isSelected() && ((Mode)mode.getSelectedItem()).generatesTechnical()
                    ? good + " dentro de ±1 OVR. " : "") + "Duplo clique para personalizar.");
        } catch (NumberFormatException ex) {
            invalidatePreview("Seed inválida.");
            JOptionPane.showMessageDialog(this, "Seed deve ser um número inteiro.", "Opções", JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException ex) {
            invalidatePreview("Erro ao gerar prévia.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Opções", JOptionPane.WARNING_MESSAGE);
        }
    }

    private Draft draftFor(String playerId) {
        for (Draft candidate : preview) if (candidate.playerId().equals(playerId)) return candidate;
        return null;
    }

    private void editSelected() {
        if (preview.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Primeiro clique em Gerar prévia.");
            return;
        }
        if (table.getSelectedRow() < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um jogador da tabela.");
            return;
        }
        String id = entries.get(table.convertRowIndexToModel(table.getSelectedRow())).player().id();
        Draft draft = draftFor(id);
        if (draft == null) {
            JOptionPane.showMessageDialog(this, "Este jogador está protegido ou fora da prévia.");
            return;
        }
        GeneratedPlayerEditorDialog editor = new GeneratedPlayerEditorDialog(this, db, draft);
        editor.setVisible(true);
        if (editor.wasSaved()) {
            model.fireTableDataChanged();
            status.setText("Proposta personalizada: " + draft.result(db, "Name")
                    + ". Continua em prévia; clique em Aplicar prévia quando estiver satisfeito.");
        }
    }

    private void applyPreview() {
        if (preview.isEmpty()) return;
        // Modo de identidade e estrito, mesmo se o usuario personalizou a previa.
        if (identitySnapshot != null) {
            var audit = IdentityPreservationVerifier.verifyPreview(db, identitySnapshot, preview, registry);
            if (!audit.approved()) {
                showVerificationReport(audit);
                JOptionPane.showMessageDialog(this, "A aplicação foi bloqueada para preservar os atributos.\n"
                        + "Confira o relatório e gere novamente se necessário.",
                        "Preservação reprovada", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        if (JOptionPane.showConfirmDialog(this,
                "Aplicar " + preview.size() + " propostas editáveis à memória?\n"
                + "IDs, aparências, altura/peso e vínculos permanecerão intactos.\n"
                + "Os arquivos originais só são afetados se você os sobrescrever fora do Studio.\n"
                + "Use Exportar 3 CSVs para criar novas cópias.",
                "Confirmar geração", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            int amount = generator.apply(db, preview);
            IdentityPreservationVerifier.Report appliedAudit = identitySnapshot == null ? null
                    : IdentityPreservationVerifier.verifyApplied(db, identitySnapshot, preview, registry);
            if (appliedAudit != null && !appliedAudit.approved()) {
                showVerificationReport(appliedAudit);
                JOptionPane.showMessageDialog(this,
                        "ATENÇÃO: inconsistência após aplicar! Não exporte esta base.\n"
                        + "Recarregue os CSVs de origem e informe o problema.",
                        "Auditoria reprovada", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String note = appliedAudit == null ? "" : " Auditoria: APROVADO (atributos e OVR inalterados).";
            if (protectGenerated.isSelected()) {
                Map<String, Boolean> existing = new LinkedHashMap<>();
                for (Draft draft : preview) {
                    existing.put(draft.playerId(), registry.isProtected(draft.playerId()));
                    registry.setProtected(draft.playerId(), db.player(draft.playerId()).name(), true);
                }
                try {
                    registry.save();
                } catch (IOException ex) {
                    for (Draft draft : preview) registry.setProtected(draft.playerId(), db.player(draft.playerId()).name(), existing.get(draft.playerId()));
                    note = " AVISO: proteção não salva: " + ex.getMessage();
                    JOptionPane.showMessageDialog(this, note, "Proteção não salva", JOptionPane.WARNING_MESSAGE);
                }
            }
            if (appliedAudit != null) showVerificationReport(appliedAudit);
            invalidatePreview("Aplicados " + amount + " perfis em memória. Exporte os CSVs na tela Clubes." + note);
            refreshCounts();
            afterApply.run();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Prévia não aplicada", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Verificacao manual disponivel somente no modo de geracao de identidades. */
    private void showVerification() {
        if (identitySnapshot == null || preview.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Selecione 'Só nome e nacionalidade' e clique em 'Gerar prévia' primeiro.",
                    "Como verificar", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        var report = IdentityPreservationVerifier.verifyPreview(db, identitySnapshot, preview, registry);
        showVerificationReport(report);
        status.setText(report.approved()
                ? "Verificação APROVADA: atributos, posições, habilidades e OVR preservados."
                : "Verificação REPROVADA: revise as alterações proibidas antes de aplicar.");
    }

    private void showVerificationReport(IdentityPreservationVerifier.Report report) {
        JTextArea area = new JTextArea(report.description(), 20, 72);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        Object[] choices = {"Fechar", "Salvar relatório TXT..."};
        int selected = JOptionPane.showOptionDialog(this, scroll,
                report.approved() ? "Verificador — APROVADO" : "Verificador — BLOQUEADO",
                JOptionPane.DEFAULT_OPTION,
                report.approved() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
                null, choices, choices[0]);
        if (selected == 1) {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("Auditoria_Preservacao_Time_" + teamId() + ".txt"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                Path destination = chooser.getSelectedFile().toPath();
                if (Files.exists(destination) && JOptionPane.showConfirmDialog(this,
                        "O arquivo já existe. Substituir?", "Confirmar",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                try {
                    Files.writeString(destination, report.description(), StandardCharsets.UTF_8);
                    JOptionPane.showMessageDialog(this, "Relatório salvo em:\n" + destination);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(),
                            "Erro ao salvar relatório", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void invalidatePreview(String message) {
        preview = List.of();
        identitySnapshot = null;
        verifyIdentity.setEnabled(false);
        apply.setEnabled(false);
        edit.setEnabled(false);
        model.fireTableDataChanged();
        status.setText(message);
    }

    private void refreshCounts() {
        long protectedCount = entries.stream().filter(e -> registry.isProtected(e.player().id())).count();
        counts.setText("Elenco: " + entries.size() + " | protegidos aqui: " + protectedCount
                + " | livres: " + (entries.size()-protectedCount)
                + " | protegidos na biblioteca: " + registry.size());
    }

    private final class Model extends AbstractTableModel {
        private final String[] columns = {"Proteção", "ID", "Nome atual", "País", "POS",
                "Nome proposto", "Novo país", "Nova POS", "Nova idade", "Estilo previsto", "OVR atual", "OVR prévia"};
        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }
        @Override public Object getValueAt(int row, int col) {
            var p = entries.get(row).player();
            var active = db.player(p.id());
            Draft d = draftFor(p.id());
            if (col == 0) return registry.isProtected(p.id()) ? "PROTEGIDO" : "Livre";
            if (col == 1) return p.id();
            if (col == 2) return active.name();
            if (col == 3) return active.country();
            if (col == 4) return active.position();
            if (col == 10) return db.calculatedOverall(p.id());
            if (col == 11) return d == null ? "—" : db.calculatedOverall(p.id(), d.edits());
            if (d == null) return "—";
            return switch (col) {
                case 5 -> d.result(db, "Name");
                case 6 -> d.result(db, "Country");
                case 7 -> d.result(db, "POS");
                case 8 -> d.result(db, "Age");
                case 9 -> PlayingStyleCatalog.byCode(Integer.parseInt(d.result(db,"PlayingStyle"))).portuguese();
                default -> "";
            };
        }
    }
}
