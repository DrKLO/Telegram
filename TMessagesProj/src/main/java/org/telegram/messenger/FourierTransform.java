package org.telegram.messenger;

public abstract class FourierTransform {
    protected static final int LINAVG = 1;
    protected static final int LOGAVG = 2;
    protected static final int NOAVG = 3;

    protected static final float TWO_PI = (float) (2 * Math.PI);
    protected int timeSize;
    protected int sampleRate;
    protected float bandWidth;
    protected float[] real;
    protected float[] imag;
    protected float[] spectrum;
    protected float[] averages;
    protected int whichAverage;
    protected int octaves;
    protected int avgPerOctave;

    /**
     * Construct a FourierTransform that will analyze sample buffers that are
     * <code>ts</code> samples long and contain samples with a <code>sr</code>
     * sample rate.
     *
     * @param ts the length of the buffers that will be analyzed
     * @param sr the sample rate of the samples that will be analyzed
     */
    FourierTransform(int ts, float sr) {
        timeSize = ts;
        sampleRate = (int) sr;
        bandWidth = (2f / timeSize) * ((float) sampleRate / 2f);
        noAverages();
        allocateArrays();
    }

    // allocating real, imag, and spectrum are the responsibility of derived
    // classes
    // because the size of the arrays will depend on the implementation being used
    // this enforces that responsibility
    protected abstract void allocateArrays();

    protected void setComplex(float[] r, float[] i) {
        if (real.length != r.length && imag.length != i.length) {
        } else {
            System.arraycopy(r, 0, real, 0, r.length);
            System.arraycopy(i, 0, imag, 0, i.length);
        }
    }

