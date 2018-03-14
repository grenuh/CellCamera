package demo;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detect.extract.NonMaxSuppression;
import boofcv.abst.feature.detect.extract.WrapperNonMaximumBlock;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.PixelTransformHomography_F32;
import boofcv.alg.distort.impl.DistortSupport;
import boofcv.alg.feature.describe.SurfDescribeOps;
import boofcv.alg.feature.detect.extract.NonMaxBlock;
import boofcv.alg.feature.detect.extract.NonMaxBlockStrict;
import boofcv.alg.feature.detect.extract.SelectNBestFeatures;
import boofcv.alg.feature.detect.intensity.GIntegralImageFeatureIntensity;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.core.image.border.BorderType;
import boofcv.core.image.border.FactoryImageBorderAlgs;
import boofcv.core.image.border.ImageBorder_F32;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.QueueCorner;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.ScalePoint;
import boofcv.struct.feature.SurfFeatureQueue;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;
import boofcv.struct.sparse.SparseImageGradient;
import com.github.sarxos.webcam.Webcam;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I16;
import georegression.struct.point.Point2D_I32;
import georegression.transform.homography.HomographyPointOps_F64;
import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.FastQueue;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import static boofcv.io.image.ConvertRaster.getOffset;
import static boofcv.io.image.ConvertRaster.stride;

public class DemoMicroVideo extends JPanel implements KeyListener {
    private static int cropped;
    private static Webcam webcam;
    private static int[] configI = new int[8];
    static {
        Webcam.setDriver(Webcam.getDriver());
        configI[0] = 1;//detectTreshold
        configI[1] = 2;//extractRadius
        configI[2] = 200;//maxFeaturesPerScale
        configI[3] = 1;//initialSampleSize
        configI[4] = 9;//initialSize
        configI[5] = 4;//numberScalesPerOctave
        configI[6] = 4;//numberOfOctaves
        configI[7] = 6;//SCALESTEPSIZE
    }
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
    private int maskCoefficient = 3;
    private int[][] rawMaskData = new int[4][];//3 colors
    private JLabel pointImage0 = new JLabel(); //grand image
    private JLabel pointImage1 = new JLabel(); //grand image
    private int[] timeA = new int[20];

