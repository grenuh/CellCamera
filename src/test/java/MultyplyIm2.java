import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.geo.ConfigRansac;
import boofcv.factory.geo.FactoryMultiViewRobust;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.feature.AssociatedIndex;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import georegression.struct.homography.Homography2D_F64;
import georegression.struct.point.Point2D_F64;
import org.ddogleg.fitting.modelset.ModelMatcher;
import org.ddogleg.struct.FastQueue;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MultyplyIm2 extends JPanel {
    private static BufferedImage bigImage;
    private static BufferedImage midImage;
    private static int[] midPosition;
    private JLabel images = new JLabel();
    static int u = 0;

    public MultyplyIm2() throws IOException {
        JFrame frame = new JFrame("SLIDE");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.getContentPane().add(this);
        add(images);
        frame.setSize(300, 300);
        //String[] files = new String[]{"01.jpg", "02.jpg", "03.jpg"};
        String[] files = new String[]{"z1.png", "z2.png", "z3.png", "z4.png", "z5.png", "z6.png", "z7.png", "z8.png"};
        images.setIcon(new ImageIcon(getOneImageFromList(files)));
        frame.setVisible(true);
    }

    public static void main(String[] args) throws IOException {
        SwingUtilities.invokeLater(() -> {
            try {
                new MultyplyIm2();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Using abstracted code, find a transform which minimizes the difference between corresponding features
     * in both images.  This code is completely model independent and is the core algorithms.
     */
    public static <T extends ImageGray<T>, FD extends TupleDesc> Homography2D_F64
    computeTransform(T imageA, T imageB,
                     DetectDescribePoint<T, FD> detDesc,
                     AssociateDescription<FD> associate,
                     ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher) {
        // get the length of the description
        List<Point2D_F64> pointsA = new ArrayList<>();
        FastQueue<FD> descA = UtilFeature.createQueue(detDesc, 100);
        List<Point2D_F64> pointsB = new ArrayList<>();
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
        List<AssociatedPair> pairs = new ArrayList<>();
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
                       List<Point2D_F64> points,
                       FastQueue<FD> listDescs) {
        detDesc.detect(image);
        listDescs.reset();
        for (int i = 0; i < detDesc.getNumberOfFeatures(); i++) {
            points.add(detDesc.getLocation(i).copy());
            listDescs.grow().setTo(detDesc.getDescription(i));
        }
    }

    /**
     * Given two input images create and display an image where the two have been overlayed on top of each other.
     */
    public static <T extends ImageGray<T>>
    BufferedImage stitch(BufferedImage lastImage) {
        GrayF32 inputA = ConvertBufferedImage.convertFromSingle(midImage, null, GrayF32.class);
        GrayF32 inputB = ConvertBufferedImage.convertFromSingle(lastImage, null, GrayF32.class);
        // Detect using the standard SURF feature descriptor and describer
        DetectDescribePoint detDesc = FactoryDetectDescribe.surfStable(
                new ConfigFastHessian(1, 2, 200, 1, 9, 4, 4), null, null, GrayF32.class);
        ScoreAssociation<BrightFeature> scorer = FactoryAssociation.scoreEuclidean(BrightFeature.class, true);
        AssociateDescription<BrightFeature> associate = FactoryAssociation.greedy(scorer, 2, true);
        // fit the images using a homography.  This works well for rotations and distant objects.
        ModelMatcher<Homography2D_F64, AssociatedPair> modelMatcher =
                FactoryMultiViewRobust.homographyRansac(null, new ConfigRansac(60, 3));
        Homography2D_F64 H = computeTransform(inputA, inputB, detDesc, associate, modelMatcher);
        //return and reneder image
        System.out.println(H.a13);
        System.out.println(H.a23);
        int xD = bigImage.getWidth();
        double xT = (Math.abs(H.a13) + midPosition[0] + lastImage.getWidth());
        if (xD < xT) {
            xD = (int) Math.round(xT);
        }
        int yD = bigImage.getHeight();
        double yT = (Math.abs(H.a23) + midPosition[1] + lastImage.getHeight());
        if (yD < yT) {
            yD = (int) Math.round(yT);
        }
        System.out.println("Dimension of map - " + xD + " " + yD);
        BufferedImage imageEnd = new BufferedImage(xD, yD, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = imageEnd.createGraphics();
        int xA1 = 0;
        if ((midPosition[0] - H.a13) < 0) {
            xA1 = ((int) Math.round(H.a13 - midPosition[0]));
        }
        int yA1 = 0;
        if ((midPosition[1] - H.a23) < 0) {
            yA1 = ((int) Math.round(H.a23 - midPosition[1]));
        }
        //  System.out.println(xA1 + " " + yA1);
        System.out.println("Location of map - " + xA1 + " " + yA1);
        g2.drawImage(bigImage, null, xA1, yA1);
        xA1 = midPosition[0] - ((int) Math.round(H.a13));
        if ((midPosition[0] - H.a13) < 0) {
            xA1 = 0;
        }
        yA1 = midPosition[1] - ((int) Math.round(H.a23));
        if ((midPosition[1] - H.a23) < 0) {
            yA1 = 0;
        }
        // System.out.println(xA1 + " " + yA1);
        System.out.println("Location of image - " + xA1 + " " + yA1);
        g2.drawImage(lastImage, null, xA1, yA1);
        g2.dispose();
        midPosition[0] = xA1;
        midPosition[1] = yA1;
        midImage = lastImage;
        JFrame framea = new JFrame("SLIDE+" + u);
        framea.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        framea.setLocationByPlatform(true);
        JLabel imagesa = new JLabel();
        framea.add(imagesa);
        framea.setSize(new Dimension(300, 400));
        imagesa.setIcon(new ImageIcon(imageEnd));
        framea.setVisible(true);
        u++;
        return imageEnd;
    }

    private BufferedImage getOneImageFromList(String[] files) throws IOException {
        bigImage = ImageIO.read(new File(files[0]));
        midImage = bigImage;
        midPosition = new int[]{0, 0};
        for (int i = 1; i < files.length; i++) {
            if (i == 2) {
                System.out.println();
            }
            BufferedImage imageB = ImageIO.read(new File(files[i]));
            bigImage = stitch(imageB);
        }
        return bigImage;
    }
}
