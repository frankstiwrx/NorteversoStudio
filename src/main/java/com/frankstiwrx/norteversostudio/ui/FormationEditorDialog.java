package com.frankstiwrx.norteversostudio.ui;

import com.frankstiwrx.norteversostudio.csv.Csv;
import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.data.TacticsFields;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;

/** Editor das posições e parâmetros táticos CSV, preservando os arquivos originais. */
public final class FormationEditorDialog extends JDialog {
    private static final String[] POSITIONS = {"GO", "ZC", "LE", "LD", "VOL", "MC", "ME", "MD", "MAT", "PTE", "PTD", "SA", "CA"};
    private final List<List<String>> formations;
    private final List<List<String>> tactics;
    private final Map<String, JSpinner> tacticSpinners = new LinkedHashMap<>();
    private final Path tacticsSource;
    private final List<PesDatabase.RosterEntry> roster;
    private final Map<String, Integer> formationColumns, tacticColumns;
    private final List<List<String>> teamTactics = new ArrayList<>();
    private final JComboBox<TacticOption> tacticSelector = new JComboBox<>();
    private final JComboBox<Integer> variantSelector = new JComboBox<>();
    private final List<List<String>> current = new ArrayList<>();
    private final FormationTableModel model = new FormationTableModel();
    private final JTable table = new JTable(model);
    private final JSpinner xSpin = new JSpinner(new SpinnerNumberModel(20, 0, 50, 1));
    private final JSpinner ySpin = new JSpinner(new SpinnerNumberModel(50, 0, 100, 1));
    private final JComboBox<String> positionSelector = new JComboBox<>();
    private final Pitch pitch = new Pitch();
    private final JLabel details = new JLabel(" ");
    private final Path source;
    private int changes = 0;
    private int tacticChanges = 0;
    private boolean selecting = false;

    private record TacticOption(String id, String strategy) {
        @Override public String toString() { return "Estratégia " + strategy + "  ·  Tática #" + id; }
    }

    public static void open(Window owner, PesDatabase database, String teamId,
                            Path tacticsFile, Path formationsFile) throws IOException {
        List<List<String>> tactics = Csv.ler(tacticsFile), formations = Csv.ler(formationsFile);
        if (tactics.isEmpty() || formations.isEmpty()) throw new IOException("CSV de tática ou formação vazio.");
        Map<String, Integer> tc = columns(tactics.get(0)), fc = columns(formations.get(0));
        for (String col : List.of("Id", "IdTeam", "NumStrategy")) require(tc, col);
        for (TacticsFields.Field field : TacticsFields.FIELDS) require(tc, field.key());
        for (String col : List.of("IdTactic", "NumFormation", "NumPlayer", "Position", "LocationX", "LocationY")) require(fc, col);
        List<List<String>> chosen = new ArrayList<>();
        for (int i=1;i<tactics.size();i++) if (teamId.equals(tactics.get(i).get(tc.get("IdTeam")))) {
            chosen.add(tactics.get(i));
        }
        if (chosen.isEmpty()) throw new IOException("Este clube não possui tática no CSV selecionado.");
        new FormationEditorDialog(owner, teamId, chosen, tactics, formations, tc, fc,
                tacticsFile, formationsFile, database.roster(teamId)).setVisible(true);
    }

