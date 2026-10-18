package com.frankstiwrx.norteversostudio;

import com.frankstiwrx.norteversostudio.data.PesDatabase;
import com.frankstiwrx.norteversostudio.generator.NameCatalog;
import com.frankstiwrx.norteversostudio.generator.ProtectionRegistry;
import com.frankstiwrx.norteversostudio.ui.SquadGeneratorDialog;
import java.awt.Robot;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Checagem visual opcional, sem aplicar propostas. */
public final class OverallTargetUiSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Informe Jogadores Times Atribuições");
        var db = PesDatabase.load(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]),s->{});
        var names = NameCatalog.load(Path.of("dados/nomes.csv"),Path.of("dados/sobrenomes.csv"));
        var reg = ProtectionRegistry.load(Files.createTempDirectory("norte_gui_").resolve("protected.csv"));
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            f.setSize(600,400);
            var dlg = new SquadGeneratorDialog(f,db,"1219",names,reg,()->{});
            Timer t = new Timer(1000,e -> {
                try { ImageIO.write(new Robot().createScreenCapture(new Rectangle(1450,950)),"png",
                        Path.of("/mnt/data/work_stage12/screenshot_etapa12.png").toFile()); }
                catch(Exception ex) {ex.printStackTrace();}
                dlg.dispose();
            });
            t.setRepeats(false);
            t.start();
            dlg.setVisible(true);
            f.dispose();
        });
        System.out.println("OK UI etapa12");
    }
}
