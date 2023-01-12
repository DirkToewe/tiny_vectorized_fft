package tiny_vectorized_fft;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;

import static java.lang.Integer.numberOfTrailingZeros;
import static java.lang.Math.*;
import static java.lang.reflect.Modifier.*;
import static java.util.Arrays.stream;


public class TinyFFT
{
  private static final VectorSpecies<Float> VSPEC_F32 = FloatVector.SPECIES_PREFERRED;
  private static final VectorSpecies<Byte>  VSPEC_I8  = stream(ByteVector.class.getDeclaredFields())
    .<VectorSpecies<Byte>>mapMulti( (f, output) -> {
      int m=f.getModifiers();
      if( isStatic(m) && isPublic(m) && isFinal(m) && f.getType().isAssignableFrom(VectorSpecies.class) )
        try {
          @SuppressWarnings("unchecked")
          var spec = (VectorSpecies<Byte>) f.get(null);
          if( spec.length() == VSPEC_F32.length() )
            output.accept(spec);
        } catch (IllegalAccessException e) {
          throw new ExceptionInInitializerError(e);
        }
    }).findAny().orElseThrow( () ->
      new ExceptionInInitializerError("Could not find VectorSpecies<Short> with the same number of lanes as DoubleVector.SPECIES_PREFERRED.")
    );

  private static boolean hasOneBitOrNone( int n ) { return (n & n-1) == 0; }

  public static final int len() {
    return VSPEC_F32.length();
  }

  /** Pre-computed primitive roots of unity. */
  private static final float[] PROUS = new float[2*len()];
  static {
    int vLen = VSPEC_F32.length();
    for( int i=0; i < vLen; i++ ) {
      var x = -2*i*PI / vLen;
      PROUS[2*i  ] = (float) cos(x);
      PROUS[2*i+1] = (float) sin(x);
    }
  }

  private static final VectorShuffle<Float>[] INDICES;
  private static final FloatVector[] FACTORS;
  static {
    int vLen = VSPEC_F32.length(),
       nLvls = numberOfTrailingZeros(vLen);

    var shuffle = new   int[vLen*2];
    var roots   = new float[vLen*2];
    INDICES = new VectorShuffle[nLvls*2];
    FACTORS = new FloatVector[nLvls*2];

    for( int k=0, b=1; b < vLen; b<<=1, k+=2 )
    {
      for( int i = 0; i < vLen; i += b )
      {
        var w0 = -PI*2 / (b << 1);
        int wi = 0;
        for( int end = i | b; i < end; i++ ) {
          int j = i | b;
          double x = w0*wi++;
          roots[j     ] = - ( roots[i     ] = (float) cos(x) );
          roots[j+vLen] = - ( roots[i+vLen] = (float) sin(x) );
          shuffle[i     ] = shuffle[j     ] = i;
          shuffle[i+vLen] = shuffle[j+vLen] = j;
        }
      }
      INDICES[k  ] = VectorShuffle.fromArray(VSPEC_F32, shuffle, 0);
      INDICES[k+1] = VectorShuffle.fromArray(VSPEC_F32, shuffle, vLen);
      FACTORS[k  ] =   FloatVector.fromArray(VSPEC_F32, roots, 0);
      FACTORS[k+1] =   FloatVector.fromArray(VSPEC_F32, roots, vLen);
    }
  }

  public static void fftV1( float[] re, float[] im )
  {
    int n = re.length;
    if( n!= im.length || n != len() || ! hasOneBitOrNone(n) )
      throw new IllegalArgumentException();

    for( int dr=n, b=1; b < n; b<<=1, dr>>=1 )
    {
      for( int i = 0; i < n; i += b )
      {
        for( int root=0; root < b; root++, i++ )
        {
          int j = i | b;
          float
            eve_re = re[i],
            eve_im = im[i],
            odd_re = re[j],
            odd_im = im[j],
            w_re = PROUS[root*dr],
            w_im = PROUS[root*dr+1],
          // odd *= w
          tmp_re = fma(odd_re, w_re, -odd_im * w_im);
          odd_im = fma(odd_re, w_im,  odd_im * w_re);
          odd_re = tmp_re;
          re[i] = eve_re + odd_re;
          im[i] = eve_im + odd_im;
          re[j] = eve_re - odd_re;
          im[j] = eve_im - odd_im;
        }
      }
    }
  }

  public static void fftV2( float[] real, float[] imag )
  {
    FloatVector
      im = FloatVector.fromArray(VSPEC_F32, imag, 0),
      re = FloatVector.fromArray(VSPEC_F32, real, 0);
    for(int lvl = 0; lvl < INDICES.length; lvl+=2 )
    {
      VectorShuffle<Float>
        eve = INDICES[lvl],
        odd = INDICES[lvl+1];
      FloatVector
        eve_re = re.rearrange(eve),
        odd_re = re.rearrange(odd),
        eve_im = im.rearrange(eve),
        odd_im = im.rearrange(odd),
        w_re = FACTORS[lvl],
        w_im = FACTORS[lvl+1];
      im = odd_im.fma(w_re,       odd_re.fma(w_im, eve_im));
      re = odd_im.fma(w_im.neg(), odd_re.fma(w_re, eve_re));
    }
    im.intoArray(imag, 0);
    re.intoArray(real, 0);
  }

  public static void fftV3( float[] real, float[] imag )
  {
    FloatVector
      im = FloatVector.fromArray(VSPEC_F32, imag, 0),
      re = FloatVector.fromArray(VSPEC_F32, real, 0);
    for(int lvl = 0; lvl < INDICES.length; lvl+=2 )
    {
      VectorShuffle<Float>
        eve = INDICES[lvl],
        odd = ( (ByteVector) eve.cast(VSPEC_I8).toVector() ).or( (byte) (1<<lvl/2) ).toShuffle().cast(VSPEC_F32);
      FloatVector
        eve_re = re.rearrange(eve),
        odd_re = re.rearrange(odd),
        eve_im = im.rearrange(eve),
        odd_im = im.rearrange(odd),
        w_re = FACTORS[lvl],
        w_im = FACTORS[lvl+1];
      im = odd_im.fma(w_re,       odd_re.fma(w_im, eve_im));
      re = odd_im.fma(w_im.neg(), odd_re.fma(w_re, eve_re));
    }
    im.intoArray(imag, 0);
    re.intoArray(real, 0);
  }
}
