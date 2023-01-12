package tiny_vectorized_fft;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.String.format;


//  REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
//  why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
//  experiments, perform baseline and negative tests that provide experimental control, make sure
//  the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
//  Do not assume the numbers tell you what you want them to tell.
//
//  NOTE: Current JVM experimentally supports Compiler Blackholes, and they are in use. Please exercise
//  extra caution when trusting the results, look into the generated code to check the benchmark still
//  works, and factor in a small probability of new VM bugs. Additionally, while comparisons between
//  different JVMs are already problematic, the performance difference caused by different Blackhole
//  modes can be very significant. Please make sure you use the consistent Blackhole mode for comparisons.
//
//  Benchmark                Mode  Cnt    Score   Error  Units
//  TinyFFT_Benchmark.fftV1  avgt   32   60.093 ± 0.929  ns/op
//  TinyFFT_Benchmark.fftV2  avgt   32   36.748 ± 0.122  ns/op
//  TinyFFT_Benchmark.fftV3  avgt   32  129.063 ± 0.830  ns/op

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 8, time = 1/*sec*/)
@Measurement(iterations = 8, time = 1/*sec*/)
@Fork( value=4, jvmArgsAppend={"--add-modules=jdk.incubator.vector"} )
@State(Scope.Benchmark)
public class TinyFFT_Benchmark
{
  public static void main( String... args ) throws RunnerException {
    runTests();

    var opt = new OptionsBuilder()
      .include( TinyFFT_Benchmark.class.getCanonicalName() )
      .build();

    new Runner(opt).run();
  }

  SplittableRandom rng = new SplittableRandom();
  float[]
    re = new float[TinyFFT.len()],
    im = new float[TinyFFT.len()];

  @Setup(Level.Invocation)
  public void randomize() {
    randomize(rng, re);
    randomize(rng, im);
  }

  @Benchmark public void fftV1( Blackhole bh ) { TinyFFT.fftV1(re,im); bh.consume(re); bh.consume(im); }
  @Benchmark public void fftV2( Blackhole bh ) { TinyFFT.fftV2(re,im); bh.consume(re); bh.consume(im); }
  @Benchmark public void fftV3( Blackhole bh ) { TinyFFT.fftV3(re,im); bh.consume(re); bh.consume(im); }

  private static void randomize( RandomGenerator rng, float[] x ) {
    for( int i=0; i < x.length; i++ )
      x[i] = (float) rng.nextDouble(-1e2, +1e2);
  }

  private static void runTests() {
    System.out.print("Running tests...");
    var rng = new SplittableRandom();
    for( int run=0; run++ < 1_000_000; )
    {
      float[]
        re = new float[TinyFFT.len()],
        im = new float[TinyFFT.len()];
      randomize(rng, re);
      randomize(rng, im);

      float[]
        re1 = re.clone(),
        im1 = im.clone();
      TinyFFT.fftV1(re1, im1);

      float[]
        re2 = re.clone(),
        im2 = im.clone();
      TinyFFT.fftV2(re2, im2);

      assertAllClose(re1,re2);
      assertAllClose(im1,im2);

      float[]
        re3 = re.clone(),
        im3 = im.clone();
      TinyFFT.fftV3(re3, im3);

      assertAllClose(re1,re3);
      assertAllClose(im1,im3);
    }
    System.out.println(" done.");
  }

  private static void assertAllClose( float[] a, float[] b ) {
    double
      atol = 1e-4,
      rtol = 1e-3;
    if( a == b )
      return;
    Consumer<String> fail = msg -> {
      msg = format(
        """
          reason: %s
          actual: %s
          expect: %S
        """,
        msg,
        Arrays.toString(a),
        Arrays.toString(b)
      );
      throw new AssertionError(msg);
    };
    if( a.length != b.length )
      fail.accept("actual.length != expect.length");
    for( int i=0; i < a.length; i++ ) {
      var x = a[i];
      var y = b[i];
      double tol = atol + rtol * max(abs(x), abs(y));
      if( ! Double.isFinite(tol) || ! (abs(x-y) <= tol) )
        fail.accept( format("actual[%1$d] = %2$s != %3$s = expect[%1$d]", i, x, y) );
    }
  }
}
