package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.ui.MainFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Ponto de entrada da interface do Norteverso Studio. */
public class NorteversoStudio {
    public static void main(String[] args) {
        if (args.length > 0 && "--teste".equals(args[0])) {
            DevSmokeTest.main(new String[0]);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // O Look & Feel padrão do Swing também funciona.
            }
            new MainFrame().setVisible(true);
        });
    }
}
