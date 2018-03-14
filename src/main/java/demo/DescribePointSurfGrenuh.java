package demo;

import boofcv.alg.feature.describe.SurfDescribeOps;
import boofcv.alg.transform.ii.DerivativeIntegralImage;
import boofcv.alg.transform.ii.GIntegralImageOps;
import boofcv.alg.transform.ii.IntegralKernel;
import boofcv.factory.filter.kernel.FactoryKernelGaussian;
import boofcv.struct.convolve.Kernel2D_F64;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.image.GrayF32;
import boofcv.struct.sparse.GradientValue;
import boofcv.struct.sparse.SparseGradientSafe;
import boofcv.struct.sparse.SparseImageGradient;
import boofcv.struct.sparse.SparseScaleGradient;

public class DescribePointSurfGrenuh {
    // Size of a sample point
    //  int widthSample;
    // DOF of feature
    //  int featureDOF;
    // integral image transform of input image
    GrayF32 ii;
    // used to weigh feature computation
    Kernel2D_F64 weight;
    // computes sparse image gradient around specified points
    SparseScaleGradient<GrayF32, ?> gradient;
    // can handle sample requests outside the image border
    SparseImageGradient<GrayF32, ?> gradientSafe;
    // radius of the descriptor at a scale of 1.  Used to determine if it touches the image boundary
    // does not include sample kernel size
    int radiusDescriptor;
    // storage for kernels used to compute laplacian sign
    IntegralKernel kerXX;
    IntegralKernel kerYY;
    // how many sample points sub-regions overlap.
    // private int overLap;
    // used to weigh feature computation
    private Kernel2D_F64 weightGrid;
    private Kernel2D_F64 weightSub;
    private double samplesX[];
    private double samplesY[];

    /**
     * Creates a SURF descriptor of arbitrary dimension by changing how the local region is sampled.
     * <p>
     * widthLargeGrid Number of sub-regions wide the large grid is. Typically 4
     * widthSubRegion Number of sample points wide a sub-region is. Typically 5
     * widthSample    The width of a sample point. Typically 4
     * weightSigma    Weighting factor's sigma.  Try 3.8
     * useHaar        If true the Haar wavelet will be used (what was used in [1]), false means an image gradient
     * approximation will be used.  False is recommended.
     * Creates a SURF descriptor of arbitrary dimension by changing how the local region is sampled.
     * <p>
     * widthLargeGrid Number of sub-regions wide the large grid is.  Typically 4.
     * widthSubRegion Number of sample points wide a sub-region is.  Typically 5.
     * widthSample    The size of a sample point. Typically 3.
     * overLap        Number of sample points sub-regions overlap, Typically 2.
     * sigmaLargeGrid Sigma used to weight points in the large grid. Typically 2.5
     * sigmaSubRegion Sigma used to weight points in the sub-region grid. Typically 2.5
     * useHaar        If true the Haar wavelet will be used (what was used in [1]), false means an image gradient
     * approximation will be used.  True is recommended.
     */
    public DescribePointSurfGrenuh() {
        // this.widthSample = 3;
        weight = FactoryKernelGaussian.gaussianWidth(1, 20);
        // normalize to reduce numerical issues.
        // not sure if this makes any difference.
        double div = weight.get(10, 10);
        for (int i = 0; i < weight.data.length; i++)
            weight.data[i] /= div;
        // each sub-region provides 4 features
        // featureDOF = 64;
        // create the function that the gradient is sampled with=
        gradient = SurfDescribeOps.createGradient(false, GrayF32.class);
        gradientSafe = new SparseGradientSafe(this.gradient);
        // radiusDescriptor = 10;
        /**/
        weightGrid = FactoryKernelGaussian.gaussianWidth(2.5, 4);
        weightSub = FactoryKernelGaussian.gaussianWidth(2.5, 9);
        double div2 = weightGrid.get(weightGrid.getRadius(), weightGrid.getRadius());
        for (int i = 0; i < weightGrid.data.length; i++)
            weightGrid.data[i] /= div2;
        div2 = weightSub.get(weightSub.getRadius(), weightSub.getRadius());
        for (int i = 0; i < weightSub.data.length; i++)
            weightSub.data[i] /= div2;
        samplesX = new double[576];
        samplesY = new double[576];
        radiusDescriptor = 12;
    }

    public BrightFeature createDescription() {
        return new BrightFeature(64);
    }

