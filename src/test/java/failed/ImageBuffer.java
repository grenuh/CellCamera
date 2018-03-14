package failed;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class ImageBuffer extends JPanel implements MouseListener, MouseMotionListener {
    int mouse_x, mouse_y, x, y;
    String modifierKeys = "";
    BufferedImage image;
    Dimension size = new Dimension();  //  @jve:decl-index=0:
    String imgFile;
    int amt_input = 0;
    int radius = 20;
    private String dir = null;

    public ImageBuffer(BufferedImage image) {
        this.image = image;
        size.setSize(image.getWidth(), image.getHeight());
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
    }

    public ImageBuffer() {
        try {
            Init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new ImageBuffer();
            }
        });
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int x = (getWidth() - size.width) / 2;
        int y = (getHeight() - size.height) / 2;
        g.drawImage(image, x, y, this);
        g.setColor(Color.black);
        g.drawString("ID : 00:24:c3:31:2b:e0", 730, 175);
        g.setColor(Color.red);
        g.drawString("User at coordinate : (" + mouse_x + "," + mouse_y + ")",
                mouse_x, mouse_y);
        g.setColor(Color.blue);
        g.fillOval(mouse_x, mouse_y, 10, 10);
        g.drawOval(mouse_x, mouse_y, 10, 10);
    }

    private void Init() throws Exception {
        JFrame frame = new JFrame();
        frame.setTitle("Picture");
        frame.setSize(200, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        MenuBar menuBar = new MenuBar();
        Menu menu = new Menu("File");
        menu.add("Image");
        menu.add("Quit");
        menuBar.add(menu);
        menu.addActionListener(new axnListener());
        frame.setMenuBar(menuBar);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        frame.setVisible(true);
    }//end init

    public void loadImage() {
        JFrame choose = new JFrame();
        FileDialog dlg = new FileDialog(choose, "Choose Image", FileDialog.LOAD);
        //set current directory
        if (dir != null) {
            dlg.setDirectory(dir);
        }
        dlg.setVisible(true);
        //get image name and path
        imgFile = dlg.getDirectory() + dlg.getFile();
        dir = dlg.getDirectory();
        //create image using filename
        try {
            image = ImageIO.read(new File(imgFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        ImageBuffer test = new ImageBuffer(image);
        JFrame f = new JFrame();
        f.setTitle("Viewer");
        f.add(new JScrollPane(test));
        f.setVisible(true);
        Insets insets = f.getInsets();
        f.setSize(image.getWidth() + insets.left + insets.right + 1, image.getHeight() + insets.top + insets.bottom + 1);
        f.setResizable(false);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }//end load image

    void setInfo(MouseEvent e) {
        // set up the information about event for display
        mouse_x = e.getX();
        mouse_y = e.getY();
        modifierKeys = "";
        if (e.isShiftDown())
            modifierKeys += "Shift  ";
        if (e.isControlDown())
            modifierKeys += "Control  ";
        if (e.isMetaDown())
            modifierKeys += "Meta  ";
        if (e.isAltDown())
            modifierKeys += "Alt";
        this.repaint();
    }

    private void printtextfile() {
        PrintWriter pw = null;
        try {
            // created as a separate variable to emphasize that I'm appending to this file
            boolean append = true;
            pw = new PrintWriter(new FileWriter(new File("C:\\Documents and Settings\\Administrator\\My Documents\\lll.txt"), append));
            // a print writer gives you many more methods to write with
            pw.println(mouse_x);
            pw.println(mouse_y);
        } catch (IOException e) {
            e.printStackTrace();
            // deal with the exception
        } finally {
            pw.close();
        }
    }

    public void drawCircle(int x, int y) {
        Graphics g = getGraphics();
        g.drawOval(x - radius, y - radius, 2 * radius, 2 * radius);
        g.setColor(Color.BLACK);
        g.fillOval(x - radius, y - radius, 2 * radius, 2 * radius);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int radius = 20;
        amt_input++;
        mouse_x = e.getX();
        mouse_y = e.getY();
        System.out.println("mouse clicked " + amt_input);
        System.out.println(mouse_x + "," + mouse_y);
        printtextfile();
        //FileChooserDemo.createAndShowGUI();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // TODO Auto-generated method stub
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // TODO Auto-generated method stub
    }

    @Override
    public void mousePressed(MouseEvent e) {
        x = e.getX();
        y = e.getY();
        drawCircle(e.getX() - (radius / 2), e.getY() - (radius / 2));
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // TODO Auto-generated method stub
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // TODO Auto-generated method stub
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        setInfo(e);
    }

    class axnListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (e.getActionCommand().equalsIgnoreCase("Image")) {
                loadImage();
            } else System.exit(0);
        }
    }//end of inner class axnListener
}