    // fill the spectrum array with the amps of the data in real and imag
    // used so that this class can handle creating the average array
    // and also do spectrum shaping if necessary
    protected void fillSpectrum() {
        for (int i = 0; i < spectrum.length; i++) {
            spectrum[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }

        if (whichAverage == LINAVG) {
            int avgWidth = spectrum.length / averages.length;
            for (int i = 0; i < averages.length; i++) {
                float avg = 0;
                int j;
                for (j = 0; j < avgWidth; j++) {
                    int offset = j + i * avgWidth;
                    if (offset < spectrum.length) {
                        avg += spectrum[offset];
                    } else {
                        break;
                    }
                }
                avg /= j + 1;
                averages[i] = avg;
            }
        } else if (whichAverage == LOGAVG) {
            for (int i = 0; i < octaves; i++) {
                float lowFreq, hiFreq, freqStep;
                if (i == 0) {
                    lowFreq = 0;
                } else {
                    lowFreq = (sampleRate / 2) / (float) Math.pow(2, octaves - i);
                }
                hiFreq = (sampleRate / 2) / (float) Math.pow(2, octaves - i - 1);
                freqStep = (hiFreq - lowFreq) / avgPerOctave;
                float f = lowFreq;
                for (int j = 0; j < avgPerOctave; j++) {
                    int offset = j + i * avgPerOctave;
                    averages[offset] = calcAvg(f, f + freqStep);
                    f += freqStep;
                }
            }
        }
    }

    /**
     * Sets the object to not compute averages.
     */
    public void noAverages() {
        averages = new float[0];
        whichAverage = NOAVG;
    }

    /**
     * Sets the number of averages used when computing the spectrum and spaces the
     * averages in a linear manner. In other words, each average band will be
     * <code>specSize() / numAvg</code> bands wide.
     *
     * @param numAvg how many averages to compute
     */
    public void linAverages(int numAvg) {
        if (numAvg > spectrum.length / 2) {
            return;
        } else {
            averages = new float[numAvg];
        }
        whichAverage = LINAVG;
    }

    /**
     * Sets the number of averages used when computing the spectrum based on the
     * minimum bandwidth for an octave and the number of bands per octave. For
     * example, with audio that has a sample rate of 44100 Hz,
     * <code>logAverages(11, 1)</code> will result in 12 averages, each
     * corresponding to an octave, the first spanning 0 to 11 Hz. To ensure that
     * each octave band is a full octave, the number of octaves is computed by
     * dividing the Nyquist frequency by two, and then the result of that by two,
     * and so on. This means that the actual bandwidth of the lowest octave may
     * not be exactly the value specified.
     *
     * @param minBandwidth   the minimum bandwidth used for an octave
     * @param bandsPerOctave how many bands to split each octave into
     */
    public void logAverages(int minBandwidth, int bandsPerOctave) {
        float nyq = (float) sampleRate / 2f;
        octaves = 1;
        while ((nyq /= 2) > minBandwidth) {
            octaves++;
        }
        avgPerOctave = bandsPerOctave;
        averages = new float[octaves * bandsPerOctave];
        whichAverage = LOGAVG;
    }

    /**
     * Returns the length of the time domain signal expected by this transform.
     *
     * @return the length of the time domain signal expected by this transform
     */
    public int timeSize() {
        return timeSize;
    }

    /**
     * Returns the size of the spectrum created by this transform. In other words,
     * the number of frequency bands produced by this transform. This is typically
     * equal to <code>timeSize()/2 + 1</code>, see above for an explanation.
     *
     * @return the size of the spectrum
     */
    public int specSize() {
        return spectrum.length;
    }

    /**
     * Returns the amplitude of the requested frequency band.
     *
     * @param i the index of a frequency band
     * @return the amplitude of the requested frequency band
     */
    public float getBand(int i) {
        if (i < 0) i = 0;
        if (i > spectrum.length - 1) i = spectrum.length - 1;
        return spectrum[i];
    }

    /**
     * Returns the width of each frequency band in the spectrum (in Hz). It should
     * be noted that the bandwidth of the first and last frequency bands is half
     * as large as the value returned by this function.
     *
     * @return the width of each frequency band in Hz.
     */
    public float getBandWidth() {
        return bandWidth;
    }
    /**
     * Sets the amplitude of the <code>i<sup>th</sup></code> frequency band to
     * <code>a</code>. You can use this to shape the spectrum before using
     * <code>inverse()</code>.
     *
     * @param i the frequency band to modify
     * @param a the new amplitude
     */
    public abstract void setBand(int i, float a);

    /**
     * Scales the amplitude of the <code>i<sup>th</sup></code> frequency band
     * by <code>s</code>. You can use this to shape the spectrum before using
     * <code>inverse()</code>.
     *
     * @param i the frequency band to modify
     * @param s the scaling factor
     */
    public abstract void scaleBand(int i, float s);

    /**
     * Returns the index of the frequency band that contains the requested
     * frequency.
     *
     * @param freq the frequency you want the index for (in Hz)
     * @return the index of the frequency band that contains freq
     */
    public int freqToIndex(float freq) {
        // special case: freq is lower than the bandwidth of spectrum[0]
        if (freq < getBandWidth() / 2) return 0;
        // special case: freq is within the bandwidth of spectrum[spectrum.length - 1]
        if (freq > sampleRate / 2 - getBandWidth() / 2) return spectrum.length - 1;
        // all other cases
        float fraction = freq / (float) sampleRate;
        return Math.round(timeSize * fraction);
    }

    /**
     * Returns the middle frequency of the i<sup>th</sup> band.
     *
     * @param i the index of the band you want to middle frequency of
     */
    public float indexToFreq(int i) {
        float bw = getBandWidth();
        // special case: the width of the first bin is half that of the others.
        //               so the center frequency is a quarter of the way.
        if (i == 0) return bw * 0.25f;
        // special case: the width of the last bin is half that of the others.
        if (i == spectrum.length - 1) {
            float lastBinBeginFreq = (sampleRate / 2) - (bw / 2);
            float binHalfWidth = bw * 0.25f;
            return lastBinBeginFreq + binHalfWidth;
        }
        // the center frequency of the ith band is simply i*bw
        // because the first band is half the width of all others.
        // treating it as if it wasn't offsets us to the middle
        // of the band.
        return i * bw;
    }

    /**
     * Calculate the average amplitude of the frequency band bounded by
     * <code>lowFreq</code> and <code>hiFreq</code>, inclusive.
     *
     * @param lowFreq the lower bound of the band
     * @param hiFreq  the upper bound of the band
     * @return the average of all spectrum values within the bounds
     */
    public float calcAvg(float lowFreq, float hiFreq) {
        int lowBound = freqToIndex(lowFreq);
        int hiBound = freqToIndex(hiFreq);
        float avg = 0;
        for (int i = lowBound; i <= hiBound; i++) {
            avg += spectrum[i];
        }
        avg /= (hiBound - lowBound + 1);
        return avg;
    }

    /**
     * Get the Real part of the Complex representation of the spectrum.
     */
    public float[] getSpectrumReal() {
        return real;
    }

    /**
     * Get the Imaginary part of the Complex representation of the spectrum.
     */
    public float[] getSpectrumImaginary() {
        return imag;
    }


    /**
     * Performs a forward transform on <code>buffer</code>.
     *
     * @param buffer the buffer to analyze
     */
    public abstract void forward(float[] buffer);

    /**
     * Performs a forward transform on values in <code>buffer</code>.
     *
     * @param buffer  the buffer of samples
     * @param startAt the index to start at in the buffer. there must be at least timeSize() samples
     *                between the starting index and the end of the buffer. If there aren't, an
     *                error will be issued and the operation will not be performed.
     */
    public void forward(float[] buffer, int startAt) {
        if (buffer.length - startAt < timeSize) {
            return;
        }

        // copy the section of samples we want to analyze
        float[] section = new float[timeSize];
        System.arraycopy(buffer, startAt, section, 0, section.length);
        forward(section);
    }

    /**
     * Performs an inverse transform of the frequency spectrum and places the
     * result in <code>buffer</code>.
     *
     * @param buffer the buffer to place the result of the inverse transform in
     */
    public abstract void inverse(float[] buffer);


    /**
     * Performs an inverse transform of the frequency spectrum represented by
     * freqReal and freqImag and places the result in buffer.
     *
     * @param freqReal the real part of the frequency spectrum
     * @param freqImag the imaginary part the frequency spectrum
     * @param buffer   the buffer to place the inverse transform in
     */
    public void inverse(float[] freqReal, float[] freqImag, float[] buffer) {
        setComplex(freqReal, freqImag);
        inverse(buffer);
    }

    /**
     * FFT stands for Fast Fourier Transform. It is an efficient way to calculate the Complex
     * Discrete Fourier Transform. There is not much to say about this class other than the fact
     * that when you want to analyze the spectrum of an audio buffer you will almost always use
     * this class. One restriction of this class is that the audio buffers you want to analyzeV
     * must have a length that is a power of two. If you try to construct an FFT with a
     * <code>timeSize</code> that is not a power of two, an IllegalArgumentException will be
     * thrown.
     *
     * @author Damien Di Fede
     * @see FourierTransform
     * @see <a href="http://www.dspguide.com/ch12.htm">The Fast Fourier Transform</a>
     */
    public static class FFT extends FourierTransform {
        /**
         * Constructs an FFT that will accept sample buffers that are
         * <code>timeSize</code> long and have been recorded with a sample rate of
         * <code>sampleRate</code>. <code>timeSize</code> <em>must</em> be a
         * power of two. This will throw an exception if it is not.
         *
         * @param timeSize   the length of the sample buffers you will be analyzing
         * @param sampleRate the sample rate of the audio you will be analyzing
         */
        public FFT(int timeSize, float sampleRate) {
            super(timeSize, sampleRate);
            if ((timeSize & (timeSize - 1)) != 0)
                throw new IllegalArgumentException(
                        "FFT: timeSize must be a power of two.");
            buildReverseTable();
            buildTrigTables();
        }

        protected void allocateArrays() {
            spectrum = new float[timeSize / 2 + 1];
            real = new float[timeSize];
            imag = new float[timeSize];
        }

        public void scaleBand(int i, float s) {
            if (s < 0) {
                // Minim.error("Can't scale a frequency band by a negative value.");
                return;
            }

            real[i] *= s;
            imag[i] *= s;
            spectrum[i] *= s;

            if (i != 0 && i != timeSize / 2) {
                real[timeSize - i] = real[i];
                imag[timeSize - i] = -imag[i];
            }
        }

        public void setBand(int i, float a) {
            if (a < 0) {
                // Minim.error("Can't set a frequency band to a negative value.");
                return;
            }
            if (real[i] == 0 && imag[i] == 0) {
                real[i] = a;
                spectrum[i] = a;
            } else {
                real[i] /= spectrum[i];
                imag[i] /= spectrum[i];
                spectrum[i] = a;
                real[i] *= spectrum[i];
                imag[i] *= spectrum[i];
            }
            if (i != 0 && i != timeSize / 2) {
                real[timeSize - i] = real[i];
                imag[timeSize - i] = -imag[i];
            }
        }

        // performs an in-place fft on the data in the real and imag arrays
        // bit reversing is not necessary as the data will already be bit reversed
        private void fft() {
            for (int halfSize = 1; halfSize < real.length; halfSize *= 2) {
                // float k = -(float)Math.PI/halfSize;
                // phase shift step
                // float phaseShiftStepR = (float)Math.cos(k);
                // float phaseShiftStepI = (float)Math.sin(k);
                // using lookup table
                float phaseShiftStepR = cos(halfSize);
                float phaseShiftStepI = sin(halfSize);
                // current phase shift
                float currentPhaseShiftR = 1.0f;
                float currentPhaseShiftI = 0.0f;
                for (int fftStep = 0; fftStep < halfSize; fftStep++) {
                    for (int i = fftStep; i < real.length; i += 2 * halfSize) {
                        int off = i + halfSize;
                        float tr = (currentPhaseShiftR * real[off]) - (currentPhaseShiftI * imag[off]);
                        float ti = (currentPhaseShiftR * imag[off]) + (currentPhaseShiftI * real[off]);
                        real[off] = real[i] - tr;
                        imag[off] = imag[i] - ti;
                        real[i] += tr;
                        imag[i] += ti;
                    }
                    float tmpR = currentPhaseShiftR;
                    currentPhaseShiftR = (tmpR * phaseShiftStepR) - (currentPhaseShiftI * phaseShiftStepI);
                    currentPhaseShiftI = (tmpR * phaseShiftStepI) + (currentPhaseShiftI * phaseShiftStepR);
                }
            }
        }

        public void forward(float[] buffer) {
            if (buffer.length != timeSize) {
                //    Minim.error("FFT.forward: The length of the passed sample buffer must be equal to timeSize().");
                return;
            }
            //  doWindow(buffer);
            // copy samples to real/imag in bit-reversed order
            bitReverseSamples(buffer, 0);
            // perform the fft
            fft();
            // fill the spectrum buffer with amplitudes
            fillSpectrum();
        }

        @Override
        public void forward(float[] buffer, int startAt) {
            if (buffer.length - startAt < timeSize) {
   /*   Minim.error( "FourierTransform.forward: not enough samples in the buffer between " +
                   startAt + " and " + buffer.length + " to perform a transform."
                 );
   */
                return;
            }

            //   windowFunction.apply( buffer, startAt, timeSize );
            bitReverseSamples(buffer, startAt);
            fft();
            fillSpectrum();
        }

        /**
         * Performs a forward transform on the passed buffers.
         *
         * @param buffReal the real part of the time domain signal to transform
         * @param buffImag the imaginary part of the time domain signal to transform
         */
        public void forward(float[] buffReal, float[] buffImag) {
            if (buffReal.length != timeSize || buffImag.length != timeSize) {
                //  Minim.error("FFT.forward: The length of the passed buffers must be equal to timeSize().");
                return;
            }
            setComplex(buffReal, buffImag);
            bitReverseComplex();
            fft();
            fillSpectrum();
        }

        public void inverse(float[] buffer) {
            if (buffer.length > real.length) {
                //   Minim.error("FFT.inverse: the passed array's length must equal FFT.timeSize().");
                return;
            }
            // conjugate
            for (int i = 0; i < timeSize; i++) {
                imag[i] *= -1;
            }
            bitReverseComplex();
            fft();
            // copy the result in real into buffer, scaling as we do
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = real[i] / real.length;
            }
        }

        private int[] reverse;

        private void buildReverseTable() {
            int N = timeSize;
            reverse = new int[N];

            // set up the bit reversing table
            reverse[0] = 0;
            for (int limit = 1, bit = N / 2; limit < N; limit <<= 1, bit >>= 1)
                for (int i = 0; i < limit; i++)
                    reverse[i + limit] = reverse[i] + bit;
        }

        // copies the values in the samples array into the real array
        // in bit reversed order. the imag array is filled with zeros.
        private void bitReverseSamples(float[] samples, int startAt) {
            for (int i = 0; i < timeSize; ++i) {
                real[i] = samples[startAt + reverse[i]];
                imag[i] = 0.0f;
            }
        }

        // bit reverse real[] and imag[]
        private void bitReverseComplex() {
            float[] revReal = new float[real.length];
            float[] revImag = new float[imag.length];
            for (int i = 0; i < real.length; i++) {
                revReal[i] = real[reverse[i]];
                revImag[i] = imag[reverse[i]];
            }
            real = revReal;
            imag = revImag;
        }

        // lookup tables

        private float[] sinlookup;
        private float[] coslookup;

        private float sin(int i) {
            return sinlookup[i];
        }

        private float cos(int i) {
            return coslookup[i];
        }

        private void buildTrigTables() {
            int N = timeSize;
            sinlookup = new float[N];
            coslookup = new float[N];
            for (int i = 0; i < N; i++) {
                sinlookup[i] = (float) Math.sin(-(float) Math.PI / i);
                coslookup[i] = (float) Math.cos(-(float) Math.PI / i);
            }
        }
    }
}

