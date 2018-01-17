import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

public class Simple_median extends JPanel {
    private JLabel images = new JLabel();

    public Simple_median() throws IOException {
        JFrame frame = new JFrame("SLIDE SHOW");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.getContentPane().add(this);
        add(images);
        frame.setSize(300, 300);
        String[] files = new String[]{"G1.JPG", "G1.JPG"};
        ArrayList<BufferedImage> lF = new ArrayList<>();
       /* for (String file : files) {
            lF.add(Main.filepathToImage(file));
        }
        images.setIcon(new ImageIcon(Main.getOneImageFromList(lF)));*/
        frame.setVisible(true);
    }

    public static void main(String[] args) throws IOException {
        SwingUtilities.invokeLater(() -> {
            try {
                new Simple_median();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}

