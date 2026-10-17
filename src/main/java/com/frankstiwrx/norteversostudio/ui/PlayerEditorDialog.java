package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.AttributeAdjuster;
import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PlayingStyleCatalog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Edicao de jogador: preserva ID, registros e recursos originais de aparencia. */
public final class PlayerEditorDialog extends JDialog {
    private static final Color GREEN = new Color(35, 137, 60);
    private static final Color PALE = new Color(248, 251, 248);
    private static final Color PENDING = new Color(255, 248, 214);

    private final PesDatabase database;
    private final String playerId;
    private final Runnable onSaved;
    private final Map<String, JTextField> text = new LinkedHashMap<>();
    private final Map<String, JCheckBox> checks = new LinkedHashMap<>();
    private final JLabel badge = new JLabel();
    private final JComboBox<PlayingStyleCatalog.Choice> style = new JComboBox<>(
            PlayingStyleCatalog.all().toArray(PlayingStyleCatalog.Choice[]::new));
    private final JSpinner reputation = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    private final JLabel bulkMessage = new JLabel("Ajustes ficam em previa ate clicar em Aplicar jogador.");

    public PlayerEditorDialog(Window parent, PesDatabase database, String playerId, Runnable onSaved) {
        super(parent, "Editar jogador — Norteverso Studio", ModalityType.APPLICATION_MODAL);
        this.database = database;
        this.playerId = playerId;
        this.onSaved = onSaved;
        setLayout(new BorderLayout(0, 8));
        setSize(1100, 755);
        setMinimumSize(new Dimension(760, 540));
        setLocationRelativeTo(parent);
        add(createBanner(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dados gerais", scroll(generalTab()));
        tabs.addTab("Atributos + ajustes", scroll(attributesTab()));
        tabs.addTab("Posicoes", scroll(positionsTab()));
        tabs.addTab("Habilidades", scroll(checkBoxes(PesDatabase.SKILLS, "Habilidades especiais")));
        tabs.addTab("Estilos COM", scroll(checkBoxes(PesDatabase.COM_STYLES, "Estilos da CPU")));
        tabs.addTab("Reputacao", scroll(reputationTab()));
        tabs.addTab("Miniface", minifaceTab());
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
        JLabel note = new JLabel("ID " + playerId + "  |  CSV de aparencia, face 3D e miniface nao alterados");
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
                "Form", "InjuryResistance", "PlayingAttitude"};
        for (int i = 0; i < fieldsLeft.length; i++) addField(left, fieldsLeft[i], i);
        for (int i = 0; i < fieldsRight.length; i++) addField(right, fieldsRight[i], i);
        addFormSpacer(left);
        addFormSpacer(right);
        outer.add(left);
        outer.add(right);
        return outer;
    }

    private JPanel attributesTab() {
        JPanel outer = new JPanel(new BorderLayout(0, 12));
        outer.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(new Color(230, 242, 234));
        toolbar.setBorder(BorderFactory.createTitledBorder("Ajuste por grupo — configuracao Norteverso"));
        JComboBox<AttributeAdjuster.Group> group = new JComboBox<>(AttributeAdjuster.Group.values());
        JSpinner intensity = new JSpinner(new SpinnerNumberModel(5, 1, 30, 1));
        JComboBox<String> mode = new JComboBox<>(new String[]{"Pontos", "Porcentagem (%)"});
        JButton plus = new JButton("+ Aumentar");
        JButton minus = new JButton("- Diminuir");
        JButton resetStats = new JButton("Recarregar atributos");
        toolbar.add(new JLabel("Grupo:"));
        toolbar.add(group);
        toolbar.add(new JLabel("Valor:"));
        toolbar.add(intensity);
        toolbar.add(mode);
        toolbar.add(plus);
        toolbar.add(minus);
        toolbar.add(resetStats);
        plus.addActionListener(e -> adjustAttributes((AttributeAdjuster.Group) group.getSelectedItem(),
                (Integer) intensity.getValue(), mode.getSelectedIndex() == 1, true));
        minus.addActionListener(e -> adjustAttributes((AttributeAdjuster.Group) group.getSelectedItem(),
                (Integer) intensity.getValue(), mode.getSelectedIndex() == 1, false));
        resetStats.addActionListener(e -> {
            for (String field : PesDatabase.ATTRIBUTES) {
                text.get(field).setText(database.playerField(playerId, field));
                text.get(field).setBackground(Color.WHITE);
            }
            bulkMessage.setText("Atributos restaurados aos valores aplicados anteriormente.");
        });
        outer.add(toolbar, BorderLayout.NORTH);
        JPanel columns = new JPanel(new java.awt.GridLayout(1, 2, 16, 0));
        JPanel left = form();
        JPanel right = form();
        int half = (PesDatabase.ATTRIBUTES.size() + 1) / 2;
        for (int i = 0; i < half; i++) addField(left, PesDatabase.ATTRIBUTES.get(i), i);
        for (int i = half; i < PesDatabase.ATTRIBUTES.size(); i++) {
            addField(right, PesDatabase.ATTRIBUTES.get(i), i-half);
        }
        addFormSpacer(left);
        addFormSpacer(right);
        columns.add(left);
        columns.add(right);
        outer.add(columns, BorderLayout.CENTER);
        JPanel foot = new JPanel(new BorderLayout());
        foot.add(bulkMessage, BorderLayout.NORTH);
        JLabel note = new JLabel("Peso por atributo definido pelo Norteverso; limite: 1 a 127. OVR nao e recalculado aqui.");
        note.setForeground(new Color(93, 98, 105));
        foot.add(note, BorderLayout.SOUTH);
        outer.add(foot, BorderLayout.SOUTH);
        return outer;
    }

    private void adjustAttributes(AttributeAdjuster.Group group, int intensity, boolean percent,
                                  boolean increase) {
        try {
            Map<String, Integer> current = new LinkedHashMap<>();
            for (String field : PesDatabase.ATTRIBUTES) {
                int value = Integer.parseInt(text.get(field).getText().trim());
                if (value < 1 || value > 127) {
                    throw new IllegalArgumentException("Valor invalido em " + field + " (1 a 127).");
                }
                current.put(field, value);
            }
            int pos = Integer.parseInt(text.get("POS").getText().trim());
            Map<String, Integer> result = AttributeAdjuster.adjust(current, group,
                    intensity, percent, increase, pos == 0);
            result.forEach((field, value) -> {
                JTextField editor = text.get(field);
                editor.setText(String.valueOf(value));
                editor.setBackground(PENDING);
            });
            bulkMessage.setText(String.format("Previa: %s%s %d%s em %s (%d atributos alterados).",
                    increase ? "+" : "-", percent ? "" : "", intensity,
                    percent ? "%" : " pts", group, result.size()));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Um atributo ou a posicao principal tem texto nao numerico. Corrija antes do ajuste.",
                    "Valor invalido", JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ajuste indisponivel",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private JPanel positionsTab() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JLabel help = new JLabel("0 = inapto  •  1 = secundaria  •  2 = natural. POS (principal) em Dados gerais.");
        container.add(help, BorderLayout.NORTH);
        JPanel panel = form();
        for (int i = 0; i < PesDatabase.POSITIONS.size(); i++) addField(panel, PesDatabase.POSITIONS.get(i), i);
        addFormSpacer(panel);
        container.add(panel, BorderLayout.CENTER);
        return container;
    }

    private JPanel checkBoxes(List<String> fields, String title) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JLabel top = new JLabel(title + " (True / False no arquivo CSV)");
        top.setFont(new Font("SansSerif", Font.BOLD, 14));
        outer.add(top, BorderLayout.NORTH);
        JPanel grid = new JPanel(new java.awt.GridLayout(0, fields.size() > 10 ? 3 : 2, 15, 6));
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

    private JPanel reputationTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JLabel title = new JLabel("Reputacao e marcadores do arquivo de jogadores");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(16));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(new JLabel("Reputacao (1 a 8):"));
        reputation.setPreferredSize(new Dimension(80, 27));
        row.add(reputation);
        row.add(new JLabel("1 = menor  |  8 = maior"));
        panel.add(row);
        JCheckBox legend = new JCheckBox("Legend (marcador de jogador lenda no banco)");
        legend.setToolTipText("O campo Legend e diferente da Team Role 'Legend' da Master League.");
        legend.setAlignmentX(LEFT_ALIGNMENT);
        checks.put("Legend", legend);
        panel.add(Box.createVerticalStrut(12));
        panel.add(legend);
        panel.add(Box.createVerticalStrut(18));
        JLabel note1 = new JLabel("O PES tambem possui Team Roles: Idolo, Bandiera, Lenda etc., na Master League.");
        note1.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(note1);
        JLabel note2 = new JLabel("Essas funcoes nao aparecem como coluna editavel nos CSVs fornecidos.");
        note2.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(note2);
        JLabel note3 = new JLabel("Aumentar Reputation ou marcar Legend NAO garante uma Team Role na Master League.");
        note3.setForeground(new Color(147, 58, 45));
        note3.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(note3);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel minifaceTab() {
        JPanel outer = new JPanel(new BorderLayout(0, 14));
        outer.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JLabel help = new JLabel("Pre-visualizacao opcional (PNG/JPG). Nenhum arquivo de aparencia sera modificado.");
        outer.add(help, BorderLayout.NORTH);
        JLabel preview = new JLabel("Nenhuma imagem selecionada", SwingConstants.CENTER);
        preview.setOpaque(true);
        preview.setBackground(new Color(235, 238, 241));
        preview.setPreferredSize(new Dimension(320, 360));
        outer.add(preview, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new java.awt.GridLayout(0, 1, 0, 7));
        JButton choose = new JButton("Abrir miniface PNG/JPG...");
        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Selecione uma miniface para visualizar (nenhuma alteracao no jogo)");
            chooser.setFileFilter(new FileNameExtensionFilter("Imagens PNG e JPG", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File file = chooser.getSelectedFile();
            try {
                BufferedImage original = ImageIO.read(file);
                if (original == null) throw new IOException("Imagem nao suportada.");
                double ratio = Math.min(320.0 / original.getWidth(), 350.0 / original.getHeight());
                int w = Math.max(1, (int) Math.round(original.getWidth() * ratio));
                int h = Math.max(1, (int) Math.round(original.getHeight() * ratio));
                preview.setText("");
                preview.setIcon(new javax.swing.ImageIcon(original.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH)));
                preview.setToolTipText(file.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Nao foi possivel abrir: " + ex.getMessage(),
                        "Imagem invalida", JOptionPane.WARNING_MESSAGE);
            }
        });
        bottom.add(choose);
        bottom.add(new JLabel("Face 3D nao pode ser renderizada somente pelos CSVs; requer modelos e texturas do PES."));
        bottom.add(new JLabel("A miniface selecionada e exibida apenas nesta janela; nao e exportada nem vinculada ao jogador."));
        outer.add(bottom, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 14));
        JLabel hint = new JLabel("OVR / ID somente leitura • Edicoes aplicadas em memoria • Exporte copias dos CSVs");
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
        buttons.add(restore);
        buttons.add(cancel);
        buttons.add(apply);
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
        Map<String, String> values = changesOnly();
        if (values.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nenhuma alteracao para aplicar.");
            return;
        }
        try {
            database.editPlayer(playerId, values);
            reloadFields();
            onSaved.run();
            JOptionPane.showMessageDialog(this,
                    "Jogador atualizado em memoria. Use Exportar CSVs editados para criar copias.",
                    "Alteracoes aplicadas", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Corrija o campo",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private Map<String, String> currentValues() {
        Map<String, String> result = new LinkedHashMap<>();
        text.forEach((name, component) -> result.put(name, component.getText()));
        checks.forEach((name, component) -> result.put(name, component.isSelected() ? "True" : "False"));
        var selection = (PlayingStyleCatalog.Choice) style.getSelectedItem();
        if (selection != null) result.put("PlayingStyle", String.valueOf(selection.code()));
        result.put("Reputation", reputation.getValue().toString());
        return result;
    }

    private Map<String, String> changesOnly() {
        Map<String, String> modified = currentValues();
        modified.entrySet().removeIf(e -> database.playerField(playerId, e.getKey()).equals(e.getValue()));
        return modified;
    }
    private boolean hasUnsaved() { return !changesOnly().isEmpty(); }

    private void reloadFields() {
        var player = database.player(playerId);
        badge.setText(player.name() + "   |   OVR " + player.overall());
        for (var e : text.entrySet()) {
            e.getValue().setText(database.playerField(playerId, e.getKey()));
            e.getValue().setBackground(Color.WHITE);
        }
        for (var e : checks.entrySet()) {
            e.getValue().setSelected("True".equalsIgnoreCase(database.playerField(playerId, e.getKey())));
        }
        style.setSelectedItem(PlayingStyleCatalog.byCode(
                Integer.parseInt(database.playerField(playerId, "PlayingStyle"))));
        reputation.setValue(Integer.parseInt(database.playerField(playerId, "Reputation")));
        bulkMessage.setText("Ajustes ficam em previa ate clicar em Aplicar jogador.");
    }

    private static void addFormSpacer(JPanel panel) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 99;
        c.weighty = 1;
        c.gridwidth = 2;
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
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        if (field.equals("PlayingStyle")) {
            style.setToolTipText("Codigo salvo no CSV; mapeamento baseado no exportador e conferido com jogadores da base.");
            panel.add(style, c);
            return;
        }
        JTextField editor = new JTextField(15);
        editor.setToolTipText("Campo CSV: " + field);
        if (field.equals("POS")) {
            editor.setToolTipText("0 GO, 1 ZC, 2 LE, 3 LD, 4 VOL, 5 MC, 6 ME, 7 MD, 8 MAT, 9 PE, 10 PD, 11 SA, 12 CA");
        }
        if (field.equals("Foot")) {
            editor.setToolTipText("False = destro; True = canhoto (base analisada)");
        }
        text.put(field, editor);
        panel.add(editor, c);
    }

    private static String label(String field) {
        return switch (field) {
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
            case "PlayingStyle" -> "Estilo de jogo";
            case "WeakFootUsage" -> "Uso do pe ruim (1-4)";
            case "WeakFootAcc" -> "Precisao do pe ruim (1-4)";
            case "Form" -> "Forma (1-8)";
            case "InjuryResistance" -> "Resistencia a lesoes (1-3)";
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
