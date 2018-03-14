package demo;

import boofcv.struct.feature.ScalePoint;

import java.io.Serializable;
import java.lang.reflect.Array;

public class FastQueueGrenuh implements Serializable {
    ScalePoint[] data;
    int size;
    //FastQueueListGrenuh<T> list;

    FastQueueGrenuh(int initialMaxSize) {
        this.init(initialMaxSize);
    }

    void init(int initialMaxSize) {
        this.size = 0;
        this.data = (ScalePoint[]) Array.newInstance(ScalePoint.class, initialMaxSize);
        for (int i = 0; i < initialMaxSize; ++i) {
            try {
                this.data[i] = this.createInstance();
            } catch (RuntimeException var6) {
                throw new RuntimeException("declareInstances is true, but createInstance() can't create a new instance.  Maybe override createInstance()?");
            }
        }
    }

    public void reset() {
        this.size = 0;
    }

    public ScalePoint grow() {
        if (this.size < this.data.length) {
            return this.data[this.size++];
        } else {
            this.growArray((this.data.length + 1) * 2);
            return this.data[this.size++];
        }
    }

    public void growArray(int length) {
        if (this.data.length < length) {
            ScalePoint[] data = (ScalePoint[]) Array.newInstance(ScalePoint.class, length);
            System.arraycopy(this.data, 0, data, 0, this.data.length);
            for (int i = this.data.length; i < length; ++i) {
                data[i] = this.createInstance();
            }
            this.data = data;
        }
    }

    protected ScalePoint createInstance() {
        try {
            return ScalePoint.class.newInstance();
        } catch (InstantiationException | IllegalAccessException var2) {
            throw new RuntimeException(var2);
        }
    }

    public int getSize() {
        return this.size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
