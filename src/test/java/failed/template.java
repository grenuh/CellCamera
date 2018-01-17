package failed;
public class template {
   /* static BufferedImage addImages(BufferedImage input1, BufferedImage input2) {
        System.out.println("Start");
        long time = System.currentTimeMillis();
        /*int width = input1.getWidth(), height = input1.getHeight();
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
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        Mat img1 = Main.bufferedImageToMat(input2);
        Mat img2 = Main.bufferedImageToMat(input1);
        Mat img1 = Highgui.imread(s1);
        Mat img2 = Highgui.imread(s0);
        Mat gray_image1 = new Mat();
        Mat gray_image2 = new Mat();
        time = System.currentTimeMillis();
        Imgproc.cvtColor(img1, gray_image1, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(img2, gray_image2, Imgproc.COLOR_RGB2GRAY);
        MatOfKeyPoint keyPoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keyPoints2 = new MatOfKeyPoint();
        FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
        System.out.println((System.currentTimeMillis() - time) + " 2");
        time = System.currentTimeMillis();
        detector.detect(gray_image1, keyPoints1);
        detector.detect(gray_image2, keyPoints2);
        Mat descriptors1 = new Mat();
        Mat descriptors2 = new Mat();
        System.out.println((System.currentTimeMillis() - time) + " 3");
        time = System.currentTimeMillis();
        DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.SURF);
        extractor.compute(gray_image1, keyPoints1, descriptors1);
        extractor.compute(gray_image2, keyPoints2, descriptors2);
        MatOfDMatch matches = new MatOfDMatch();
        System.out.println((System.currentTimeMillis() - time) + " 4");
        time = System.currentTimeMillis();
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        matcher.match(descriptors1, descriptors2, matches);
        double max_dist = 0;
        double min_dist = 100;
        System.out.println((System.currentTimeMillis() - time) + " 5");
        time = System.currentTimeMillis();
        java.util.List<DMatch> listMatches = matches.toList();
        for (int i = 0; i < listMatches.size(); i++) {
            double dist = listMatches.get(i).distance;
            if (dist < min_dist) min_dist = dist;
            if (dist > max_dist) max_dist = dist;
        }
        System.out.println((System.currentTimeMillis() - time) + " 6");
        time = System.currentTimeMillis();
        System.out.println("Min: " + min_dist);
        System.out.println("Max: " + max_dist);
        LinkedList<DMatch> good_matches = new LinkedList<DMatch>();
        MatOfDMatch goodMatches = new MatOfDMatch();
        for (int i = 0; i < listMatches.size(); i++) {
            if (listMatches.get(i).distance < 2 * min_dist) {
                good_matches.addLast(listMatches.get(i));
            }
        }
        System.out.println((System.currentTimeMillis() - time) + " 7");
        time = System.currentTimeMillis();
        goodMatches.fromList(good_matches);
        Mat img_matches = new Mat(new Size(img1.cols() + img2.cols(), img1.rows()), CvType.CV_32FC2);
        LinkedList<org.opencv.core.Point> imgPoints1List = new LinkedList<org.opencv.core.Point>();
        LinkedList<org.opencv.core.Point> imgPoints2List = new LinkedList<org.opencv.core.Point>();
        java.util.List<KeyPoint> keypoints1List = keyPoints1.toList();
        java.util.List<KeyPoint> keypoints2List = keyPoints2.toList();
        for (int i = 0; i < good_matches.size(); i++) {
            imgPoints1List.addLast(keypoints1List.get(good_matches.get(i).queryIdx).pt);
            imgPoints2List.addLast(keypoints2List.get(good_matches.get(i).trainIdx).pt);
        }
        System.out.println((System.currentTimeMillis() - time) + " 8");
        time = System.currentTimeMillis();
        MatOfPoint2f obj = new MatOfPoint2f();
        obj.fromList(imgPoints1List);
        MatOfPoint2f scene = new MatOfPoint2f();
        scene.fromList(imgPoints2List);
        System.out.println((System.currentTimeMillis() - time) + " 9");
        time = System.currentTimeMillis();
        Mat H = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 3);
        Size s = new Size(img2.cols() + img1.cols(), img1.rows());
        Imgproc.warpPerspective(img1, img_matches, H, s);
        Mat m = new Mat(img_matches, new Rect(0, 0, img2.cols(), img2.rows()));
        System.out.println((System.currentTimeMillis() - time) + " 10");
        time = System.currentTimeMillis();
        img2.copyTo(m);
        Highgui.imwrite("out!.jpg", img_matches);
        return Main.matToBufferedImage(img_matches);
    }*/
}
