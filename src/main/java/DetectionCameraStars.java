import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import com.github.sarxos.webcam.Webcam;
import georegression.struct.point.Point2D_F64;
import sl.shapes.StarPolygon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public final class DetectionCameraStars extends JPanel {
    private static Webcam webcam;
    private JLabel images = new JLabel();

    private DetectionCameraStars() {
        JFrame frame = new JFrame("SLIDE SHOW");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.getContentPane().add(this);
        add(images);
        frame.setSize(300, 400);
        frame.setVisible(true);
        ActionListener action = ae -> {
            BufferedImage image = webcam.getImage();
            BufferedImage newImage = addImages(image, GrayF32.class);
            images.setIcon(new ImageIcon(newImage));
            revalidate();
            repaint();
        };
        Timer timer = new Timer(80, action);
        timer.start();
    }

    public static void main(String[] args) throws IOException {
        // get default webcam and open it
        ArrayList<Webcam> cameras = new ArrayList<>(Webcam.getWebcams());
        webcam = cameras.get(0);
        Dimension[] sizes = webcam.getViewSizes();
        Dimension s2 = sizes[0];
        for (int i = 1; i < sizes.length; i++) {
            if (s2.height < sizes[i].height) {
                s2 = sizes[i];
            }
        }
        webcam.setViewSize(s2);
        webcam.open();
        SwingUtilities.invokeLater(DetectionCameraStars::new);
    }

    private static <T extends ImageGray<T>> BufferedImage addImages(BufferedImage image2, Class<T> imageType) {
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
        Graphics2D g2 = image2.createGraphics();
        Random rd = new Random();
        for (int i1 = 0; i1 < img2Points.size(); i1++) {
            Point2D_F64 pp = img2Points.get(i1);
            int radius = img2R.get(i1).intValue();
            int vertex = 3 + rd.nextInt(10);
            StarPolygon st = new StarPolygon((int) pp.x, (int) pp.y, radius, 3, vertex);
            g2.draw(st);
        }
        g2.setStroke(new BasicStroke(3));
        return image2;
    }
}

