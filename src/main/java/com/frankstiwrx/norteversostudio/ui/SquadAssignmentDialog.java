package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Camisa e responsabilidades do jogador em um clube; evita mudanças no registro de jogador. */
public final class SquadAssignmentDialog extends JDialog {
    private static final String[] LABELS = {
        "Capitão", "Falta curta", "Falta longa", "Escanteio direito", "Escanteio esquerdo", "Pênalti"
    };

    public SquadAssignmentDialog(Window owner, PesDatabase db, String assignmentId, Runnable changed) {
        super(owner, "Camisa e funções no clube", ModalityType.APPLICATION_MODAL);
        var p = db.player(db.assignmentField(assignmentId, "IdPlayer"));
        setLayout(new BorderLayout(10, 10));
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));
        content.add(new JLabel("Jogador: " + p.name() + "  (#" + p.id() + ")"), BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridLayout(0, 2, 10, 10));
        fields.add(new JLabel("Número da camisa:"));
        JSpinner shirt = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(db.assignmentField(assignmentId, "ShirtNumber")), 1, 99, 1));
        fields.add(shirt);
        Map<String, JCheckBox> flags = new LinkedHashMap<>();
        for (int i = 0; i < PesDatabase.ASSIGNMENT_FLAGS.size(); i++) {
            String key = PesDatabase.ASSIGNMENT_FLAGS.get(i);
            JCheckBox box = new JCheckBox(LABELS[i],
                    "True".equalsIgnoreCase(db.assignmentField(assignmentId, key)));
            fields.add(box);
            fields.add(new JLabel(" "));
            flags.put(key, box);
        }
        content.add(fields, BorderLayout.CENTER);
        content.add(new JLabel("Uma função marcada será retirada de outros jogadores deste clube."), BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton("Restaurar atribuição original");
        JButton cancel = new JButton("Cancelar");
        JButton save = new JButton("Aplicar");
        reset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Restaurar camisa e funções originais deste vínculo?",
                    "Restaurar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            db.restoreAssignment(assignmentId);
            changed.run(); dispose();
        });
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> {
            Map<String, String> edits = new LinkedHashMap<>();
            edits.put("ShirtNumber", shirt.getValue().toString());
            flags.forEach((key, box) -> edits.put(key, box.isSelected() ? "True" : "False"));
            try {
                db.editAssignment(assignmentId, edits);
                changed.run(); dispose();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Não foi possível aplicar", JOptionPane.WARNING_MESSAGE);
            }
        });
        buttons.add(reset); buttons.add(cancel); buttons.add(save);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setMinimumSize(new java.awt.Dimension(510, getHeight()));
        setLocationRelativeTo(owner);
    }
}
