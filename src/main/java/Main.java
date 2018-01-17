import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.gui.feature.FancyInterestPointRender;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import com.github.sarxos.webcam.Webcam;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.FastQueue;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Main method
 */
public class Main extends JPanel {
    private static Webcam webcam;
    private Timer timerMask;
    private BufferedImage bigImage;
    private BufferedImage lastImage;//variable for saving last image
    private int[] maskAlpha;
    private BufferedImage maskColor;
    private boolean masked = false; //if mask are applied
    private boolean process = false; //if program create big image
    private JLabel bIjl = new JLabel();//big image label
    private JLabel pIjl = new JLabel();//image with points
    private JLabel mIjl = new JLabel();// mask image
    private JLabel rIjl = new JLabel(); //real camera image
    private JLabel grandMap = new JLabel(); //grand image

    /**
     * Constructor with GUI
     */
    private Main() {
        JFrame frame = new JFrame("SLIDE SHOW - not mask!");
        setLayout(new GridBagLayout());
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
                        ImageIO.write(bigImage, extension, f1);
                    } catch (IOException e1) {
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
                        ImageIO.write(maskColor, extension, f1);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        JMenuItem loadMask = new JMenuItem("Load alpha mask image");
        loadMask.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            int result = fc.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                try {
                    BufferedImage bi = ImageIO.read(file);
                    int width = bi.getWidth(), height = bi.getHeight();
                    int[] rgb = bi.getRGB(0, 0, width, height, new int[width * height], 0, width);
                    maskAlpha = new int[rgb.length];
                    for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
                        maskAlpha[rgbIndex] = (rgb[rgbIndex] >> 24) & 0xff0;
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        maskMenu.add(setMask);
        maskMenu.add(saveMask);
        maskMenu.add(loadMask);
        menuBar.add(maskMenu);
        JMenu processMenu = new JMenu("Process");
        JMenuItem bigMapItem = new JMenuItem("Start process");
        bigMapItem.addActionListener(e -> {
            if (process) {
                process = false;
                bigMapItem.setText("Start process");
            } else {
                if (masked) {
                    bigMapItem.setText("Stop process");
                    process = true;
                } else {
                    JOptionPane.showMessageDialog(frame, "Please, add mask first!", "Warning",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        processMenu.add(bigMapItem);
        menuBar.add(processMenu);
        /*Images*/
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 0.1;
        add(new JLabel("Main image"), c);
        c.gridx = 1;
        add(new JLabel("Point image"), c);
        c.gridx = 2;
        add(new JLabel("Mask image"), c);
        c.gridx = 3;
        add(new JLabel("Raw image"), c);
        c.weighty = 0.4;
        c.gridx = 0;
        c.gridy = 1;
        add(bIjl, c);
        c.gridx = 1;
        add(pIjl, c);
        c.gridx = 2;
        add(mIjl, c);
        c.gridx = 3;
        add(rIjl, c);
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 4;
        add(grandMap, c);
        final int[] i = {0};
        ActionListener actionMask = ae -> {
            if (i[0] == 0) {
                maskColor = webcam.getImage();
            } else {
                maskColor = getMedianImage(maskColor, webcam.getImage());
            }
            i[0]++;
            System.out.println(i[0]);
            if (i[0] > 10) {
                timerMask.stop();
                createMaskImages();
                mIjl.setIcon(new ImageIcon(scaleImageHoriz(maskColor, 320)));
                masked = true;
                frame.setTitle("SLIDE SHOW");
            }
        };
        timerMask = new Timer(5, actionMask);
        c.gridy = 2;
        c.weighty = 0.1;
        c.gridx = 3;
        frame.getContentPane().add(this);
        /*Only white */
        {
            maskColor = new BufferedImage(webcam.getViewSize().width / 2, webcam.getViewSize().height / 2,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = maskColor.createGraphics();
            graphics.setPaint(Color.white);
            graphics.fillRect(0, 0, maskColor.getWidth(), maskColor.getHeight());
            mIjl.setIcon(new ImageIcon(maskColor));
        }
        frame.setJMenuBar(menuBar);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.setSize(1240, 500);
        frame.setVisible(true);
        ActionListener action = ae -> {
            BufferedImage bufImg = webcam.getImage();
            lastImage = new BufferedImage(bufImg.getWidth(), bufImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
            lastImage.getGraphics().drawImage(bufImg, 0, 0, null);
            rIjl.setIcon(new ImageIcon(scaleImageHoriz(lastImage, 320)));
            if (masked) {
                getImageMasked();
                if (process) {
                    if (bigImage == null) {
                        bigImage = lastImage;
                    }
                    bigImage = stitch(bigImage, lastImage, GrayF32.class);
                    grandMap.setIcon(new ImageIcon(scaleImageHoriz(bigImage, 320)));
                }
            }
            pIjl.setIcon(new ImageIcon(scaleImageHoriz(addPoints(lastImage, GrayF32.class), 320)));
            bIjl.setIcon(new ImageIcon(scaleImageHoriz(lastImage, 320)));
            revalidate();
            repaint();
        };
        frame.getContentPane().add(this);
        Timer timer = new Timer(80, action);
        timer.start();
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
            webcam = cameras.get(0);
        }
        Dimension[] sizes = webcam.getViewSizes();
        Dimension s2 = sizes[0];
        for (int i = 1; i < sizes.length; i++) {
            if (s2.height < sizes[i].height) {
                s2 = sizes[i];
            }
        }
        webcam.setViewSize(s2);
        webcam.open();
        SwingUtilities.invokeLater(Main::new);
    }

    /**
     * Visualizations of core points
     *
     * @param image2    image to detection
     * @param imageType Gray32.class
     * @param <T>       Image
     * @return image with points
     */
    private static <T extends ImageGray<T>> BufferedImage addPoints(BufferedImage image2, Class<T> imageType) {
        T input2 = ConvertBufferedImage.convertFromSingle(image2, null, imageType);
        InterestPointDetector<T> detector2 = FactoryInterestPoint.fastHessian(
                new ConfigFastHessian(10, 2, 100, 2, 9, 3, 4));
        detector2.detect(input2);
        ArrayList<Point2D_F64> img2Points = new ArrayList<>();
        ArrayList<Double> img2R = new ArrayList<>();
        for (int i = 0; i < detector2.getNumberOfFeatures(); i++) {
            img2Points.add(detector2.getLocation(i));
            img2R.add(detector2.getRadius(i));
        }
        BufferedImage copyOfImage =
                new BufferedImage(image2.getWidth(), image2.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics g = copyOfImage.createGraphics();
        g.drawImage(image2, 0, 0, null);
        Graphics2D g2 = copyOfImage.createGraphics();
        FancyInterestPointRender render = new FancyInterestPointRender();
        for (int i1 = 0; i1 < img2Points.size(); i1++) {
            Point2D_F64 pp = img2Points.get(i1);
            int radius = img2R.get(i1).intValue();
            render.addCircle((int) pp.x, (int) pp.y, radius);
        }
        g2.setStroke(new BasicStroke(3));
        render.draw(g2);
        return copyOfImage;
    }

    /**
     * Using abstracted code, find a transform which minimizes the difference between corresponding features
     * in both images.  This code is completely model independent and is the core algorithms.
     */
    private static <T extends ImageGray<T>, FD extends TupleDesc> Homography2D_F64
    computeTransform(T imageA, T imageB,
                     DetectDescribePoint<T, FD> detDesc,
                     AssociateDescription<FD> associate,
                     ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher) {
        // get the length of the description
        java.util.List<Point2D_F64> pointsA = new ArrayList<>();
        FastQueue<FD> descA = UtilFeature.createQueue(detDesc, 100);
        java.util.List<Point2D_F64> pointsB = new ArrayList<>();
        FastQueue<FD> descB = UtilFeature.createQueue(detDesc, 100);
        // extract feature locations and descriptions from each image
        describeImage(imageA, detDesc, pointsA, descA);
        describeImage(imageB, detDesc, pointsB, descB);
        // Associate features between the two images
        associate.setSource(descA);
        associate.setDestination(descB);
        associate.associate();
        // create a list of AssociatedPairs that tell the model matcher how a feature moved
        FastQueue<AssociatedIndex> matches = associate.getMatches();
        java.util.List<AssociatedPair> pairs = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            AssociatedIndex match = matches.get(i);
            Point2D_F64 a = pointsA.get(match.src);
            Point2D_F64 b = pointsB.get(match.dst);
            pairs.add(new AssociatedPair(a, b, false));
        }
        // find the best fit model to describe the change between these images
        if (!modelMatcher.process(pairs))
            throw new RuntimeException("Model Matcher failed!");
        // return the found image transform
        return modelMatcher.getModelParameters().copy();
    }

    /**
     * Detects features inside the two images and computes descriptions at those points.
     */
    private static <T extends ImageGray<T>, FD extends TupleDesc>
    void describeImage(T image,
                       DetectDescribePoint<T, FD> detDesc,
                       java.util.List<Point2D_F64> points,
                       FastQueue<FD> listDescs) {
        detDesc.detect(image);
        listDescs.reset();
        for (int i = 0; i < detDesc.getNumberOfFeatures(); i++) {
            points.add(detDesc.getLocation(i).copy());
            listDescs.grow().setTo(detDesc.getDescription(i));
        }
    }

    private static <T extends ImageGray<T>>
    BufferedImage stitch(BufferedImage imageA, BufferedImage imageB, Class<T> imageType) {
        long time = System.currentTimeMillis();
        T inputA = ConvertBufferedImage.convertFromSingle(imageA, null, imageType);
        T inputB = ConvertBufferedImage.convertFromSingle(imageB, null, imageType);
        // Detect using the standard SURF feature descriptor and describer
        DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(
                new ConfigFastHessian(1, 2, 200, 1, 9, 4, 4), null, null, imageType);
        ScoreAssociation<BrightFeature> scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class, true);
        AssociateDescription<BrightFeature> associate = FactoryAssociation.greedy(scorer, 2, true);
        // fit the images using a homography.  This works well for rotations and distant objects.
        ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher =
                FactoryMultiViewRobust.homographyRansac(null, new ConfigRansac(60, 3));
        Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);
        //return and reneder image
        System.out.println(H.a13);
        System.out.println(H.a23);
        int xD = imageA.getWidth();
        double xT = (Math.abs(H.a13) + imageB.getWidth());
        if (xD < xT) {
            xD = (int) Math.round(xT);
        }
        int yD = imageA.getHeight();
        double yT = (Math.abs(H.a23) + imageB.getHeight());
        if (yD < yT) {
            yD = (int) Math.round(yT);
        }
        BufferedImage imageEnd = new BufferedImage(xD, yD, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = imageEnd.createGraphics();
        int xA1 = 0;
        if (H.a13 > 0) {
            xA1 = ((int) Math.round(H.a13));
        }
        int yA1 = 0;
        if (H.a23 > 0) {
            yA1 = ((int) Math.round(H.a23));
        }
        System.out.println(xA1 + " " + yA1);
        g2.drawImage(imageA, null, xA1, yA1);
        xA1 = -((int) Math.round(H.a13));
        if (H.a13 > 0) {
            xA1 = 0;
        }
        yA1 = -((int) Math.round(H.a23));
        if (H.a23 > 0) {
            yA1 = 0;
        }
        System.out.println(xA1 + " " + yA1);
        g2.drawImage(imageB, null, xA1, yA1);
        g2.dispose();
        System.out.println((System.currentTimeMillis() - time) + " ms");
        return imageEnd;
    }

    /**
     * Accept color and alpha mask to image
     */
    private void getImageMasked() {
        int width = lastImage.getWidth(), height = lastImage.getHeight();
        int[] rgb = lastImage.getRGB(0, 0, width, height, new int[width * height], 0, width);
        int[] rgbColor = maskColor.getRGB(0, 0, width, height, new int[width * height], 0, width);
        lastImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
            int red = ((rgb[rgbIndex] >> 16) & 0xff) + ((rgbColor[rgbIndex] >> 16) & 0xff);
            int green = ((rgb[rgbIndex] >> 8) & 0xff) + ((rgbColor[rgbIndex] >> 8) & 0xff);
            int blue = ((rgb[rgbIndex]) & 0xff) + ((rgbColor[rgbIndex]) & 0xff);
            if (red > 255) {
                red = 255;
            }
            if (green > 255) {
                green = 255;
            }
            if (blue > 255) {
                blue = 255;
            }
            rgb[rgbIndex] = (maskAlpha[rgbIndex] << 24) | (((red)) << 16)
                    | (((green)) << 8) | (blue);
        }
        lastImage.setRGB(0, 0, width, height, rgb, 0, width);
    }

    /**
     * Create alpha and color masks
     */
    private void createMaskImages() {
        int width = maskColor.getWidth(), height = maskColor.getHeight();
        int[] rgb = maskColor.getRGB(0, 0, width, height, new int[width * height], 0, width);
        int[] rgbColor = maskColor.getRGB(0, 0, width, height, new int[width * height], 0, width);
        maskAlpha = new int[rgbColor.length];
        for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
            int red = (rgb[rgbIndex] >> 16) & 0xff;
            int green = (rgb[rgbIndex] >> 8) & 0xff;
            int blue = (rgb[rgbIndex]) & 0xff;
            if (blue < 135 | red < 135 | green < 135) {
                maskAlpha[rgbIndex] = 0;
            } else {
                maskAlpha[rgbIndex] = 255;
            }
            rgbColor[rgbIndex] = maskAlpha[rgbIndex] << 24 | (255 - red) >> 1 << 16 | (((255 - green) / 2) << 8) | ((255 - blue) / 2);
        }
        maskColor.setRGB(0, 0, width, height, rgbColor, 0, width);
    }

    /**
     * Get summary image
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
     *
     * @param source bigger image
     * @param scale  coefficient of scale
     * @return smaller image
     */
    private BufferedImage scaleImageHoriz(BufferedImage source, int scale) {
        int newHeight = new Double(source.getHeight() / (source.getWidth() / scale)).intValue();
        BufferedImage resized = null;
        try {
            resized = new BufferedImage(scale, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, scale, newHeight, 0, 0, source.getWidth(),
                    source.getHeight(), null);
            g.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resized;
    }
}

