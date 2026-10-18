package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.PesDatabase.Team;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Move uma atribuição de clube sem tocar no ID do jogador ou convocações. */
public final class TransferDialog extends JDialog {
    public TransferDialog(Window owner, PesDatabase db, String assignmentId, Runnable changed) {
        super(owner, "Transferir jogador entre clubes", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(10, 10));
        String playerId = db.assignmentField(assignmentId, "IdPlayer");
        var player = db.player(playerId);
        var origin = db.team(db.assignmentField(assignmentId, "IdTeam"));
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 18, 8, 18));
        content.add(new JLabel("Mover " + player.name() + " de " + origin.name() + " para:"), BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridLayout(0, 2, 10, 9));
        fields.add(new JLabel("Clube de destino:"));
        JComboBox<Team> destination = new JComboBox<>();
        db.teams().stream().filter(t -> !t.national() && !t.id().equals(origin.id()))
                .forEach(destination::addItem);
        fields.add(destination);
        fields.add(new JLabel("Camisa no destino:"));
        JSpinner shirt = new JSpinner(new SpinnerNumberModel(30, 1, 99, 1));
        fields.add(shirt);
        content.add(fields, BorderLayout.CENTER);
        content.add(new JLabel("Capitão e cobradores serão desmarcados na transferência."), BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancelar"), move = new JButton("Transferir");
        cancel.addActionListener(e -> dispose());
        move.addActionListener(e -> {
            Team to = (Team) destination.getSelectedItem();
            if (to == null) return;
            if (JOptionPane.showConfirmDialog(this,
                    "Transferir " + player.name() + " para " + to.name() + "?\n"
                    + "O ID e vínculos com seleções permanecem intactos.",
                    "Confirmar transferência", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try {
                db.transferPlayer(assignmentId, to.id(), shirt.getValue().toString());
                changed.run(); dispose();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Transferência não realizada", JOptionPane.WARNING_MESSAGE);
            }
        });
        buttons.add(cancel); buttons.add(move); add(buttons, BorderLayout.SOUTH);
        pack(); setMinimumSize(new java.awt.Dimension(560, getHeight()));
        setLocationRelativeTo(owner);
    }
}
