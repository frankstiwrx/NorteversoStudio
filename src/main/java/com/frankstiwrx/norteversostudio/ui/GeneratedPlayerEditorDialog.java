package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PlayingStyleCatalog;
import com.frankstiwrx.norteversostudio.generator.PesNationalityCatalog;
import com.frankstiwrx.norteversostudio.generator.PlayerNameFormat;
import com.frankstiwrx.norteversostudio.generator.PlayerGenerationService.Draft;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

/** Edita SOMENTE uma proposta em memoria. Nem uma linha do CSV e alterada aqui. */
public final class GeneratedPlayerEditorDialog extends JDialog {
    private final PesDatabase db;
    private final Draft draft;
    private final Map<String, JTextField> text = new LinkedHashMap<>();
    private final Map<String, JCheckBox> checks = new LinkedHashMap<>();
    private final JComboBox<PlayingStyleCatalog.Choice> style = new JComboBox<>(
            PlayingStyleCatalog.all().toArray(PlayingStyleCatalog.Choice[]::new));
    private final JSpinner position = new JSpinner(new SpinnerNumberModel(0, 0, 12, 1));
    private final DefaultTableModel attributes = new DefaultTableModel(new String[]{"Atributo", "Valor (1-127)"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return col == 1; }
    };
    private final JTable attributeTable = new JTable(attributes);
    private final Map<String, JSpinner> positions = new LinkedHashMap<>();
    private boolean saved;

    public GeneratedPlayerEditorDialog(Window parent, PesDatabase db, Draft draft) {
        super(parent, "Editar proposta — " + draft.playerId(), ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.draft = draft;
        setMinimumSize(new Dimension(720, 530));
        setSize(890, 670);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));
        JLabel banner = new JLabel("  ID " + draft.playerId() + " | ORIGINAL: " + draft.originalName()
                + " | Campos marcados sao alterados apenas na prévia");
        banner.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        add(banner, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Identidade / carreira", buildBasics());
        tabs.addTab("Atributos", buildAttributes());
        tabs.addTab("Posições", buildPositions());
        tabs.addTab("Habilidades / COM", buildSkills());
        add(tabs, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel note = new JLabel("OVR nao e recalculado aqui. ID, face, miniface, altura/peso e clube intactos.");
        actions.add(note);
        JButton cancel = new JButton("Cancelar");
        JButton save = new JButton("Salvar ajustes na prévia");
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> save());
        actions.add(cancel);
        actions.add(save);
        add(actions, BorderLayout.SOUTH);
    }

    public boolean wasSaved() { return saved; }

