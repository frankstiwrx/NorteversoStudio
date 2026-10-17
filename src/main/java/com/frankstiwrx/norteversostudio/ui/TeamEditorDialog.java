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
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/** Editor focado em nomes/codigos basicos de time, sem alterar liga, emblemas ou uniformes. */
public final class TeamEditorDialog extends JDialog {
    private static final Color GREEN = new Color(35, 137, 60);
    private final PesDatabase database;
    private final String teamId;
    private final Runnable onSaved;
    private final Map<String, JTextField> editors = new LinkedHashMap<>();
    private final JLabel title = new JLabel();

    public TeamEditorDialog(Window parent, PesDatabase database, String teamId, Runnable onSaved) {
        super(parent, "Editar time — Norteverso Studio", ModalityType.APPLICATION_MODAL);
        this.database = database;
        this.teamId = teamId;
        this.onSaved = onSaved;
        setLayout(new BorderLayout());
        setSize(690, 640);
        setMinimumSize(new Dimension(510, 460));
        setLocationRelativeTo(parent);
        add(banner(), BorderLayout.NORTH);
        add(form(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        reload();
    }
    private JPanel banner() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(GREEN);
        p.setBorder(BorderFactory.createEmptyBorder(14, 17, 14, 17));
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        p.add(title, BorderLayout.NORTH);
        JLabel note = new JLabel("ID " + teamId + "  |  Emblemas, uniformes e atribuicoes preservados");
        note.setForeground(Color.WHITE);
        p.add(note, BorderLayout.SOUTH);
        return p;
    }
    private JScrollPane form() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        for (int i=0; i<PesDatabase.TEAM_FIELDS.size(); i++) {
            String field = PesDatabase.TEAM_FIELDS.get(i);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = i; c.gridx = 0; c.insets = new Insets(5, 5, 5, 10);
            c.anchor = GridBagConstraints.WEST;
            p.add(new JLabel(label(field)), c);
            JTextField editor = new JTextField(25);
            editor.setToolTipText("Coluna original: " + field);
            editors.put(field, editor);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            p.add(editor, c);
        }
        JScrollPane scroll = new JScrollPane(p);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }
    private JPanel footer() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton restore = new JButton("Restaurar original");
        JButton close = new JButton("Fechar");
        JButton save = new JButton("Aplicar time");
        save.setBackground(GREEN); save.setForeground(Color.WHITE);
        restore.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Restaurar os dados originais deste time?",
                    "Restaurar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            database.restoreTeam(teamId);
            reload(); onSaved.run();
        });
        close.addActionListener(e -> {
            if (hasUnsaved() && JOptionPane.showConfirmDialog(this, "Descartar os campos nao aplicados?",
                    "Alteracoes pendentes", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            dispose();
        });
        save.addActionListener(e -> {
            Map<String, String> values = new LinkedHashMap<>();
            editors.forEach((field,component) -> {
                if (!component.getText().equals(database.teamField(teamId,field))) {
                    values.put(field,component.getText());
                }
            });
            if (values.isEmpty()) { JOptionPane.showMessageDialog(this, "Nenhuma alteracao para aplicar."); return; }
            try {
                database.editTeam(teamId, values);
                reload(); onSaved.run();
                JOptionPane.showMessageDialog(this, "Time atualizado na memoria. Exporte copias para usar no Ejogc327.");
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Corrija o campo", JOptionPane.WARNING_MESSAGE);
            }
        });
        footer.add(restore); footer.add(close); footer.add(save);
        return footer;
    }
    private void reload() {
        title.setText(database.team(teamId).name());
        editors.forEach((field,component) -> component.setText(database.teamField(teamId, field)));
    }
    private boolean hasUnsaved() {
        for (var e : editors.entrySet()) {
            if (!database.teamField(teamId, e.getKey()).equals(e.getValue().getText())) return true;
        }
        return false;
    }
    private static String label(String f) {
        return switch(f) {
            case "Name" -> "Nome principal";
            case "Country" -> "Pais (codigo)";
            case "Stadium" -> "Estadio (ID)";
            case "Coach" -> "Tecnico (ID)";
            case "NameEnglish" -> "Nome ingles";
            case "NameUSEnglish" -> "Nome ingles (EUA)";
            case "NamePortuguese" -> "Nome portugues";
            case "NameBrazilian" -> "Nome brasileiro";
            case "ShortNameLicensed" -> "Nome curto licenciado";
            case "ShortNameFake" -> "Nome curto alternativo";
            case "Abbreviation" -> "Abreviacao";
            default -> f;
        };
    }
}
