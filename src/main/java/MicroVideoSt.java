import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class MicroVideoSt extends JPanel implements KeyListener {
    private static int cropped;
    private static Webcam webcam;
    private int[] maskDim = new int[2];
    private int[] cropPoints;
    private Timer timerMask;
    private Timer timer;
    private BufferedImage lastImage;//variable for saving last image
    private BufferedImage midImage;
    @SuppressWarnings("unused")
    private BufferedImage bigImage;
    private boolean masked = false; //if mask are applied
    private JLabel bIjl = new JLabel();//big image label
    private JLabel rIjl = new JLabel(); //real camera image
    private JLabel grandMap = new JLabel(); //grand image
    private int maskCoefficient = 1;
    private int[][] rawMaskData = new int[3][];//3 colors
    private JLabel pointImage0 = new JLabel(); //grand image
    private JLabel pointImage1 = new JLabel(); //grand image
    private int u = 0;
    private int shift = 50;

    /**
     * Constructor with GUI
     */
    private MicroVideoSt() {
        JFrame frame = new JFrame("SLIDE SHOW - not mask!");
        setLayout(new GridBagLayout());
        addKeyListener(this);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
                 /*Menu*/
        JMenuBar menuBar = new JMenuBar();
        JMenu imgMenu = new JMenu("Image");
        JMenuItem save = new JMenuItem("Save map image");
        save.addActionListener(e -> {
            JFrame parentFrame = new JFrame();
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Specify a file to save");
            int userSelection = fileChooser.showSaveDialog(parentFrame);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File f1 = fileChooser.getSelectedFile();
                if (f1 != null) {
                    String name = f1.getName();
                    String extension = name.substring(1 + name.lastIndexOf(".")).toLowerCase();
                    try {
                        //TODO  - export to file
                        ImageIO.write(midImage, extension, f1);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        imgMenu.add(save);
        menuBar.add(imgMenu);
        JMenu maskMenu = new JMenu("Mask");
        JMenuItem setMask = new JMenuItem("Set mask");
        setMask.addActionListener(e -> {
            frame.setTitle("SLIDE SHOW - proceeed");
            timerMask.start();
        });
        JMenuItem saveMask = new JMenuItem("Save mask image");
        saveMask.addActionListener(e -> {
            JFrame parentFrame = new JFrame();
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Specify a file to save");
            int userSelection = fileChooser.showSaveDialog(parentFrame);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File f1 = fileChooser.getSelectedFile();
                if (f1 != null) {
                    String name = f1.getName();
                    String extension = name.substring(1 + name.lastIndexOf(".")).toLowerCase();
                    try {
                        BufferedImage maskColor = new BufferedImage(maskDim[0], maskDim[1], BufferedImage.TYPE_4BYTE_ABGR);
                        int width = maskColor.getWidth(), height = maskColor.getHeight();
                        int[] rgbColor = maskColor.getRGB(0, 0, width, height, new int[width * height], 0, width);
                        for (int rgbIndex = 0; rgbIndex < rgbColor.length; rgbIndex++) {
                            rgbColor[rgbIndex] = 255 << 24 | (((255 - rawMaskData[0][rgbIndex]) * 2) / maskCoefficient) >> 1 << 16 | (((255 - rawMaskData[1][rgbIndex]) / maskCoefficient) << 8) | ((255 - rawMaskData[2][rgbIndex]) / maskCoefficient);
                        }
                        maskColor.setRGB(0, 0, width, height, rgbColor, 0, width);
                        ImageIO.write(maskColor, extension, f1);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        JMenuItem cropMask = new JMenuItem("Crop image");
        cropMask.addActionListener(e -> {
            BufferedImage curr = lastImage;
            JFrame frameCrop = new JFrame();
            frameCrop.setTitle("Picture");
            JLabel img = new JLabel(new ImageIcon(curr));
            JPanel imgP = new JPanel();
            imgP.add(img);
            cropPoints = new int[4];
            frameCrop.setSize(curr.getWidth() + 20, curr.getHeight() + 20);
            frameCrop.setResizable(false);
            frameCrop.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frameCrop.setResizable(false);
            MouseListener ml = new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    switch (cropped) {
                        case 0:
                            cropPoints[0] = e.getX();
                            cropPoints[1] = e.getY();
                            System.out.println(cropPoints[0] + " " + cropPoints[1]);
                            cropped++;
                            break;
                        case 1:
                            cropPoints[2] = e.getX();
                            cropPoints[3] = e.getY();
                            System.out.println(cropPoints[2] + " " + cropPoints[3]);
                            cropped++;
                            int x;
                            int y;
                            int w;
                            int h;
                            if (cropPoints[0] > cropPoints[2]) {
                                x = cropPoints[2];
                                w = cropPoints[0] - cropPoints[2];
                            } else {
                                x = cropPoints[0];
                                w = cropPoints[2] - cropPoints[0];
                            }
                            if (cropPoints[1] > cropPoints[3]) {
                                y = cropPoints[3];
                                h = cropPoints[1] - cropPoints[3];
                            } else {
                                y = cropPoints[1];
                                h = cropPoints[3] - cropPoints[1];
                            }
                            cropPoints[0] = x;
                            cropPoints[1] = y;
                            cropPoints[2] = w;
                            cropPoints[3] = h;
                            Graphics g = curr.getGraphics();
                            g.setColor(Color.red);
                            g.drawRect(x, y, w, h);
                            g.dispose();
                            img.setIcon(new ImageIcon(curr));
                            repaint();
                            break;
                        case 2:
                            frameCrop.dispose();
                            try {
                                String data = cropPoints[0] + " " + cropPoints[1] + "\n" + cropPoints[2] + " " + cropPoints[3];
                                File f = new File("data.txt");
                                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                                bw.write(data);
                                bw.close();
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }
                            break;
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                }

                @Override
                public void mouseExited(MouseEvent e) {
                }
            };
            imgP.addMouseListener(ml);
            frameCrop.add(imgP);
            frameCrop.setVisible(true);
        });
        JMenuItem intensityMask = new JMenuItem("Set intensity of mask");
        intensityMask.addActionListener(e -> {
        });
        maskMenu.add(setMask);
        maskMenu.add(saveMask);
        maskMenu.add(cropMask);
        maskMenu.add(intensityMask);
        menuBar.add(maskMenu);
                 /*Images*/
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.gridx = 0;
        c.gridy = 0;
        add(new JLabel("Main image"), c);
        c.gridx = 1;
        add(new JLabel("Raw image"), c);
        c.weighty = 1;
        c.gridx = 0;
        c.gridy = 1;
        add(bIjl, c);
        c.gridx = 1;
        add(rIjl, c);
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 4;
        add(new JLabel("Press SPACE to add image"), c);
        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 1;
        add(grandMap, c);
        c.gridx = 1;
        add(pointImage0, c);
        c.gridx = 2;
        add(pointImage1, c);
        c.gridx = 3;
        c.gridy = 3;
        JLabel bufIm = new JLabel();
        add(bufIm, c);
        final BufferedImage[] maskColor = new BufferedImage[1];
        ActionListener actionMask = ae -> {
            if (u == 0) {
                maskColor[0] = webcam.getImage();
            } else {
                maskColor[0] = getMedianImage(maskColor[0], webcam.getImage());
            }
            u++;
            if (u > 10) {
                timerMask.stop();
                createMaskImages(maskColor[0]);
                masked = true;
                frame.setTitle("SLIDE SHOW");
                Path path = Paths.get("mask.png");
                if (Files.notExists(path)) {
                    try {
                        ImageIO.write(maskColor[0], "png", new File("mask.png"));
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        };
        timerMask = new Timer(5, actionMask);
        c.gridy = 2;
        c.gridx = 3;
    /*Mask*/
        Path path = Paths.get("mask.png");
        if (Files.exists(path)) {
            File file = new File("mask.png");
            try {
                BufferedImage maskColor0 = ImageIO.read(file);
                int width = maskColor0.getWidth(), height = maskColor0.getHeight();
                maskDim[0] = width;
                maskDim[1] = height;
                int[] rgb = maskColor0.getRGB(0, 0, width, height, new int[width * height], 0, width);
                rawMaskData[1] = new int[rgb.length];
                rawMaskData[2] = new int[rgb.length];
                rawMaskData[0] = new int[rgb.length];
                for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
                    rawMaskData[0][rgbIndex] = ((rgb[rgbIndex] >> 16) & 0xff) - shift;//red
                    rawMaskData[1][rgbIndex] = ((rgb[rgbIndex] >> 8) & 0xff) - shift;//green
                    rawMaskData[2][rgbIndex] = ((rgb[rgbIndex]) & 0xff) - shift;//blue
                }
                masked = true;
                frame.setTitle("SLIDE SHOW");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        path = Paths.get("data.txt");
        if (Files.exists(path)) {
            File file = new File("data.txt");
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String[] a1 = br.readLine().split(" ");
                String[] a2 = br.readLine().split(" ");
                cropPoints = new int[4];
                cropPoints[0] = Integer.parseInt(a1[0]);
                cropPoints[1] = Integer.parseInt(a1[1]);
                cropPoints[2] = Integer.parseInt(a2[0]);
                cropPoints[3] = Integer.parseInt(a2[1]);
                cropped = 2;
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        ActionListener action = ae -> {
            BufferedImage bufImg = webcam.getImage();
            lastImage = new BufferedImage(bufImg.getWidth(), bufImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
            lastImage.getGraphics().drawImage(bufImg, 0, 0, null);
            rIjl.setIcon(new ImageIcon(scaleImageHoriz(lastImage)));
            if (masked) {
                getImageMasked();
            }
            bIjl.setIcon(new ImageIcon(scaleImageHoriz(lastImage)));
            revalidate();
            repaint();
        };
        timer = new Timer(80, action);
        timer.start();
        frame.setJMenuBar(menuBar);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().add(this);
        frame.setLocationByPlatform(true);
        frame.setSize(1240, 600);
        frame.setVisible(true);
    }

    /**
     * Main entry point
     *
     * @param args none
     * @throws IOException when problem are gone
     */
    public static void main(String[] args) throws IOException {
        // get default webcam and open it
        ArrayList<Webcam> cameras = new ArrayList<>(Webcam.getWebcams());
        if (cameras.size() > 1) {
            webcam = cameras.get(1);
        } else {
            webcam = Webcam.getWebcams().get(0);
        }
        Dimension[] sizes = webcam.getViewSizes();
        Dimension cameraDimension = sizes[0];
        for (int i = 1; i < sizes.length; i++) {
            if (cameraDimension.height < sizes[i].height) {
                cameraDimension = sizes[i];
            }
        }
        webcam.setViewSize(cameraDimension);
        webcam.open();
        SwingUtilities.invokeLater(MicroVideoSt::new);
    }

    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage b = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        Graphics g = b.getGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return b;
    }

    /**
     * Accept color and alpha mask to image
     */
    private void getImageMasked() {
        int width = lastImage.getWidth(), height = lastImage.getHeight();
        int[] rgb = lastImage.getRGB(0, 0, width, height, new int[width * height], 0, width);
        // int[] rgbColor = maskColor.getRGB(0, 0, width, height, new int[width * height], 0, width);
        lastImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
            int red = ((rgb[rgbIndex] >> 16) & 0xff) - rawMaskData[0][rgbIndex];
            int green = ((rgb[rgbIndex] >> 8) & 0xff) - rawMaskData[1][rgbIndex];
            int blue = ((rgb[rgbIndex]) & 0xff) - rawMaskData[2][rgbIndex];
            if (red > 255) {
                red = 255;
            }
            if (green > 255) {
                green = 255;
            }
            if (blue > 255) {
                blue = 255;
            }
            rgb[rgbIndex] = (255 << 24) | (((red)) << 16)
                    | (((green)) << 8) | (blue);
        }
        lastImage.setRGB(0, 0, width, height, rgb, 0, width);
        if (cropped == 2) {
            lastImage = lastImage.getSubimage(cropPoints[0], cropPoints[1], cropPoints[2], cropPoints[3]);
        }
    }

    /**
     * Create alpha and color masks
     */
    private void createMaskImages(BufferedImage maskColor) {
        int width = maskColor.getWidth(), height = maskColor.getHeight();
        int[] rgb = maskColor.getRGB(0, 0, width, height, new int[width * height], 0, width);
        int[] rgbColor = maskColor.getRGB(0, 0, width, height, new int[width * height], 0, width);
        rawMaskData[0] = new int[rgbColor.length];
        rawMaskData[1] = new int[rgbColor.length];
        rawMaskData[2] = new int[rgbColor.length];
        int sum[] = new int[3];
        for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
            rawMaskData[0][rgbIndex] = (rgb[rgbIndex] >> 16) & 0xff;//red
            rawMaskData[1][rgbIndex] = (rgb[rgbIndex] >> 8) & 0xff;//green
            rawMaskData[2][rgbIndex] = (rgb[rgbIndex]) & 0xff;//blue
            sum[0] = sum[0] + rawMaskData[0][rgbIndex];
            sum[1] = sum[1] + rawMaskData[1][rgbIndex];
            sum[2] = sum[2] + rawMaskData[2][rgbIndex];
        }
        sum[0] = sum[0] / rgb.length;
        sum[1] = sum[1] / rgb.length;
        sum[2] = sum[2] / rgb.length;
        for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
            rawMaskData[0][rgbIndex] = ((rawMaskData[0][rgbIndex] - sum[0]) / maskCoefficient) + shift;
            rawMaskData[1][rgbIndex] = ((rawMaskData[1][rgbIndex] - sum[1]) / maskCoefficient) + shift;
            rawMaskData[2][rgbIndex] = ((rawMaskData[2][rgbIndex] - sum[2]) / maskCoefficient) + shift;
            rgbColor[rgbIndex] = (255 << 24) | ((rawMaskData[0][rgbIndex] >> 1) << 16)
                    | (rawMaskData[1][rgbIndex] << 8) | (rawMaskData[2][rgbIndex]);
        }
        /*
        *  rgbColor[rgbIndex] = (255 << 24) | (((((255 - rawMaskData[0][rgbIndex]) * 2) / maskCoefficient) >> 1) << 16)
                    | (((255 - rawMaskData[1][rgbIndex]) / maskCoefficient) << 8) | ((255 - rawMaskData[2][rgbIndex]) / maskCoefficient);
        * */
        System.out.println();
        maskColor.setRGB(0, 0, width, height, rgbColor, 0, width);
        System.out.println();
    }

    /**
     * Get summary image
     * 5
     *
     * @param input1 first photo
     * @param input2 second photo
     * @return summed image
     */
    private BufferedImage getMedianImage(BufferedImage input1, BufferedImage input2) {
        int width = input1.getWidth(), height = input1.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] rgb1 = input1.getRGB(0, 0, width, height, new int[width * height], 0, width);
        int[] rgb2 = input2.getRGB(0, 0, width, height, new int[width * height], 0, width);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int rgbIndex = i * height + j;
                rgb1[rgbIndex] = ((rgb1[rgbIndex] >> 1) & 0x7f7f7f7f) + ((rgb2[rgbIndex] >> 1) & 0x7f7f7f7f) + (rgb1[rgbIndex] & rgb2[rgbIndex] & 0x01010101);
            }
        }
        output.setRGB(0, 0, width, height, rgb1, 0, width);
        return output;
    }

    /**
     * Resize big image
     * to 320*240 or smaller
     *
     * @param source bigger image
     * @return smaller image
     */
    private BufferedImage scaleImageHoriz(BufferedImage source) {
        int newWidght;
        int newHeight;
        double a1 = source.getWidth();
        double a2 = source.getHeight();
        double u = ((a1 * 3.0) / a2);
        if (u > 4.0) {
            newWidght = 320;
            newHeight = new Double(a2 / (a1 / 320.0)).intValue();
        } else {
            newHeight = 240;
            newWidght = new Double(a1 / (a2 / 240.0)).intValue();
        }
        BufferedImage resized = null;
        try {
            resized = new BufferedImage(newWidght, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, newWidght, newHeight, 0, 0, source.getWidth(),
                    source.getHeight(), null);
            g.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resized;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_BACK_SPACE) {
            if (masked & cropped == 2) {
                startVideo();
            } else {
                JOptionPane.showMessageDialog(null, "Perform calibration", "Warning",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    private void startVideo() {
        BufferedImage bufImg = webcam.getImage();
        lastImage = new BufferedImage(bufImg.getWidth(), bufImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
        lastImage.getGraphics().drawImage(bufImg, 0, 0, null);
        rIjl.setIcon(new ImageIcon(scaleImageHoriz(lastImage)));
        if (masked) {
            getImageMasked();
        }
        bIjl.setIcon(new ImageIcon(scaleImageHoriz(lastImage)));
        midImage = copyImage(lastImage);
        ActionListener actionN = ae -> {
        };
        timer.stop();
        Timer timingNew = new Timer(0, actionN);
        timingNew.start();
    }
}