    private static Map<String, Integer> columns(List<String> header) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i=0;i<header.size();i++) out.put(header.get(i).replace("\ufeff", "").trim(),i);
        return out;
    }
    private static void require(Map<String,Integer> headers,String field) throws IOException {
        if (!headers.containsKey(field)) throw new IOException("Coluna obrigatória não encontrada: " + field);
    }
    private FormationEditorDialog(Window owner, String teamId, List<List<String>> chosen,
                                  List<List<String>> tactics, List<List<String>> formations,
                                  Map<String,Integer> tc, Map<String,Integer> fc,
                                  Path tacticsSource, Path source,
                                  List<PesDatabase.RosterEntry> roster) {
        super(owner, "Formações — clube #" + teamId, ModalityType.APPLICATION_MODAL);
        this.formations = formations;
        this.tactics = tactics;
        this.tacticsSource = tacticsSource.toAbsolutePath().normalize();
        this.roster = new ArrayList<>(roster);
        this.tacticColumns = tc;
        this.formationColumns = fc;
        this.source = source.toAbsolutePath().normalize();
        this.teamTactics.addAll(chosen);
        setLayout(new BorderLayout(8, 8));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 9));
        header.add(new JLabel("Tática:"));
        for (List<String> row : teamTactics) tacticSelector.addItem(new TacticOption(
                row.get(tc.get("Id")), row.get(tc.get("NumStrategy"))));
        header.add(tacticSelector);
        header.add(new JLabel("Formação:"));
        header.add(variantSelector);
        add(header, BorderLayout.NORTH);

        JPanel editing = new JPanel(new BorderLayout(8, 8));
        editing.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.setRowHeight(26);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        editing.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Profundidade (X):")); controls.add(xSpin);
        controls.add(new JLabel("Lateral (Y):")); controls.add(ySpin);
        controls.add(new JLabel("Posição:"));
        for (int i=0;i<POSITIONS.length;i++) positionSelector.addItem(i + " — " + POSITIONS[i]);
        controls.add(positionSelector);
        JButton apply = new JButton("Aplicar à posição");
        apply.addActionListener(e -> applyPosition());
        controls.add(apply);
        editing.add(controls, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editing, pitch);
        split.setResizeWeight(.63); split.setDividerLocation(660);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Formações (campo)", split);
        tabs.addTab("Tática (ataque/defesa)", createTacticsPanel());
        tabs.addTab("Elenco (consulta)", createRosterPanel());
        add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(5, 5));
        footer.setBorder(BorderFactory.createEmptyBorder(6, 12, 10, 12));
        details.setText("X: 0–50 (defesa → ataque) • Y: 0–100 (lateral) • Alterações apenas em memória");
        footer.add(details, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Fechar"), export = new JButton("Exportar 2 CSVs (tática + formação)...");
        close.addActionListener(e -> close());
        export.addActionListener(e -> exportChanges());
        buttons.add(close); buttons.add(export); footer.add(buttons, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        tacticSelector.addActionListener(e -> changeTactic());
        variantSelector.addActionListener(e -> refreshList());
        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) updateControls();
        });
        changeTactic();
        setSize(1200, 760); setMinimumSize(new Dimension(920, 580));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { close(); }
        });
    }

    private void changeTactic() {
        TacticOption tactic = (TacticOption) tacticSelector.getSelectedItem();
        if (tactic == null) return;
        selecting = true;
        variantSelector.removeAllItems();
        List<Integer> variants = new ArrayList<>();
        for (int i=1;i<formations.size();i++) {
            List<String> row = formations.get(i);
            if (tactic.id().equals(row.get(formationColumns.get("IdTactic")))) {
                int number = Integer.parseInt(row.get(formationColumns.get("NumFormation")));
                if (!variants.contains(number)) variants.add(number);
            }
        }
        variants.sort(Comparator.naturalOrder());
        variants.forEach(variantSelector::addItem);
        selecting = false;
        refreshList();
        loadTacticControls();
    }
    private void refreshList() {
        if (selecting) return;
        TacticOption tactic = (TacticOption) tacticSelector.getSelectedItem();
        Integer variant = (Integer) variantSelector.getSelectedItem();
        current.clear();
        if (tactic != null && variant != null) {
            for (int i=1;i<formations.size();i++) {
                List<String> row = formations.get(i);
                if (tactic.id().equals(row.get(formationColumns.get("IdTactic")))
                        && variant.toString().equals(row.get(formationColumns.get("NumFormation")))) current.add(row);
            }
            current.sort(Comparator.comparingInt(r -> Integer.parseInt(r.get(formationColumns.get("NumPlayer")))));
        }
        model.fireTableDataChanged(); pitch.repaint();
        if (!current.isEmpty()) table.setRowSelectionInterval(0,0);
        else details.setText("Nenhuma posição cadastrada para esta formação.");
    }
    private void updateControls() {
        int row = table.getSelectedRow();
        if (row<0 || row>=current.size()) return;
        List<String> f=current.get(row);
        xSpin.setValue(Integer.parseInt(f.get(formationColumns.get("LocationX"))));
        ySpin.setValue(Integer.parseInt(f.get(formationColumns.get("LocationY"))));
        int pos=Integer.parseInt(f.get(formationColumns.get("Position")));
        positionSelector.setSelectedIndex(Math.min(POSITIONS.length-1,Math.max(0,pos)));
        pitch.repaint();
    }
    private void applyPosition() {
        int selected=table.getSelectedRow();
        if (selected<0) { JOptionPane.showMessageDialog(this,"Selecione uma linha da formação."); return; }
        List<String> r=current.get(selected);
        String x=xSpin.getValue().toString(), y=ySpin.getValue().toString();
        String p=Integer.toString(positionSelector.getSelectedIndex());
        boolean changed=false;
        for (String[] entry : new String[][] {{"LocationX",x},{"LocationY",y},{"Position",p}}) {
            int col=formationColumns.get(entry[0]);
            if (!r.get(col).equals(entry[1])) { r.set(col,entry[1]); changed=true; }
        }
        if (changed) { changes++; model.fireTableRowsUpdated(selected,selected); pitch.repaint(); }
        details.setText(changes + " alterações aplicadas (em memória) — exporte um CSV novo para salvar.");
    }
    private JPanel createTacticsPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(10, 10));
        wrapper.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        JLabel heading = new JLabel("Parâmetros do PES · tática selecionada no topo");
        heading.setFont(new Font("SansSerif", Font.BOLD, 17));
        wrapper.add(heading, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 8, 5, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        String group = "";
        for (TacticsFields.Field field : TacticsFields.FIELDS) {
            if (!field.group().equals(group)) {
                group = field.group();
                c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
                JLabel title = new JLabel(group.toUpperCase());
                title.setForeground(new Color(12, 104, 77));
                title.setFont(new Font("SansSerif", Font.BOLD, 14));
                grid.add(title, c);
                c.gridwidth = 1;
            }
            c.gridx = 0; c.gridy = row;
            grid.add(new JLabel(field.label() + " (" + field.key() + "):"), c);
            c.gridx = 1;
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(field.min(), field.min(), field.max(), 1));
            spinner.setPreferredSize(new Dimension(82, 27));
            spinner.setToolTipText("Código original do PES: " + field.min() + " a " + field.max());
            tacticSpinners.put(field.key(), spinner);
            grid.add(spinner, c);
            row++;
        }
        JScrollPane scroller = new JScrollPane(grid);
        scroller.getVerticalScrollBar().setUnitIncrement(18);
        wrapper.add(scroller, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(10, 6));
        JButton apply = new JButton("Aplicar parâmetros à tática selecionada");
        apply.addActionListener(e -> applyTactics());
        bottom.add(apply, BorderLayout.NORTH);
        JLabel note = new JLabel("Códigos 0/1/2 originais, sem traduzir opções não verificadas; IDs protegidos.");
        note.setForeground(new Color(90, 94, 102));
        bottom.add(note, BorderLayout.SOUTH);
        wrapper.add(bottom, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel createRosterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        String[] headers = {"Camisa", "Jogador", "ID", "Posição (código)", "Overall"};
        Object[][] rows = new Object[roster.size()][5];
        for (int i = 0; i < roster.size(); i++) {
            PesDatabase.RosterEntry e = roster.get(i);
            rows[i] = new Object[] {e.shirtNumber(), e.player().name(), e.player().id(),
                    e.player().position(), e.player().overall()};
        }
        javax.swing.table.DefaultTableModel data = new javax.swing.table.DefaultTableModel(rows, headers) {
            @Override public boolean isCellEditable(int r, int col) { return false; }
        };
        JTable list = new JTable(data);
        list.setRowHeight(25);
        list.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        JTextArea note = new JTextArea("CONSULTA DO ELENCO: as linhas TacticsFormation usam NumPlayer "
                + "(ordem da posição), não ID de jogador. Portanto, selecionar um jogador aqui não modifica "
                + "automaticamente os titulares do PES. A escalação real exige mapear os dados do EDIT/save.");
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setEditable(false);
        note.setFocusable(false);
        note.setBackground(panel.getBackground());
        note.setRows(3);
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private void loadTacticControls() {
        TacticOption selected = (TacticOption) tacticSelector.getSelectedItem();
        if (selected == null) return;
        for (List<String> row : teamTactics) {
            if (!selected.id().equals(row.get(tacticColumns.get("Id")))) continue;
            for (TacticsFields.Field field : TacticsFields.FIELDS) {
                JSpinner spinner = tacticSpinners.get(field.key());
                if (spinner != null && tacticColumns.containsKey(field.key())) {
                    int value = Integer.parseInt(row.get(tacticColumns.get(field.key())));
                    // Vale para o domínio encontrado nos arquivos fornecidos pelo usuário.
                    spinner.setValue(Math.max(field.min(), Math.min(field.max(), value)));
                }
            }
            break;
        }
    }

    private void applyTactics() {
        TacticOption selected = (TacticOption) tacticSelector.getSelectedItem();
        if (selected == null) return;
        for (List<String> row : teamTactics) {
            if (!selected.id().equals(row.get(tacticColumns.get("Id")))) continue;
            Map<String, Integer> updates = new LinkedHashMap<>();
            for (TacticsFields.Field field : TacticsFields.FIELDS) {
                updates.put(field.key(), ((Number) tacticSpinners.get(field.key()).getValue()).intValue());
            }
            try {
                if (TacticsFields.apply(tacticColumns, row, updates)) {
                    tacticChanges++;
                    details.setText("Tática alterada em memória • " + tacticChanges
                            + " aplicações em táticas / " + changes + " em formações");
                } else details.setText("Tática sem diferenças. Nada foi alterado.");
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Valor inválido", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
    }

    private void exportChanges() {
        if (changes == 0 && tacticChanges == 0) {
            JOptionPane.showMessageDialog(this, "Nenhuma alteração para exportar."); return;
        }
        JFileChooser chooser = new JFileChooser(source.getParent().toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Escolha a pasta para os dois CSVs (originais preservados)");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path dir = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        String label = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path tacticOut = dir.resolve("Taticas_Norteverso_" + label + ".csv");
        Path formationOut = dir.resolve("TacticsFormation_Norteverso_" + label + ".csv");
        if (tacticOut.equals(tacticsSource) || formationOut.equals(source)
                || Files.exists(tacticOut) || Files.exists(formationOut)) {
            JOptionPane.showMessageDialog(this, "Destino existente ou igual à origem.",
                    "Destino inválido", JOptionPane.WARNING_MESSAGE); return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Exportar os DOIS arquivos completos?\n" + tacticOut.getFileName() + "\n"
                        + formationOut.getFileName() + "\n\nNão modifica os CSVs originais.",
                "Confirmar exportação", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            exportPair(tactics, formations, tacticOut, formationOut);
            changes = 0;
            tacticChanges = 0;
            details.setText("Exportadas cópias de táticas e formações para " + dir);
            JOptionPane.showMessageDialog(this, "Arquivos exportados. Teste a importação no Ejogc327 "
                    + "antes de aplicar ao patch.\n" + tacticOut + "\n" + formationOut);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro de gravação", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void exportPair(List<List<String>> tactics, List<List<String>> formations,
                                   Path tacticOut, Path formationOut) throws IOException {
        if (tacticOut.equals(formationOut) || Files.exists(tacticOut) || Files.exists(formationOut)) {
            throw new IOException("Os arquivos de destino precisam ser novos e diferentes.");
        }
        List<Path> saved = new ArrayList<>();
        try {
            Csv.gravar(tacticOut, tactics); saved.add(tacticOut);
            Csv.gravar(formationOut, formations); saved.add(formationOut);
        } catch (IOException ex) {
            for (Path output : saved) try { Files.deleteIfExists(output); } catch (IOException ignored) { }
            throw ex;
        }
    }

    private void close() {
        if ((changes > 0 || tacticChanges > 0) && JOptionPane.showConfirmDialog(this,
                "Há alterações táticas ou formações ainda não exportadas. Descartar?", "Descartar",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        dispose();
    }
    private final class FormationTableModel extends AbstractTableModel {
        private final String[] labels={"Ordem", "Posição", "X", "Y", "Linha ID"};
        @Override public int getRowCount(){return current.size();}
        @Override public int getColumnCount(){return labels.length;}
        @Override public String getColumnName(int c){return labels[c];}
        @Override public Object getValueAt(int row,int col) {
            var r=current.get(row);
            return switch(col) {
                case 0 -> r.get(formationColumns.get("NumPlayer"));
                case 1 -> { int p=Integer.parseInt(r.get(formationColumns.get("Position"))); yield p>=0&&p<POSITIONS.length?POSITIONS[p]:"#"+p; }
                case 2 -> r.get(formationColumns.get("LocationX"));
                case 3 -> r.get(formationColumns.get("LocationY"));
                default -> r.get(formationColumns.get("Id"));
            };
        }
    }
    private final class Pitch extends JPanel {
        Pitch(){ setPreferredSize(new Dimension(340,500)); setBackground(new Color(19,100,56)); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int pad=25, w=getWidth()-2*pad,h=getHeight()-2*pad;
            g2.setColor(new Color(36,126,73));g2.fillRect(pad,pad,w,h);
            g2.setColor(new Color(228,246,229));
            g2.drawRect(pad,pad,w,h);g2.drawLine(pad,pad+h/2,pad+w,pad+h/2);
            g2.drawOval(pad+w/2-33,pad+h/2-33,66,66);
            g2.drawRect(pad+w/4,pad,(int)(w*.5),(int)(h*.16));
            g2.drawRect(pad+w/4,pad+(int)(h*.84),(int)(w*.5),(int)(h*.16));
            for(int i=0;i<current.size();i++){
                var r=current.get(i);
                int depth=Integer.parseInt(r.get(formationColumns.get("LocationX")));
                int across=Integer.parseInt(r.get(formationColumns.get("LocationY")));
                int px=pad+(int)(w*across/100.0);
                int py=pad+(int)(h*(1.0-depth/50.0));
                g2.setColor(i==table.getSelectedRow()?new Color(247,202,69):Color.WHITE);
                g2.fillOval(px-16,py-16,32,32);
                g2.setColor(new Color(13,55,50));g2.drawOval(px-16,py-16,32,32);
                int code=Integer.parseInt(r.get(formationColumns.get("Position")));
                String label=code>=0&&code<POSITIONS.length?POSITIONS[code]:"#"+code;
                g2.setFont(new Font("SansSerif",Font.BOLD,10));
                int textWidth=g2.getFontMetrics().stringWidth(label);
                g2.drawString(label,px-textWidth/2,py+4);
            }
            g2.dispose();
        }
    }
}
