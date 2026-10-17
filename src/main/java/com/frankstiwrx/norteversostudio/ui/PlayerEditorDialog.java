package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/** Edicao explicita do CSV de jogadores; nenhuma coluna de aparencia e tocada. */
public final class PlayerEditorDialog extends JDialog {
    private static final Color GREEN = new Color(35, 137, 60);
    private static final Color PALE = new Color(248, 251, 248);
    private final PesDatabase database;
    private final String playerId;
    private final Runnable onSaved;
    private final Map<String, JTextField> text = new LinkedHashMap<>();
    private final Map<String, JCheckBox> checks = new LinkedHashMap<>();
    private final JLabel badge = new JLabel();

    public PlayerEditorDialog(Window parent, PesDatabase database, String playerId, Runnable onSaved) {
        super(parent, "Editar jogador — Norteverso Studio", ModalityType.APPLICATION_MODAL);
        this.database = database;
        this.playerId = playerId;
        this.onSaved = onSaved;
        setLayout(new BorderLayout(0, 8));
        setSize(1020, 735);
        setMinimumSize(new Dimension(700, 520));
        setLocationRelativeTo(parent);
        add(createBanner(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dados gerais", scroll(generalTab()));
        tabs.addTab("Atributos", scroll(attributesTab()));
        tabs.addTab("Posicoes", scroll(positionsTab()));
        tabs.addTab("Habilidades", scroll(checkBoxes(PesDatabase.SKILLS, "Habilidades especiais")));
        tabs.addTab("Estilos COM", scroll(checkBoxes(PesDatabase.COM_STYLES, "Estilos de jogo da CPU")));
        add(tabs, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
        reloadFields();
    }

    private JPanel createBanner() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(GREEN);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        badge.setFont(new Font("SansSerif", Font.BOLD, 19));
        badge.setForeground(Color.WHITE);
        panel.add(badge, BorderLayout.CENTER);
        JLabel note = new JLabel("ID " + playerId + "  |  Aparencia e miniface preservadas");
        note.setForeground(Color.WHITE);
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel generalTab() {
        JPanel outer = new JPanel(new java.awt.GridLayout(1, 2, 16, 0));
        outer.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel left = form();
        JPanel right = form();
        String[] fieldsLeft = {"Name", "JapName", "Shirt", "ShirtNational", "Country", "Country2",
                "Age", "Height", "Weight"};
        String[] fieldsRight = {"Foot", "POS", "PlayingStyle", "WeakFootUsage", "WeakFootAcc",
                "Form", "InjuryResistance", "Reputation", "PlayingAttitude"};
        for (int i=0; i<fieldsLeft.length; i++) addField(left, fieldsLeft[i], i);
        for (int i=0; i<fieldsRight.length; i++) addField(right, fieldsRight[i], i);
        addFormSpacer(left); addFormSpacer(right);
        outer.add(left);
        outer.add(right);
        return outer;
    }

    private JPanel attributesTab() {
        JPanel outer = new JPanel(new java.awt.GridLayout(1, 2, 16, 0));
        outer.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel left = form();
        JPanel right = form();
        int half = (PesDatabase.ATTRIBUTES.size()+1)/2;
        for (int i=0;i<half;i++) addField(left, PesDatabase.ATTRIBUTES.get(i), i);
        for (int i=half;i<PesDatabase.ATTRIBUTES.size();i++) addField(right, PesDatabase.ATTRIBUTES.get(i), i-half);
        addFormSpacer(left); addFormSpacer(right);
        outer.add(left);
        outer.add(right);
        return outer;
    }

    private JPanel positionsTab() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JLabel help = new JLabel("0 = inapto  •  1 = posicao secundaria  •  2 = posicao natural. POS (posicao principal) fica em Dados gerais.");
        container.add(help, BorderLayout.NORTH);
        JPanel panel = form();
        for (int i=0; i<PesDatabase.POSITIONS.size(); i++) addField(panel, PesDatabase.POSITIONS.get(i), i);
        addFormSpacer(panel);
        container.add(panel, BorderLayout.CENTER);
        return container;
    }

    private JPanel checkBoxes(List<String> fields, String title) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JLabel top = new JLabel(title + "  (valores True / False do CSV original)");
        top.setFont(new Font("SansSerif", Font.BOLD, 14));
        outer.add(top, BorderLayout.NORTH);
        JPanel grid = new JPanel(new java.awt.GridLayout(0, fields.size()>10 ? 3 : 2, 15, 6));
        grid.setBorder(BorderFactory.createEmptyBorder(12, 2, 5, 2));
        for (String field : fields) {
            JCheckBox check = new JCheckBox(field);
            check.setToolTipText("Campo original: " + field);
            checks.put(field, check);
            grid.add(check);
        }
        outer.add(grid, BorderLayout.CENTER);
        return outer;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 14));
        JLabel hint = new JLabel("Overall e ID sao somente leitura; valores em codigo seguem o CSV original.");
        hint.setForeground(new Color(90, 95, 105));
        footer.add(hint, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton restore = new JButton("Restaurar original");
        JButton cancel = new JButton("Fechar");
        JButton apply = new JButton("Aplicar jogador");
        apply.setBackground(GREEN);
        apply.setForeground(Color.WHITE);
        restore.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this,
                    "Restaurar este jogador ao estado em que foi importado?", "Restaurar",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            database.restorePlayer(playerId);
            reloadFields();
            onSaved.run();
        });
        cancel.addActionListener(e -> {
            if (hasUnsaved() && JOptionPane.showConfirmDialog(this,
                    "Descartar os campos ainda nao aplicados?", "Alteracoes pendentes",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            dispose();
        });
        apply.addActionListener(e -> applyFields());
        buttons.add(restore); buttons.add(cancel); buttons.add(apply);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void applyFields() {
        long countSkills = PesDatabase.SKILLS.stream().filter(f -> checks.get(f).isSelected()).count();
        long countCom = PesDatabase.COM_STYLES.stream().filter(f -> checks.get(f).isSelected()).count();
        if (countSkills > 10 || countCom > 5) {
            JOptionPane.showMessageDialog(this,
                    "O PES permite no maximo 10 habilidades especiais e 5 estilos COM.\n"
                    + "Selecionados: " + countSkills + " habilidades / " + countCom + " estilos COM.",
                    "Limite de habilidades", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Map<String,String> values = changesOnly();
        if (values.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nenhuma alteracao para aplicar.");
            return;
        }
        try {
            database.editPlayer(playerId, values);
            reloadFields();
            onSaved.run();
            JOptionPane.showMessageDialog(this,
                    "Jogador atualizado na memoria. Use Exportar CSVs para criar as copias editadas.",
                    "Alteracoes aplicadas", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Corrija o campo",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private Map<String,String> currentValues() {
        Map<String,String> result = new LinkedHashMap<>();
        text.forEach((name, component) -> result.put(name, component.getText()));
        checks.forEach((name, component) -> result.put(name, component.isSelected() ? "True" : "False"));
        return result;
    }

    private Map<String,String> changesOnly() {
        Map<String,String> modified = currentValues();
        modified.entrySet().removeIf(e -> database.playerField(playerId,e.getKey()).equals(e.getValue()));
        return modified;
    }
    private boolean hasUnsaved() { return !changesOnly().isEmpty(); }

    private void reloadFields() {
        var player = database.player(playerId);
        badge.setText(player.name() + "   |   OVR " + player.overall());
        for (var e : text.entrySet()) e.getValue().setText(database.playerField(playerId,e.getKey()));
        for (var e : checks.entrySet()) e.getValue().setSelected("True".equalsIgnoreCase(database.playerField(playerId,e.getKey())));
    }

    private static void addFormSpacer(JPanel panel) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 99; c.weighty = 1; c.gridwidth = 2;
        c.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), c);
    }

    private static JPanel form() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PALE);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return panel;
    }
    private void addField(JPanel panel, String field, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label(field)), c);
        JTextField editor = new JTextField(15);
        editor.setToolTipText("Campo original do CSV: " + field);
        if (field.equals("POS")) editor.setToolTipText("0 GK, 1 CB, 2 LB, 3 RB, 4 DMF, 5 CMF, 6 LMF, 7 RMF, 8 AMF, 9 LWF, 10 RWF, 11 SS, 12 CF");
        if (field.equals("Foot")) editor.setToolTipText("False = destro; True = canhoto (conforme base analisada)");
        if (field.equals("PlayingStyle")) editor.setToolTipText("Codigo numerico 0 a 21; ainda nao mapeamos os nomes para cada codigo.");
        text.put(field, editor);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(editor, c);
    }
    private static String label(String field) {
        return switch(field) {
            case "Name" -> "Nome";
            case "JapName" -> "Nome japones";
            case "Shirt" -> "Nome na camisa";
            case "ShirtNational" -> "Camisa da selecao";
            case "Country" -> "Pais (codigo)";
            case "Country2" -> "Pais 2 (codigo)";
            case "Age" -> "Idade";
            case "Height" -> "Altura (cm)";
            case "Weight" -> "Peso (kg)";
            case "Foot" -> "Canhoto (True/False)";
            case "POS" -> "Posicao principal (0-12)";
            case "PlayingStyle" -> "Estilo de jogo (codigo)";
            case "WeakFootUsage" -> "Uso do pe ruim (1-4)";
            case "WeakFootAcc" -> "Precisao do pe ruim (1-4)";
            case "Form" -> "Forma (1-8)";
            case "InjuryResistance" -> "Resistencia a lesoes (1-3)";
            case "Reputation" -> "Reputacao (1-8)";
            case "PlayingAttitude" -> "Atitude (1-3)";
            default -> field;
        };
    }
    private static JScrollPane scroll(JPanel child) {
        JScrollPane scroll = new JScrollPane(child);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }
}
