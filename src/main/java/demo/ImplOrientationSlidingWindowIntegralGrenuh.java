package demo;

import boofcv.alg.feature.describe.SurfDescribeOps;
import boofcv.factory.filter.kernel.FactoryKernelGaussian;
import boofcv.struct.convolve.Kernel2D_F64;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import boofcv.struct.sparse.GradientValue;
import boofcv.struct.sparse.SparseScaleGradient;
import georegression.metric.UtilAngle;
import org.ddogleg.sorting.QuickSort_F64;

public class ImplOrientationSlidingWindowIntegralGrenuh<T extends ImageGray<T>, G extends GradientValue> {
    // where the output from the derivative is stored
    double[] derivX;
    double[] derivY;
    // the size of the angle window it will consider in radians
    protected double windowSize;
    // the angle each pixel is pointing
    protected double angles[];
    // clockwise ordering of angles
    protected int order[];
    int total = 0;
    QuickSort_F64 sorter = new QuickSort_F64();
    // integral image transform of input image
    protected GrayF32 ii;
    // the scale at which the feature was detected
    protected double scale = 1;
    // size of the area being considered in wavelets samples
    protected int sampleRadius;
    protected int sampleWidth;
    // optional weights
    protected Kernel2D_F64 weights;
    // size of sample kernels
    protected int kernelWidth;
    // how often the image is sampled
    protected double period;
    protected double objectRadiusToScale;
    // used to sample the image when it's on the image's border
    protected SparseScaleGradient<GrayF32, G> g;
    Class<GrayF32> integralType;
    /**
     * Configure orientation estimation.
     *
     * @param sampleRadius        The radius of samples that it will do.  Typically 6.
     * @param period              How often the image is sampled in pixels at canonical size. Internally, this value
     *                            is scaled by scaledPeriod = period*objectRadius/sampleRadius.  Typically 1.
     * @param kernelWidth         How wide of a kernel should be used to sample. Try 4
     * @param weightSigma         Sigma for weighting.  Set to zero for unweighted, negative to use sampleRadius.
     * @param assignDefaultRadius If true it will set the object's radius to a scale of 1
     */
    /**
     * Specifies configuration parameters and initializes data structures
     * <p>
     * samplePeriod      How often the image is sampled.  This number is scaled.  Typically 1.
     * windowSize        Angular window that is slide across
     * sampleRadius      Radius of the region being considered in terms of samples. Typically 6.
     * weightSigma       Sigma for weighting distribution.  Zero for unweighted.
     * sampleKernelWidth Size of kernel doing the sampling.  Typically 4.
     * integralType      Type of integral image being processed.
     */
    public ImplOrientationSlidingWindowIntegralGrenuh() {
      /*   public OrientationIntegralBase(double objectRadiusToScale, int sampleRadius, double period,
        int kernelWidth, double weightSigma,
        boolean assignDefaultRadius, Class<II> integralType) {*/
        this.objectRadiusToScale = 0.5;
        this.sampleRadius = 8;
        this.period = 0.65;
        this.kernelWidth = 6;
        this.sampleWidth = sampleRadius * 2 + 1;
        this.integralType = GrayF32.class;
        this.weights = FactoryKernelGaussian.gaussian(2, true, 64, -1, sampleRadius);
        g = (SparseScaleGradient<GrayF32, G>) SurfDescribeOps.createGradient(false, integralType);
        setObjectRadius(1.0 / objectRadiusToScale);
        this.windowSize = Math.PI / 3.0;
        derivX = new double[sampleWidth * sampleWidth];
        derivY = new double[sampleWidth * sampleWidth];
        angles = new double[sampleWidth * sampleWidth];
        order = new int[angles.length];
    }

    public double compute(double c_x, double c_y) {
        double period = scale * this.period;
        // top left corner of the region being sampled
        double tl_x = c_x - sampleRadius * period;
        double tl_y = c_y - sampleRadius * period;
        computeGradient(tl_x, tl_y, period);
        // apply weight to each gradient dependent on its position
        if (weights != null) {
            for (int i = 0; i < total; i++) {
                double w = weights.data[i];
                derivX[i] *= w;
                derivY[i] *= w;
            }
        }
        for (int i = 0; i < total; i++) {
            angles[i] = Math.atan2(derivY[i], derivX[i]);
        }
        // order points from lowest to highest
        sorter.sort(angles, 0, angles.length, order);
        return estimateAngle();
    }

    private void computeGradient(double tl_x, double tl_y, double samplePeriod) {
        // add 0.5 to c_x and c_y to have it round when converted to an integer pixel
        // this is faster than the straight forward method
        tl_x += 0.5;
        tl_y += 0.5;
        total = 0;
        for (int y = 0; y < sampleWidth; y++) {
            for (int x = 0; x < sampleWidth; x++, total++) {
                int xx = (int) (tl_x + x * samplePeriod);
                int yy = (int) (tl_y + y * samplePeriod);
                if (g.isInBounds(xx, yy)) {
                    GradientValue deriv = g.compute(xx, yy);
                    double dx = deriv.getX();
                    double dy = deriv.getY();
                    derivX[total] = dx;
                    derivY[total] = dy;
                } else {
                    derivX[total] = 0;
                    derivY[total] = 0;
                }
            }
        }
    }

    private double estimateAngle() {
        int start = 0;
        int end = 1;
        int startIndex = order[start];
        int endIndex = order[end];
        double sumX = derivX[startIndex];
        double sumY = derivY[startIndex];
        double best = sumX * sumX + sumY * sumY;
        double bestX = sumX;
        double bestY = sumY;
        double endAngle = angles[endIndex];
        while (start != total) {
            startIndex = order[start];
            double startAngle = angles[startIndex];
            // only compute the average if the angles are close to each other
            while (UtilAngle.dist(startAngle, endAngle) <= windowSize) {
                sumX += derivX[endIndex];
                sumY += derivY[endIndex];
                // see if the magnitude of the gradient inside this bound is greater
                // than the previous best
                double mag = sumX * sumX + sumY * sumY;
                if (mag > best) {
                    best = mag;
                    bestX = sumX;
                    bestY = sumY;
                }
                end++;
                if (end >= total)
                    end = 0;
                endIndex = order[end];
                endAngle = angles[endIndex];
                // if it cycled all the way around stop
                if (endIndex == startIndex)
                    break;
            }
            // remove the first element from the list
            sumX -= derivX[startIndex];
            sumY -= derivY[startIndex];
            start++;
        }
        return Math.atan2(bestY, bestX);
    }

    public void setObjectRadius(double radius) {
        this.scale = radius * objectRadiusToScale;
        g.setWidth(scale * kernelWidth);
    }

    public void setImage(GrayF32 integralImage) {
        this.ii = integralImage;
        g.setImage(ii);
    }

    public Class<GrayF32> getImageType() {
        return integralType;
    }
}