    /**
     * Constructor with GUI
     */
    private DemoMicroVideo() {
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
                            rgbColor[rgbIndex] = rawMaskData[3][rgbIndex] << 24 | (((255 - rawMaskData[0][rgbIndex]) * 2) / maskCoefficient) >> 1 << 16 | (((255 - rawMaskData[1][rgbIndex]) / maskCoefficient) << 8) | ((255 - rawMaskData[2][rgbIndex]) / maskCoefficient);
                        }
                        maskColor.setRGB(0, 0, width, height, rgbColor, 0, width);
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
                    rawMaskData[3] = new int[rgb.length];
                    for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
                        rawMaskData[3][rgbIndex] = (rgb[rgbIndex] >> 24) & 0xff0;
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
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
        maskMenu.add(loadMask);
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
        final int[] i = {0};
        ActionListener actionMask = ae -> {
            BufferedImage maskColor = null;
            if (i[0] == 0) maskColor = webcam.getImage();
            else maskColor = getMedianImage(maskColor, webcam.getImage());
            i[0]++;
            if (i[0] > 10) {
                timerMask.stop();
                createMaskImages(maskColor);
                masked = true;
                frame.setTitle("SLIDE SHOW");
                Path path = Paths.get("mask.png");
                if (Files.notExists(path)) {
                    try {
                        ImageIO.write(maskColor, "png", new File("mask.png"));
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
                BufferedImage maskColor = ImageIO.read(file);
                int width = maskColor.getWidth(), height = maskColor.getHeight();
                maskDim[0] = width;
                maskDim[1] = height;
                int[] rgb = maskColor.getRGB(0, 0, width, height, new int[width * height], 0, width);
                rawMaskData[3] = new int[rgb.length];
                rawMaskData[1] = new int[rgb.length];
                rawMaskData[2] = new int[rgb.length];
                rawMaskData[0] = new int[rgb.length];
                for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
                    rawMaskData[3][rgbIndex] = (rgb[rgbIndex] >> 24) & 0xff0;
                    rawMaskData[0][rgbIndex] = (rgb[rgbIndex] >> 16) & 0xff;//red
                    rawMaskData[1][rgbIndex] = (rgb[rgbIndex] >> 8) & 0xff;//green
                    rawMaskData[2][rgbIndex] = (rgb[rgbIndex]) & 0xff;//blue
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
        /*ArrayList<Webcam> cameras = new ArrayList<>(Webcam.getWebcams());
        if (cameras.size() > 1) {
            webcam = cameras.get(1);
        } else {**/
        webcam = Webcam.getWebcams().get(0);
        // }
        Dimension[] sizes = webcam.getViewSizes();
        Dimension cameraDimension = sizes[0];
        for (int i = 1; i < sizes.length; i++) {
            if (cameraDimension.height < sizes[i].height) {
                cameraDimension = sizes[i];
            }
        }
        webcam.setViewSize(cameraDimension);
        webcam.open();
        SwingUtilities.invokeLater(DemoMicroVideo::new);
    }

    private static void describeImageAlt(GrayF32 inputA,
                                         java.util.List<Point2D_F64> pointsA, FastQueue<BrightFeature> descA) {
        GrayF32 ii = inputA.createNew(inputA.width, inputA.height);
        double[] featureAnglesArr = new double[10];
        NonMaxBlock ret0 = new NonMaxBlockStrict.Max();
        ret0.setSearchRadius(configI[1]);
        ret0.setThresholdMax(configI[0]);
        ret0.setThresholdMin(configI[0]);
        ret0.setBorder(0);
        NonMaxSuppression extractor = new WrapperNonMaximumBlock(ret0);
        FastQueueGrenuh foundPoints = new FastQueueGrenuh(50);
        int spaceIndex = 0;
        QueueCorner foundFeatures = new QueueCorner(100);
        int maxFeaturesPerScale = configI[2];
        SelectNBestFeatures sortBest = new SelectNBestFeatures(maxFeaturesPerScale);
        int[] sizes = new int[configI[5]];
        ImplOrientationSlidingWindowIntegralGrenuh orientation = new ImplOrientationSlidingWindowIntegralGrenuh();
        DescribePointSurfGrenuh describe = new DescribePointSurfGrenuh();
        SurfFeatureQueue features = new SurfFeatureQueue(64);
        int indexSrcQ = inputA.startIndex;
        int indexDst = ii.startIndex;
        int end = indexSrcQ + inputA.width;
        float total = 0;
        while (indexSrcQ < end) {
            ii.data[indexDst++] = total += inputA.data[indexSrcQ];
            indexSrcQ++;
        }
        for (int y = 1; y < inputA.height; y++) {
            indexSrcQ = inputA.startIndex + inputA.stride * y;
            indexDst = ii.startIndex + ii.stride * y;
            int indexPrev = indexDst - ii.stride;
            end = indexSrcQ + inputA.width;
            total = 0;
            for (; indexSrcQ < end; indexSrcQ++) {
                total += inputA.data[indexSrcQ];
                ii.data[indexDst++] = ii.data[indexPrev++] + total;
            }
        }
        orientation.setImage(ii);
        describe.ii = ii;
        describe.gradient.setImage(ii);
        features.reset();
        int featureAnglesArrI = 0;
        GrayF32[] intensity = new GrayF32[3];
        for (int i = 0; i < intensity.length; i++) {
            intensity[i] = new GrayF32(ii.width, ii.height);
        }
        foundPoints.reset();
        int skip = configI[3];
        int sizeStep = configI[7];
        int octaveSize = configI[4];
        for (int octave = 0; octave < configI[6]; octave++) {
            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = octaveSize + i * sizeStep;
            }
            // if the maximum kernel size is larger than the image don't process
            // the image any more
            int maxSize = sizes[sizes.length - 1];
            if (maxSize > ii.width || maxSize > ii.height)
                break;
            // detect features inside of this octave
            // detectOctave(ii, skip, sizes);
            // protected void detectOctave (GrayF32 integral,int skip, int...featureSize){
            int w = ii.width / skip;
            int h = ii.height / skip;
            // resize the output intensity image taking in account subsampling
            for (GrayF32 anIntensity : intensity) {
                anIntensity.reshape(w, h);
            }
            // compute feature intensity in each level
            for (int i = 0; i < sizes.length; i++) {
                GIntegralImageFeatureIntensity.hessian(ii, skip, sizes[i], intensity[spaceIndex]);
                spaceIndex++;
                if (spaceIndex >= 3)
                    spaceIndex = 0;
                // find maximum in scale space
                if (i >= 2) {
                    // findLocalScaleSpaceMax(sizes, i - 1, skip);
                    //  private void findLocalScaleSpaceMax(int[] size, int level, int skip) {
                    int index1 = (spaceIndex + 1) % 3;
                    int index2 = (spaceIndex + 2) % 3;
                    ImageBorder_F32 inten0 = FactoryImageBorderAlgs.value(intensity[spaceIndex], 0);
                    GrayF32 inten1 = intensity[index1];
                    ImageBorder_F32 inten2 = FactoryImageBorderAlgs.value(intensity[index2], 0);
                    // find local maximums in image 2D space.  Borders need to be ignored since
                    // false positives are found around them as an artifact of pixels outside being
                    // treated as being zero.
                    foundFeatures.reset();
                    extractor.setIgnoreBorder(sizes[i - 1] / (2 * skip));
                    extractor.process(intensity[index1], null, null, null, foundFeatures);
                    // Can't consider feature which are right up against the border since they might not be a true local
                    // maximum when you consider the features on the other side of the ignore border
                    int ignoreRadius = extractor.getIgnoreBorder() + extractor.getSearchRadius();
                    int ignoreWidth = intensity[index1].width - ignoreRadius;
                    int ignoreHeight = intensity[index1].height - ignoreRadius;
                    // number of features which can be added
                    int numberRemaining;
                    // if configured to do so, only select the features with the highest intensity
                    QueueCorner features2;
                    if (sortBest != null) {
                        sortBest.process(intensity[index1], foundFeatures, true);
                        features2 = sortBest.getBestCorners();
                        numberRemaining = maxFeaturesPerScale;
                    } else {
                        features2 = foundFeatures;
                        numberRemaining = Integer.MAX_VALUE;
                    }
                    int levelSize = sizes[i - 1];
                    int sizeStep2 = levelSize - sizes[i - 2];
                    // see if these local maximums are also a maximum in scale-space
                    for (int i2 = 0; i2 < features2.size && numberRemaining > 0; i2++) {
                        Point2D_I16 f = features2.get(i2);
                        // avoid false positives.  see above comment
                        if (f.x < ignoreRadius || f.x >= ignoreWidth || f.y < ignoreRadius || f.y >= ignoreHeight)
                            continue;
                        float val = inten1.get(f.x, f.y);
                        // see if it is a max in scale-space too
                        if (checkMax(inten0, val, f.x, f.y) && checkMax(inten2, val, f.x, f.y)) {
                            // find the feature's location to sub-pixel accuracy using a second order polynomial
                            // NOTE: In the original paper this was done using a quadratic.  See comments above.
                            // NOTE: Using a 2D polynomial for x and y might produce better results.
                            float peakX = polyPeak(inten1.get(f.x - 1, f.y), inten1.get(f.x, f.y), inten1.get(f.x + 1, f.y));
                            float peakY = polyPeak(inten1.get(f.x, f.y - 1), inten1.get(f.x, f.y), inten1.get(f.x, f.y + 1));
                            float peakS = polyPeak(inten0.get(f.x, f.y), inten1.get(f.x, f.y), inten2.get(f.x, f.y));
                            float interpX = (f.x + peakX) * skip;
                            float interpY = (f.y + peakY) * skip;
                            float interpS = levelSize + peakS * sizeStep2;
                            double scale = 1.2 * interpS / 9.0;
                            foundPoints.grow().set(interpX, interpY, scale);
                            numberRemaining--;
                        }
                    }
                }
            }
            skip += skip;
            octaveSize += sizeStep;
            sizeStep += sizeStep;
        }
        for (ScalePoint aFoundPointsArr : foundPoints.data) {
            orientation.setObjectRadius(aFoundPointsArr.scale * 2.0);
            double angle = orientation.compute(aFoundPointsArr.x, aFoundPointsArr.y);
            BrightFeature ret = features.grow();
            // detDesc.describe.describe(detDesc.foundPointsArr[i].x, detDesc.foundPointsArr[i].y, angle, detDesc.foundPointsArr[i].scale, ret);
            describe.describe(aFoundPointsArr.x, aFoundPointsArr.y, angle, aFoundPointsArr.scale, ret);
            //    public void describe(double x, double y, double angle, double scale, TupleDesc_F64 ret)
            double c = Math.cos(angle), s = Math.sin(angle);
            // By assuming that the entire feature is inside the image faster algorithms can be used
            // the results are also of dubious value when interacting with the image border.
            boolean isInBounds =
                    SurfDescribeOps.isInside(describe.ii, aFoundPointsArr.x,
                            aFoundPointsArr.y, describe.radiusDescriptor, 3, aFoundPointsArr.scale, c, s);
            if (ret == null)
                ret = new BrightFeature(64);
            describe.gradient.setImage(describe.ii);
            describe.gradient.setWidth(3 * aFoundPointsArr.scale);
            // use a safe method if its along the image border
            SparseImageGradient gradient = isInBounds ? describe.gradient : describe.gradientSafe;
            // extract descriptor
            describe.features(aFoundPointsArr.x, aFoundPointsArr.y, c, s, aFoundPointsArr.scale, gradient, ret.value);
            // normalize feature vector to have an Euclidean length of 1
            // adds light invariance
            UtilFeature.normalizeL2(ret);
            // Laplacian's sign
            ret.white = describe.computeLaplaceSign((int) (aFoundPointsArr.x + 0.5), (int) (aFoundPointsArr.y + 0.5), aFoundPointsArr.scale);
            if (featureAnglesArrI == featureAnglesArr.length) {
                double[] temp;
                try {
                    temp = new double[featureAnglesArrI * 2];
                } catch (OutOfMemoryError var5) {
                    System.gc();
                    temp = new double[3 * featureAnglesArrI / 2];
                }
                System.arraycopy(featureAnglesArr, 0, temp, 0, featureAnglesArrI);
                featureAnglesArr = temp;
            }
            featureAnglesArr[featureAnglesArrI++] = angle;
        }
        descA.size = 0;
        for (int i = 0; i < foundPoints.data.length; i++) {
            pointsA.add(new Point2D_F64(foundPoints.data[i]));
            if (descA.size >= descA.data.length) {
                descA.growArray((descA.data.length + 1) * 2);
            }
            descA.data[descA.size++].setTo(features.get(i));
        }
    }

    /**
     * <p>
     * Fits a second order polynomial to the data and determines the location of the peak.
     * <br>
     * y = a*x<sup>2</sup>+b*x + c<br>
     * x = {-1,0,1}<br>
     * y = Feature value
     * </p>
     * <p>
     * <p>
     * Note: The original paper fit a 3D Quadratic to the data instead.  This required the first
     * and second derivative of the Laplacian to be estimated.  Such estimates are error prone
     * and using the technique found in OpenSURF produced erratic results and required some hackery
     * to get to work.  This should always produce stable results and is much faster.
     * </p>
     *
     * @param lower  Value at x=-1
     * @param middle Value at x=0
     * @param upper  Value at x=1
     * @return x-coordinate of the peak
     */
    private static float polyPeak(float lower, float middle, float upper) {
//		if( lower >= middle || upper >= middle )
//			throw new IllegalArgumentException("Crap");
        // only need two coefficients to compute the peak's location
        float a = 0.5f * lower - middle + 0.5f * upper;
        float b = 0.5f * upper - 0.5f * lower;
        return -b / (2.0f * a);
    }

    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage b = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        Graphics g = b.getGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return b;
    }

    private static Point2D_I32 renderPoint(int x0, int y0, Homography2D_F64 fromBtoWork) {
        Point2D_F64 result = new Point2D_F64();
        HomographyPointOps_F64.transform(fromBtoWork, new Point2D_F64(x0, y0), result);
        return new Point2D_I32((int) result.x, (int) result.y);
    }

    private static boolean checkMax(ImageBorder_F32 inten, float bestScore, int c_x, int c_y) {
        for (int y = c_y - 1; y <= c_y + 1; y++) {
            for (int x = c_x - 1; x <= c_x + 1; x++) {
                if (inten.get(x, y) >= bestScore) {
                    return false;
                }
            }
        }
        return true;
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
            int red = ((rgb[rgbIndex] >> 16) & 0xff) + ((rawMaskData[0][rgbIndex] >> 16) & 0xff);
            int green = ((rgb[rgbIndex] >> 8) & 0xff) + ((rawMaskData[1][rgbIndex] >> 8) & 0xff);
            int blue = ((rgb[rgbIndex]) & 0xff) + ((rawMaskData[2][rgbIndex]) & 0xff);
            if (red > 255) {
                red = 255;
            }
            if (green > 255) {
                green = 255;
            }
            if (blue > 255) {
                blue = 255;
            }
            rgb[rgbIndex] = (rawMaskData[3][rgbIndex] << 24) | (((red)) << 16)
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
        rawMaskData[3] = new int[rgbColor.length];
        for (int rgbIndex = 0; rgbIndex < rgb.length; rgbIndex++) {
            rawMaskData[0][rgbIndex] = (rgb[rgbIndex] >> 16) & 0xff;//red
            rawMaskData[1][rgbIndex] = (rgb[rgbIndex] >> 8) & 0xff;//green
            rawMaskData[2][rgbIndex] = (rgb[rgbIndex]) & 0xff;//blue
            if (rawMaskData[0][rgbIndex] < 135 | rawMaskData[1][rgbIndex] < 135 | rawMaskData[2][rgbIndex] < 135) {
                rawMaskData[3][rgbIndex] = 0;
            } else {
                rawMaskData[3][rgbIndex] = 255;
            }
            rgbColor[rgbIndex] = rawMaskData[3][rgbIndex] << 24 | (((255 - rawMaskData[0][rgbIndex]) * 2) / maskCoefficient) >> 1 << 16 | (((255 - rawMaskData[1][rgbIndex]) / maskCoefficient) << 8) | ((255 - rawMaskData[2][rgbIndex]) / maskCoefficient);
        }
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
            long time = System.currentTimeMillis();
            BufferedImage bufImg0 = webcam.getImage();
            lastImage = new BufferedImage(bufImg0.getWidth(), bufImg0.getHeight(), BufferedImage.TYPE_INT_ARGB);
            lastImage.getGraphics().drawImage(bufImg0, 0, 0, null);
            rIjl.setIcon(new ImageIcon(scaleImageHoriz(lastImage)));
            timeA[1] += (System.currentTimeMillis() - time);
            if (masked) {
                getImageMasked();
            }
            timeA[2] += (System.currentTimeMillis() - time);
            bIjl.setIcon(new ImageIcon(scaleImageHoriz(lastImage)));
            BufferedImage as1 = copyImage(midImage);
            BufferedImage as2 = copyImage(lastImage);
            timeA[3] += (System.currentTimeMillis() - time);
      /*Begin of magic*/
            // GrayF32 inputA = ConvertBufferedImage.convertFrom(as1, (GrayF32) null);
            GrayF32 inputA = new GrayF32(as1.getWidth(), as1.getHeight());
            DataBuffer buff = as1.getRaster().getDataBuffer();
            DataBufferInt buff1 = (DataBufferInt) buff;
            int[] srcData = buff1.getData();
            float[] data = inputA.data;
            int srcStride = stride(as1.getRaster());
            int srcOffset = getOffset(as1.getRaster());
            int srcStrideDiff = srcStride - as1.getRaster().getNumDataElements() * inputA.width;
            int indexSrc = srcOffset;
            for (int y = 0; y < inputA.height; y++) {
                int indexDst = inputA.startIndex + y * inputA.stride;
                for (int x = 0; x < inputA.width; x++) {
                    int rgb = srcData[indexSrc++];
                    int r = ((rgb >>> 16) & 0xFF) + ((rgb >>> 8) & 0xFF) + (rgb & 0xFF);
                    float ave = r / 3.0f;
                    data[indexDst++] = ave;
                }
                indexSrc += srcStrideDiff;
            }
            /**/
            GrayF32 inputB = ConvertBufferedImage.convertFrom(as2, (GrayF32) null);
            // Detect using the standard SURF feature descriptor and describer
            ScoreAssociation<BrightFeature> scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class, true);
            AssociateDescription<BrightFeature> associate = FactoryAssociation.greedy(scorer, 2, true);
            // fit the images using a homography.  This works well for rotations and distant objects.
            ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher =
                    FactoryMultiViewRobust.homographyRansac(null, new ConfigRansac(60, 3));
            timeA[4] += (System.currentTimeMillis() - time);/*!!! begin*/
            // get the length of the description
            java.util.List<Point2D_F64> pointsA = new ArrayList<>();
            FastQueue<BrightFeature> descA = new FastQueue<BrightFeature>(200, BrightFeature.class, true) {
                @Override
                protected BrightFeature createInstance() {
                    return new BrightFeature(64);
                }
            };
            java.util.List<Point2D_F64> pointsB = new ArrayList<>();
            FastQueue<BrightFeature> descB = new FastQueue<BrightFeature>(200, BrightFeature.class, true) {
                @Override
                protected BrightFeature createInstance() {
                    return new BrightFeature(64);
                }
            };
            timeA[5] += (System.currentTimeMillis() - time);
            describeImageAlt(inputA, pointsA, descA);
            timeA[19] += (System.currentTimeMillis() - time);
            describeImageAlt(inputB, pointsB, descB);
            timeA[6] += (System.currentTimeMillis() - time);
            // Associate features between the two images
            associate.setSource(descA);
            associate.setDestination(descB);
            associate.associate();
            timeA[7] += (System.currentTimeMillis() - time);
            /*!!! begin*/
            // create a list of AssociatedPairs that tell the model matcher how a feature moved
            FastQueue<AssociatedIndex> matches = associate.getMatches();
            java.util.List<AssociatedPair> pairs = new ArrayList<>();
            Graphics2D gB = as2.createGraphics();
            Graphics2D gA = as1.createGraphics();
            // System.out.println("\t Point 3.5 - " + (System.currentTimeMillis() - time));
            for (int i = 0; i < matches.size(); i++) {
                AssociatedIndex match = matches.get(i);
                Point2D_F64 a = pointsA.get(match.src);
                gA.draw(new Ellipse2D.Double((int) a.x, (int) a.y, 2, 2));
                Point2D_F64 b = pointsB.get(match.dst);
                gB.draw(new Ellipse2D.Double((int) b.x, (int) b.y, 2, 2));
                pairs.add(new AssociatedPair(a, b, false));
            }
            /*!!! end*/
            timeA[8] += (System.currentTimeMillis() - time); // find the best fit model to describe the change between these images
            if (!modelMatcher.process(pairs)) {
                System.out.println("i am deaddd");
                return;
            }
            // return the found image transform
            Homography2D_F64 H = modelMatcher.getModelParameters().copy();
            // specify size of output image
            double scale = 0.5;
            // Convert into a BoofCV color format
            Planar<GrayF32> colorA =
                    ConvertBufferedImage.convertFromPlanar(as1, null, true, GrayF32.class);
            Planar<GrayF32> colorB =
                    ConvertBufferedImage.convertFromPlanar(as2, null, true, GrayF32.class);
            // Where the output images are rendered into
            Planar<GrayF32> work = colorA.createSameShape();
            // Adjust the transform so that the whole image can appear inside of it
            Homography2D_F64 fromAToWork = new Homography2D_F64(scale, 0, colorA.width / 4, 0, scale, colorA.height / 4, 0, 0, 1);
            Homography2D_F64 fromWorkToA = fromAToWork.invert(null);
            // Used to render the results onto an image
            PixelTransformHomography_F32 model = new PixelTransformHomography_F32();
            InterpolatePixelS<GrayF32> interp = FactoryInterpolation.bilinearPixelS(GrayF32.class, BorderType.ZERO);
            ImageDistort<Planar<GrayF32>, Planar<GrayF32>> distort =
                    DistortSupport.createDistortPL(GrayF32.class, model, interp, false);
            distort.setRenderAll(false);
            timeA[9] += (System.currentTimeMillis() - time); // Render first image
            model.set(fromWorkToA);
            distort.apply(colorA, work);
            // Render second image
            Homography2D_F64 fromWorkToB = fromWorkToA.concat(H, null);
            model.set(fromWorkToB);
            distort.apply(colorB, work);
            timeA[10] += (System.currentTimeMillis() - time);// Convert the rendered image into a BufferedImage
            /*!!! begin*/
            BufferedImage output = new BufferedImage(work.width, work.height, as1.getType());
            ConvertBufferedImage.convertTo(work, output, true);
            Graphics2D g2 = output.createGraphics();
            // draw lines around the distorted image to make it easier to see
            Homography2D_F64 fromBtoWork = fromWorkToB.invert(null);
            /*!!! end*/
            timeA[11] += (System.currentTimeMillis() - time);
            Point2D_I32 corners[] = new Point2D_I32[4];
            corners[0] = renderPoint(0, 0, fromBtoWork);
            corners[1] = renderPoint(colorB.width, 0, fromBtoWork);
            corners[2] = renderPoint(colorB.width, colorB.height, fromBtoWork);
            corners[3] = renderPoint(0, colorB.height, fromBtoWork);
            g2.setColor(Color.ORANGE);
            g2.setStroke(new BasicStroke(4));
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawLine(corners[0].x, corners[0].y, corners[1].x, corners[1].y);
            g2.drawLine(corners[1].x, corners[1].y, corners[2].x, corners[2].y);
            g2.drawLine(corners[2].x, corners[2].y, corners[3].x, corners[3].y);
            g2.drawLine(corners[3].x, corners[3].y, corners[0].x, corners[0].y);
            timeA[12] += (System.currentTimeMillis() - time);
                            /*End of magic*/
            pointImage0.setIcon(new ImageIcon(scaleImageHoriz(as1)));
            pointImage1.setIcon(new ImageIcon(scaleImageHoriz(as2)));
            grandMap.setIcon(new ImageIcon(scaleImageHoriz(output)));
            midImage = lastImage;
            revalidate();
            repaint();
            timeA[0]++;
            for (int i = 1; i < 20; i++) {
                System.out.println("point " + i + " " + (timeA[i] / timeA[0]));
            }
            System.out.println("Frame " + (System.currentTimeMillis() - time));
        };
        timer.stop();
        Timer timingNew = new Timer(0, actionN);
        timingNew.start();
    }
}
