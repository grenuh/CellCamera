package demo;

import boofcv.abst.feature.detect.extract.NonMaxSuppression;
import boofcv.alg.feature.detect.extract.SelectNBestFeatures;
import boofcv.alg.feature.detect.intensity.GIntegralImageFeatureIntensity;
import boofcv.core.image.border.FactoryImageBorderAlgs;
import boofcv.core.image.border.ImageBorder_F32;
import boofcv.struct.QueueCorner;
import boofcv.struct.feature.ScalePoint;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import georegression.struct.point.Point2D_I16;

public class FastHessianFeatureDetectorGrenuh {
    // local sub-space
    protected GrayF32[] intensity;
    // List of found feature points
    FastQueueGrenuh foundPoints = new FastQueueGrenuh(10);
    // size of detected feature at the smallest scale
    int initialSize;
    // increment between kernel sizes as it goes up in scale
    int scaleStepSize;
    // the number of octaves it examines
    int numberOfOctaves;
    // local variables that are predeclared
    int[] sizes;
    // how often the image is sampled in the first octave
    // a value of 1 would mean every pixel is sampled
    int initialSampleRate;
    // finds features from 2D intensity image
    NonMaxSuppression extractor;
    // sorts feature by their intensity
    SelectNBestFeatures sortBest;
    // the maximum number of returned feature per scale
    int maxFeaturesPerScale;
    int spaceIndex = 0;
    QueueCorner foundFeatures = new QueueCorner(100);

    /**
     * <p>
     * Defines the feature detector by specifying the size of features.
     * </p>
     * <p>
     * Configuration for FH-9: initialSampleSize=1, initialSize=9, numberScalesPerOctave=4, numberOfOctaves=4<br>
     * Configuration for FH-15: initialSampleSize=1, initialSize=15, numberScalesPerOctave=5, numberOfOctaves=4<br>
     * * Note that FH-15 requires the image to be up sampled first. See [1] for details.
     * </p>
     *
     * @param extractor             Feature extractor used to find local maximums in 2D image.
     * @param maxFeaturesPerScale   Maximum number of features it can find per image scale.  If set &le; 0 then the all potential
     *                              features will be returned, which is how it is in the original paper.
     * @param initialSampleRate     How often pixels are sampled in the first octave.
     * @param initialSize           Size/width of the smallest feature/kernel in the lowest octave.
     * @param numberScalesPerOctave How many different feature sizes are considered in a single octave
     * @param numberOfOctaves       How many different octaves are considered.
     * @param scaleStepSize         Increment between kernel sizes as it goes up in scale.  Try 6
     */
    public FastHessianFeatureDetectorGrenuh(NonMaxSuppression extractor, int maxFeaturesPerScale,
                                            int initialSampleRate, int initialSize,
                                            int numberScalesPerOctave,
                                            int numberOfOctaves, int scaleStepSize) {
        this.extractor = extractor;
        if (maxFeaturesPerScale > 0) {
            this.maxFeaturesPerScale = maxFeaturesPerScale;
            sortBest = new SelectNBestFeatures(maxFeaturesPerScale);
        }
        this.initialSampleRate = initialSampleRate;
        this.initialSize = initialSize;
        this.numberOfOctaves = numberOfOctaves;
        this.scaleStepSize = scaleStepSize;
        sizes = new int[numberScalesPerOctave];
    }