    /**
     * Compute SURF descriptor, but without laplacian sign
     *
     * @param x     Location of interest point.
     * @param y     Location of interest point.
     * @param angle The angle the feature is pointing at in radians.
     * @param scale Scale of the interest point. Null is returned if the feature goes outside the image border.
     * @param ret   storage for the feature. Must have 64 values.
     */
    public void describe(double x, double y, double angle, double scale, TupleDesc_F64 ret) {
        double c = Math.cos(angle), s = Math.sin(angle);
        // By assuming that the entire feature is inside the image faster algorithms can be used
        // the results are also of dubious value when interacting with the image border.
        boolean isInBounds =
                SurfDescribeOps.isInside(ii, x, y, radiusDescriptor, 3, scale, c, s);
        // declare the feature if needed
        if (ret == null)
            ret = new BrightFeature(64);
        else if (ret.value.length != 64)
            throw new IllegalArgumentException("Provided feature must have 64 values");
        gradient.setImage(ii);
        gradient.setWidth(3 * scale);
        // use a safe method if its along the image border
        SparseImageGradient gradient = isInBounds ? this.gradient : this.gradientSafe;
        // extract descriptor
        features(x, y, c, s, scale, gradient, ret.value);
    }

    /**
     * Compute the sign of the Laplacian using a sparse convolution.
     *
     * @param x     center
     * @param y     center
     * @param scale scale of the feature
     * @return true if positive
     */
    public boolean computeLaplaceSign(int x, int y, double scale) {
        int s = (int) Math.ceil(scale);
        kerXX = DerivativeIntegralImage.kernelDerivXX(9 * s, kerXX);
        kerYY = DerivativeIntegralImage.kernelDerivYY(9 * s, kerYY);
        double lap = GIntegralImageOps.convolveSparse(ii, kerXX, x, y);
        lap += GIntegralImageOps.convolveSparse(ii, kerYY, x, y);
        return lap > 0;
    }

    /**
     * <p>
     * An improved SURF descriptor as presented in CenSurE paper.   The sub-regions now overlap and more
     * points are sampled in the sub-region to allow overlap.
     * </p>
     *
     * @param c_x      Center of the feature x-coordinate.
     * @param c_y      Center of the feature y-coordinate.
     * @param c        cosine of the orientation
     * @param s        sine of the orientation
     * @param scale    The scale of the wavelets.
     * @param features Where the features are written to.  Must be 4*(widthLargeGrid*widthSubRegion)^2 large.
     */
    public void features(double c_x, double c_y,
                         double c, double s,
                         double scale, SparseImageGradient gradient, double[] features) {
        int regionSize = 4 * 5;
        int totalSampleWidth = 5 + 4;
        int regionR = regionSize / 2;
        int regionEnd = regionSize - regionR;
        int sampleGridWidth = regionSize + 4;
        int regionIndex = 0;
        // when computing the pixel coordinates it is more precise to round to the nearest integer
        // since pixels are always positive round() is equivalent to adding 0.5 and then converting
        // to an int, which floors the variable.
        c_x += 0.5;
        c_y += 0.5;
        // first sample the whole grid at once to avoid sampling overlapping regions twice
        int index = 0;
        for (int rY = -regionR - 2; rY < regionEnd + 2; rY++) {
            double regionY = rY * scale;
            for (int rX = -regionR - 2; rX < regionEnd + 2; rX++, index++) {
                double regionX = rX * scale;
                // rotate the pixel along the feature's direction
                int pixelX = (int) (c_x + c * regionX - s * regionY);
                int pixelY = (int) (c_y + s * regionX + c * regionY);
                GradientValue g = gradient.compute(pixelX, pixelY);
                samplesX[index] = g.getX();
                samplesY[index] = g.getY();
            }
        }
        // compute descriptor using precomputed samples
        int indexGridWeight = 0;
        for (int rY = -regionR; rY < regionEnd; rY += 5) {
            for (int rX = -regionR; rX < regionEnd; rX += 5) {
                double sum_dx = 0, sum_dy = 0, sum_adx = 0, sum_ady = 0;
                // compute and sum up the response  inside the sub-region
                for (int i = 0; i < totalSampleWidth; i++) {
                    index = (rY + regionR + i) * sampleGridWidth + rX + regionR;
                    for (int j = 0; j < totalSampleWidth; j++, index++) {
                        double w = weightSub.get(j, i);
                        double dx = w * samplesX[index];
                        double dy = w * samplesY[index];
                        // align the gradient along image patch
                        // note the transform is transposed
                        double pdx = c * dx + s * dy;
                        double pdy = -s * dx + c * dy;
                        sum_dx += pdx;
                        sum_adx += Math.abs(pdx);
                        sum_dy += pdy;
                        sum_ady += Math.abs(pdy);
                    }
                }
                double w = weightGrid.data[indexGridWeight++];
                features[regionIndex++] = w * sum_dx;
                features[regionIndex++] = w * sum_adx;
                features[regionIndex++] = w * sum_dy;
                features[regionIndex++] = w * sum_ady;
            }
        }
    }
}