    private JPanel buildBasics() {
        JPanel pane = new JPanel(new BorderLayout(5, 5));
        pane.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 9));
        for (String key : List.of("Name", "Shirt", "Country", "Country2", "Age", "WeakFootUsage", "WeakFootAcc", "Reputation")) {
            String caption = switch (key) {
                case "Name" -> "Nome completo";
                case "Shirt" -> "Nome na camisa";
                case "Country" -> "Nacionalidade (207 = Finlândia)";
                case "Country2" -> "Segunda nacionalidade PES (0 = nenhuma)";
                case "Reputation" -> "Reputação (1 a 8)";
                case "Age" -> "Idade";
                case "WeakFootUsage" -> "Frequência do pé ruim (1-4)";
                default -> "Precisão do pé ruim (1-4)";
            };
            grid.add(new JLabel(caption));
            JTextField editor = new JTextField(draft.result(db, key));
            text.put(key, editor);
            grid.add(editor);
        }
        grid.add(new JLabel("Posição principal (0 GO ... 12 CA)"));
        position.setValue(Integer.parseInt(draft.result(db, "POS")));
        grid.add(position);
        grid.add(new JLabel("Estilo de jogo"));
        int current = Integer.parseInt(draft.result(db, "PlayingStyle"));
        for (int index = 0; index < style.getItemCount(); index++) {
            if (style.getItemAt(index).code() == current) { style.setSelectedIndex(index); break; }
        }
        grid.add(style);
        grid.add(new JLabel("Pé dominante (False = direito)"));
        JComboBox<String> dominantFoot = new JComboBox<>(new String[]{"False", "True"});
        dominantFoot.setName("Foot");
        dominantFoot.setSelectedItem(draft.result(db, "Foot"));
        grid.add(dominantFoot);
        text.put("Foot", new JTextField(draft.result(db, "Foot"))); // atualizado pelo seletor
        dominantFoot.addActionListener(e -> text.get("Foot").setText((String)dominantFoot.getSelectedItem()));
        pane.add(grid, BorderLayout.NORTH);

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 7));
        presets.setBorder(BorderFactory.createTitledBorder("Nacionalidades conhecidas (selecionar não muda a origem do nome)"));
        var choices = new ArrayList<>(PesNationalityCatalog.knownCountries().entrySet());
        choices.sort(Comparator.<Map.Entry<String, String>, String>comparing(Map.Entry::getValue)
                .thenComparing(Map.Entry::getKey));
        JComboBox<String> countryChoices = new JComboBox<>();
        countryChoices.addItem("Selecionar nacionalidade...");
        countryChoices.addItem("FI — Finlândia [207]");
        for (var p : choices) if (!p.getKey().equals("207")) {
            countryChoices.addItem(p.getValue() + " — código PES " + p.getKey());
        }
        countryChoices.addActionListener(e -> {
            String selected = (String) countryChoices.getSelectedItem();
            if (selected == null || !selected.contains("[" ) && !selected.contains("código PES ")) return;
            String code = selected.contains("[207]") ? "207" : selected.substring(selected.lastIndexOf(' ') + 1);
            text.get("Country").setText(code);
        });
        presets.add(countryChoices);
        pane.add(presets, BorderLayout.CENTER);
        JLabel tip = new JLabel("Ex.: Martin Silva com origem latina pode representar a Finlândia: selecione FI [207].");
        pane.add(tip, BorderLayout.SOUTH);
        return pane;
    }

    private JPanel buildAttributes() {
        for (String key : PesDatabase.ATTRIBUTES) {
            attributes.addRow(new Object[]{key, draft.result(db, key)});
        }
        attributeTable.setRowHeight(24);
        attributeTable.getColumnModel().getColumn(0).setPreferredWidth(240);
        attributeTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        JPanel pane = new JPanel(new BorderLayout(8, 8));
        pane.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        pane.add(new JLabel("Edite a segunda coluna. Nivel base é referência, não overall garantido."), BorderLayout.NORTH);
        pane.add(new JScrollPane(attributeTable), BorderLayout.CENTER);
        return pane;
    }

    private JPanel buildPositions() {
        JPanel outer = new JPanel(new BorderLayout(5, 5));
        outer.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        outer.add(new JLabel("Proficiencia nas posições: 0 = não atua | 1 = secundária | 2 = natural. Ajuste se trocar POS."), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(0, 4, 12, 10));
        for (String key : PesDatabase.POSITIONS) {
            grid.add(new JLabel(key));
            JSpinner control = new JSpinner(new SpinnerNumberModel(
                    Integer.parseInt(draft.result(db, key)), 0, 2, 1));
            positions.put(key, control);
            grid.add(control);
        }
        outer.add(grid, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildSkills() {
        JPanel list = new JPanel(new GridLayout(0, 3, 8, 6));
        for (String key : PesDatabase.SKILLS) addCheck(list, key);
        for (String key : PesDatabase.COM_STYLES) addCheck(list, key);
        JPanel pane = new JPanel(new BorderLayout());
        pane.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        pane.add(new JLabel("Habilidades e comportamentos COM. Marcar = True, desmarcar = False."), BorderLayout.NORTH);
        pane.add(new JScrollPane(list), BorderLayout.CENTER);
        return pane;
    }

    private void addCheck(JPanel parent, String key) {
        JCheckBox check = new JCheckBox(key, "True".equalsIgnoreCase(draft.result(db, key)));
        checks.put(key, check);
        parent.add(check);
    }

    private void save() {
        try {
            // Só acrescenta campos fora do modo original quando o usuário os mudou.
            Map<String, String> changed = new LinkedHashMap<>(draft.edits());
            for (var e : text.entrySet()) {
                String value = e.getValue().getText().strip();
                if (e.getKey().equals("Name") || e.getKey().equals("Shirt")) {
                    value = PlayerNameFormat.upper(value);
                    e.getValue().setText(value);
                }
                putIfChanged(changed, e.getKey(), value);
            }
            putIfChanged(changed, "POS", position.getValue().toString());
            putIfChanged(changed, "PlayingStyle", Integer.toString(((PlayingStyleCatalog.Choice)style.getSelectedItem()).code()));
            if (attributeTable.isEditing()) attributeTable.getCellEditor().stopCellEditing();
            for (int i = 0; i < attributes.getRowCount(); i++) {
                putIfChanged(changed, (String) attributes.getValueAt(i, 0),
                        String.valueOf(attributes.getValueAt(i, 1)).strip());
            }
            for (var e : checks.entrySet()) putIfChanged(changed, e.getKey(), e.getValue().isSelected() ? "True" : "False");
            for (var e : positions.entrySet()) putIfChanged(changed, e.getKey(), e.getValue().getValue().toString());
            if (changed.containsKey("Name") && changed.get("Name").length() > 32) {
                throw new IllegalArgumentException("Nome completo deve ter no maximo 32 caracteres.");
            }
            if (changed.containsKey("Shirt") && changed.get("Shirt").length() > 16) {
                throw new IllegalArgumentException("Nome na camisa deve ter no maximo 16 caracteres.");
            }
            db.validatePlayerChanges(changed);
            int selectedPosition = (Integer)position.getValue();
            String primary = PesDatabase.POSITIONS.get(selectedPosition);
            String primaryGrade = changed.getOrDefault(primary, db.playerField(draft.playerId(), primary));
            if ((changed.containsKey("POS") || changed.containsKey(primary))
                    && Integer.parseInt(primaryGrade) == 0) {
                throw new IllegalArgumentException("A posição principal " + primary + " não pode ter proficiência 0. Ajuste na aba Posições.");
            }
            draft.replaceEdits(changed);
            saved = true;
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dados inválidos", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void putIfChanged(Map<String, String> next, String key, String value) {
        if (next.containsKey(key) || !value.equals(db.playerField(draft.playerId(), key))) next.put(key, value);
    }
}