    /**
     * Sees if the best score in the current layer is greater than all the scores in a 3x3 neighborhood
     * in another layer.
     */
    protected static boolean checkMax(ImageBorder_F32 inten, float bestScore, int c_x, int c_y) {
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
    public static float polyPeak(float lower, float middle, float upper) {
//		if( lower >= middle || upper >= middle )
//			throw new IllegalArgumentException("Crap");
        // only need two coefficients to compute the peak's location
        float a = 0.5f * lower - middle + 0.5f * upper;
        float b = 0.5f * upper - 0.5f * lower;
        return -b / (2.0f * a);
    }

    /**
     * Detect interest points inside of the image.
     *
     * @param integral Image transformed into an integral image.
     */
    public void detect(GrayF32 integral) {
        if (intensity == null) {
            intensity = new GrayF32[3];
            for (int i = 0; i < intensity.length; i++) {
                intensity[i] = new GrayF32(integral.width, integral.height);
            }
        }
        foundPoints.reset();
        // computes feature intensity every 'skip' pixels
        int skip = initialSampleRate;
        // increment between kernel sizes
        int sizeStep = scaleStepSize;
        // initial size of the kernel in the first octave
        int octaveSize = initialSize;
        for (int octave = 0; octave < numberOfOctaves; octave++) {
            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = octaveSize + i * sizeStep;
            }
            // if the maximum kernel size is larger than the image don't process
            // the image any more
            int maxSize = sizes[sizes.length - 1];
            if (maxSize > integral.width || maxSize > integral.height)
                break;
            // detect features inside of this octave
            detectOctave(integral, skip, sizes);
            skip += skip;
            octaveSize += sizeStep;
            sizeStep += sizeStep;
        }
        // todo save previously computed sizes for reuse in higher octaves and reuse it
    }

    /**
     * Computes feature intensities for all the specified feature sizes and finds features
     * inside of the middle feature sizes.
     *
     * @param integral    Integral image.
     * @param skip        Pixel skip factor
     * @param featureSize which feature sizes should be detected.
     */
    protected void detectOctave(GrayF32 integral, int skip, int... featureSize) {
        int w = integral.width / skip;
        int h = integral.height / skip;
        // resize the output intensity image taking in account subsampling
        for (int i = 0; i < intensity.length; i++) {
            intensity[i].reshape(w, h);
        }
        // compute feature intensity in each level
        for (int i = 0; i < featureSize.length; i++) {
            GIntegralImageFeatureIntensity.hessian(integral, skip, featureSize[i], intensity[spaceIndex]);
            spaceIndex++;
            if (spaceIndex >= 3)
                spaceIndex = 0;
            // find maximum in scale space
            if (i >= 2) {
                findLocalScaleSpaceMax(featureSize, i - 1, skip);
            }
        }
    }

    /**
     * Looks for features which are local maximums in the image and scale-space.
     *
     * @param size  Size of features in different scale-spaces.
     * @param level Which level in the scale-space
     * @param skip  How many pixels are skipped over.
     */
    private void findLocalScaleSpaceMax(int[] size, int level, int skip) {
        int index0 = spaceIndex;
        int index1 = (spaceIndex + 1) % 3;
        int index2 = (spaceIndex + 2) % 3;
        ImageBorder_F32 inten0 = (ImageBorder_F32) FactoryImageBorderAlgs.value(intensity[index0], 0);
        GrayF32 inten1 = intensity[index1];
        ImageBorder_F32 inten2 = (ImageBorder_F32) FactoryImageBorderAlgs.value(intensity[index2], 0);
        // find local maximums in image 2D space.  Borders need to be ignored since
        // false positives are found around them as an artifact of pixels outside being
        // treated as being zero.
        foundFeatures.reset();
        extractor.setIgnoreBorder(size[level] / (2 * skip));
        extractor.process(intensity[index1], null, null, null, foundFeatures);
        // Can't consider feature which are right up against the border since they might not be a true local
        // maximum when you consider the features on the other side of the ignore border
        int ignoreRadius = extractor.getIgnoreBorder() + extractor.getSearchRadius();
        int ignoreWidth = intensity[index1].width - ignoreRadius;
        int ignoreHeight = intensity[index1].height - ignoreRadius;
        // number of features which can be added
        int numberRemaining;
        // if configured to do so, only select the features with the highest intensity
        QueueCorner features;
        if (sortBest != null) {
            sortBest.process(intensity[index1], foundFeatures, true);
            features = sortBest.getBestCorners();
            numberRemaining = maxFeaturesPerScale;
        } else {
            features = foundFeatures;
            numberRemaining = Integer.MAX_VALUE;
        }
        int levelSize = size[level];
        int sizeStep = levelSize - size[level - 1];
        // see if these local maximums are also a maximum in scale-space
        for (int i = 0; i < features.size && numberRemaining > 0; i++) {
            Point2D_I16 f = features.get(i);
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
                float interpS = levelSize + peakS * sizeStep;
                double scale = 1.2 * interpS / 9.0;
                foundPoints.grow().set(interpX, interpY, scale);
                numberRemaining--;
            }
        }
    }
}