package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import com.frankstiwrx.norteversostudio.generator.SquadAttributeAdjuster;
import com.frankstiwrx.norteversostudio.generator.SquadAttributeAdjuster.Area;
import com.frankstiwrx.norteversostudio.generator.SquadAttributeAdjuster.Change;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.Window;
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
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;

/** Ajuste percentual de um clube com prévia que respeita IDs protegidos. */
public final class SquadAdjustDialog extends JDialog {
    private final PesDatabase db;
    private final String teamId;
    private final SquadAttributeAdjuster engine;
    private final Runnable onApply;
    private final JComboBox<Area> category = new JComboBox<>(Area.values());
    private final JSpinner percentage = new JSpinner(new SpinnerNumberModel(10, 1, 30, 1));
    private final JComboBox<String> direction = new JComboBox<>(new String[]{"Aumentar (+)", "Reduzir (-)"});
    private final JCheckBox limit = new JCheckBox("Limitar novos picos a 94 (recomendado)", true);
    private final JLabel summary = new JLabel("Configure e clique em 'Calcular prévia'. Nenhum valor será salvo ainda.");
    private final PreviewTable model = new PreviewTable();
    private SquadAttributeAdjuster.Preview preview;
    private final JButton apply = new JButton("Aplicar ajustes à memória");

    public SquadAdjustDialog(Window owner, PesDatabase db, String teamId,
                             ProtectionRegistry registry, Runnable onApply) {
        super(owner, "Norteverso Studio — Ajuste de atributos do elenco", ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.teamId = teamId;
        this.engine = new SquadAttributeAdjuster(registry);
        this.onApply = onApply;
        build();
        setMinimumSize(new Dimension(780, 490));
        setSize(960, 590);
        setLocationRelativeTo(owner);
    }

    private void build() {
        JPanel base = new JPanel(new BorderLayout(8, 8));
        base.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setContentPane(base);
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("AJUSTAR ELENCO — " + db.team(teamId).name());
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 17f));
        north.add(title);
        north.add(Box.createVerticalStrut(7));
        north.add(new JLabel("Exemplo: 80 com +10% vira 88; 80 com -10% vira 72. Arredondamento ao inteiro."));
        north.add(new JLabel("Preserva nomes, IDs, faces, posições, clubes e jogadores protegidos. Não recalcula OverallStats."));
        north.add(Box.createVerticalStrut(8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controls.setBorder(BorderFactory.createTitledBorder("Como deseja ajustar?"));
        controls.add(new JLabel("Categoria:")); controls.add(category);
        controls.add(new JLabel("Operação:")); controls.add(direction);
        controls.add(new JLabel("Percentual:")); controls.add(percentage);
        controls.add(new JLabel("%"));
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        north.add(controls);
        north.add(limit);
        north.add(Box.createVerticalStrut(7));
        north.add(new JLabel("'Todos relevantes' ajusta 20 atributos de linha, ou apenas 5 atributos GK para goleiros."));
        north.add(new JLabel("Categorias direcionadas usam pesos diferentes; goleiros são ignorados fora do modo GO."));
        north.add(Box.createVerticalStrut(9));
        for (java.awt.Component item : north.getComponents()) {
            if (item instanceof javax.swing.JComponent component) component.setAlignmentX(LEFT_ALIGNMENT);
        }
        limit.setToolTipText("Em upgrades, valores abaixo de 94 não ultrapassam 94. Valores já acima de 94 não são reduzidos.");
        category.setToolTipText("Escolhe quais características receberão o ajuste percentual.");
        base.add(north, BorderLayout.NORTH);
        JTable table = new JTable(model);
        table.setRowHeight(25);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        base.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(summary, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton calculate = new JButton("Calcular prévia (não aplica)");
        JButton close = new JButton("Fechar");
        actions.add(calculate); actions.add(apply); actions.add(close);
        bottom.add(actions, BorderLayout.SOUTH);
        base.add(bottom, BorderLayout.SOUTH);
        apply.setEnabled(false);
        calculate.addActionListener(e -> calculate());
        apply.addActionListener(e -> applyChanges());
        close.addActionListener(e -> dispose());
        category.addActionListener(e -> resetPreview());
        percentage.addChangeListener(e -> resetPreview());
        direction.addActionListener(e -> resetPreview());
        limit.addActionListener(e -> resetPreview());
    }

    private void resetPreview() {
        preview = null;
        apply.setEnabled(false);
        model.fireTableDataChanged();
        summary.setText("Opção alterada: calcule nova prévia antes de aplicar.");
    }

    private void calculate() {
        try {
            int amount = (int)percentage.getValue() * (direction.getSelectedIndex() == 0 ? 1 : -1);
            preview = engine.preview(db, teamId, (Area)category.getSelectedItem(), amount, limit.isSelected());
            model.fireTableDataChanged();
            apply.setEnabled(!preview.changes().isEmpty());
            summary.setText("Prévia: " + preview.changes().size() + " jogadores afetados; "
                    + preview.protectedCount() + " protegidos; " + preview.skippedCount()
                    + " sem alteração. A aplicação continua em memória até exportar.");
        } catch (RuntimeException ex) {
            resetPreview();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Não foi possível calcular", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void applyChanges() {
        if (preview == null) return;
        if (JOptionPane.showConfirmDialog(this,
                "Aplicar ajuste a " + preview.changes().size() + " jogadores?\n"
              + "Os protegidos não serão alterados.\n"
              + "Somente uma EXPORTAÇÃO COMPLETA deve ser importada nos BINs/EDIT.",
                "Confirmar ajuste de elenco", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            int count = engine.apply(db, preview);
            preview = null;
            apply.setEnabled(false);
            model.fireTableDataChanged();
            onApply.run();
            summary.setText("Ajustes aplicados em " + count + " jogadores na memória. Exporte o banco completo quando aprovado.");
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Prévia desatualizada", JOptionPane.ERROR_MESSAGE);
        }
    }

    private final class PreviewTable extends AbstractTableModel {
        private final String[] cols = {"ID", "Jogador", "POS", "Atributos alterados", "Média antes", "Média depois", "Maior depois"};
        @Override public int getRowCount() { return preview == null ? 0 : preview.changes().size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int col) { return cols[col]; }
        @Override public Object getValueAt(int row, int col) {
            Change change = preview.changes().get(row);
            double before = change.before().values().stream().mapToInt(Integer::parseInt).average().orElse(0);
            double after = change.after().values().stream().mapToInt(Integer::parseInt).average().orElse(0);
            return switch (col) {
                case 0 -> change.playerId();
                case 1 -> change.name();
                case 2 -> change.position();
                case 3 -> change.after().size();
                case 4 -> String.format(java.util.Locale.ROOT, "%.1f", before);
                case 5 -> String.format(java.util.Locale.ROOT, "%.1f", after);
                case 6 -> change.after().values().stream().mapToInt(Integer::parseInt).max().orElse(0);
                default -> "";
            };
        }
    }
}
