package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import com.frankstiwrx.norteversostudio.ui.SquadAdjustDialog;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Renderizacao de conferencia opcional em uma tela virtual. */
public final class Stage10UiSmokeTest {
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) { System.out.println("UI ignorada no ambiente sem tela"); return; }
        var db = PesDatabase.load(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]), msg->{});
        var team = db.teams().stream().filter(t -> !t.national() && db.roster(t.id()).size()>=25).findFirst().orElseThrow();
        var protection = ProtectionRegistry.load(Path.of("/tmp/test_ui_protection_stage10.csv"));
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            frame.setSize(1150,720);
            frame.setLocationRelativeTo(null);
            var dialog = new SquadAdjustDialog(frame,db,team.id(),protection,()->{});
            Timer timer = new Timer(900,e->{
                try { ImageIO.write(new Robot().createScreenCapture(new Rectangle(1400,850)),"png",
                        Path.of("/mnt/data/work_stage10/preview_stage10.png").toFile()); }
                catch(Exception ex) { ex.printStackTrace(); }
                dialog.dispose();
            });
            timer.setRepeats(false);
            timer.start();
            dialog.setVisible(true);
            frame.dispose();
        });
        System.out.println("UI Etapa10 OK");
    }
}
